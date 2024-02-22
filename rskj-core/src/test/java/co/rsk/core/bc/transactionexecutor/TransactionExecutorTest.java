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
package co.rsk.core.bc.transactionexecutor;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.crypto.Keccak256;
import co.rsk.pcc.environment.Environment;
import org.ethereum.TestUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.*;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.BlockStore;
import org.ethereum.db.MutableRepository;
import org.ethereum.db.ReceiptStore;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContractArgs;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigInteger;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TransactionExecutorTest {

    private static final int MAX_CACHE_SIZE = 900;
    private ActivationConfig activationConfig;
    private Constants constants;
    private Repository repository;
    private BlockStore blockStore;
    private ReceiptStore receiptStore;
    private BlockFactory blockFactory;
    private ProgramInvokeFactory programInvokeFactory;
    private Block executionBlock;
    private PrecompiledContracts precompiledContracts;
    private int txIndex;
    private TestSystemProperties config;
    private RskAddress receiver;
    private RskAddress sender;

    @BeforeEach
    void setUp() {
        // paperwork: mock a whole nice transaction executor
        receiver = new RskAddress("0000000000000000000000000000000000000002");
        sender = new RskAddress("0000000000000000000000000000000000000001");
        txIndex = 1;
        activationConfig = ActivationConfigsForTest.all();
        constants = mock(Constants.class);
        repository = mock(Repository.class);
        blockStore = mock(BlockStore.class);
        receiptStore = mock(ReceiptStore.class);
        blockFactory = mock(BlockFactory.class);
        programInvokeFactory = mock(ProgramInvokeFactory.class);
        executionBlock = mock(Block.class);
        precompiledContracts = mock(PrecompiledContracts.class);
        config = spy(new TestSystemProperties());
        when(config.getActivationConfig()).thenReturn(activationConfig);
        when(config.getNetworkConstants()).thenReturn(constants);
        when(executionBlock.getNumber()).thenReturn(10L);
    }

    @Test
    void testInitHandlesFreeTransactionsOK() {
        BlockTxSignatureCache blockTxSignatureCache = mock(BlockTxSignatureCache.class);
        Transaction transaction = mock(Transaction.class);
        // paperwork: transaction has high gas limit, execution block has normal gas limit
        // and the nonces are okey
        when(transaction.getGasLimit()).thenReturn(BigInteger.valueOf(4000000).toByteArray());
        when(executionBlock.getGasLimit()).thenReturn(BigInteger.valueOf(6800000).toByteArray());

        TransactionExecutorFactory transactionExecutorFactory = new TransactionExecutorFactory(config, blockStore, receiptStore, blockFactory, programInvokeFactory, precompiledContracts, blockTxSignatureCache);
        TransactionExecutor txExecutor = transactionExecutorFactory.newInstance(transaction, txIndex, executionBlock.getCoinbase(), repository, executionBlock, 0L);

        when(repository.getNonce(transaction.getSender())).thenReturn(BigInteger.valueOf(1L));
        when(transaction.getNonce()).thenReturn(BigInteger.valueOf(1L).toByteArray());
        // more paperwork, the receiver is just someone
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

        byte[] gasLimit = BigInteger.valueOf(4000000).toByteArray();
        byte[] txNonce = BigInteger.valueOf(1L).toByteArray();
        Coin gasPrice = Coin.valueOf(1);
        Coin value = new Coin(BigInteger.valueOf(2));

        when(repository.startTracking()).thenReturn(cacheTrack);
        mockRepositoryForAnAccountWithBalance(sender, 68000L);
        when(executionBlock.getGasLimit()).thenReturn(BigInteger.valueOf(6800000).toByteArray());

        Transaction transaction = getTransaction(sender, receiver, gasLimit, txNonce, gasPrice, value);
        TransactionExecutorFactory transactionExecutorFactory = new TransactionExecutorFactory(config, blockStore, receiptStore, blockFactory, programInvokeFactory, precompiledContracts, blockTxSignatureCache);
        TransactionExecutor txExecutor = transactionExecutorFactory.newInstance(transaction, txIndex, executionBlock.getCoinbase(), repository, executionBlock, 0L);

        assertTrue(txExecutor.executeTransaction());
        assertNotNull(blockTxSignatureCache.getSender(transaction));
        assertArrayEquals(blockTxSignatureCache.getSender(transaction).getBytes(), sender.getBytes());
    }

    @Test
    void TwoTxsAreInBlockAndThemShouldBeContainedInCache() {
        ReceivedTxSignatureCache receivedTxSignatureCache = mock(ReceivedTxSignatureCache.class);
        BlockTxSignatureCache blockTxSignatureCache = new BlockTxSignatureCache(receivedTxSignatureCache);
        MutableRepository cacheTrack = mock(MutableRepository.class);

        RskAddress sender2 = new RskAddress("0000000000000000000000000000000000000003");
        byte[] gasLimit = BigInteger.valueOf(4000000).toByteArray();
        byte[] txNonce = BigInteger.valueOf(1L).toByteArray();
        Coin gasPrice = Coin.valueOf(1);
        Coin value = new Coin(BigInteger.valueOf(2));

        when(repository.startTracking()).thenReturn(cacheTrack);
        mockRepositoryForAnAccountWithBalance(sender, 68000L);
        mockRepositoryForAnAccountWithBalance(sender2, 68000L);
        when(executionBlock.getGasLimit()).thenReturn(BigInteger.valueOf(6800000).toByteArray());

        Transaction transaction = getTransaction(sender, receiver, gasLimit, txNonce, gasPrice, value, 1);
        TransactionExecutorFactory transactionExecutorFactory = new TransactionExecutorFactory(config, blockStore, receiptStore, blockFactory, programInvokeFactory, precompiledContracts, blockTxSignatureCache);
        TransactionExecutor txExecutor = transactionExecutorFactory.newInstance(transaction, txIndex++, executionBlock.getCoinbase(), repository, executionBlock, 0L);
        assertTrue(txExecutor.executeTransaction());

        Transaction transaction2 = getTransaction(sender2, receiver, gasLimit, txNonce, gasPrice, value, 2);
        when(executionBlock.getGasLimit()).thenReturn(BigInteger.valueOf(6800000).toByteArray());
        TransactionExecutor txExecutor1 = transactionExecutorFactory.newInstance(transaction2, txIndex, executionBlock.getCoinbase(), repository, executionBlock, 0L);
        assertTrue(txExecutor1.executeTransaction());

        assertNotNull(blockTxSignatureCache.getSender(transaction));
        assertArrayEquals(blockTxSignatureCache.getSender(transaction).getBytes(), sender.getBytes());

        assertNotNull(blockTxSignatureCache.getSender(transaction2));
        assertArrayEquals(blockTxSignatureCache.getSender(transaction2).getBytes(), sender2.getBytes());
    }

    @Test
    void PrecompiledContractInitShouldBeCalledWithCacheTrack() {
        ReceivedTxSignatureCache receivedTxSignatureCache = mock(ReceivedTxSignatureCache.class);
        BlockTxSignatureCache blockTxSignatureCache = new BlockTxSignatureCache(receivedTxSignatureCache);
        MutableRepository cacheTrack = mock(MutableRepository.class);
        PrecompiledContracts.PrecompiledContract precompiledContract = mock(PrecompiledContracts.PrecompiledContract.class);

        when(repository.startTracking()).thenReturn(cacheTrack);

        RskAddress sender = new RskAddress("0000000000000000000000000000000000000001");
        RskAddress receiver = new RskAddress("0000000000000000000000000000000000000002");
        byte[] gasLimit = BigInteger.valueOf(4000000).toByteArray();
        byte[] txNonce = BigInteger.valueOf(1L).toByteArray();
        Coin gasPrice = Coin.valueOf(1);
        Coin value = new Coin(BigInteger.valueOf(2));


        when(precompiledContracts.getContractForAddress(any(ActivationConfig.ForBlock.class), eq(DataWord.valueOf(receiver.getBytes())))).thenReturn(precompiledContract);
        when(repository.getNonce(sender)).thenReturn(BigInteger.valueOf(1L));
        when(repository.getBalance(sender)).thenReturn(new Coin(BigInteger.valueOf(68000L)));
        when(executionBlock.getGasLimit()).thenReturn(BigInteger.valueOf(6800000).toByteArray());

        Transaction transaction = getTransaction(sender, receiver, gasLimit, txNonce, gasPrice, value, 1);

        TransactionExecutorFactory transactionExecutorFactory = new TransactionExecutorFactory(config, blockStore, receiptStore, blockFactory, programInvokeFactory, precompiledContracts, blockTxSignatureCache);
        TransactionExecutor txExecutor = transactionExecutorFactory.newInstance(transaction, txIndex++, executionBlock.getCoinbase(), repository, executionBlock, 0L);
        assertTrue(txExecutor.executeTransaction());

        ArgumentCaptor<PrecompiledContractArgs> argsCaptor = ArgumentCaptor.forClass(PrecompiledContractArgs.class);

        verify(precompiledContract, times(1)).init(argsCaptor.capture());

        assertEquals(cacheTrack, argsCaptor.getValue().getRepository());
    }

    @Test
    void InvalidTxsIsInBlockAndShouldntBeInCache() {
        ReceivedTxSignatureCache receivedTxSignatureCache = mock(ReceivedTxSignatureCache.class);
        BlockTxSignatureCache blockTxSignatureCache = new BlockTxSignatureCache(receivedTxSignatureCache);
        MutableRepository cacheTrack = mock(MutableRepository.class);

        byte[] gasLimit = BigInteger.valueOf(4000000).toByteArray();
        byte[] txNonce = BigInteger.valueOf(1L).toByteArray();
        Coin gasPrice = Coin.valueOf(1);
        Coin value = new Coin(BigInteger.valueOf(2));

        when(repository.startTracking()).thenReturn(cacheTrack);
        mockRepositoryForAnAccountWithBalance(sender, 0L);
        when(executionBlock.getGasLimit()).thenReturn(BigInteger.valueOf(6800000).toByteArray());

        Transaction transaction = getTransaction(sender, receiver, gasLimit, txNonce, gasPrice, value);
        TransactionExecutorFactory transactionExecutorFactory = new TransactionExecutorFactory(config, blockStore, receiptStore, blockFactory, programInvokeFactory, precompiledContracts, blockTxSignatureCache);
        TransactionExecutor txExecutor = transactionExecutorFactory.newInstance(transaction, txIndex, executionBlock.getCoinbase(), repository, executionBlock, 0L);

        assertEquals(0, transaction.transactionCost(constants, activationConfig.forBlock(executionBlock.getNumber()), new BlockTxSignatureCache(new ReceivedTxSignatureCache())));
        assertFalse(txExecutor.executeTransaction());
        assertNotNull(blockTxSignatureCache.getSender(transaction));
    }

    @Test
    void remascTxIsReceivedAndShouldntBeInCache(){
        ReceivedTxSignatureCache receivedTxSignatureCache = mock(ReceivedTxSignatureCache.class);
        BlockTxSignatureCache blockTxSignatureCache = new BlockTxSignatureCache(receivedTxSignatureCache);
        MutableRepository cacheTrack = mock(MutableRepository.class);

        RskAddress sender = PrecompiledContracts.REMASC_ADDR;
        byte[] gasLimit = BigInteger.valueOf(4000000).toByteArray();
        byte[] txNonce = BigInteger.valueOf(1L).toByteArray();
        Coin gasPrice = Coin.valueOf(1);
        Coin value = new Coin(BigInteger.valueOf(2));

        when(repository.startTracking()).thenReturn(cacheTrack);
        mockRepositoryForAnAccountWithBalance(sender, 0L);
        when(executionBlock.getGasLimit()).thenReturn(BigInteger.valueOf(6800000).toByteArray());

        Transaction transaction = getTransaction(sender, receiver, gasLimit, txNonce, gasPrice, value);
        TransactionExecutorFactory transactionExecutorFactory = new TransactionExecutorFactory(config, blockStore, receiptStore, blockFactory, programInvokeFactory, precompiledContracts, blockTxSignatureCache);
        TransactionExecutor txExecutor = transactionExecutorFactory.newInstance(transaction, txIndex, executionBlock.getCoinbase(), repository, executionBlock, 0L);

        assertEquals(0, transaction.transactionCost(constants, activationConfig.forBlock(executionBlock.getNumber()), new BlockTxSignatureCache(new ReceivedTxSignatureCache())));
        assertFalse(txExecutor.executeTransaction());
        assertNotNull(blockTxSignatureCache.getSender(transaction));
    }

    @Test
    void txInBlockIsReceivedAndShouldBeUsedInTxExecutorInsteadOfComputeSender(){
        ReceivedTxSignatureCache receivedTxSignatureCache = mock(ReceivedTxSignatureCache.class);
        BlockTxSignatureCache blockTxSignatureCache = new BlockTxSignatureCache(receivedTxSignatureCache);
        MutableRepository cacheTrack = mock(MutableRepository.class);

        byte[] gasLimit = BigInteger.valueOf(4000000).toByteArray();
        byte[] txNonce = BigInteger.valueOf(1L).toByteArray();
        Coin gasPrice = Coin.valueOf(1);
        Coin value = new Coin(BigInteger.valueOf(2));

        when(repository.startTracking()).thenReturn(cacheTrack);
        mockRepositoryForAnAccountWithBalance(sender, 68000L);
        when(executionBlock.getGasLimit()).thenReturn(BigInteger.valueOf(6800000).toByteArray());

        Transaction transaction = getTransaction(sender, receiver, gasLimit, txNonce, gasPrice, value);
        when(receivedTxSignatureCache.getSender(transaction)).thenReturn(sender);

        TransactionExecutorFactory transactionExecutorFactory = new TransactionExecutorFactory(config, blockStore, receiptStore, blockFactory, programInvokeFactory, precompiledContracts, blockTxSignatureCache);
        TransactionExecutor txExecutor = transactionExecutorFactory.newInstance(transaction, txIndex++, executionBlock.getCoinbase(), repository, executionBlock, 0L);
        assertTrue(txExecutor.executeTransaction());

        TransactionExecutor txExecutor1 = transactionExecutorFactory.newInstance(transaction, txIndex, executionBlock.getCoinbase(), repository, executionBlock, 0L);
        assertTrue(txExecutor1.executeTransaction()); //Execute two times the same tx

        verify(receivedTxSignatureCache, times(1)).getSender(transaction);
        assertArrayEquals(blockTxSignatureCache.getSender(transaction).getBytes(), sender.getBytes());
    }

    @Test
    void firstTxIsRemovedWhenTheCacheLimitSizeIsExceeded() {
        ReceivedTxSignatureCache receivedTxSignatureCache = mock(ReceivedTxSignatureCache.class);
        BlockTxSignatureCache blockTxSignatureCache = new BlockTxSignatureCache(receivedTxSignatureCache);
        MutableRepository cacheTrack = mock(MutableRepository.class);


        byte[] gasLimit = BigInteger.valueOf(4000000).toByteArray();
        byte[] txNonce = BigInteger.valueOf(1L).toByteArray();
        Coin gasPrice = Coin.valueOf(1);
        Coin value = new Coin(BigInteger.valueOf(2));

        when(repository.startTracking()).thenReturn(cacheTrack);
        mockRepositoryForAnAccountWithBalance(sender, 68000L);
        when(executionBlock.getGasLimit()).thenReturn(BigInteger.valueOf(6800000).toByteArray());

        Transaction transaction = getTransaction(sender, receiver, gasLimit, txNonce, gasPrice, value, -1);

        TransactionExecutorFactory transactionExecutorFactory = new TransactionExecutorFactory(config, blockStore, receiptStore, blockFactory, programInvokeFactory, precompiledContracts, blockTxSignatureCache);
        TransactionExecutor txExecutor = transactionExecutorFactory.newInstance(transaction, txIndex++, executionBlock.getCoinbase(), repository, executionBlock, 0L);
        assertTrue(txExecutor.executeTransaction());

        Random random = new Random(TransactionExecutorTest.class.hashCode());
        for (int i = 0; i < MAX_CACHE_SIZE; i++) {
            if (i == MAX_CACHE_SIZE - 1) {
                assertNotNull(blockTxSignatureCache.getSender(transaction));
            }
            sender = new RskAddress(TestUtils.generateBytesFromRandom(random,20));
            mockRepositoryForAnAccountWithBalance(sender, 68000L);
            when(executionBlock.getGasLimit()).thenReturn(BigInteger.valueOf(6800000).toByteArray());

            Transaction transactionAux = getTransaction(sender, receiver, gasLimit, txNonce, gasPrice, value, i);
            txExecutor = transactionExecutorFactory.newInstance(transactionAux, txIndex++, executionBlock.getCoinbase(), repository, executionBlock, 0L);
            assertTrue(txExecutor.executeTransaction());
        }

        assertNotNull(blockTxSignatureCache.getSender(transaction));
    }

    @Test
    void callInitFromPrecompiledContract() {
        PrecompiledContracts.PrecompiledContract precompiledContract = spy(new Environment(activationConfig, PrecompiledContracts.ENVIRONMENT_ADDR));
        when(precompiledContracts.getContractForAddress(any(ActivationConfig.ForBlock.class), eq(PrecompiledContracts.ENVIRONMENT_ADDR_DW)))
                .thenReturn(precompiledContract);

        ReceivedTxSignatureCache receivedTxSignatureCache = mock(ReceivedTxSignatureCache.class);
        BlockTxSignatureCache blockTxSignatureCache = new BlockTxSignatureCache(receivedTxSignatureCache);
        MutableRepository cacheTrack = mock(MutableRepository.class);
        when(repository.startTracking()).thenReturn(cacheTrack);

        RskAddress sender = new RskAddress("0000000000000000000000000000000000000001");
        RskAddress receiver = new RskAddress(PrecompiledContracts.ENVIRONMENT_ADDR_STR);
        byte[] gasLimit = BigInteger.valueOf(4000000).toByteArray();
        byte[] txNonce = BigInteger.valueOf(1L).toByteArray();
        Coin gasPrice = Coin.valueOf(1);
        Coin value = new Coin(BigInteger.valueOf(2));

        when(repository.getNonce(sender)).thenReturn(BigInteger.valueOf(1L));
        when(repository.getBalance(sender)).thenReturn(new Coin(BigInteger.valueOf(68000L)));
        when(executionBlock.getGasLimit()).thenReturn(BigInteger.valueOf(6800000).toByteArray());

        Transaction transaction = getTransaction(sender, receiver, gasLimit, txNonce, gasPrice, value, 1);

        TransactionExecutorFactory transactionExecutorFactory = new TransactionExecutorFactory(config, blockStore, receiptStore, blockFactory, programInvokeFactory, precompiledContracts, blockTxSignatureCache);
        TransactionExecutor txExecutor = transactionExecutorFactory.newInstance(transaction, txIndex++, executionBlock.getCoinbase(), repository, executionBlock, 0L);
        assertTrue(txExecutor.executeTransaction());

        ArgumentCaptor<PrecompiledContractArgs> argsCaptor = ArgumentCaptor.forClass(PrecompiledContractArgs.class);

        verify(precompiledContract, times(1)).init(argsCaptor.capture());

        assertNull(argsCaptor.getValue().getProgramInvoke());
    }

    private void mockRepositoryForAnAccountWithBalance(RskAddress sender, long val) {
        when(repository.getNonce(sender)).thenReturn(BigInteger.valueOf(1L));
        when(repository.getBalance(sender)).thenReturn(new Coin(BigInteger.valueOf(val)));
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
