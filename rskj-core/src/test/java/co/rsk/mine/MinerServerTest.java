/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.mine;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.ethereum.TestUtils;
import org.ethereum.core.Account;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Genesis;
import org.ethereum.core.ImportResult;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionPool;
import org.ethereum.db.BlockStore;
import org.ethereum.facade.Ethereum;
import org.ethereum.facade.EthereumImpl;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.GasPriceTracker;
import org.ethereum.util.BuildInfo;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.ethereum.util.RskTestFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.mockito.Mockito;

import co.rsk.TestHelpers.Tx;
import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.VerificationException;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.config.ConfigUtils;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.BlockDifficulty;
import co.rsk.core.Coin;
import co.rsk.core.DifficultyCalculator;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.core.bc.BlockResult;
import co.rsk.core.bc.MiningMainchainView;
import co.rsk.core.bc.TransactionPoolImpl;
import co.rsk.crypto.Keccak256;
import co.rsk.db.RepositoryLocator;
import co.rsk.net.BlockProcessor;
import co.rsk.net.NodeBlockProcessor;
import co.rsk.net.handler.quota.TxQuotaChecker;
import co.rsk.remasc.RemascTransaction;
import co.rsk.test.World;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import co.rsk.util.HexUtils;
import co.rsk.validators.BlockUnclesValidationRule;
import co.rsk.validators.ProofOfWorkRule;
import org.ethereum.TestUtils;
import org.ethereum.core.*;
import org.ethereum.db.BlockStore;
import org.ethereum.db.MutableRepository;
import org.ethereum.facade.EthereumImpl;
import org.ethereum.util.BuildInfo;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.ethereum.util.RskTestFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


/**
 * Created by adrian.eidelman on 3/16/2016.
 */
public abstract class MinerServerTest {

    private TestSystemProperties config;
    private DifficultyCalculator difficultyCalculator;
    private MiningMainchainView miningMainchainView;
    private Blockchain standardBlockchain;
    private RepositoryLocator repositoryLocator;
    private BlockStore blockStore;
    private TransactionPool transactionPool;
    private BlockFactory blockFactory;
    private BlockExecutor blockExecutor;
    private MinimumGasPriceCalculator minimumGasPriceCalculator;
    private MinerUtils minerUtils;
    private ReceivedTxSignatureCache signatureCache;
    private Repository repository;
    private CompositeEthereumListener compositeEthereumListener;
    private RskTestFactory rskTestContext;

    protected void setUp(TestSystemProperties config) {
        this.config = config;
        this.difficultyCalculator = new DifficultyCalculator(config.getActivationConfig(), config.getNetworkConstants());
        rskTestContext = new RskTestFactory(config) {
            @Override
            protected RepositoryLocator buildRepositoryLocator() {
                return Mockito.spy(super.buildRepositoryLocator());
            }
        };
        miningMainchainView = rskTestContext.getMiningMainchainView();
        repositoryLocator = rskTestContext.getRepositoryLocator();
        blockStore = rskTestContext.getBlockStore();
        standardBlockchain = rskTestContext.getBlockchain();
        repository = repositoryLocator.startTrackingAt(standardBlockchain.getBestBlock().getHeader());
        signatureCache = spy(rskTestContext.getReceivedTxSignatureCache());
        compositeEthereumListener = rskTestContext.getCompositeEthereumListener();
        transactionPool = new TransactionPoolImpl(
                rskTestContext.getRskSystemProperties(),
                repositoryLocator,
                rskTestContext.getBlockStore(),
                rskTestContext.getBlockFactory(),
                compositeEthereumListener,
                rskTestContext.getTransactionExecutorFactory(),
                signatureCache,
                10,
                100,
                Mockito.mock(TxQuotaChecker.class),
                Mockito.mock(GasPriceTracker.class));

        transactionPool.processBest(standardBlockchain.getBestBlock());

        blockFactory = rskTestContext.getBlockFactory();
        blockExecutor = rskTestContext.getBlockExecutor();
        minimumGasPriceCalculator = new MinimumGasPriceCalculator(Coin.ZERO);
        minerUtils = new MinerUtils();
    }

