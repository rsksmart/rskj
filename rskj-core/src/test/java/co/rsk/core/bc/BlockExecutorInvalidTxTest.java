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
package co.rsk.core.bc;

import co.rsk.config.RskSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.db.RepositoryLocator;
import co.rsk.trie.Trie;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionExecutor;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.vm.program.ProgramResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for the mining safety net: when a transaction throws an unexpected
 * exception during block execution, the miner should be able to skip the
 * transaction, continue mining the rest of the block, and flag the transaction
 * for eviction from the mempool.
 */
@ExtendWith(MockitoExtension.class)
class BlockExecutorInvalidTxTest {

    @Mock
    private RepositoryLocator repositoryLocator;
    @Mock
    private TransactionExecutorFactory transactionExecutorFactory;
    @Mock
    private RskSystemProperties systemProperties;
    @Mock
    private ActivationConfig activationConfig;
    @Mock
    private ActivationConfig.ForBlock forBlock;
    @Mock
    private Repository track;

    private BlockExecutor blockExecutor;

    @BeforeEach
    void setUp() {
        when(systemProperties.getActivationConfig()).thenReturn(activationConfig);
        when(systemProperties.isRemascEnabled()).thenReturn(false);
        when(systemProperties.concurrentContractsDisallowed()).thenReturn(Collections.emptySet());
        Constants constants = Constants.regtest();
        when(systemProperties.getNetworkConstants()).thenReturn(constants);
        // track.startTracking() is called per-tx inside the execution loop; stub it so
        // txSubTrack is not null when rollback()/commit() are called on it.
        lenient().when(track.startTracking()).thenReturn(mock(Repository.class));

        blockExecutor = new BlockExecutor(repositoryLocator, transactionExecutorFactory, systemProperties);
    }

    @Test
    void executeForMining_postRSKIP144_whenTxThrows_shouldSkipTxAndContinue() {
        when(activationConfig.isActive(eq(ConsensusRule.RSKIP144), anyLong())).thenReturn(true);
        when(activationConfig.forBlock(anyLong())).thenReturn(forBlock);
        when(repositoryLocator.startTrackingAt(any(BlockHeader.class), any())).thenReturn(track);

        Transaction validTx1 = mockTransaction("tx1");
        Transaction poisonTx = mockTransaction("poison");
        Transaction validTx2 = mockTransaction("tx2");

        Block block = mockBlock(Arrays.asList(validTx1, poisonTx, validTx2));

        TransactionExecutor validExecutor1 = mockSuccessfulExecutor();
        TransactionExecutor poisonExecutor = mockThrowingExecutor();
        TransactionExecutor validExecutor2 = mockSuccessfulExecutor();

        when(transactionExecutorFactory.newInstance(
                eq(validTx1), anyInt(), any(), any(), any(), anyLong(), anyBoolean(), anyInt(), anySet(), anyBoolean(), anyLong()))
                .thenReturn(validExecutor1);
        when(transactionExecutorFactory.newInstance(
                eq(poisonTx), anyInt(), any(), any(), any(), anyLong(), anyBoolean(), anyInt(), anySet(), anyBoolean(), anyLong()))
                .thenReturn(poisonExecutor);
        when(transactionExecutorFactory.newInstance(
                eq(validTx2), anyInt(), any(), any(), any(), anyLong(), anyBoolean(), anyInt(), anySet(), anyBoolean(), anyLong()))
                .thenReturn(validExecutor2);

        BlockResult result = blockExecutor.executeForMining(block, block.getHeader(), true, false, false);

        assertNotSame(BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT, result);
        assertFalse(result.getInvalidTransactions().isEmpty());
        assertEquals(1, result.getInvalidTransactions().size());
        assertSame(poisonTx, result.getInvalidTransactions().get(0));
    }

    @Test
    void executeForMining_preRSKIP144_whenTxThrows_shouldSkipTxAndContinue() {
        when(activationConfig.isActive(eq(ConsensusRule.RSKIP144), anyLong())).thenReturn(false);
        when(activationConfig.forBlock(anyLong())).thenReturn(forBlock);
        when(repositoryLocator.startTrackingAt(any(BlockHeader.class))).thenReturn(track);
        when(track.getTrie()).thenReturn(mock(Trie.class));

        Transaction validTx = mockTransaction("tx1");
        Transaction poisonTx = mockTransaction("poison");

        Block block = mockBlock(Arrays.asList(validTx, poisonTx));

        TransactionExecutor validExecutor = mockSuccessfulExecutor();
        TransactionExecutor poisonExecutor = mockThrowingExecutor();

        when(transactionExecutorFactory.newInstance(
                eq(validTx), anyInt(), any(), any(), any(), anyLong(), anyBoolean(), anyInt(), anySet()))
                .thenReturn(validExecutor);
        when(transactionExecutorFactory.newInstance(
                eq(poisonTx), anyInt(), any(), any(), any(), anyLong(), anyBoolean(), anyInt(), anySet()))
                .thenReturn(poisonExecutor);

        BlockResult result = blockExecutor.executeForMining(block, block.getHeader(), true, false, false);

        assertNotSame(BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT, result);
        assertEquals(1, result.getInvalidTransactions().size());
        assertSame(poisonTx, result.getInvalidTransactions().get(0));
    }

