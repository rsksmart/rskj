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

import co.rsk.TestHelpers.Tx;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.VerificationException;
import co.rsk.config.ConfigUtils;
import co.rsk.config.RskMiningConstants;
import co.rsk.config.RskSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.DifficultyCalculator;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.remasc.RemascTransaction;
import co.rsk.test.World;
import co.rsk.validators.BlockUnclesValidationRule;
import co.rsk.validators.ProofOfWorkRule;
import org.ethereum.core.*;
import org.ethereum.facade.EthereumImpl;
import org.ethereum.rpc.TypeConverter;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.*;

/**
 * Created by adrian.eidelman on 3/16/2016.
 */
public class MinerServerTest {
    private static final RskSystemProperties config = new RskSystemProperties();
    public static final DifficultyCalculator DIFFICULTY_CALCULATOR = new DifficultyCalculator(config);

    private BlockChainImpl blockchain;

    @Before
    public void setUp() {
        World world = new World();
        blockchain = world.getBlockChain();
    }

    @Test
    public void buildBlockToMineCheckThatLastTransactionIsForREMASC() {
        EthereumImpl ethereumImpl = Mockito.mock(EthereumImpl.class);
        Repository repository = Mockito.mock(Repository.class);
        Mockito.when(repository.getSnapshotTo(Mockito.any())).thenReturn(repository);
        Mockito.when(repository.getRoot()).thenReturn(blockchain.getRepository().getRoot());
        Mockito.when(repository.startTracking()).thenReturn(repository);

        Transaction tx1 = Tx.create(config, 0, 21000, 100, 0, 0, 0, new Random(0));
        byte[] s1 = new byte[32];
        byte[] s2 = new byte[32];
        s1[0] = 0;
        s2[0] = 1;
        Mockito.when(tx1.getHash()).thenReturn(s1);
        Mockito.when(tx1.getEncoded()).thenReturn(new byte[32]);

        Mockito.when(repository.getNonce(tx1.getSender())).thenReturn(BigInteger.ZERO);
        Mockito.when(repository.getNonce(RemascTransaction.REMASC_ADDRESS)).thenReturn(BigInteger.ZERO);
        Mockito.when(repository.getBalance(tx1.getSender())).thenReturn(Coin.valueOf(4200000L));
        Mockito.when(repository.getBalance(RemascTransaction.REMASC_ADDRESS)).thenReturn(Coin.valueOf(4200000L));

        List<Transaction> txs = new ArrayList<>(Arrays.asList(tx1));

        PendingState localPendingState = Mockito.mock(PendingState.class);
        Mockito.when(localPendingState.getPendingTransactions()).thenReturn(txs);

        BlockUnclesValidationRule unclesValidationRule = Mockito.mock(BlockUnclesValidationRule.class);
        Mockito.when(unclesValidationRule.isValid(Mockito.any())).thenReturn(true);
        MinerServerImpl minerServer = new MinerServerImpl(config, ethereumImpl, this.blockchain, null, localPendingState,
                repository, ConfigUtils.getDefaultMiningConfig(), unclesValidationRule,
                null, DIFFICULTY_CALCULATOR, new GasLimitCalculator(config),
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false));

        minerServer.buildBlockToMine(blockchain.getBestBlock(), false);
        Block blockAtHeightOne = minerServer.getBlocksWaitingforPoW().entrySet().iterator().next().getValue();

        List<Transaction> blockTransactions = blockAtHeightOne.getTransactionsList();
        assertNotNull(blockTransactions);
        assertEquals(2, blockTransactions.size());