    @Test
    void buildBlockToMineCheckThatLastTransactionIsForREMASC() {
        Transaction tx1 = Tx.create(config, 0, 21000, 100, 0, 0, 0);
        byte[] s1 = new byte[32];
        s1[0] = 0;
        when(tx1.getHash()).thenReturn(new Keccak256(s1));
        when(tx1.getEncoded()).thenReturn(new byte[32]);

        Repository repository = repositoryLocator.startTrackingAt(blockStore.getBestBlock().getHeader());
        MutableRepository track = mock(MutableRepository.class);
        BlockTxSignatureCache blockTxSignatureCache = mock(BlockTxSignatureCache.class);
        Mockito.doReturn(repository.getRoot()).when(track).getRoot();
        Mockito.doReturn(repository.getTrie()).when(track).getTrie();
        when(track.getNonce(tx1.getSender())).thenReturn(BigInteger.ZERO);
        when(track.getNonce(tx1.getSender(blockTxSignatureCache))).thenReturn(BigInteger.ZERO);
        when(track.getNonce(RemascTransaction.REMASC_ADDRESS)).thenReturn(BigInteger.ZERO);
        when(track.getBalance(tx1.getSender())).thenReturn(Coin.valueOf(4200000L));
        when(track.getBalance(RemascTransaction.REMASC_ADDRESS)).thenReturn(Coin.valueOf(4200000L));
        // todo(fedejinich) this test was relying in startTracking() method to create a new repository,
        //  but after this PR the BlockExecutor:268 creates a new tracked repository with trackedRepositoryAt().
        //  THIS SHOULD BE REMOVED
//         Mockito.doReturn(track).when(repositoryLocator).startTrackingAt(any());
        Mockito.doReturn(track).when(repositoryLocator).trackedRepositoryAt(any());
        Mockito.doReturn(track).when(track).startTracking();

        List<Transaction> txs = new ArrayList<>(Collections.singletonList(tx1));

        TransactionPool localTransactionPool = mock(TransactionPool.class);
        when(localTransactionPool.getPendingTransactions()).thenReturn(txs);

        BlockUnclesValidationRule unclesValidationRule = mock(BlockUnclesValidationRule.class);
        when(unclesValidationRule.isValid(any())).thenReturn(true);
        MinerClock clock = new MinerClock(true, Clock.systemUTC());
        MinerServer minerServer = makeMinerServer(mock(Ethereum.class), unclesValidationRule, clock, localTransactionPool);

        minerServer.buildBlockToMine(false);
        Block blockAtHeightOne = minerServer.getBlocksWaitingForPoW().entrySet().iterator().next().getValue();

        List<Transaction> blockTransactions = blockAtHeightOne.getTransactionsList();
        assertNotNull(blockTransactions);
        assertEquals(2, blockTransactions.size());

        Transaction remascTransaction = blockTransactions.get(1);
        assertThat(remascTransaction, instanceOf(RemascTransaction.class));
    }

    @Test
    void submitBitcoinBlockTwoTags() {
        EthereumImpl ethereumImpl = mock(EthereumImpl.class);
        when(ethereumImpl.addNewMinedBlock(any())).thenReturn(ImportResult.IMPORTED_BEST);

        BlockUnclesValidationRule unclesValidationRule = mock(BlockUnclesValidationRule.class);
        when(unclesValidationRule.isValid(any())).thenReturn(true);
        MinerClock clock = new MinerClock(true, Clock.systemUTC());
        MinerServer minerServer = makeMinerServer(ethereumImpl, unclesValidationRule, clock);
        try {
        byte[] extraData = ByteBuffer.allocate(4).putInt(1).array();
        minerServer.setExtraData(extraData);
        minerServer.start();
        MinerWork work = minerServer.getWork();

        extraData = ByteBuffer.allocate(4).putInt(2).array();
        minerServer.setExtraData(extraData);
        minerServer.buildBlockToMine(false);
        MinerWork work2 = minerServer.getWork(); // only the tag is used
        Assertions.assertNotEquals(work2.getBlockHashForMergedMining(),work.getBlockHashForMergedMining());

        BtcBlock bitcoinMergedMiningBlock = getMergedMiningBlockWithTwoTags(work,work2);

        findNonce(work, bitcoinMergedMiningBlock);
        SubmitBlockResult result;
        result = minerServer.submitBitcoinBlock(work2.getBlockHashForMergedMining(), bitcoinMergedMiningBlock,true);


        Assertions.assertEquals("OK", result.getStatus());
        Assertions.assertNotNull(result.getBlockInfo());
        Assertions.assertEquals("0x1", result.getBlockInfo().getBlockIncludedHeight());
        Assertions.assertEquals("0x494d504f525445445f42455354", result.getBlockInfo().getBlockImportedResult());

        // Submit again the save PoW for a different header
        result = minerServer.submitBitcoinBlock(work.getBlockHashForMergedMining(), bitcoinMergedMiningBlock,false);

        Assertions.assertEquals("ERROR", result.getStatus());

        Mockito.verify(ethereumImpl, Mockito.times(1)).addNewMinedBlock(any());
        } finally {
            minerServer.stop();
        }
    }

