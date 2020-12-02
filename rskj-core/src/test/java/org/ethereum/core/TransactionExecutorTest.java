package org.ethereum.core;

import co.rsk.config.VmConfig;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.types.ints.Uint24;

import org.ethereum.TestUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.MutableRepository;
import org.ethereum.db.ReceiptStore;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;
import org.ethereum.vm.program.RentData;
import org.ethereum.vm.GasCost;

import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.time.Instant;

import static org.junit.Assert.*;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.powermock.api.mockito.PowerMockito.when;

public class TransactionExecutorTest {

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
    private ExecutorService vmExecution;
    private int txIndex;
    private long gasUsedInTheBlock;

    @Before
    public void setUp() {
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
        vmExecution = mock(ExecutorService.class);
        when(executionBlock.getNumber()).thenReturn(10L);
    }

    @Test
    public void testInitHandlesFreeTransactionsOK() {

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
        when(transaction.getRentGasLimit()).thenReturn(BigInteger.valueOf(2_000_000).toByteArray());
        when(executionBlock.getGasLimit()).thenReturn(BigInteger.valueOf(6800000).toByteArray());
        when(repository.getNonce(transaction.getSender())).thenReturn(BigInteger.valueOf(1L));
        when(transaction.getNonce()).thenReturn(BigInteger.valueOf(1L).toByteArray());
        // more paperwork, the receiver is just someone
        RskAddress receiver = new RskAddress("0000000000000000000000000000000000000001");
        when(transaction.getReceiveAddress()).thenReturn(receiver);
        when(transaction.acceptTransactionSignature(constants.getChainId())).thenReturn(true);
        // sender has no balance
        when(repository.getBalance(transaction.getSender())).thenReturn(new Coin(BigInteger.valueOf(0L)));
        // but is sending some nice value over tLet's see if I can he wire
        when(transaction.getValue()).thenReturn(new Coin(BigInteger.valueOf(68000)));
        // note that the transaction is free of cost
        assertEquals(0, transaction.transactionCost(constants, activationConfig.forBlock(executionBlock.getNumber())));
        assertFalse(txExecutor.executeTransaction());
    }

    @Test
    public void txInBlockIsExecutedAndShouldBeAddedInCache(){
        ReceivedTxSignatureCache receivedTxSignatureCache = mock(ReceivedTxSignatureCache.class);
        BlockTxSignatureCache blockTxSignatureCache = new BlockTxSignatureCache(receivedTxSignatureCache);
        MutableRepository cacheTrack = mock(MutableRepository.class);

        when(repository.startTracking()).thenReturn(cacheTrack);

        RskAddress sender = new RskAddress("0000000000000000000000000000000000000001");
        RskAddress receiver = new RskAddress("0000000000000000000000000000000000000002");
        byte[] gasLimit = BigInteger.valueOf(4000000).toByteArray();
        byte[] rentGasLimit = BigInteger.valueOf(2_000_000).toByteArray();
        byte[] txNonce = BigInteger.valueOf(1L).toByteArray();
        Coin gasPrice = Coin.valueOf(1);
        Coin value = new Coin(BigInteger.valueOf(2));

        when(repository.getNonce(sender)).thenReturn(BigInteger.valueOf(1L));
        when(repository.getBalance(sender)).thenReturn(new Coin(BigInteger.valueOf(68000L)));
        Transaction transaction = getTransaction(sender, receiver, gasLimit, txNonce, gasPrice, value);

        assertTrue(executeValidTransaction(transaction, blockTxSignatureCache));
        assertTrue(blockTxSignatureCache.containsTx(transaction));
        assertArrayEquals(blockTxSignatureCache.getSender(transaction).getBytes(), sender.getBytes());
    }

