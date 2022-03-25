package org.ethereum.core;

import co.rsk.config.TestSystemProperties;
import co.rsk.config.VmConfig;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.test.builders.AccountBuilder;
import org.ethereum.TestUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.BlockStore;
import org.ethereum.db.MutableRepository;
import org.ethereum.db.ReceiptStore;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;

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
    public void TwoTxsAreInBlockAndThemShouldBeContainedInCache() {
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
    public void InvalidTxsIsInBlockAndShouldntBeInCache(){
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

        assertEquals(0, transaction.transactionCost(constants, activationConfig.forBlock(executionBlock.getNumber())));
        assertFalse(txExecutor.executeTransaction());
        assertNotNull(blockTxSignatureCache.getSender(transaction));
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

        assertEquals(0, transaction.transactionCost(constants, activationConfig.forBlock(executionBlock.getNumber())));
        assertFalse(txExecutor.executeTransaction());
        assertNotNull(blockTxSignatureCache.getSender(transaction));
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
    public void firstTxIsRemovedWhenTheCacheLimitSizeIsExceeded() {
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

    @Test @Ignore // todo(fedejinich) right now we're ignoring this test, it will be enabled when the major test refactor it's done
    public void isStorageRentEnabled() {
        Transaction transaction = new TransactionBuilder()
                // a simple call
                .gasLimit(new BigInteger(String.valueOf(GasCost.TRANSACTION + 1))) // over the expected limit
                .data("someData".getBytes(StandardCharsets.UTF_8))
                .build();
        checkStorageRentEnabled(transaction, true);

        transaction = new TransactionBuilder()
                // just a value transfer (no data)
                .gasLimit(new BigInteger(String.valueOf(GasCost.TRANSACTION)))
                .destination(new AccountBuilder().name("another").build().getAddress())
                .value(BigInteger.ONE)
                .build();
        checkStorageRentEnabled(transaction, false);

        transaction = new TransactionBuilder()
                // just a value transfer (no data)
                .gasLimit(new BigInteger(String.valueOf(GasCost.TRANSACTION + 1))) // over the expected limit
                .destination(new AccountBuilder().name("another").build().getAddress())
                .value(BigInteger.ONE)
                .build();
        checkStorageRentEnabled(transaction, true);

        transaction = new TransactionBuilder()
                // a value transfer (with data)
                .gasLimit(new BigInteger(String.valueOf(GasCost.TRANSACTION)))
                .value(BigInteger.ONE)
                .data("something".getBytes(StandardCharsets.UTF_8))
                .build();
        checkStorageRentEnabled(transaction, true);
    }

    private void checkStorageRentEnabled(Transaction transaction, boolean shouldBeEnabled) {
        TransactionExecutor transactionExecutor = buildTransactionExecutor(transaction, activationConfig);
        assertEquals(shouldBeEnabled, transactionExecutor.isStorageRentEnabled());

        // now disable storage rent
        TransactionExecutor transactionExecutorBeforeStorageRent = buildTransactionExecutor(transaction,
                ActivationConfigsForTest.allBut(ConsensusRule.RSKIP240));
        assertFalse(transactionExecutorBeforeStorageRent.isStorageRentEnabled());
    }

    private TransactionExecutor buildTransactionExecutor(Transaction transaction, ActivationConfig activationConfig) {
        return new TransactionExecutor(
                constants, activationConfig, transaction, txIndex, rskAddress,
                repository, blockStore, receiptStore, blockFactory,
                programInvokeFactory, executionBlock, gasUsedInTheBlock, vmConfig,
                true, precompiledContracts, deletedAccounts,
                mock(SignatureCache.class)
        );
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