    @Test
    void submitBitcoinBlock() {
        EthereumImpl ethereumImpl = mock(EthereumImpl.class);
        when(ethereumImpl.addNewMinedBlock(any())).thenReturn(ImportResult.IMPORTED_BEST);

        BlockUnclesValidationRule unclesValidationRule = mock(BlockUnclesValidationRule.class);
        when(unclesValidationRule.isValid(any())).thenReturn(true);
        MinerClock clock = new MinerClock(true, Clock.systemUTC());
        MinerServer minerServer = makeMinerServer(ethereumImpl, unclesValidationRule, clock);
        try {
            minerServer.start();
            MinerWork work = minerServer.getWork();

            BtcBlock bitcoinMergedMiningBlock = getMergedMiningBlockWithOnlyCoinbase(work);

            findNonce(work, bitcoinMergedMiningBlock);

            SubmitBlockResult result = minerServer.submitBitcoinBlock(work.getBlockHashForMergedMining(), bitcoinMergedMiningBlock);

            Assertions.assertEquals("OK", result.getStatus());
            Assertions.assertNotNull(result.getBlockInfo());
            Assertions.assertEquals("0x1", result.getBlockInfo().getBlockIncludedHeight());
            Assertions.assertEquals("0x494d504f525445445f42455354", result.getBlockInfo().getBlockImportedResult());

            Mockito.verify(ethereumImpl, Mockito.times(1)).addNewMinedBlock(any());
        } finally {
            minerServer.stop();
        }
    }

    @Test
    void submitBitcoinBlockPartialMerkleWhenBlockIsEmpty() {
        EthereumImpl ethereumImpl = mock(EthereumImpl.class);
        when(ethereumImpl.addNewMinedBlock(any())).thenReturn(ImportResult.IMPORTED_BEST);

        BlockUnclesValidationRule unclesValidationRule = mock(BlockUnclesValidationRule.class);
        when(unclesValidationRule.isValid(any())).thenReturn(true);
        MinerClock clock = new MinerClock(true, Clock.systemUTC());
        MinerServer minerServer = makeMinerServer(ethereumImpl, unclesValidationRule, clock);
        try {
            minerServer.start();
            MinerWork work = minerServer.getWork();

            BtcBlock bitcoinMergedMiningBlock = getMergedMiningBlockWithOnlyCoinbase(work);

            findNonce(work, bitcoinMergedMiningBlock);

            //noinspection ConstantConditions
            BtcTransaction coinbase = bitcoinMergedMiningBlock.getTransactions().get(0);
            List<String> coinbaseReversedHash = Collections.singletonList(Sha256Hash.wrap(coinbase.getHash().getReversedBytes()).toString());
            SubmitBlockResult result = minerServer.submitBitcoinBlockPartialMerkle(work.getBlockHashForMergedMining(), bitcoinMergedMiningBlock, coinbase, coinbaseReversedHash, 1);

            Assertions.assertEquals("OK", result.getStatus());
            Assertions.assertNotNull(result.getBlockInfo());
            Assertions.assertEquals("0x1", result.getBlockInfo().getBlockIncludedHeight());
            Assertions.assertEquals("0x494d504f525445445f42455354", result.getBlockInfo().getBlockImportedResult());

            Mockito.verify(ethereumImpl, Mockito.times(1)).addNewMinedBlock(any());
        } finally {
            minerServer.stop();
        }
    }

    @Test
    void submitBitcoinBlockPartialMerkleWhenBlockHasTransactions() {
        EthereumImpl ethereumImpl = mock(EthereumImpl.class);
        when(ethereumImpl.addNewMinedBlock(any())).thenReturn(ImportResult.IMPORTED_BEST);

        BlockUnclesValidationRule unclesValidationRule = mock(BlockUnclesValidationRule.class);
        when(unclesValidationRule.isValid(any())).thenReturn(true);
        MinerClock clock = new MinerClock(true, Clock.systemUTC());
        MinerServer minerServer = makeMinerServer(ethereumImpl, unclesValidationRule, clock);
        try {
            minerServer.start();
            MinerWork work = minerServer.getWork();

            BtcTransaction otherTx = mock(BtcTransaction.class);
            Sha256Hash otherTxHash = Sha256Hash.wrap("aaaabbbbccccddddaaaabbbbccccddddaaaabbbbccccddddaaaabbbbccccdddd");
            when(otherTx.getHash()).thenReturn(otherTxHash);
            when(otherTx.getHashAsString()).thenReturn(otherTxHash.toString());

            BtcBlock bitcoinMergedMiningBlock = getMergedMiningBlock(work, Collections.singletonList(otherTx));

            findNonce(work, bitcoinMergedMiningBlock);

            //noinspection ConstantConditions
            BtcTransaction coinbase = bitcoinMergedMiningBlock.getTransactions().get(0);
            String coinbaseReversedHash = Sha256Hash.wrap(coinbase.getHash().getReversedBytes()).toString();
            String otherTxHashReversed = Sha256Hash.wrap(otherTxHash.getReversedBytes()).toString();
            List<String> merkleHashes = Arrays.asList(coinbaseReversedHash, otherTxHashReversed);
            SubmitBlockResult result = minerServer.submitBitcoinBlockPartialMerkle(work.getBlockHashForMergedMining(), bitcoinMergedMiningBlock, coinbase, merkleHashes, 2);

            Assertions.assertEquals("OK", result.getStatus());
            Assertions.assertNotNull(result.getBlockInfo());
            Assertions.assertEquals("0x1", result.getBlockInfo().getBlockIncludedHeight());
            Assertions.assertEquals("0x494d504f525445445f42455354", result.getBlockInfo().getBlockImportedResult());

            Mockito.verify(ethereumImpl, Mockito.times(1)).addNewMinedBlock(any());
        } finally {
            minerServer.stop();
        }
    }