    @Test
    public void TwoTxsAreInBlockAndTheyShouldBeContainedInCache(){
        ReceivedTxSignatureCache receivedTxSignatureCache = mock(ReceivedTxSignatureCache.class);
        BlockTxSignatureCache blockTxSignatureCache = new BlockTxSignatureCache(receivedTxSignatureCache);
        MutableRepository cacheTrack = mock(MutableRepository.class);
        when(repository.startTracking()).thenReturn(cacheTrack);

        RskAddress sender = new RskAddress("0000000000000000000000000000000000000001");
        RskAddress sender2 = new RskAddress("0000000000000000000000000000000000000003");
        RskAddress receiver = new RskAddress("0000000000000000000000000000000000000002");
        byte[] gasLimit = BigInteger.valueOf(4000000).toByteArray();
        byte[] rentGasLimit = BigInteger.valueOf(2_000_000).toByteArray();
        byte[] txNonce = BigInteger.valueOf(1L).toByteArray();
        Coin gasPrice = Coin.valueOf(1);
        Coin value = new Coin(BigInteger.valueOf(2));

        when(repository.getNonce(sender)).thenReturn(BigInteger.valueOf(1L));
        when(repository.getBalance(sender)).thenReturn(new Coin(BigInteger.valueOf(68000L)));
        Transaction transaction = getTransaction(sender, receiver, gasLimit, txNonce, gasPrice, value);

        assertTrue(executeValidTransaction(transaction, blockTxSignatureCache));

        when(repository.getNonce(sender2)).thenReturn(BigInteger.valueOf(1L));
        when(repository.getBalance(sender2)).thenReturn(new Coin(BigInteger.valueOf(68000L)));
        Transaction transaction2 = getTransaction(sender2, receiver, gasLimit, txNonce, gasPrice, value);

        assertTrue(executeValidTransaction(transaction2, blockTxSignatureCache));

        assertTrue(blockTxSignatureCache.containsTx(transaction));
        assertArrayEquals(blockTxSignatureCache.getSender(transaction).getBytes(), sender.getBytes());

        assertTrue(blockTxSignatureCache.containsTx(transaction2));
        assertArrayEquals(blockTxSignatureCache.getSender(transaction2).getBytes(), sender2.getBytes());
    }

    @Test
    public void InvalidTxsIsInBlockAndShouldntBeInCache(){
        ReceivedTxSignatureCache receivedTxSignatureCache = mock(ReceivedTxSignatureCache.class);
        BlockTxSignatureCache blockTxSignatureCache = new BlockTxSignatureCache(receivedTxSignatureCache);
        MutableRepository cacheTrack = mock(MutableRepository.class);

        when(repository.startTracking()).thenReturn(cacheTrack);

        RskAddress sender = new RskAddress("0000000000000000000000000000000000000001");
        RskAddress receiver = new RskAddress("0000000000000000000000000000000000000002");
        byte[] gasLimit = BigInteger.valueOf(4000000).toByteArray();
        byte[] rentGasLimit = BigInteger.valueOf(2_000_000).toByteArray();
        byte[] txNonce = BigInteger.valueOf(1L).toByteArray();
        Coin gasPrice = Coin.valueOf(1);
        Coin value = new Coin(BigInteger.valueOf(2));

        Transaction transaction = getTransaction(sender, receiver, gasLimit,  txNonce, gasPrice, value);
        when(executionBlock.getGasLimit()).thenReturn(BigInteger.valueOf(6800000).toByteArray());
        when(repository.getNonce(sender)).thenReturn(BigInteger.valueOf(1L));
        //#mish this will trip Tx exec, insufficient balance
        when(repository.getBalance(sender)).thenReturn(new Coin(BigInteger.valueOf(0L)));

        TransactionExecutor txExecutor = new TransactionExecutor(
                constants, activationConfig, transaction, txIndex, rskAddress,
                repository, blockStore, receiptStore, blockFactory,
                programInvokeFactory, executionBlock, gasUsedInTheBlock, vmConfig,
                true, precompiledContracts, deletedAccounts,
                blockTxSignatureCache
        );

        assertEquals(0, transaction.transactionCost(constants, activationConfig.forBlock(executionBlock.getNumber())));
        assertFalse(txExecutor.executeTransaction());
        assertFalse(blockTxSignatureCache.containsTx(transaction));
    }

    @Test
    public void remascTxIsReceivedAndShouldntBeInCache(){
        ReceivedTxSignatureCache receivedTxSignatureCache = mock(ReceivedTxSignatureCache.class);
        BlockTxSignatureCache blockTxSignatureCache = new BlockTxSignatureCache(receivedTxSignatureCache);
        MutableRepository cacheTrack = mock(MutableRepository.class);

        when(repository.startTracking()).thenReturn(cacheTrack);

        RskAddress sender = PrecompiledContracts.REMASC_ADDR;
        RskAddress receiver = new RskAddress("0000000000000000000000000000000000000002");
        byte[] gasLimit = BigInteger.valueOf(4000000).toByteArray();
        byte[] rentGasLimit = BigInteger.valueOf(2_000_000).toByteArray();
        byte[] txNonce = BigInteger.valueOf(1L).toByteArray();
        Coin gasPrice = Coin.valueOf(1);
        Coin value = new Coin(BigInteger.valueOf(2));

        Transaction transaction = getTransaction(sender, receiver, gasLimit, txNonce, gasPrice, value);
        when(executionBlock.getGasLimit()).thenReturn(BigInteger.valueOf(6800000).toByteArray());
        when(repository.getNonce(sender)).thenReturn(BigInteger.valueOf(1L));
        // #mish: execution fails because of insufficient balance (failure unrelated to remasc)
        when(repository.getBalance(sender)).thenReturn(new Coin(BigInteger.valueOf(0L)));

        TransactionExecutor txExecutor = new TransactionExecutor(
                constants, activationConfig, transaction, txIndex, rskAddress,
                repository, blockStore, receiptStore, blockFactory,
                programInvokeFactory, executionBlock, gasUsedInTheBlock, vmConfig,
                true, precompiledContracts, deletedAccounts,
                blockTxSignatureCache
        );

        assertEquals(0, transaction.transactionCost(constants, activationConfig.forBlock(executionBlock.getNumber())));
        assertFalse(txExecutor.executeTransaction());
        assertFalse(blockTxSignatureCache.containsTx(transaction));
    }