    @Test
    void executeForMining_blockImport_whenTxThrows_shouldReturnInterrupted() {
        when(activationConfig.isActive(eq(ConsensusRule.RSKIP144), anyLong())).thenReturn(false);
        when(activationConfig.forBlock(anyLong())).thenReturn(forBlock);
        when(repositoryLocator.startTrackingAt(any(BlockHeader.class))).thenReturn(track);

        Transaction poisonTx = mockTransaction("poison");

        Block block = mockBlock(Collections.singletonList(poisonTx));

        TransactionExecutor poisonExecutor = mockThrowingExecutor();

        when(transactionExecutorFactory.newInstance(
                eq(poisonTx), anyInt(), any(), any(), any(), anyLong(), anyBoolean(), anyInt(), anySet()))
                .thenReturn(poisonExecutor);

        // discardInvalidTxs = false simulates block import behavior
        BlockResult result = blockExecutor.executeForMining(block, block.getHeader(), false, false, false);

        assertSame(BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT, result);
    }

    @Test
    void executeForMining_preRSKIP144_whenTxReturnsFalse_shouldNotBeInInvalidList() {
        when(activationConfig.isActive(eq(ConsensusRule.RSKIP144), anyLong())).thenReturn(false);
        when(activationConfig.forBlock(anyLong())).thenReturn(forBlock);
        when(repositoryLocator.startTrackingAt(any(BlockHeader.class))).thenReturn(track);
        when(track.getTrie()).thenReturn(mock(Trie.class));

        Transaction failingTx = mockTransaction("failing");
        Transaction poisonTx = mockTransaction("poison");
        Transaction validTx = mockTransaction("valid");

        Block block = mockBlock(Arrays.asList(failingTx, poisonTx, validTx));

        TransactionExecutor failingExecutor = mockFailingExecutor();
        TransactionExecutor poisonExecutor = mockThrowingExecutor();
        TransactionExecutor validExecutor = mockSuccessfulExecutor();

        when(transactionExecutorFactory.newInstance(
                eq(failingTx), anyInt(), any(), any(), any(), anyLong(), anyBoolean(), anyInt(), anySet()))
                .thenReturn(failingExecutor);
        when(transactionExecutorFactory.newInstance(
                eq(poisonTx), anyInt(), any(), any(), any(), anyLong(), anyBoolean(), anyInt(), anySet()))
                .thenReturn(poisonExecutor);
        when(transactionExecutorFactory.newInstance(
                eq(validTx), anyInt(), any(), any(), any(), anyLong(), anyBoolean(), anyInt(), anySet()))
                .thenReturn(validExecutor);

        BlockResult result = blockExecutor.executeForMining(block, block.getHeader(), true, false, false);

        assertNotSame(BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT, result);
        assertEquals(1, result.getInvalidTransactions().size());
        assertSame(poisonTx, result.getInvalidTransactions().get(0));
    }

    private Transaction mockTransaction(String name) {
        Transaction tx = mock(Transaction.class, name);
        lenient().when(tx.getHash()).thenReturn(new co.rsk.crypto.Keccak256(org.ethereum.TestUtils.generateBytes(name, 32)));
        lenient().when(tx.isRemascTransaction(anyInt(), anyInt())).thenReturn(false);
        lenient().when(tx.getGasLimit()).thenReturn(BigInteger.valueOf(21000).toByteArray());
        return tx;
    }

    private Block mockBlock(List<Transaction> txs) {
        Block block = mock(Block.class);
        BlockHeader header = mock(BlockHeader.class);
        when(block.getTransactionsList()).thenReturn(txs);
        when(block.getHeader()).thenReturn(header);
        when(block.getNumber()).thenReturn(1L);
        when(block.getCoinbase()).thenReturn(new RskAddress(new byte[20]));
        when(header.getNumber()).thenReturn(1L);
        lenient().when(header.getGasLimit()).thenReturn(new byte[]{0x7f, (byte) 0xff, (byte) 0xff, (byte) 0xff});
        lenient().when(block.getGasLimit()).thenReturn(new byte[]{0x7f, (byte) 0xff, (byte) 0xff, (byte) 0xff});
        return block;
    }

    private TransactionExecutor mockSuccessfulExecutor() {
        TransactionExecutor executor = mock(TransactionExecutor.class);
        when(executor.executeTransaction()).thenReturn(true);
        when(executor.getGasConsumed()).thenReturn(21000L);

        Coin paidFees = Coin.valueOf(21000L);
        when(executor.getPaidFees()).thenReturn(paidFees);

        ProgramResult programResult = mock(ProgramResult.class);
        when(programResult.getDeleteAccounts()).thenReturn(Collections.emptySet());
        when(executor.getResult()).thenReturn(programResult);

        TransactionReceipt receipt = mock(TransactionReceipt.class);
        when(receipt.isSuccessful()).thenReturn(true);
        lenient().when(receipt.getGasUsed()).thenReturn(new byte[]{});
        when(receipt.getStatus()).thenReturn(new byte[]{0x01});
        when(executor.getReceipt()).thenReturn(receipt);
        when(executor.getVMLogs()).thenReturn(Collections.emptyList());
        lenient().when(executor.precompiledContractsCalled()).thenReturn(Collections.emptySet());

        return executor;
    }

    private TransactionExecutor mockThrowingExecutor() {
        TransactionExecutor executor = mock(TransactionExecutor.class);
        when(executor.executeTransaction()).thenThrow(new NullPointerException("Simulated precompile NPE"));
        return executor;
    }

    private TransactionExecutor mockFailingExecutor() {
        TransactionExecutor executor = mock(TransactionExecutor.class);
        when(executor.executeTransaction()).thenReturn(false);
        return executor;
    }
}