    @Test
    void submitBitcoinBlockTransactionsWhenBlockIsEmpty() {
        EthereumImpl ethereumImpl = mock(EthereumImpl.class);
        when(ethereumImpl.addNewMinedBlock(any())).thenReturn(ImportResult.IMPORTED_BEST);

        BlockUnclesValidationRule unclesValidationRule = mock(BlockUnclesValidationRule.class);
        when(unclesValidationRule.isValid(any())).thenReturn(true);
        MinerClock clock = new MinerClock(true, Clock.systemUTC());
        MinerServer minerServer = makeMinerServer(ethereumImpl, unclesValidationRule, clock);
        try {
            minerServer.start();
            MinerWork work = minerServer.getWork();

            BtcBlock bitcoinMergedMiningBlock = getMergedMiningBlockWithOnlyCoinbase(work);

            findNonce(work, bitcoinMergedMiningBlock);

            //noinspection ConstantConditions
            BtcTransaction coinbase = bitcoinMergedMiningBlock.getTransactions().get(0);
            SubmitBlockResult result = minerServer.submitBitcoinBlockTransactions(work.getBlockHashForMergedMining(), bitcoinMergedMiningBlock, coinbase, Collections.singletonList(coinbase.getHashAsString()));

            Assertions.assertEquals("OK", result.getStatus());
            Assertions.assertNotNull(result.getBlockInfo());
            Assertions.assertEquals("0x1", result.getBlockInfo().getBlockIncludedHeight());
            Assertions.assertEquals("0x494d504f525445445f42455354", result.getBlockInfo().getBlockImportedResult());

            Mockito.verify(ethereumImpl, Mockito.times(1)).addNewMinedBlock(any());
        } finally {
            minerServer.stop();
        }
    }

    @Test
    void submitBitcoinBlockTransactionsWhenBlockHasTransactions() {
        EthereumImpl ethereumImpl = mock(EthereumImpl.class);
        when(ethereumImpl.addNewMinedBlock(any())).thenReturn(ImportResult.IMPORTED_BEST);

        BlockUnclesValidationRule unclesValidationRule = mock(BlockUnclesValidationRule.class);
        when(unclesValidationRule.isValid(any())).thenReturn(true);
        MinerClock clock = new MinerClock(true, Clock.systemUTC());
        MinerServer minerServer = makeMinerServer(ethereumImpl, unclesValidationRule, clock);
        try {
            minerServer.start();
            MinerWork work = minerServer.getWork();

            BtcTransaction otherTx = mock(BtcTransaction.class);
            Sha256Hash otherTxHash = Sha256Hash.wrap("aaaabbbbccccddddaaaabbbbccccddddaaaabbbbccccddddaaaabbbbccccdddd");
            when(otherTx.getHash()).thenReturn(otherTxHash);
            when(otherTx.getHashAsString()).thenReturn(otherTxHash.toString());

            BtcBlock bitcoinMergedMiningBlock = getMergedMiningBlock(work, Collections.singletonList(otherTx));

            findNonce(work, bitcoinMergedMiningBlock);

            //noinspection ConstantConditions
            BtcTransaction coinbase = bitcoinMergedMiningBlock.getTransactions().get(0);
            List<String> txs = Arrays.asList(coinbase.getHashAsString(), otherTxHash.toString());
            SubmitBlockResult result = minerServer.submitBitcoinBlockTransactions(work.getBlockHashForMergedMining(), bitcoinMergedMiningBlock, coinbase, txs);

            Assertions.assertEquals("OK", result.getStatus());
            Assertions.assertNotNull(result.getBlockInfo());
            Assertions.assertEquals("0x1", result.getBlockInfo().getBlockIncludedHeight());
            Assertions.assertEquals("0x494d504f525445445f42455354", result.getBlockInfo().getBlockImportedResult());

            Mockito.verify(ethereumImpl, Mockito.times(1)).addNewMinedBlock(any());
        } finally {
            minerServer.stop();
        }
    }

    @Test
    void workWithNoTransactionsZeroFees() {
        EthereumImpl ethereumImpl = mock(EthereumImpl.class);

        BlockUnclesValidationRule unclesValidationRule = mock(BlockUnclesValidationRule.class);
        when(unclesValidationRule.isValid(any())).thenReturn(true);
        MinerClock clock = new MinerClock(true, Clock.systemUTC());
        MinerServer minerServer = makeMinerServer(ethereumImpl, unclesValidationRule, clock, transactionPool);

        minerServer.start();
        try {
        MinerWork work = minerServer.getWork();

        assertEquals("0", work.getFeesPaidToMiner());
        } finally {
            minerServer.stop();
        }
    }