    @Test
    public void txInBlockIsReceivedAndShouldBeUsedInTxExecutorInsteadOfComputeSender(){
        ReceivedTxSignatureCache receivedTxSignatureCache = mock(ReceivedTxSignatureCache.class);
        BlockTxSignatureCache blockTxSignatureCache = new BlockTxSignatureCache(receivedTxSignatureCache);
        MutableRepository cacheTrack = mock(MutableRepository.class);

        when(repository.startTracking()).thenReturn(cacheTrack);

        RskAddress sender = new RskAddress("0000000000000000000000000000000000000001");
        RskAddress receiver = new RskAddress("0000000000000000000000000000000000000002");
        byte[] gasLimit = BigInteger.valueOf(4000000).toByteArray();
        byte[] rentGasLimit = BigInteger.valueOf(2_000_000).toByteArray();
        byte[] txNonce = BigInteger.valueOf(1L).toByteArray();
        Coin gasPrice = Coin.valueOf(1);
        Coin value = new Coin(BigInteger.valueOf(2));

        when(repository.getNonce(sender)).thenReturn(BigInteger.valueOf(1L));
        when(repository.getBalance(sender)).thenReturn(new Coin(BigInteger.valueOf(68000L)));
        Transaction transaction = getTransaction(sender, receiver, gasLimit, txNonce, gasPrice, value);
        when(receivedTxSignatureCache.getSender(transaction)).thenReturn(sender);
        when(receivedTxSignatureCache.containsTx(transaction)).thenReturn(true);

        assertTrue(executeValidTransaction(transaction, blockTxSignatureCache));
        assertTrue(executeValidTransaction(transaction, blockTxSignatureCache)); //Execute two times the same tx
        verify(receivedTxSignatureCache, times(1)).getSender(transaction);
        assertArrayEquals(blockTxSignatureCache.getSender(transaction).getBytes(), sender.getBytes());
    }

    @Test
    public void firstTxIsRemovedWhenTheCacheLimitSizeIsExceeded() {
        ReceivedTxSignatureCache receivedTxSignatureCache = mock(ReceivedTxSignatureCache.class);
        BlockTxSignatureCache blockTxSignatureCache = new BlockTxSignatureCache(receivedTxSignatureCache);
        MutableRepository cacheTrack = mock(MutableRepository.class);

        when(repository.startTracking()).thenReturn(cacheTrack);

        RskAddress sender = new RskAddress("0000000000000000000000000000000000000001");
        RskAddress receiver = new RskAddress("0000000000000000000000000000000000000002");
        byte[] gasLimit = BigInteger.valueOf(4000000).toByteArray();
        byte[] rentGasLimit = BigInteger.valueOf(2_000_000).toByteArray();
        byte[] txNonce = BigInteger.valueOf(1L).toByteArray();
        Coin gasPrice = Coin.valueOf(1);
        Coin value = new Coin(BigInteger.valueOf(2));

        when(repository.getNonce(sender)).thenReturn(BigInteger.valueOf(1L));
        when(repository.getBalance(sender)).thenReturn(new Coin(BigInteger.valueOf(68000L)));
        Transaction transaction = getTransaction(sender, receiver, gasLimit, txNonce, gasPrice, value);
        assertTrue(executeValidTransaction(transaction, blockTxSignatureCache));

        for (int i = 0; i < MAX_CACHE_SIZE; i++) {
            if (i == MAX_CACHE_SIZE - 1) {
                assertTrue(blockTxSignatureCache.containsTx(transaction));
            }
            sender = new RskAddress(TestUtils.randomAddress().getBytes());
            when(repository.getNonce(sender)).thenReturn(BigInteger.valueOf(1L));
            when(repository.getBalance(sender)).thenReturn(new Coin(BigInteger.valueOf(68000L)));
            Transaction transactionAux = getTransaction(sender, receiver, gasLimit, txNonce, gasPrice, value);
            assertTrue(executeValidTransaction(transactionAux, blockTxSignatureCache));
        }

        assertFalse(blockTxSignatureCache.containsTx(transaction));
    }


