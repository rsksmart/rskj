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
import co.rsk.config.RskSystemProperties;
import co.rsk.core.DifficultyCalculator;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.core.bc.BlockChainImplTest;
import co.rsk.remasc.RemascTransaction;
import co.rsk.test.World;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.validators.BlockUnclesValidationRule;
import co.rsk.validators.ProofOfWorkRule;
import org.ethereum.core.*;
import org.ethereum.facade.EthereumImpl;
import org.ethereum.rpc.TypeConverter;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.math.BigInteger;
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
    public static final DifficultyCalculator DIFFICULTY_CALCULATOR = new DifficultyCalculator(RskSystemProperties.CONFIG);

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

        Transaction tx1 = Tx.create(0, 21000, 100, 0, 0, 0, new Random(0));
        byte[] s1 = new byte[32];
        byte[] s2 = new byte[32];
        s1[0] = 0;
        s2[0] = 1;
        Mockito.when(tx1.getHash()).thenReturn(s1);
        Mockito.when(tx1.getEncoded()).thenReturn(new byte[32]);

        Mockito.when(repository.getNonce(tx1.getSender())).thenReturn(BigInteger.ZERO);
        Mockito.when(repository.getNonce(new byte[]{0})).thenReturn(BigInteger.ZERO);
        Mockito.when(repository.getBalance(tx1.getSender())).thenReturn(BigInteger.valueOf(4200000L));
        Mockito.when(repository.getBalance(new byte[]{0})).thenReturn(BigInteger.valueOf(4200000L));

        List<Transaction> txs = new ArrayList<>(Arrays.asList(tx1));

        PendingState localPendingState = Mockito.mock(PendingState.class);
        Mockito.when(localPendingState.getPendingTransactions()).thenReturn(txs);

        BlockUnclesValidationRule unclesValidationRule = Mockito.mock(BlockUnclesValidationRule.class);
        Mockito.when(unclesValidationRule.isValid(Mockito.any())).thenReturn(true);
        MinerServerImpl minerServer = new MinerServerImpl(ethereumImpl, this.blockchain, null, localPendingState, repository, ConfigUtils.getDefaultMiningConfig(), unclesValidationRule, null, DIFFICULTY_CALCULATOR, new GasLimitCalculator(RskSystemProperties.CONFIG), new ProofOfWorkRule(RskSystemProperties.CONFIG));

        minerServer.buildBlockToMine(blockchain.getBestBlock(), false);
        Block blockAtHeightOne = minerServer.getBlocksWaitingforPoW().entrySet().iterator().next().getValue();

        List<Transaction> blockTransactions = blockAtHeightOne.getTransactionsList();
        assertNotNull(blockTransactions);
        assertEquals(2, blockTransactions.size());

        Transaction remascTransaction = blockTransactions.get(1);
        assertThat(remascTransaction, instanceOf(RemascTransaction.class));
    }

    @Test
    public void submitBitcoinBlock() {
        EthereumImpl ethereumImpl = Mockito.mock(EthereumImpl.class);
        Mockito.when(ethereumImpl.addNewMinedBlock(Mockito.any())).thenReturn(ImportResult.IMPORTED_BEST);

        BlockUnclesValidationRule unclesValidationRule = Mockito.mock(BlockUnclesValidationRule.class);
        Mockito.when(unclesValidationRule.isValid(Mockito.any())).thenReturn(true);
        MinerServer minerServer = new MinerServerImpl(ethereumImpl, blockchain, null, blockchain.getPendingState(), blockchain.getRepository(), ConfigUtils.getDefaultMiningConfig(), unclesValidationRule, null, DIFFICULTY_CALCULATOR, new GasLimitCalculator(RskSystemProperties.CONFIG), new ProofOfWorkRule(RskSystemProperties.CONFIG));

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
    }

    /*
     * This test is probabilistic, but it has a really high chance to pass. We will generate
     * a random block that it is unlikely to pass the Long.MAX_VALUE difficulty, though
     * it may happen once. Twice would be suspicious.
     */
    @Test
    public void submitBitcoinBlockProofOfWorkNotGoodEnough() {
        /* We need a low target */
        BlockChainImpl bc = new BlockChainBuilder().build();
        Genesis gen = (Genesis) BlockChainImplTest.getGenesisBlock(bc);
        gen.getHeader().setDifficulty(BigInteger.valueOf(Long.MAX_VALUE).toByteArray());
        bc.setStatus(gen, gen.getCumulativeDifficulty());
        World world = new World(bc, gen);
        blockchain = world.getBlockChain();

        EthereumImpl ethereumImpl = Mockito.mock(EthereumImpl.class);

        BlockUnclesValidationRule unclesValidationRule = Mockito.mock(BlockUnclesValidationRule.class);
        Mockito.when(unclesValidationRule.isValid(Mockito.any())).thenReturn(true);
        MinerServer minerServer = new MinerServerImpl(ethereumImpl, this.blockchain, null, this.blockchain.getPendingState(), blockchain.getRepository(), ConfigUtils.getDefaultMiningConfig(), unclesValidationRule, world.getBlockProcessor(), DIFFICULTY_CALCULATOR, new GasLimitCalculator(RskSystemProperties.CONFIG), new ProofOfWorkRule(RskSystemProperties.CONFIG));

        minerServer.start();
        MinerWork work = minerServer.getWork();

        co.rsk.bitcoinj.core.BtcBlock bitcoinMergedMiningBlock = getMergedMiningBlock(work);

        bitcoinMergedMiningBlock.setNonce(2);

        SubmitBlockResult result = minerServer.submitBitcoinBlock(work.getBlockHashForMergedMining(), bitcoinMergedMiningBlock);

        Assert.assertEquals("ERROR", result.getStatus());
        Assert.assertNull(result.getBlockInfo());

        Mockito.verify(ethereumImpl, Mockito.times(0)).addNewMinedBlock(Mockito.any());
    }

    /*
     * This test is much more likely to fail than the
     * submitBitcoinBlockProofOfWorkNotGoodEnough test. Even then
     * it should almost never fail.
     */
    @Test
    public void submitBitcoinBlockInvalidBlockDoesntEliminateCache() {
        /* We need a low, but not too low, target */
        BlockChainImpl bc = new BlockChainBuilder().build();
        Genesis gen = (Genesis) BlockChainImplTest.getGenesisBlock(bc);
        gen.getHeader().setDifficulty(BigInteger.valueOf(300000).toByteArray());
        bc.setStatus(gen, gen.getCumulativeDifficulty());
        World world = new World(bc, gen);
        blockchain = world.getBlockChain();

        EthereumImpl ethereumImpl = Mockito.mock(EthereumImpl.class);
        Mockito.when(ethereumImpl.addNewMinedBlock(Mockito.any())).thenReturn(ImportResult.IMPORTED_BEST);

        BlockUnclesValidationRule unclesValidationRule = Mockito.mock(BlockUnclesValidationRule.class);
        Mockito.when(unclesValidationRule.isValid(Mockito.any())).thenReturn(true);
        MinerServer minerServer = new MinerServerImpl(ethereumImpl, this.blockchain, null, this.blockchain.getPendingState(), blockchain.getRepository(), ConfigUtils.getDefaultMiningConfig(), unclesValidationRule, world.getBlockProcessor(), DIFFICULTY_CALCULATOR, new GasLimitCalculator(RskSystemProperties.CONFIG), new ProofOfWorkRule(RskSystemProperties.CONFIG));

        minerServer.start();
        MinerWork work = minerServer.getWork();

        co.rsk.bitcoinj.core.BtcBlock bitcoinMergedMiningBlock = getMergedMiningBlock(work);

        bitcoinMergedMiningBlock.setNonce(1);

        // Try to submit a block with invalid PoW, this should not eliminate the block from the cache
        SubmitBlockResult result1 = minerServer.submitBitcoinBlock(work.getBlockHashForMergedMining(), bitcoinMergedMiningBlock);

        Assert.assertEquals("ERROR", result1.getStatus());
        Assert.assertNull(result1.getBlockInfo());
        Mockito.verify(ethereumImpl, Mockito.times(0)).addNewMinedBlock(Mockito.any());

        // Now try to submit the same block, this should work fine since the block remains in the cache
        findNonce(work, bitcoinMergedMiningBlock);

        SubmitBlockResult result2 = minerServer.submitBitcoinBlock(work.getBlockHashForMergedMining(), bitcoinMergedMiningBlock);

        Assert.assertEquals("OK", result2.getStatus());
        Assert.assertNotNull(result2.getBlockInfo());
        Mockito.verify(ethereumImpl, Mockito.times(1)).addNewMinedBlock(Mockito.any());

        // Finally, submit the same block again and validate that addNewMinedBlock is called again
        SubmitBlockResult result3 = minerServer.submitBitcoinBlock(work.getBlockHashForMergedMining(), bitcoinMergedMiningBlock);

        Assert.assertEquals("OK", result3.getStatus());
        Assert.assertNotNull(result3.getBlockInfo());
        Mockito.verify(ethereumImpl, Mockito.times(2)).addNewMinedBlock(Mockito.any());
    }

    @Test
    public void workWithNoTransactionsZeroFees() {
        EthereumImpl ethereumImpl = Mockito.mock(EthereumImpl.class);

        BlockUnclesValidationRule unclesValidationRule = Mockito.mock(BlockUnclesValidationRule.class);
        Mockito.when(unclesValidationRule.isValid(Mockito.any())).thenReturn(true);
        MinerServer minerServer = new MinerServerImpl(ethereumImpl, this.blockchain, null, this.blockchain.getPendingState(), blockchain.getRepository(), ConfigUtils.getDefaultMiningConfig(), unclesValidationRule, null, DIFFICULTY_CALCULATOR, new GasLimitCalculator(RskSystemProperties.CONFIG), new ProofOfWorkRule(RskSystemProperties.CONFIG));

        minerServer.start();

        MinerWork work = minerServer.getWork();

        assertEquals("0", work.getFeesPaidToMiner());
    }

    @Test
    public void initialWorkTurnsNotifyFlagOn() {
        EthereumImpl ethereumImpl = Mockito.mock(EthereumImpl.class);

        BlockUnclesValidationRule unclesValidationRule = Mockito.mock(BlockUnclesValidationRule.class);
        Mockito.when(unclesValidationRule.isValid(Mockito.any())).thenReturn(true);
        MinerServer minerServer = new MinerServerImpl(ethereumImpl, this.blockchain, null, this.blockchain.getPendingState(), blockchain.getRepository(), ConfigUtils.getDefaultMiningConfig(), unclesValidationRule, null, DIFFICULTY_CALCULATOR, new GasLimitCalculator(RskSystemProperties.CONFIG), new ProofOfWorkRule(RskSystemProperties.CONFIG));

        minerServer.start();

        MinerWork work = minerServer.getWork();

        assertEquals(true, work.getNotify());
    }

    @Test
    public void secondWorkWithNoChangesTurnsNotifyFlagOff() {
        EthereumImpl ethereumImpl = Mockito.mock(EthereumImpl.class);

        BlockUnclesValidationRule unclesValidationRule = Mockito.mock(BlockUnclesValidationRule.class);
        Mockito.when(unclesValidationRule.isValid(Mockito.any())).thenReturn(true);
        MinerServer minerServer = new MinerServerImpl(ethereumImpl, this.blockchain, null, this.blockchain.getPendingState(), blockchain.getRepository(), ConfigUtils.getDefaultMiningConfig(), unclesValidationRule, null, DIFFICULTY_CALCULATOR, new GasLimitCalculator(RskSystemProperties.CONFIG), new ProofOfWorkRule(RskSystemProperties.CONFIG));

        minerServer.start();

        MinerWork work = minerServer.getWork();

        assertEquals(true, work.getNotify());

        work = minerServer.getWork();

        assertEquals(false, work.getNotify());
    }

    @Test
    public void secondBuildBlockToMineTurnsNotifyFlagOff() {
        EthereumImpl ethereumImpl = Mockito.mock(EthereumImpl.class);

        BlockUnclesValidationRule unclesValidationRule = Mockito.mock(BlockUnclesValidationRule.class);
        Mockito.when(unclesValidationRule.isValid(Mockito.any())).thenReturn(true);
        MinerServer minerServer = new MinerServerImpl(ethereumImpl, this.blockchain, null, blockchain.getPendingState(), blockchain.getRepository(), ConfigUtils.getDefaultMiningConfig(), unclesValidationRule, null, DIFFICULTY_CALCULATOR, new GasLimitCalculator(RskSystemProperties.CONFIG), new ProofOfWorkRule(RskSystemProperties.CONFIG));

        minerServer.start();

        MinerWork work = minerServer.getWork();

        String hashForMergedMining = work.getBlockHashForMergedMining();

        minerServer.buildBlockToMine(blockchain.getBestBlock(), false);

        work = minerServer.getWork();
        assertEquals(hashForMergedMining, work.getBlockHashForMergedMining());
        assertEquals(false, work.getNotify());
    }

    @Test
    public void getCurrentTimeInMilliseconds() {
        long current = System.currentTimeMillis() / 1000;

        MinerServer server = new MinerServerImpl(null, null, null, null, null, ConfigUtils.getDefaultMiningConfig(), null, null, DIFFICULTY_CALCULATOR, new GasLimitCalculator(RskSystemProperties.CONFIG), new ProofOfWorkRule(RskSystemProperties.CONFIG));

        long result = server.getCurrentTimeInSeconds();

        Assert.assertTrue(result >= current);
        Assert.assertTrue(result <= current + 1);
    }

    @Test
    public void increaseTime() {
        long current = System.currentTimeMillis() / 1000;

        MinerServer server = new MinerServerImpl(null, null, null, null, null, ConfigUtils.getDefaultMiningConfig(), null, null, DIFFICULTY_CALCULATOR, new GasLimitCalculator(RskSystemProperties.CONFIG), new ProofOfWorkRule(RskSystemProperties.CONFIG));

        Assert.assertEquals(10, server.increaseTime(10));

        long result = server.getCurrentTimeInSeconds();

        Assert.assertTrue(result >= current + 10);
        Assert.assertTrue(result <= current + 11);
    }

    @Test
    public void increaseTimeUsingNegativeNumberHasNoEffect() {
        long current = System.currentTimeMillis() / 1000;

        MinerServer server = new MinerServerImpl(null, null, null, null, null, ConfigUtils.getDefaultMiningConfig(), null, null, DIFFICULTY_CALCULATOR, new GasLimitCalculator(RskSystemProperties.CONFIG), new ProofOfWorkRule(RskSystemProperties.CONFIG));

        Assert.assertEquals(0, server.increaseTime(-10));

        long result = server.getCurrentTimeInSeconds();

        Assert.assertTrue(result >= current);
    }

    @Test
    public void increaseTimeTwice() {
        long current = System.currentTimeMillis() / 1000;

        MinerServer server = new MinerServerImpl(null, null, null, null, null, ConfigUtils.getDefaultMiningConfig(), null, null, DIFFICULTY_CALCULATOR, new GasLimitCalculator(RskSystemProperties.CONFIG), new ProofOfWorkRule(RskSystemProperties.CONFIG));

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