    @Test
    void initialWorkTurnsNotifyFlagOn() {
        EthereumImpl ethereumImpl = mock(EthereumImpl.class);

        BlockUnclesValidationRule unclesValidationRule = mock(BlockUnclesValidationRule.class);
        when(unclesValidationRule.isValid(any())).thenReturn(true);
        MinerClock clock = new MinerClock(true, Clock.systemUTC());
        MinerServer minerServer = makeMinerServer(ethereumImpl, unclesValidationRule, clock, transactionPool);
        try {
            minerServer.start();

            MinerWork work = minerServer.getWork();

            assertTrue(work.getNotify());
        } finally {
            minerServer.stop();
        }
    }

    @Test
    void secondWorkWithNoChangesTurnsNotifyFlagOff() {
        EthereumImpl ethereumImpl = mock(EthereumImpl.class);

        BlockUnclesValidationRule unclesValidationRule = mock(BlockUnclesValidationRule.class);
        when(unclesValidationRule.isValid(any())).thenReturn(true);
        MinerClock clock = new MinerClock(true, Clock.systemUTC());
        MinerServer minerServer = makeMinerServer(ethereumImpl, unclesValidationRule, clock, transactionPool);

        minerServer.start();
        try {
            MinerWork work = minerServer.getWork();

            assertTrue(work.getNotify());

            work = minerServer.getWork();

            assertFalse(work.getNotify());
        } finally {
            minerServer.stop();
        }
    }

    @Test
    void gasUnitInDollarsIsInitializedOkAtConstructor() {
        Block block1 = mock(Block.class);
        when(block1.getFeesPaidToMiner()).thenReturn(new Coin(BigInteger.valueOf(10)));
        when(block1.getHashForMergedMining()).thenReturn(TestUtils.randomHash().getBytes());
        when(block1.getHash()).thenReturn(TestUtils.randomHash());
        when(block1.getDifficulty()).thenReturn(BlockDifficulty.ZERO);
        when(block1.getParentHashJsonString()).thenReturn(TestUtils.randomHash().toJsonString());

        Block block2 = mock(Block.class);
        when(block2.getFeesPaidToMiner()).thenReturn(new Coin(BigInteger.valueOf(24)));
        when(block2.getHashForMergedMining()).thenReturn(TestUtils.randomHash().getBytes());
        when(block2.getHash()).thenReturn(TestUtils.randomHash());
        when(block2.getDifficulty()).thenReturn(BlockDifficulty.ZERO);
        when(block2.getParentHashJsonString()).thenReturn(TestUtils.randomHash().toJsonString());

        BlockToMineBuilder builder = mock(BlockToMineBuilder.class);
        BlockResult blockResult = mock(BlockResult.class);
        BlockResult blockResult2 = mock(BlockResult.class);
        when(blockResult.getBlock()).thenReturn(block1);
        when(blockResult2.getBlock()).thenReturn(block2);
        when(builder.build(any(), any())).thenReturn(blockResult).thenReturn(blockResult2);

        MinerClock clock = new MinerClock(true, Clock.systemUTC());
        MinerServer minerServer = new MinerServerImpl(
                config,
                mock(EthereumImpl.class),
                this.miningMainchainView,
                null,
                mock(ProofOfWorkRule.class),
                builder,
                clock,
                mock(BlockFactory.class),
                new BuildInfo("cb7f28e", "master"),
                ConfigUtils.getDefaultMiningConfig()
        );
        try {
            minerServer.start();
            minerServer.getWork();
            minerServer.buildBlockToMine(false);
            MinerWork work = minerServer.getWork();

            assertTrue(work.getNotify());
        } finally {
            minerServer.stop();
        }
    }

    @Test
    void secondBuildBlockToMineTurnsNotifyFlagOff() {
        Ethereum ethereum = mock(EthereumImpl.class);

        BlockUnclesValidationRule unclesValidationRule = mock(BlockUnclesValidationRule.class);
        when(unclesValidationRule.isValid(any())).thenReturn(true);
        MinerClock clock = new MinerClock(true, Clock.systemUTC());
        MinerServer minerServer = makeMinerServer(ethereum, unclesValidationRule, clock, transactionPool);
        try {
            minerServer.start();

            MinerWork work = minerServer.getWork();

            String hashForMergedMining = work.getBlockHashForMergedMining();

            minerServer.buildBlockToMine(false);

            work = minerServer.getWork();
            assertEquals(hashForMergedMining, work.getBlockHashForMergedMining());
            assertFalse(work.getNotify());
        } finally {
            minerServer.stop();
        }
    }