    // #mish: some helper functions to get and execute transations

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
        when(transaction.getRentGasLimit()).thenReturn(gasLimit); //#mish
        when(transaction.getSender(any())).thenCallRealMethod();
        when(transaction.getNonce()).thenReturn(txNonce);
        when(transaction.getReceiveAddress()).thenReturn(receiver);
        when(transaction.acceptTransactionSignature(constants.getChainId())).thenReturn(true);
        when(transaction.getValue()).thenReturn(value);
        return transaction;
    }

    // #mish: May 2020 Storage rent related tests
    
    
    // #mish: modified from txInBlockIsExecutedAndShouldBeAddedInCache()
    @Test
    public void txExecRentGas(){
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
        when(repository.getBalance(sender)).thenReturn(new Coin(BigInteger.valueOf(68_000L)));
        Transaction transaction = getTransaction(sender, receiver, gasLimit, txNonce, gasPrice, value);

        when(executionBlock.getGasLimit()).thenReturn(BigInteger.valueOf(6_800_000).toByteArray());
        
        //mock repository for accessedNodeAdder()
        when(repository.getAccountNodeKey(sender)).thenReturn(new ByteArrayWrapper("senderKey".getBytes()));
        when(repository.getAccountNodeValueLength(sender)).thenReturn(new Uint24(128));
        when(repository.getAccountNodeLRPTime(sender)).thenReturn(70_000L);

        when(repository.getAccountNodeKey(receiver)).thenReturn(new ByteArrayWrapper("receiverKey".getBytes()));
        when(repository.getAccountNodeValueLength(receiver)).thenReturn(new Uint24(10));
        when(repository.getAccountNodeLRPTime(receiver)).thenReturn(130_000L);
        
        TransactionExecutor txExecutor = new TransactionExecutor(
                constants, activationConfig, transaction, txIndex, rskAddress,
                repository, blockStore, receiptStore, blockFactory,
                programInvokeFactory, executionBlock, gasUsedInTheBlock, vmConfig,
                true, true, precompiledContracts, deletedAccounts,
                blockTxSignatureCache
        );
        
                
        if (txExecutor.executeTransaction()){
            System.out.println("TX executed");
                        
            // one entry for sender and one for receiver
            assertEquals(2,txExecutor.getResult().getAccessedNodes().size());

            assertEquals(new Uint24(128), txExecutor.getResult().getAccessedNodes().get(new ByteArrayWrapper("senderKey".getBytes())).getValueLength());
            assertEquals(new Uint24(10), txExecutor.getResult().getAccessedNodes().get(new ByteArrayWrapper("receiverKey".getBytes())).getValueLength());
            //these assertions (from prior test version) will fail for nodes with updated LRPT
            //assertEquals(70_000L, txExecutor.getResult().getAccessedNodes().get(DataWord.fromString("senderKey")).getLRPTime());
            //assertEquals(130_000L, txExecutor.getResult().getAccessedNodes().get(DataWord.fromString("receiverKey")).getLRPTime());

            // Rent computation within Tx Exec uses time.now() value, so mocking is not needed    
            long rentDueSender = txExecutor.getResult().getAccessedNodes().get(new ByteArrayWrapper("senderKey".getBytes())).getRentDue();
            long rentDueReceiver = txExecutor.getResult().getAccessedNodes().get(new ByteArrayWrapper("receiverKey".getBytes())).getRentDue(); 
            long estimatedRentGas = txExecutor.getEstRentGas();
            //System.out.println(Instant.now().getEpochSecond());
            System.out.println("Sender rent: " + rentDueSender);
            System.out.println("Receiver rent: " + rentDueReceiver);
            //System.out.println("Estimted rent: " + estimatedRentGas);
            assertEquals(rentDueSender + rentDueReceiver, estimatedRentGas);
            System.out.println("execution gas used: " + txExecutor.getResult().getGasUsed());
            System.out.println("rentgas used: " + txExecutor.getResult().getRentGasUsed());
        } else {
            System.out.println("TX execution failed");
        }
    }

}