        Transaction remascTransaction = blockTransactions.get(1);
        assertThat(remascTransaction, instanceOf(RemascTransaction.class));
    }



    @Test
    public void submitBitcoinBlockTwoTags() {
        EthereumImpl ethereumImpl = Mockito.mock(EthereumImpl.class);
        Mockito.when(ethereumImpl.addNewMinedBlock(Mockito.any())).thenReturn(ImportResult.IMPORTED_BEST);

        BlockUnclesValidationRule unclesValidationRule = Mockito.mock(BlockUnclesValidationRule.class);
        Mockito.when(unclesValidationRule.isValid(Mockito.any())).thenReturn(true);
        MinerServerImpl minerServer = new MinerServerImpl(config, ethereumImpl, blockchain, null,
                blockchain.getPendingState(), blockchain.getRepository(), ConfigUtils.getDefaultMiningConfig(),
                unclesValidationRule, null, DIFFICULTY_CALCULATOR,
                new GasLimitCalculator(config),
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false));
        try {
        minerServer.start();
        MinerWork work = minerServer.getWork();
        Block bestBlock = blockchain.getBestBlock();

        minerServer.buildBlockToMine(bestBlock, false);
        MinerWork work2 = minerServer.getWork(); // only the tag is used

        co.rsk.bitcoinj.core.BtcBlock bitcoinMergedMiningBlock = getMergedMiningBlockWithTwoTags(work,work2);

        findNonce(work, bitcoinMergedMiningBlock);
        SubmitBlockResult result;
        result = minerServer.submitBitcoinBlock(work2.getBlockHashForMergedMining(), bitcoinMergedMiningBlock, true);


        Assert.assertEquals("OK", result.getStatus());
        Assert.assertNotNull(result.getBlockInfo());
        Assert.assertEquals("0x1", result.getBlockInfo().getBlockIncludedHeight());
        Assert.assertEquals("0x494d504f525445445f42455354", result.getBlockInfo().getBlockImportedResult());

        // Submit again the save PoW for a different header
        result = minerServer.submitBitcoinBlock(work.getBlockHashForMergedMining(), bitcoinMergedMiningBlock, false);

        Assert.assertEquals("ERROR", result.getStatus());

        Mockito.verify(ethereumImpl, Mockito.times(1)).addNewMinedBlock(Mockito.any());
        } finally {
            minerServer.stop();
        }
    }
    @Test
    public void submitBitcoinBlock() {
        EthereumImpl ethereumImpl = Mockito.mock(EthereumImpl.class);
        Mockito.when(ethereumImpl.addNewMinedBlock(Mockito.any())).thenReturn(ImportResult.IMPORTED_BEST);

        BlockUnclesValidationRule unclesValidationRule = Mockito.mock(BlockUnclesValidationRule.class);
        Mockito.when(unclesValidationRule.isValid(Mockito.any())).thenReturn(true);
        MinerServer minerServer = new MinerServerImpl(config, ethereumImpl, blockchain, null,
                blockchain.getPendingState(), blockchain.getRepository(), ConfigUtils.getDefaultMiningConfig(),
                unclesValidationRule, null, DIFFICULTY_CALCULATOR,
                new GasLimitCalculator(config),
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false));
        try {
        minerServer.start();
        MinerWork work = minerServer.getWork();

        co.rsk.bitcoinj.core.BtcBlock bitcoinMergedMiningBlock = getMergedMiningBlock(work);

        findNonce(work, bitcoinMergedMiningBlock);

        SubmitBlockResult result = minerServer.submitBitcoinBlock(work.getBlockHashForMergedMining(), bitcoinMergedMiningBlock);

        Assert.assertEquals("OK", result.getStatus());
        Assert.assertNotNull(result.getBlockInfo());
        Assert.assertEquals("0x1", result.getBlockInfo().getBlockIncludedHeight());
        Assert.assertEquals("0x494d504f525445445f42455354", result.getBlockInfo().getBlockImportedResult());

        Mockito.verify(ethereumImpl, Mockito.times(1)).addNewMinedBlock(Mockito.any());
        } finally {
            minerServer.stop();
        }
    }



    @Test
    public void workWithNoTransactionsZeroFees() {
        EthereumImpl ethereumImpl = Mockito.mock(EthereumImpl.class);

        BlockUnclesValidationRule unclesValidationRule = Mockito.mock(BlockUnclesValidationRule.class);
        Mockito.when(unclesValidationRule.isValid(Mockito.any())).thenReturn(true);
        MinerServer minerServer = new MinerServerImpl(config, ethereumImpl, this.blockchain, null, this.blockchain.getPendingState(), blockchain.getRepository(), ConfigUtils.getDefaultMiningConfig(), unclesValidationRule, null, DIFFICULTY_CALCULATOR,
                new GasLimitCalculator(config),
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false));

        minerServer.start();
        try {
        MinerWork work = minerServer.getWork();

        assertEquals("0", work.getFeesPaidToMiner());
        } finally {
            minerServer.stop();
        }
    }

    @Test
    public void initialWorkTurnsNotifyFlagOn() {
        EthereumImpl ethereumImpl = Mockito.mock(EthereumImpl.class);

        BlockUnclesValidationRule unclesValidationRule = Mockito.mock(BlockUnclesValidationRule.class);
        Mockito.when(unclesValidationRule.isValid(Mockito.any())).thenReturn(true);
        MinerServer minerServer = new MinerServerImpl(config, ethereumImpl, this.blockchain, null, this.blockchain.getPendingState(), blockchain.getRepository(), ConfigUtils.getDefaultMiningConfig(), unclesValidationRule, null, DIFFICULTY_CALCULATOR,
                new GasLimitCalculator(config),
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false));
        try {
        minerServer.start();

        MinerWork work = minerServer.getWork();

        assertEquals(true, work.getNotify());
        } finally {
            minerServer.stop();
        }
    }

    @Test
    public void secondWorkWithNoChangesTurnsNotifyFlagOff() {
        EthereumImpl ethereumImpl = Mockito.mock(EthereumImpl.class);

        BlockUnclesValidationRule unclesValidationRule = Mockito.mock(BlockUnclesValidationRule.class);
        Mockito.when(unclesValidationRule.isValid(Mockito.any())).thenReturn(true);
        MinerServer minerServer = new MinerServerImpl(config, ethereumImpl, this.blockchain, null, this.blockchain.getPendingState(), blockchain.getRepository(), ConfigUtils.getDefaultMiningConfig(), unclesValidationRule, null, DIFFICULTY_CALCULATOR, new GasLimitCalculator(config),
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false));

        minerServer.start();
        try {
        MinerWork work = minerServer.getWork();

        assertEquals(true, work.getNotify());

        work = minerServer.getWork();

        assertEquals(false, work.getNotify());
        } finally {
            minerServer.stop();
        }
    }

    @Test
    public void secondBuildBlockToMineTurnsNotifyFlagOff() {
        EthereumImpl ethereumImpl = Mockito.mock(EthereumImpl.class);

        BlockUnclesValidationRule unclesValidationRule = Mockito.mock(BlockUnclesValidationRule.class);
        Mockito.when(unclesValidationRule.isValid(Mockito.any())).thenReturn(true);
        MinerServer minerServer = new MinerServerImpl(config, ethereumImpl, this.blockchain, null, blockchain.getPendingState(), blockchain.getRepository(), ConfigUtils.getDefaultMiningConfig(), unclesValidationRule, null, DIFFICULTY_CALCULATOR, new GasLimitCalculator(config),
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false));
        try {
        minerServer.start();

        MinerWork work = minerServer.getWork();

        String hashForMergedMining = work.getBlockHashForMergedMining();

        minerServer.buildBlockToMine(blockchain.getBestBlock(), false);

        work = minerServer.getWork();
        assertEquals(hashForMergedMining, work.getBlockHashForMergedMining());
        assertEquals(false, work.getNotify());
        } finally {
            minerServer.stop();
        }
    }

    @Test
    public void getCurrentTimeInMilliseconds() {
        long current = System.currentTimeMillis() / 1000;

        MinerServerImpl server = new MinerServerImpl(config, null, null, null, null,
                null, ConfigUtils.getDefaultMiningConfig(), null, null, DIFFICULTY_CALCULATOR,
                new GasLimitCalculator(config), new ProofOfWorkRule(config).setFallbackMiningEnabled(false));

        long result = server.getCurrentTimeInSeconds();

        Assert.assertTrue(result >= current);
        Assert.assertTrue(result <= current + 1);
    }

    @Test
    public void increaseTime() {
        long current = System.currentTimeMillis() / 1000;

        MinerServerImpl server = new MinerServerImpl(config, null, null, null, null,
                null, ConfigUtils.getDefaultMiningConfig(), null, null,
                DIFFICULTY_CALCULATOR, new GasLimitCalculator(config),
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false));

        Assert.assertEquals(10, server.increaseTime(10));

        long result = server.getCurrentTimeInSeconds();

        Assert.assertTrue(result >= current + 10);
        Assert.assertTrue(result <= current + 11);
    }

    @Test
    public void increaseTimeUsingNegativeNumberHasNoEffect() {
        long current = System.currentTimeMillis() / 1000;

        MinerServerImpl server = new MinerServerImpl(config, null, null, null, null, null, ConfigUtils.getDefaultMiningConfig(), null, null, DIFFICULTY_CALCULATOR, new GasLimitCalculator(config),
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false));

        Assert.assertEquals(0, server.increaseTime(-10));

        long result = server.getCurrentTimeInSeconds();

        Assert.assertTrue(result >= current);
    }

    @Test
    public void increaseTimeTwice() {
        long current = System.currentTimeMillis() / 1000;

        MinerServerImpl server = new MinerServerImpl(config, null, null, null, null, null, ConfigUtils.getDefaultMiningConfig(), null, null, DIFFICULTY_CALCULATOR, new GasLimitCalculator(config),
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false));

        Assert.assertEquals(5, server.increaseTime(5));
        Assert.assertEquals(10, server.increaseTime(5));

        long result = server.getCurrentTimeInSeconds();

        Assert.assertTrue(result >= current + 10);
        Assert.assertTrue(result <= current + 11);
    }

    private co.rsk.bitcoinj.core.BtcBlock getMergedMiningBlock(MinerWork work) {
        NetworkParameters bitcoinNetworkParameters = co.rsk.bitcoinj.params.RegTestParams.get();
        co.rsk.bitcoinj.core.BtcTransaction bitcoinMergedMiningCoinbaseTransaction = MinerUtils.getBitcoinMergedMiningCoinbaseTransaction(bitcoinNetworkParameters, work);
        return MinerUtils.getBitcoinMergedMiningBlock(bitcoinNetworkParameters, bitcoinMergedMiningCoinbaseTransaction);
    }

    private co.rsk.bitcoinj.core.BtcBlock getMergedMiningBlockWithTwoTags(MinerWork work,MinerWork work2) {
        NetworkParameters bitcoinNetworkParameters = co.rsk.bitcoinj.params.RegTestParams.get();
        co.rsk.bitcoinj.core.BtcTransaction bitcoinMergedMiningCoinbaseTransaction =
                getBitcoinMergedMiningCoinbaseTransactionWithTwoTags(bitcoinNetworkParameters, work,work2);
        return MinerUtils.getBitcoinMergedMiningBlock(bitcoinNetworkParameters, bitcoinMergedMiningCoinbaseTransaction);
    }

    private static co.rsk.bitcoinj.core.BtcTransaction getBitcoinMergedMiningCoinbaseTransactionWithTwoTags(
            NetworkParameters params,
            MinerWork work,
            MinerWork work2) {
        return getBitcoinMergedMiningCoinbaseTransactionWithTwoTags(
                params,
                TypeConverter.stringHexToByteArray(work.getBlockHashForMergedMining()),
                TypeConverter.stringHexToByteArray(work2.getBlockHashForMergedMining()));
    }

    private static co.rsk.bitcoinj.core.BtcTransaction getBitcoinMergedMiningCoinbaseTransactionWithTwoTags(
            NetworkParameters params,
            byte[] blockHashForMergedMining1,
            byte[] blockHashForMergedMining2) {
        co.rsk.bitcoinj.core.BtcTransaction coinbaseTransaction = new co.rsk.bitcoinj.core.BtcTransaction(params);
        //Add a random number of random bytes before the RSK tag
        SecureRandom random = new SecureRandom();
        byte[] prefix = new byte[random.nextInt(1000)];
        random.nextBytes(prefix);

        byte[] bytes0 = org.spongycastle.util.Arrays.concatenate(RskMiningConstants.RSK_TAG, blockHashForMergedMining1);
        // addsecond tag
        byte[] bytes1 = org.spongycastle.util.Arrays.concatenate(bytes0, RskMiningConstants.RSK_TAG, blockHashForMergedMining2);

        co.rsk.bitcoinj.core.TransactionInput ti = new co.rsk.bitcoinj.core.TransactionInput(params, coinbaseTransaction, prefix);
        coinbaseTransaction.addInput(ti);
        ByteArrayOutputStream scriptPubKeyBytes = new ByteArrayOutputStream();
        co.rsk.bitcoinj.core.BtcECKey key = new co.rsk.bitcoinj.core.BtcECKey();
        try {
            co.rsk.bitcoinj.script.Script.writeBytes(scriptPubKeyBytes, key.getPubKey());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        scriptPubKeyBytes.write(co.rsk.bitcoinj.script.ScriptOpCodes.OP_CHECKSIG);
        coinbaseTransaction.addOutput(new co.rsk.bitcoinj.core.TransactionOutput(params, coinbaseTransaction, co.rsk.bitcoinj.core.Coin.valueOf(50, 0), scriptPubKeyBytes.toByteArray()));
        // add opreturn output with two tags
        ByteArrayOutputStream output2Bytes = new ByteArrayOutputStream();
        output2Bytes.write(co.rsk.bitcoinj.script.ScriptOpCodes.OP_RETURN);

        try {
            co.rsk.bitcoinj.script.Script.writeBytes(output2Bytes, bytes1);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        coinbaseTransaction.addOutput(
                new co.rsk.bitcoinj.core.TransactionOutput(params, coinbaseTransaction, co.rsk.bitcoinj.core.Coin.valueOf(1), output2Bytes.toByteArray()));

        return coinbaseTransaction;
    }

    private void findNonce(MinerWork work, co.rsk.bitcoinj.core.BtcBlock bitcoinMergedMiningBlock) {
        BigInteger target = new BigInteger(TypeConverter.stringHexToByteArray(work.getTarget()));

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
}