    @Test
    void submitTwoBitcoinBlocksAtSameTimeWithoutRateLimit() {
        Ethereum ethereum = mock(EthereumImpl.class);
        when(ethereum.addNewMinedBlock(any())).thenReturn(ImportResult.IMPORTED_BEST);

        BlockUnclesValidationRule unclesValidationRule = mock(BlockUnclesValidationRule.class);
        when(unclesValidationRule.isValid(any())).thenReturn(true);
        MinerClock clock = new MinerClock(true, Clock.systemUTC());
        SubmissionRateLimitHandler submissionRateLimitHandler = mock(SubmissionRateLimitHandler.class);
        doReturn(false).when(submissionRateLimitHandler).isEnabled();
        doReturn(true).when(submissionRateLimitHandler).isSubmissionAllowed();
        MinerServer minerServer = makeMinerServer(ethereum, unclesValidationRule, clock, transactionPool, submissionRateLimitHandler);

        minerServer.buildBlockToMine(false);
        MinerWork work = minerServer.getWork();
        BtcBlock bitcoinMergedMiningBlock = getMergedMiningBlockWithOnlyCoinbase(work);
        findNonce(work, bitcoinMergedMiningBlock);

        SubmitBlockResult result = minerServer.submitBitcoinBlock(work.getBlockHashForMergedMining(), bitcoinMergedMiningBlock);
        Assertions.assertEquals("OK", result.getStatus());

        result = minerServer.submitBitcoinBlock(work.getBlockHashForMergedMining(), bitcoinMergedMiningBlock);
        Assertions.assertEquals("OK", result.getStatus());
    }

    @Test
    void submitTwoBitcoinBlocksAtSameTimeWithRateLimit() {
        EthereumImpl ethereumImpl = mock(EthereumImpl.class);
        when(ethereumImpl.addNewMinedBlock(any())).thenReturn(ImportResult.IMPORTED_BEST);

        BlockUnclesValidationRule unclesValidationRule = mock(BlockUnclesValidationRule.class);
        when(unclesValidationRule.isValid(any())).thenReturn(true);
        MinerClock clock = new MinerClock(true, Clock.systemUTC());
        SubmissionRateLimitHandler submissionRateLimitHandler = mock(SubmissionRateLimitHandler.class);
        doReturn(true).when(submissionRateLimitHandler).isEnabled();
        MinerServer minerServer = makeMinerServer(ethereumImpl, unclesValidationRule, clock, transactionPool, submissionRateLimitHandler);

        minerServer.buildBlockToMine(false);
        MinerWork work = minerServer.getWork();
        BtcBlock bitcoinMergedMiningBlock = getMergedMiningBlockWithOnlyCoinbase(work);
        findNonce(work, bitcoinMergedMiningBlock);

        doReturn(false).when(submissionRateLimitHandler).isSubmissionAllowed();
        SubmitBlockResult result = minerServer.submitBitcoinBlock(work.getBlockHashForMergedMining(), bitcoinMergedMiningBlock);
        Assertions.assertEquals("ERROR", result.getStatus());

        verify(submissionRateLimitHandler, atLeastOnce()).isSubmissionAllowed();
        verify(submissionRateLimitHandler, never()).onSubmit();

        doReturn(true).when(submissionRateLimitHandler).isSubmissionAllowed();
        result = minerServer.submitBitcoinBlock(work.getBlockHashForMergedMining(), bitcoinMergedMiningBlock);
        Assertions.assertEquals("OK", result.getStatus());

        verify(submissionRateLimitHandler, times(1)).onSubmit();
    }

    @Test
    void extraDataNotInitializedWithClientData() {
        MinerServer minerServer = new MinerServerImpl(
                config,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new BuildInfo("cb7f28e", "master"),
                ConfigUtils.getDefaultMiningConfig()
        );

        byte[] extraData = minerServer.getExtraData();
        RLPList decodedExtraData = RLP.decodeList(extraData);
        assertEquals(2, decodedExtraData.size());

        byte[] firstItem = decodedExtraData.get(0).getRLPData();
        assertNotNull(firstItem);
        assertEquals(1, (RLP.decodeInt(firstItem,0)));

        byte[] secondItem = decodedExtraData.get(1).getRLPData();
        assertNotNull(secondItem);
        assertEquals(config.projectVersionModifier().concat("-cb7f28e"), new String(secondItem));
    }

    @Test
    void extraDataWithClientData() {
        MinerServer minerServer = new MinerServerImpl(
                config,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new BuildInfo("cb7f28e", "master"),
                ConfigUtils.getDefaultMiningConfig()
        );

        minerServer.setExtraData("tincho".getBytes());

        byte[] extraData = minerServer.getExtraData();
        RLPList decodedExtraData = RLP.decodeList(extraData);
        assertEquals(3, decodedExtraData.size());

        byte[] firstItem = decodedExtraData.get(0).getRLPData();
        assertNotNull(firstItem);
        assertEquals(1, (RLP.decodeInt(firstItem,0)));

        byte[] secondItem = decodedExtraData.get(1).getRLPData();
        assertNotNull(secondItem);
        assertEquals(config.projectVersionModifier().concat("-cb7f28e"), new String(secondItem));

        byte[] thirdItem = decodedExtraData.get(2).getRLPData();
        assertNotNull(thirdItem);
        assertEquals("tincho", new String(thirdItem));
    }

