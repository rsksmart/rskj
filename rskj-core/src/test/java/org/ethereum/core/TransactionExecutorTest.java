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
package org.ethereum.core;

import co.rsk.config.VmConfig;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import org.ethereum.TestUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.BlockStore;
import org.ethereum.db.MutableRepository;
import org.ethereum.db.ReceiptStore;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TransactionExecutorTest {

    private static final int MAX_CACHE_SIZE = 900;
    private ActivationConfig activationConfig;
    private Constants constants;
    private RskAddress rskAddress;
    private Repository repository;
    private BlockStore blockStore;
    private ReceiptStore receiptStore;
    private BlockFactory blockFactory;
    private ProgramInvokeFactory programInvokeFactory;
    private Block executionBlock;
    private VmConfig vmConfig;
    private PrecompiledContracts precompiledContracts;
    private Set<DataWord> deletedAccounts;
    private int txIndex;
    private long gasUsedInTheBlock;

    @BeforeEach
    void setUp() {
        // paperwork: mock a whole nice transaction executor
        txIndex = 1;
        gasUsedInTheBlock = 0;

        activationConfig = ActivationConfigsForTest.all();
        constants = mock(Constants.class);
        rskAddress = mock(RskAddress.class);
        repository = mock(Repository.class);
        blockStore = mock(BlockStore.class);
        receiptStore = mock(ReceiptStore.class);
        blockFactory = mock(BlockFactory.class);
        programInvokeFactory = mock(ProgramInvokeFactory.class);
        executionBlock = mock(Block.class);
        vmConfig = mock(VmConfig.class);
        precompiledContracts = mock(PrecompiledContracts.class);
        deletedAccounts = new HashSet<>();
        when(executionBlock.getNumber()).thenReturn(10L);
    }

    @Test
    void testInitHandlesFreeTransactionsOK() {

        BlockTxSignatureCache blockTxSignatureCache = mock(BlockTxSignatureCache.class);
        Transaction transaction = mock(Transaction.class);
        TransactionExecutor txExecutor = new TransactionExecutor(
                constants, activationConfig, transaction, txIndex, rskAddress,
                repository, blockStore, receiptStore, blockFactory,
                programInvokeFactory, executionBlock, gasUsedInTheBlock, vmConfig,
                true, precompiledContracts, deletedAccounts,
                blockTxSignatureCache
        );


        // paperwork: transaction has high gas limit, execution block has normal gas limit
        // and the nonces are okey
        when(transaction.getGasLimit()).thenReturn(BigInteger.valueOf(4000000).toByteArray());
        when(executionBlock.getGasLimit()).thenReturn(BigInteger.valueOf(6800000).toByteArray());
        when(repository.getNonce(transaction.getSender())).thenReturn(BigInteger.valueOf(1L));
        when(transaction.getNonce()).thenReturn(BigInteger.valueOf(1L).toByteArray());
        // more paperwork, the receiver is just someone
        RskAddress receiver = new RskAddress("0000000000000000000000000000000000000001");
        when(transaction.getReceiveAddress()).thenReturn(receiver);
        when(transaction.acceptTransactionSignature(constants.getChainId())).thenReturn(true);
        // sender has no balance
        when(repository.getBalance(transaction.getSender())).thenReturn(new Coin(BigInteger.valueOf(0L)));
        // but is sending some nice value over the wire
        when(transaction.getValue()).thenReturn(new Coin(BigInteger.valueOf(68000)));
        // note that the transaction is free of cost
        assertEquals(0, transaction.transactionCost(constants, activationConfig.forBlock(executionBlock.getNumber()), new BlockTxSignatureCache(new ReceivedTxSignatureCache())));
        assertFalse(txExecutor.executeTransaction());
    }

    @Test
    void txInBlockIsExecutedAndShouldBeAddedInCache(){
        ReceivedTxSignatureCache receivedTxSignatureCache = mock(ReceivedTxSignatureCache.class);
        BlockTxSignatureCache blockTxSignatureCache = new BlockTxSignatureCache(receivedTxSignatureCache);
        MutableRepository cacheTrack = mock(MutableRepository.class);

        when(repository.startTracking()).thenReturn(cacheTrack);

        RskAddress sender = new RskAddress("0000000000000000000000000000000000000001");
        RskAddress receiver = new RskAddress("0000000000000000000000000000000000000002");
        byte[] gasLimit = BigInteger.valueOf(4000000).toByteArray();
        byte[] txNonce = BigInteger.valueOf(1L).toByteArray();
        Coin gasPrice = Coin.valueOf(1);
        Coin value = new Coin(BigInteger.valueOf(2));

        when(repository.getNonce(sender)).thenReturn(BigInteger.valueOf(1L));
        when(repository.getBalance(sender)).thenReturn(new Coin(BigInteger.valueOf(68000L)));
        Transaction transaction = getTransaction(sender, receiver, gasLimit, txNonce, gasPrice, value);

        assertTrue(executeValidTransaction(transaction, blockTxSignatureCache));
        assertNotNull(blockTxSignatureCache.getSender(transaction));
        assertArrayEquals(blockTxSignatureCache.getSender(transaction).getBytes(), sender.getBytes());
    }

    @Test
    void TwoTxsAreInBlockAndThemShouldBeContainedInCache() {
        ReceivedTxSignatureCache receivedTxSignatureCache = mock(ReceivedTxSignatureCache.class);
        BlockTxSignatureCache blockTxSignatureCache = new BlockTxSignatureCache(receivedTxSignatureCache);
        MutableRepository cacheTrack = mock(MutableRepository.class);
        when(repository.startTracking()).thenReturn(cacheTrack);

        RskAddress sender = new RskAddress("0000000000000000000000000000000000000001");
        RskAddress sender2 = new RskAddress("0000000000000000000000000000000000000003");
        RskAddress receiver = new RskAddress("0000000000000000000000000000000000000002");
        byte[] gasLimit = BigInteger.valueOf(4000000).toByteArray();
        byte[] txNonce = BigInteger.valueOf(1L).toByteArray();
        Coin gasPrice = Coin.valueOf(1);
        Coin value = new Coin(BigInteger.valueOf(2));

        when(repository.getNonce(sender)).thenReturn(BigInteger.valueOf(1L));
        when(repository.getBalance(sender)).thenReturn(new Coin(BigInteger.valueOf(68000L)));
        Transaction transaction = getTransaction(sender, receiver, gasLimit, txNonce, gasPrice, value, 1);

        assertTrue(executeValidTransaction(transaction, blockTxSignatureCache));

        when(repository.getNonce(sender2)).thenReturn(BigInteger.valueOf(1L));
        when(repository.getBalance(sender2)).thenReturn(new Coin(BigInteger.valueOf(68000L)));
        Transaction transaction2 = getTransaction(sender2, receiver, gasLimit, txNonce, gasPrice, value, 2);

        assertTrue(executeValidTransaction(transaction2, blockTxSignatureCache));

        assertNotNull(blockTxSignatureCache.getSender(transaction));
        assertArrayEquals(blockTxSignatureCache.getSender(transaction).getBytes(), sender.getBytes());

        assertNotNull(blockTxSignatureCache.getSender(transaction2));
        assertArrayEquals(blockTxSignatureCache.getSender(transaction2).getBytes(), sender2.getBytes());
    }

    @Test
    void InvalidTxsIsInBlockAndShouldntBeInCache(){
        ReceivedTxSignatureCache receivedTxSignatureCache = mock(ReceivedTxSignatureCache.class);
        BlockTxSignatureCache blockTxSignatureCache = new BlockTxSignatureCache(receivedTxSignatureCache);
        MutableRepository cacheTrack = mock(MutableRepository.class);

        when(repository.startTracking()).thenReturn(cacheTrack);

        RskAddress sender = new RskAddress("0000000000000000000000000000000000000001");
        RskAddress receiver = new RskAddress("0000000000000000000000000000000000000002");
        byte[] gasLimit = BigInteger.valueOf(4000000).toByteArray();
        byte[] txNonce = BigInteger.valueOf(1L).toByteArray();
        Coin gasPrice = Coin.valueOf(1);
        Coin value = new Coin(BigInteger.valueOf(2));

        Transaction transaction = getTransaction(sender, receiver, gasLimit, txNonce, gasPrice, value);
        when(executionBlock.getGasLimit()).thenReturn(BigInteger.valueOf(6800000).toByteArray());
        when(repository.getNonce(sender)).thenReturn(BigInteger.valueOf(1L));
        when(repository.getBalance(sender)).thenReturn(new Coin(BigInteger.valueOf(0L)));

        TransactionExecutor txExecutor = new TransactionExecutor(
                constants, activationConfig, transaction, txIndex, rskAddress,
                repository, blockStore, receiptStore, blockFactory,
                programInvokeFactory, executionBlock, gasUsedInTheBlock, vmConfig,
                true, precompiledContracts, deletedAccounts,
                blockTxSignatureCache
        );

        assertEquals(0, transaction.transactionCost(constants, activationConfig.forBlock(executionBlock.getNumber()), new BlockTxSignatureCache(new ReceivedTxSignatureCache())));
        assertFalse(txExecutor.executeTransaction());
        assertNotNull(blockTxSignatureCache.getSender(transaction));
    }

    @Test
    void remascTxIsReceivedAndShouldntBeInCache(){
        ReceivedTxSignatureCache receivedTxSignatureCache = mock(ReceivedTxSignatureCache.class);
        BlockTxSignatureCache blockTxSignatureCache = new BlockTxSignatureCache(receivedTxSignatureCache);
        MutableRepository cacheTrack = mock(MutableRepository.class);

        when(repository.startTracking()).thenReturn(cacheTrack);

        RskAddress sender = PrecompiledContracts.REMASC_ADDR;
        RskAddress receiver = new RskAddress("0000000000000000000000000000000000000002");
        byte[] gasLimit = BigInteger.valueOf(4000000).toByteArray();
        byte[] txNonce = BigInteger.valueOf(1L).toByteArray();
        Coin gasPrice = Coin.valueOf(1);
        Coin value = new Coin(BigInteger.valueOf(2));

        Transaction transaction = getTransaction(sender, receiver, gasLimit, txNonce, gasPrice, value);
        when(executionBlock.getGasLimit()).thenReturn(BigInteger.valueOf(6800000).toByteArray());
        when(repository.getNonce(sender)).thenReturn(BigInteger.valueOf(1L));
        when(repository.getBalance(sender)).thenReturn(new Coin(BigInteger.valueOf(0L)));

        TransactionExecutor txExecutor = new TransactionExecutor(
                constants, activationConfig, transaction, txIndex, rskAddress,
                repository, blockStore, receiptStore, blockFactory,
                programInvokeFactory, executionBlock, gasUsedInTheBlock, vmConfig,
                true, precompiledContracts, deletedAccounts,
                blockTxSignatureCache
        );

        assertEquals(0, transaction.transactionCost(constants, activationConfig.forBlock(executionBlock.getNumber()), new BlockTxSignatureCache(new ReceivedTxSignatureCache())));
        assertFalse(txExecutor.executeTransaction());
        assertNotNull(blockTxSignatureCache.getSender(transaction));
    }

    @Test
    void txInBlockIsReceivedAndShouldBeUsedInTxExecutorInsteadOfComputeSender(){
        ReceivedTxSignatureCache receivedTxSignatureCache = mock(ReceivedTxSignatureCache.class);
        BlockTxSignatureCache blockTxSignatureCache = new BlockTxSignatureCache(receivedTxSignatureCache);
        MutableRepository cacheTrack = mock(MutableRepository.class);

        when(repository.startTracking()).thenReturn(cacheTrack);

        RskAddress sender = new RskAddress("0000000000000000000000000000000000000001");
        RskAddress receiver = new RskAddress("0000000000000000000000000000000000000002");
        byte[] gasLimit = BigInteger.valueOf(4000000).toByteArray();
        byte[] txNonce = BigInteger.valueOf(1L).toByteArray();
        Coin gasPrice = Coin.valueOf(1);
        Coin value = new Coin(BigInteger.valueOf(2));

        when(repository.getNonce(sender)).thenReturn(BigInteger.valueOf(1L));
        when(repository.getBalance(sender)).thenReturn(new Coin(BigInteger.valueOf(68000L)));
        Transaction transaction = getTransaction(sender, receiver, gasLimit, txNonce, gasPrice, value);
        when(receivedTxSignatureCache.getSender(transaction)).thenReturn(sender);

        assertTrue(executeValidTransaction(transaction, blockTxSignatureCache));
        assertTrue(executeValidTransaction(transaction, blockTxSignatureCache)); //Execute two times the same tx
        verify(receivedTxSignatureCache, times(1)).getSender(transaction);
        assertArrayEquals(blockTxSignatureCache.getSender(transaction).getBytes(), sender.getBytes());
    }

    @Test
    void firstTxIsRemovedWhenTheCacheLimitSizeIsExceeded() {
        ReceivedTxSignatureCache receivedTxSignatureCache = mock(ReceivedTxSignatureCache.class);
        BlockTxSignatureCache blockTxSignatureCache = new BlockTxSignatureCache(receivedTxSignatureCache);
        MutableRepository cacheTrack = mock(MutableRepository.class);

        when(repository.startTracking()).thenReturn(cacheTrack);

        RskAddress sender = new RskAddress("0000000000000000000000000000000000000001");
        RskAddress receiver = new RskAddress("0000000000000000000000000000000000000002");
        byte[] gasLimit = BigInteger.valueOf(4000000).toByteArray();
        byte[] txNonce = BigInteger.valueOf(1L).toByteArray();
        Coin gasPrice = Coin.valueOf(1);
        Coin value = new Coin(BigInteger.valueOf(2));

        when(repository.getNonce(sender)).thenReturn(BigInteger.valueOf(1L));
        when(repository.getBalance(sender)).thenReturn(new Coin(BigInteger.valueOf(68000L)));
        Transaction transaction = getTransaction(sender, receiver, gasLimit, txNonce, gasPrice, value, -1);
        assertTrue(executeValidTransaction(transaction, blockTxSignatureCache));

        for (int i = 0; i < MAX_CACHE_SIZE; i++) {
            if (i == MAX_CACHE_SIZE - 1) {
                assertNotNull(blockTxSignatureCache.getSender(transaction));
            }
            sender = new RskAddress(TestUtils.randomAddress().getBytes());
            when(repository.getNonce(sender)).thenReturn(BigInteger.valueOf(1L));
            when(repository.getBalance(sender)).thenReturn(new Coin(BigInteger.valueOf(68000L)));
            Transaction transactionAux = getTransaction(sender, receiver, gasLimit, txNonce, gasPrice, value, i);
            assertTrue(executeValidTransaction(transactionAux, blockTxSignatureCache));
        }

        assertNotNull(blockTxSignatureCache.getSender(transaction));
    }

    private boolean executeValidTransaction(Transaction transaction, BlockTxSignatureCache blockTxSignatureCache) {
        when(executionBlock.getGasLimit()).thenReturn(BigInteger.valueOf(6800000).toByteArray());

        TransactionExecutor txExecutor = new TransactionExecutor(
                constants, activationConfig, transaction, txIndex, rskAddress,
                repository, blockStore, receiptStore, blockFactory,
                programInvokeFactory, executionBlock, gasUsedInTheBlock, vmConfig,
                true, precompiledContracts, deletedAccounts,
                blockTxSignatureCache
        );

        return txExecutor.executeTransaction();
    }

    private Transaction getTransaction(RskAddress sender, RskAddress receiver, byte[] gasLimit, byte[] txNonce, Coin gasPrice, Coin value) {
        Transaction transaction = mock(Transaction.class);
        when(transaction.getSender()).thenReturn(sender);
        when(transaction.getGasPrice()).thenReturn(gasPrice);
        when(transaction.getGasLimit()).thenReturn(gasLimit);
        when(transaction.getSender(any())).thenCallRealMethod();
        when(transaction.getNonce()).thenReturn(txNonce);
        when(transaction.getReceiveAddress()).thenReturn(receiver);
        when(transaction.acceptTransactionSignature(constants.getChainId())).thenReturn(true);
        when(transaction.getValue()).thenReturn(value);
        return transaction;
    }

    private Transaction getTransaction(RskAddress sender, RskAddress receiver, byte[] gasLimit, byte[] txNonce, Coin gasPrice, Coin value, int hashSeed) {
        Transaction transaction = getTransaction(sender, receiver, gasLimit, txNonce, gasPrice, value);
        when(transaction.getHash()).thenReturn(new Keccak256(HashUtil.keccak256(BigInteger.valueOf(hashSeed).toByteArray())));
        return transaction;
    }

}