    @Test
    void extraDataWithClientDataMoreThan32Bytes() {
        MinerServer minerServer = new MinerServerImpl(
                config,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new BuildInfo("cb7f28e", "master"),
                ConfigUtils.getDefaultMiningConfig()
        );

        minerServer.setExtraData("tincho is the king of mining".getBytes());

        byte[] extraData = minerServer.getExtraData();
        assertEquals(32, extraData.length);
        RLPList decodedExtraData = RLP.decodeList(extraData);
        assertEquals(3, decodedExtraData.size());

        byte[] firstItem = decodedExtraData.get(0).getRLPData();
        assertNotNull(firstItem);
        assertEquals(1, (RLP.decodeInt(firstItem,0)));

        byte[] secondItem = decodedExtraData.get(1).getRLPData();
        assertNotNull(secondItem);
        assertEquals(config.projectVersionModifier().concat("-cb7f28e"), new String(secondItem));

        byte[] thirdItem = decodedExtraData.get(2).getRLPData();
        assertNotNull(thirdItem);

        // The final client extra data may be truncated by the combined size of the other encoded elements
        int extraDataMaxLength = 32;
        int extraDataEncodingOverhead = 3;
        int clientExtraDataSize = extraDataMaxLength - extraDataEncodingOverhead - firstItem.length - secondItem.length;

        assertEquals("tincho is the king of mining".substring(0, clientExtraDataSize), new String(thirdItem));
    }

    @Test
    void onBestBlockBuildBlockToMine() {

        // prepare for miner server
        Ethereum ethereum = mock(EthereumImpl.class);
        when(ethereum.addNewMinedBlock(any())).thenReturn(ImportResult.IMPORTED_BEST);

        BlockProcessor blockProcessor = mock(NodeBlockProcessor.class);
        when(blockProcessor.hasBetterBlockToSync()).thenReturn(false);

        BlockUnclesValidationRule unclesValidationRule = mock(BlockUnclesValidationRule.class);
        when(unclesValidationRule.isValid(any())).thenReturn(true);

        MinerClock clock = new MinerClock(true, Clock.systemUTC());

        // create miner server
        MinerServer minerServer = spy(makeMinerServer(ethereum, blockProcessor, unclesValidationRule, clock, transactionPool));
        minerServer.start();

        // create listener
        MinerServerImpl.NewBlockTxListener listener = new MinerServerImpl.NewBlockTxListener(this.miningMainchainView, minerServer::buildBlockToMine, blockProcessor, true);

        Block block = mock(Block.class);
        when(block.getHeader()).thenReturn(this.miningMainchainView.get().get(0));

        // call best block
        listener.onBestBlock(block, null);

        // assert the event was received and build block was called
        // it need to be 2 because the minerServer.start() calls it once
        verify(minerServer, times(2)).buildBlockToMine(false);

        minerServer.stop();
    }

    @Test
    void onNewTxBuildBlockToMine() throws InterruptedException {

        // prepare for miner server
        Ethereum ethereum = spy(new EthereumImpl(null, null, compositeEthereumListener, standardBlockchain, Mockito.mock(GasPriceTracker.class)) );
        doReturn(ImportResult.IMPORTED_BEST).when(ethereum).addNewMinedBlock(any());

        BlockUnclesValidationRule unclesValidationRule = mock(BlockUnclesValidationRule.class);
        when(unclesValidationRule.isValid(any())).thenReturn(true);

        MinerClock clock = new MinerClock(true, Clock.systemUTC());

        BlockProcessor blockProcessor = mock(NodeBlockProcessor.class);
        when(blockProcessor.hasBetterBlockToSync()).thenReturn(false);

        // create the transaction
        World world = new World((BlockChainImpl) standardBlockchain, blockStore, rskTestContext.getReceiptStore(), rskTestContext.getTrieStore(), repository, transactionPool, (Genesis)null);

        Account sender = new AccountBuilder(world).name("sender").balance(new Coin(BigInteger.valueOf(2000))).build();
        Account receiver = new AccountBuilder(world).name("receiver").build();

        Transaction tx = new TransactionBuilder()
                .sender(sender)
                .receiver(receiver)
                .nonce(0)
                .value(BigInteger.valueOf(1000))
                .build();

        List<Transaction> txs = new ArrayList<>(Collections.singletonList(tx));


        // create miner server
        MinerServer minerServer = spy(makeMinerServer(ethereum, blockProcessor, unclesValidationRule, clock, transactionPool));
        minerServer.start();

        // create listener
        MinerServerImpl.NewBlockTxListener listener = new MinerServerImpl.NewBlockTxListener(this.miningMainchainView, minerServer::buildBlockToMine, blockProcessor, true);

        Block block = mock(Block.class);
        when(block.getHeader()).thenReturn(this.miningMainchainView.get().get(0));

        // call best block
        listener.onPendingTransactionsReceived(txs);

        // assert the event was received and build block was called
        // it need to be 2 because the minerServer.start() calls it once
        verify(minerServer, times(2)).buildBlockToMine(false);

        minerServer.stop();



    }


    private BtcBlock getMergedMiningBlockWithOnlyCoinbase(MinerWork work) {
        return getMergedMiningBlock(work, Collections.emptyList());
    }

    private BtcBlock getMergedMiningBlock(MinerWork work, List<BtcTransaction> txs) {
        NetworkParameters bitcoinNetworkParameters = RegTestParams.get();
        BtcTransaction bitcoinMergedMiningCoinbaseTransaction = MinerUtils.getBitcoinMergedMiningCoinbaseTransaction(bitcoinNetworkParameters, work);

        List<BtcTransaction> blockTxs = new ArrayList<>();
        blockTxs.add(bitcoinMergedMiningCoinbaseTransaction);
        blockTxs.addAll(txs);

        return MinerUtils.getBitcoinMergedMiningBlock(bitcoinNetworkParameters, blockTxs);
    }

    private BtcBlock getMergedMiningBlockWithTwoTags(MinerWork work, MinerWork work2) {
        NetworkParameters bitcoinNetworkParameters = RegTestParams.get();
        BtcTransaction bitcoinMergedMiningCoinbaseTransaction =
                MinerUtils.getBitcoinMergedMiningCoinbaseTransactionWithTwoTags(bitcoinNetworkParameters, work, work2);
        return MinerUtils.getBitcoinMergedMiningBlock(bitcoinNetworkParameters, bitcoinMergedMiningCoinbaseTransaction);
    }

    private void findNonce(MinerWork work, BtcBlock bitcoinMergedMiningBlock) {
        BigInteger target = new BigInteger(HexUtils.stringHexToByteArray(work.getTarget()));

        while (true) {
            try {
                // Is our proof of work valid yet?
                BigInteger blockHashBI = bitcoinMergedMiningBlock.getHash().toBigInteger();
                if (blockHashBI.compareTo(target) <= 0) {
                    break;
                }
                // No, so increment the nonce and try again.
                bitcoinMergedMiningBlock.setNonce(bitcoinMergedMiningBlock.getNonce() + 1);
            } catch (VerificationException e) {
                throw new RuntimeException(e); // Cannot happen.
            }
        }
    }

    private MinerServer makeMinerServer(Ethereum ethereum, BlockUnclesValidationRule unclesValidationRule,
                                        MinerClock clock) {
        return makeMinerServer(ethereum, null, unclesValidationRule, clock, transactionPool);
    }

    private MinerServer makeMinerServer(Ethereum ethereum, BlockUnclesValidationRule unclesValidationRule,
            MinerClock clock, TransactionPool transactionPool) {
        return makeMinerServer(ethereum, null, unclesValidationRule, clock, transactionPool);
    }

    private MinerServer makeMinerServer(Ethereum ethereum, BlockProcessor blockProcessor, BlockUnclesValidationRule unclesValidationRule,
                                            MinerClock clock, TransactionPool transactionPool) {
        return new MinerServerImpl(
                config,
                ethereum,
                this.miningMainchainView,
                blockProcessor,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new BlockToMineBuilder(
                        config.getActivationConfig(),
                        ConfigUtils.getDefaultMiningConfig(),
                        repositoryLocator,
                        blockStore,
                        transactionPool,
                        difficultyCalculator,
                        new GasLimitCalculator(config.getNetworkConstants()),
                        new ForkDetectionDataCalculator(),
                        unclesValidationRule,
                        clock,
                        blockFactory,
                        blockExecutor,
                        minimumGasPriceCalculator,
                        minerUtils
                ),
                clock,
                blockFactory,
                new BuildInfo("cb7f28e", "master"),
                ConfigUtils.getDefaultMiningConfig()
        );
    }

    private MinerServer makeMinerServer(Ethereum ethereum, BlockUnclesValidationRule unclesValidationRule,
                                            MinerClock clock, TransactionPool transactionPool,
                                            SubmissionRateLimitHandler submissionRateLimitHandler) {
        return new MinerServerImpl(
                config,
                ethereum,
                this.miningMainchainView,
                null,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new BlockToMineBuilder(
                        config.getActivationConfig(),
                        ConfigUtils.getDefaultMiningConfig(),
                        repositoryLocator,
                        blockStore,
                        transactionPool,
                        difficultyCalculator,
                        new GasLimitCalculator(config.getNetworkConstants()),
                        new ForkDetectionDataCalculator(),
                        unclesValidationRule,
                        clock,
                        blockFactory,
                        blockExecutor,
                        minimumGasPriceCalculator,
                        minerUtils
                ),
                clock,
                blockFactory,
                new BuildInfo("cb7f28e", "master"),
                ConfigUtils.getDefaultMiningConfig(),
                submissionRateLimitHandler
        );
    }
}
