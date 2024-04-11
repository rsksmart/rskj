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

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.RskSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.genesis.TestGenesisLoader;
import co.rsk.db.RepositoryLocator;
import co.rsk.net.handler.quota.TxQuotaChecker;
import co.rsk.remasc.RemascTransaction;
import co.rsk.test.builders.BlockBuilder;
import org.ethereum.TestUtils;
import org.ethereum.core.*;
import org.ethereum.core.genesis.GenesisLoader;
import org.ethereum.listener.GasPriceTracker;
import org.ethereum.util.RskTestContext;
import org.ethereum.vm.DataWord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.ethereum.util.TransactionFactoryHelper.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Created by ajlopez on 08/08/2016.
 */
class TransactionPoolImplTest {
    private static final int MAX_CACHE_SIZE = 6000;

    @TempDir
    public Path tempDir;

    private RskTestContext rskTestContext;
    private Blockchain blockChain;
    private TransactionPoolImpl transactionPool;
    private Repository repository;
    private ReceivedTxSignatureCache signatureCache;
    private TxQuotaChecker quotaChecker;

    @BeforeEach
    void setUp() {
        rskTestContext = new RskTestContext(tempDir, "--regtest") {
            @Override
            protected GenesisLoader buildGenesisLoader() {
                return new TestGenesisLoader(getTrieStore(), "rsk-unittests.json", BigInteger.ZERO, true, true, true);
            }

            @Override
            protected RepositoryLocator buildRepositoryLocator() {
                return spy(super.buildRepositoryLocator());
            }
        };

        blockChain = rskTestContext.getBlockchain();
        RepositoryLocator repositoryLocator = rskTestContext.getRepositoryLocator();
        repository = repositoryLocator.startTrackingAt(blockChain.getBestBlock().getHeader());
        signatureCache = spy(rskTestContext.getReceivedTxSignatureCache());

        RskSystemProperties rskSystemProperties = spy(rskTestContext.getRskSystemProperties());
        when(rskSystemProperties.isAccountTxRateLimitEnabled()).thenReturn(true);

        transactionPool = new TransactionPoolImpl(
                rskSystemProperties,
                repositoryLocator,
                rskTestContext.getBlockStore(),
                rskTestContext.getBlockFactory(),
                rskTestContext.getCompositeEthereumListener(),
                rskTestContext.getTransactionExecutorFactory(),
                signatureCache,
                10,
                100,
                Mockito.mock(TxQuotaChecker.class),
                Mockito.mock(GasPriceTracker.class),
                null);

        quotaChecker = mock(TxQuotaChecker.class);
        when(quotaChecker.acceptTx(any(), any(), any())).thenReturn(true);
        TestUtils.setInternalState(transactionPool, "quotaChecker", quotaChecker);

        // don't call start to avoid creating threads
        transactionPool.processBest(blockChain.getBestBlock());

        // this is to workaround the current test structure, which abuses the Repository by
        // modifying it in place
        doReturn(repository).when(repositoryLocator).snapshotAt(any());
    }

    @AfterEach
    void tearDown() {
        rskTestContext.close();
    }

    @Test
    void usingInit() {
        Assertions.assertFalse(transactionPool.hasCleanerFuture());
        Assertions.assertNotEquals(0, transactionPool.getOutdatedThreshold());
        Assertions.assertNotEquals(0, transactionPool.getOutdatedTimeout());
    }

    @Test
    void usingCleanUp() {
        transactionPool.cleanUp();

        Assertions.assertTrue(transactionPool.getPendingTransactions().isEmpty());
    }

    @Test
    void usingStart() {
        Assertions.assertFalse(transactionPool.hasCleanerFuture());
        transactionPool.start();
        Assertions.assertTrue(transactionPool.hasCleanerFuture());
    }

    @Test
    void usingAccountsWithInitialBalance() {
        createTestAccounts(2, Coin.valueOf(10L));

        PendingState pendingState = transactionPool.getPendingState();

        Assertions.assertNotNull(pendingState);

        Account account1 = createAccount(1);
        Account account2 = createAccount(2);

        Assertions.assertEquals(BigInteger.TEN, pendingState.getBalance(account1.getAddress()).asBigInteger());
        Assertions.assertEquals(BigInteger.TEN, pendingState.getBalance(account2.getAddress()).asBigInteger());
    }

    @Test
    void getEmptyPendingTransactionList() {
        List<Transaction> transactions = transactionPool.getPendingTransactions();

        Assertions.assertNotNull(transactions);
        Assertions.assertTrue(transactions.isEmpty());
    }

    @Test
    void addAndGetPendingTransaction() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(1, balance);
        Transaction tx = createSampleTransaction(1, 2, 1000, 0);

        transactionPool.addTransaction(tx);
        List<Transaction> transactions = transactionPool.getPendingTransactions();

        Assertions.assertNotNull(transactions);
        Assertions.assertFalse(transactions.isEmpty());
        Assertions.assertTrue(transactions.contains(tx));
    }

    @Test
    void addAndGetQueuedTransaction() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(1, balance);
        Transaction tx = createSampleTransaction(1, 2, 1000, 4);

        transactionPool.addTransaction(tx);

        List<Transaction> pendingTransactions = transactionPool.getPendingTransactions();

        Assertions.assertNotNull(pendingTransactions);
        Assertions.assertTrue(pendingTransactions.isEmpty());

        List<Transaction> queuedTransactions = transactionPool.getQueuedTransactions();

        Assertions.assertNotNull(queuedTransactions);
        Assertions.assertFalse(queuedTransactions.isEmpty());
        Assertions.assertEquals(1, queuedTransactions.size());
        Assertions.assertTrue(queuedTransactions.contains(tx));
    }

    @Test
    void addAndGetTwoQueuedTransaction() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(1, balance);
        Transaction tx1 = createSampleTransaction(1, 2, 1000, 1);
        Transaction tx2 = createSampleTransaction(1, 2, 1000, 2);

        transactionPool.addTransaction(tx1);
        transactionPool.addTransaction(tx2);

        List<Transaction> pendingTransactions = transactionPool.getPendingTransactions();

        Assertions.assertNotNull(pendingTransactions);
        Assertions.assertTrue(pendingTransactions.isEmpty());

        List<Transaction> queuedTransactions = transactionPool.getQueuedTransactions();

        Assertions.assertNotNull(queuedTransactions);
        Assertions.assertFalse(queuedTransactions.isEmpty());
        Assertions.assertEquals(2, queuedTransactions.size());
        Assertions.assertTrue(queuedTransactions.contains(tx1));
        Assertions.assertTrue(queuedTransactions.contains(tx2));
    }

    @Test
    void addAndGetTwoQueuedTransactionAsPendingOnes() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(1, balance);
        Transaction tx0 = createSampleTransaction(1, 2, 1000, 0);
        Transaction tx1 = createSampleTransaction(1, 2, 1000, 1);
        Transaction tx2 = createSampleTransaction(1, 2, 1000, 2);

        Assertions.assertTrue(transactionPool.addTransaction(tx1).transactionsWereAdded());
        Assertions.assertTrue(transactionPool.addTransaction(tx2).transactionsWereAdded());

        List<Transaction> transactionsToProcess = new ArrayList<>();
        transactionsToProcess.add(tx0);

        List<Transaction> processedTransactions = transactionPool.addTransactions(transactionsToProcess);

        Assertions.assertNotNull(processedTransactions);
        Assertions.assertFalse(processedTransactions.isEmpty());
        Assertions.assertEquals(3, processedTransactions.size());
        Assertions.assertTrue(processedTransactions.contains(tx0));
        Assertions.assertTrue(processedTransactions.contains(tx1));
        Assertions.assertTrue(processedTransactions.contains(tx2));

        List<Transaction> pendingTransactions = transactionPool.getPendingTransactions();

        Assertions.assertNotNull(pendingTransactions);
        Assertions.assertFalse(pendingTransactions.isEmpty());
        Assertions.assertEquals(3, pendingTransactions.size());
        Assertions.assertTrue(pendingTransactions.contains(tx0));
        Assertions.assertTrue(pendingTransactions.contains(tx1));
        Assertions.assertTrue(pendingTransactions.contains(tx2));

        List<Transaction> queuedTransactions = transactionPool.getQueuedTransactions();

        Assertions.assertNotNull(queuedTransactions);
        Assertions.assertTrue(queuedTransactions.isEmpty());
    }

    @Test
    void addAndExecutePendingTransaction() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);
        Transaction tx = createSampleTransaction(1, 2, 1000, 0);
        Account receiver = createAccount(2);

        transactionPool.addTransaction(tx);

        PendingState pendingState = transactionPool.getPendingState();
        Assertions.assertEquals(BigInteger.valueOf(1001000), pendingState.getBalance(receiver.getAddress()).asBigInteger());
    }

    @Test
    void removeObsoletePendingTransactionsByBlock() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);
        Transaction tx1 = createSampleTransaction(1, 2, 1000, 0);
        Transaction tx2 = createSampleTransaction(1, 2, 3000, 1);

        transactionPool.addTransaction(tx1);
        transactionPool.addTransaction(tx2);

        Assertions.assertEquals(10, transactionPool.getOutdatedThreshold());

        List<Transaction> list = transactionPool.getPendingTransactions();

        Assertions.assertNotNull(list);
        Assertions.assertFalse(list.isEmpty());
        Assertions.assertEquals(2, list.size());

        transactionPool.removeObsoleteTransactions(1, 1, 100);

        list = transactionPool.getPendingTransactions();

        Assertions.assertNotNull(list);
        Assertions.assertFalse(list.isEmpty());
        Assertions.assertEquals(2, list.size());

        transactionPool.removeObsoleteTransactions(20, transactionPool.getOutdatedThreshold(), 100);

        list = transactionPool.getPendingTransactions();

        Assertions.assertNotNull(list);
        Assertions.assertTrue(list.isEmpty());
    }

    @Test
    void removeObsoleteQueuedTransactionsByBlock() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);
        Transaction tx1 = createSampleTransaction(1, 2, 1000, 1);
        Transaction tx2 = createSampleTransaction(1, 2, 3000, 2);

        transactionPool.addTransaction(tx1);
        transactionPool.addTransaction(tx2);

        Assertions.assertEquals(10, transactionPool.getOutdatedThreshold());

        List<Transaction> list = transactionPool.getQueuedTransactions();

        Assertions.assertNotNull(list);
        Assertions.assertFalse(list.isEmpty());
        Assertions.assertEquals(2, list.size());

        transactionPool.removeObsoleteTransactions(1, 1, 100);

        list = transactionPool.getQueuedTransactions();

        Assertions.assertNotNull(list);
        Assertions.assertFalse(list.isEmpty());
        Assertions.assertEquals(2, list.size());

        transactionPool.removeObsoleteTransactions(20, transactionPool.getOutdatedThreshold(), 100);

        list = transactionPool.getQueuedTransactions();

        Assertions.assertNotNull(list);
        Assertions.assertTrue(list.isEmpty());
    }

    @Test
    @SuppressWarnings("squid:S2925") // Thread.sleep() used
    void removeObsoletePendingTransactionsByTimeout() throws InterruptedException {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);
        Transaction tx1 = createSampleTransaction(1, 2, 1000, 0);
        Transaction tx2 = createSampleTransaction(1, 2, 3000, 1);

        transactionPool.addTransaction(tx1);
        transactionPool.addTransaction(tx2);

        Assertions.assertEquals(10, transactionPool.getOutdatedThreshold());

        List<Transaction> list = transactionPool.getPendingTransactions();

        Assertions.assertNotNull(list);
        Assertions.assertFalse(list.isEmpty());
        Assertions.assertEquals(2, list.size());

        Thread.sleep(3000);

        transactionPool.removeObsoleteTransactions(1, 1, 1);

        list = transactionPool.getPendingTransactions();

        Assertions.assertNotNull(list);
        Assertions.assertTrue(list.isEmpty());
    }

    @Test
    @SuppressWarnings("squid:S2925") // Thread.sleep() used
    void removeObsoleteQueuedTransactionsByTimeout() throws InterruptedException {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);
        Transaction tx1 = createSampleTransaction(1, 2, 1000, 1);
        Transaction tx2 = createSampleTransaction(1, 2, 3000, 2);

        transactionPool.addTransaction(tx1);
        transactionPool.addTransaction(tx2);

        Assertions.assertEquals(10, transactionPool.getOutdatedThreshold());

        List<Transaction> list = transactionPool.getQueuedTransactions();

        Assertions.assertNotNull(list);
        Assertions.assertFalse(list.isEmpty());
        Assertions.assertEquals(2, list.size());

        Thread.sleep(3000);

        transactionPool.removeObsoleteTransactions(1, 1, 1);

        list = transactionPool.getQueuedTransactions();

        Assertions.assertNotNull(list);
        Assertions.assertTrue(list.isEmpty());
    }

    @Test
    void getAllPendingTransactions() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);
        Transaction tx1 = createSampleTransaction(1, 2, 1000, 0);
        Transaction tx2 = createSampleTransaction(1, 2, 3000, 1);

        transactionPool.addTransaction(tx1);
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx1);
        txs.add(tx2);
        transactionPool.addTransactions(txs);

        List<Transaction> alltxs = transactionPool.getPendingTransactions();

        Assertions.assertNotNull(alltxs);
        Assertions.assertFalse(alltxs.isEmpty());
        Assertions.assertEquals(2, alltxs.size());
        Assertions.assertTrue(alltxs.contains(tx1));
        Assertions.assertTrue(alltxs.contains(tx2));
    }

    @Test
    void processBestBlockRemovesTransactionsInBlock() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(3, balance);
        Transaction tx1 = createSampleTransaction(1, 2, 1000, 0);
        Transaction tx2 = createSampleTransaction(1, 2, 3000, 1);
        Transaction tx3 = createSampleTransaction(2, 3, 1000, 0);
        Transaction tx4 = createSampleTransaction(2, 3, 3000, 1);

        transactionPool.addTransaction(tx1);
        transactionPool.addTransaction(tx2);
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx1);
        txs.add(tx2);
        txs.add(tx3);
        txs.add(tx4);

        transactionPool.addTransactions(txs);

        List<Transaction> btxs = new ArrayList<>();
        btxs.add(tx1);
        btxs.add(tx3);

        Block genesis = blockChain.getBestBlock();
        Block block = new BlockBuilder(null, null, null).parent(genesis).transactions(btxs).build();

        transactionPool.processBest(block);

        List<Transaction> alltxs = transactionPool.getPendingTransactions();

        Assertions.assertNotNull(alltxs);
        Assertions.assertFalse(alltxs.isEmpty());
        Assertions.assertEquals(2, alltxs.size());
        Assertions.assertFalse(alltxs.contains(tx1));
        Assertions.assertTrue(alltxs.contains(tx2));
        Assertions.assertFalse(alltxs.contains(tx3));
        Assertions.assertTrue(alltxs.contains(tx4));

        Assertions.assertSame(block, transactionPool.getBestBlock());
    }

    @Test
    void retractBlockAddsTransactionsAsPending() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(3, balance);
        Transaction tx1 = createSampleTransaction(1, 2, 1000, 0);
        Transaction tx2 = createSampleTransaction(1, 2, 3000, 1);
        Transaction tx3 = createSampleTransaction(2, 3, 1000, 0);
        Transaction tx4 = createSampleTransaction(2, 3, 3000, 1);

        transactionPool.addTransaction(tx1);
        transactionPool.addTransaction(tx2);

        List<Transaction> txs = new ArrayList<>();
        txs.add(tx3);
        txs.add(tx4);

        Block block = new BlockBuilder(null, null, null)
                .parent(new BlockGenerator().getGenesisBlock()).transactions(txs).build();

        transactionPool.retractBlock(block);

        List<Transaction> alltxs = transactionPool.getPendingTransactions();

        Assertions.assertNotNull(alltxs);
        Assertions.assertFalse(alltxs.isEmpty());
        Assertions.assertEquals(4, alltxs.size());
        Assertions.assertTrue(alltxs.contains(tx1));
        Assertions.assertTrue(alltxs.contains(tx2));
        Assertions.assertTrue(alltxs.contains(tx3));
        Assertions.assertTrue(alltxs.contains(tx4));

        List<Transaction> ptxs = transactionPool.getPendingTransactions();

        Assertions.assertNotNull(ptxs);
        Assertions.assertFalse(ptxs.isEmpty());
        Assertions.assertEquals(4, ptxs.size());
        Assertions.assertTrue(ptxs.contains(tx1));
        Assertions.assertTrue(ptxs.contains(tx2));
        Assertions.assertTrue(ptxs.contains(tx3));
        Assertions.assertTrue(ptxs.contains(tx4));
    }

    @Test
    void updateTransactionPool() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);
        Transaction tx1 = createSampleTransaction(1, 2, 1000, 0);
        Transaction tx2 = createSampleTransaction(1, 2, 3000, 1);
        Account receiver = createAccount(2);

        transactionPool.addTransaction(tx1);
        transactionPool.addTransaction(tx2);

        PendingState pendingState = transactionPool.getPendingState();
        Assertions.assertEquals(BigInteger.valueOf(1004000), pendingState.getBalance(receiver.getAddress()).asBigInteger());
    }

    @Test
    void addTwiceAndGetPendingTransaction() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(1, balance);
        Transaction tx = createSampleTransaction(1, 2, 1000, 0);

        transactionPool.addTransaction(tx);

        TransactionPoolAddResult result = transactionPool.addTransaction(tx);
        Assertions.assertFalse(result.transactionsWereAdded());
        Assertions.assertEquals("pending transaction with same hash already exists", result.getErrorMessage());

        List<Transaction> transactions = transactionPool.getPendingTransactions();

        Assertions.assertNotNull(transactions);
        Assertions.assertFalse(transactions.isEmpty());
        Assertions.assertEquals(1, transactions.size());
        Assertions.assertTrue(transactions.contains(tx));
    }

    @Test
    void addTwiceAndGetQueuedTransaction() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(1, balance);
        Transaction tx = createSampleTransaction(1, 2, 1000, 1);

        transactionPool.addTransaction(tx);

        TransactionPoolAddResult result = transactionPool.addTransaction(tx);
        Assertions.assertFalse(result.transactionsWereAdded());
        Assertions.assertEquals("queued transaction with same hash already exists", result.getErrorMessage());

        List<Transaction> transactions = transactionPool.getQueuedTransactions();

        Assertions.assertNotNull(transactions);
        Assertions.assertFalse(transactions.isEmpty());
        Assertions.assertEquals(1, transactions.size());
        Assertions.assertTrue(transactions.contains(tx));
    }

    @Test
    void getEmptyTransactionList() {
        List<Transaction> transactions = transactionPool.getPendingTransactions();

        Assertions.assertNotNull(transactions);
        Assertions.assertTrue(transactions.isEmpty());
    }

    @Test
    void executeContractWithFakeBlock() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);
        // "NUMBER PUSH1 0x00 SSTORE" compiled to bytecodes
        String code = "43600055";
        Transaction tx = createSampleTransactionWithData(1, 0, code);

        transactionPool.addTransaction(tx);

        Assertions.assertNotNull(tx.getContractAddress().getBytes());
        // Stored value at 0 position should be 1, one more than the blockChain best block
        Assertions.assertEquals(DataWord.ONE, transactionPool.getPendingState().getStorageValue(tx.getContractAddress(), DataWord.ZERO));
    }

    @Test
    void checkTxWithSameNonceIsRejected() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);
        Transaction tx = createSampleTransaction(1, 0, 1000, 0);
        Transaction tx2 = createSampleTransaction(1, 0, 2000, 0);

        transactionPool.addTransaction(tx);

        TransactionPoolAddResult result = transactionPool.addTransaction(tx2);

        Assertions.assertFalse(result.transactionsWereAdded());
        Assertions.assertEquals("gas price not enough to bump transaction", result.getErrorMessage());
    }

    @Test
    void checkTxQuotaValidatorAccepted() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);
        Transaction tx = createSampleTransaction(1, 0, 1000, 0);

        when(quotaChecker.acceptTx(eq(tx), any(), any())).thenReturn(true);
        TransactionPoolAddResult result = transactionPool.addTransaction(tx);

        Assertions.assertTrue(result.transactionsWereAdded());
    }

    @Test
    void checkTxQuotaValidatorRejected() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);
        Transaction tx = createSampleTransaction(1, 0, 1000, 0);

        when(quotaChecker.acceptTx(eq(tx), any(), any())).thenReturn(false);
        TransactionPoolAddResult result = transactionPool.addTransaction(tx);

        Assertions.assertFalse(result.transactionsWereAdded());
        Assertions.assertEquals("account exceeds quota", result.getErrorMessage());
    }

    @Test
    void checkTxMaxSizeAccepted() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);

        Transaction mockedTx = spy(createSampleTransaction(1, 0, 1000, 0));
        long txMaxSize = 128L * 1024;
        when(mockedTx.getSize()).thenReturn(txMaxSize);

        TransactionPoolAddResult result = transactionPool.addTransaction(mockedTx);
        Assertions.assertTrue(result.transactionsWereAdded());
    }

    @Test
    void checkTxMaxSizeRejected() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);

        Transaction mockedTx = spy(createSampleTransaction(1, 0, 1000, 0));
        long txMaxSize = 128L * 1024;
        when(mockedTx.getSize()).thenReturn(txMaxSize + 1);

        TransactionPoolAddResult result = transactionPool.addTransaction(mockedTx);
        Assertions.assertFalse(result.transactionsWereAdded());
        Assertions.assertTrue(result.getErrorMessage().contains("transaction's size is higher than defined maximum"));
    }

    @Test
    void checkTxWithSameNonceBumpedIsAccepted() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);
        Transaction tx1 = createSampleTransactionWithGasPrice(1, 0, 1000, 0, 1);
        Transaction tx2 = createSampleTransactionWithGasPrice(1, 0, 2000, 0, 2);

        transactionPool.addTransaction(tx1);
        Assertions.assertTrue(transactionPool.addTransaction(tx2).transactionsWereAdded());
        Assertions.assertTrue(transactionPool.getPendingTransactions().stream().anyMatch(tx -> tx.getHash().equals(tx2.getHash())));
        Assertions.assertFalse(transactionPool.getPendingTransactions().stream().anyMatch(tx -> tx.getHash().equals(tx1.getHash())));
    }

    @Test
    void checkTxWithHighGasLimitIsRejected() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);
        Transaction tx = createSampleTransaction(1, 2, 1000, 0, BigInteger.valueOf(3000001));
        Account receiver = createAccount(2);

        TransactionPoolAddResult result = transactionPool.addTransaction(tx);

        Assertions.assertFalse(result.transactionsWereAdded());
        Assertions.assertEquals("transaction's gas limit of 3000001 is higher than the block's gas limit of 3000000", result.getErrorMessage());

        List<Transaction> pending = transactionPool.getPendingTransactions();
        Assertions.assertTrue(pending.isEmpty());
    }

    @Test
    void checkTxWithHighNonceIsRejected() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);
        Transaction tx = createSampleTransaction(1, 2, 1000, 16);
        TransactionPoolAddResult result = transactionPool.addTransaction(tx);

        Assertions.assertFalse(result.transactionsWereAdded());
        Assertions.assertEquals("transaction nonce too high", result.getErrorMessage());

        Assertions.assertTrue(transactionPool.getPendingTransactions().isEmpty());
        Assertions.assertTrue(transactionPool.getQueuedTransactions().isEmpty());
    }

    @Test
    void checkTxWithLowNonceIsRejected() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);

        Transaction tx = createSampleTransaction(1, 2, 3000, 0);

        repository.increaseNonce(tx.getSender());

        TransactionPoolAddResult result = transactionPool.addTransaction(tx);

        Assertions.assertFalse(result.transactionsWereAdded());
        Assertions.assertEquals("transaction nonce too low", result.getErrorMessage());

        Assertions.assertTrue(transactionPool.getPendingTransactions().isEmpty());
        Assertions.assertTrue(transactionPool.getQueuedTransactions().isEmpty());
    }

    @Test
    void checkRemascTxIsRejected() {
        RemascTransaction tx = new RemascTransaction(10);

        TransactionPoolAddResult result = transactionPool.addTransaction(tx);

        Assertions.assertFalse(result.transactionsWereAdded());
        Assertions.assertEquals("transaction is a remasc transaction", result.getErrorMessage());
    }

    @Test
    void checkTxWithLowGasPriceIsRejected() {
        Block newBest = new BlockBuilder(null, null, null)
                .parent(transactionPool.getBestBlock()).minGasPrice(BigInteger.valueOf(100)).build();
        transactionPool.processBest(newBest);

        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);
        Transaction tx = createSampleTransactionWithGasPrice(1, 2, 1000, 0, 1);

        TransactionPoolAddResult result = transactionPool.addTransaction(tx);

        Assertions.assertFalse(result.transactionsWereAdded());
        Assertions.assertEquals("transaction's gas price lower than block's minimum", result.getErrorMessage());

        Assertions.assertTrue(transactionPool.getPendingTransactions().isEmpty());
    }

    @Test
    void checkTxFromAccountWithLowBalanceIsRejected() {
        Coin balance = Coin.valueOf(1000);
        createTestAccounts(2, balance);
        Transaction tx = createSampleTransaction(1, 2, 10000, 0);

        TransactionPoolAddResult result = transactionPool.addTransaction(tx);

        Assertions.assertFalse(result.transactionsWereAdded());
        Assertions.assertEquals("insufficient funds", result.getErrorMessage());

        Assertions.assertTrue(transactionPool.getPendingTransactions().isEmpty());
    }

    @Test
    void checkTxFromNullStateIsRejected() {
        Transaction tx = createSampleTransaction(1, 2, 1000, 0);

        TransactionPoolAddResult result = transactionPool.addTransaction(tx);

        Assertions.assertFalse(result.transactionsWereAdded());
        Assertions.assertEquals("the sender account doesn't exist", result.getErrorMessage());

        Assertions.assertTrue(transactionPool.getPendingTransactions().isEmpty());
    }

    @Test
    void checkTxWithHighIntrinsicGasIsRejected() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);

        // basic tx cost is 21000, set the gas limit below that
        Transaction tx = createSampleTransaction(1, 2, 1000, 0, BigInteger.valueOf(20000));

        TransactionPoolAddResult result = transactionPool.addTransaction(tx);

        Assertions.assertFalse(result.transactionsWereAdded());
        Assertions.assertEquals("transaction's basic cost is above the gas limit", result.getErrorMessage());

        Assertions.assertTrue(transactionPool.getPendingTransactions().isEmpty());
    }

    @Test
    void checkTxWhichCanNotBePaidIsRejected() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);

        // basic tx cost is 21000
        Transaction tx1 = createSampleTransaction(1, 2, 500000 - 21000, 0);
        Transaction tx2 = createSampleTransaction(1, 2, 500001 - 21000, 1);

        transactionPool.addTransaction(tx1);
        TransactionPoolAddResult result = transactionPool.addTransaction(tx2);

        Assertions.assertFalse(result.transactionsWereAdded());
        Assertions.assertEquals("insufficient funds to pay for pending and new transactions", result.getErrorMessage());

        Assertions.assertEquals(1, transactionPool.getPendingTransactions().size());
        Assertions.assertTrue(transactionPool.getQueuedTransactions().isEmpty());
    }

    @Test
    void checkTxBumpIsNotConsideredOnTotalCosts() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);

        // basic tx cost is 21000
        int basicCost = 21000;
        int gasPrice = 500000 / basicCost;
        int gasPriceBumped = (int) (gasPrice * 1.4);

        // tx cost: 483000, accumulated after: 483000
        Transaction tx1 = createSampleTransactionWithGasPrice(1, 2, 0, 0, gasPrice);
        // tx cost: 672000, accumulated after: 672000 (replaced tx cost is skipped from calculations
        Transaction tx1Replaced = createSampleTransactionWithGasPrice(1, 2, 0, 0, gasPriceBumped);
        // tx cost: 483000, accumulated after: 1155000 (not enough balance for this tx)
        Transaction tx2 = createSampleTransactionWithGasPrice(1, 2, 0, 1, gasPrice);

        TransactionPoolAddResult resultTx1 = transactionPool.addTransaction(tx1);
        Assertions.assertTrue(resultTx1.transactionsWereAdded(), "tx1 should be added, balance is enough for its cost");
        Assertions.assertEquals(1, transactionPool.getPendingTransactions().size());
        Assertions.assertTrue(transactionPool.getQueuedTransactions().isEmpty());

        TransactionPoolAddResult resultTx1Replaced = transactionPool.addTransaction(tx1Replaced);
        Assertions.assertTrue(resultTx1Replaced.transactionsWereAdded(), "tx1Replaced should be added, it was replacing a pending tx and balance is enough for its new cost (replaced tx cost should be skipped from calculations)");
        Assertions.assertEquals(1, transactionPool.getPendingTransactions().size());
        Assertions.assertTrue(transactionPool.getQueuedTransactions().isEmpty());

        TransactionPoolAddResult resultTx2 = transactionPool.addTransaction(tx2);
        Assertions.assertFalse(resultTx2.transactionsWereAdded(), "tx2 should NOT be added, with tx1Replaced bumped price there should not be enough balance to pay for both tx1Replaced and tx2");
        Assertions.assertEquals("insufficient funds to pay for pending and new transactions", resultTx2.getErrorMessage());
        Assertions.assertEquals(1, transactionPool.getPendingTransactions().size());
        Assertions.assertTrue(transactionPool.getQueuedTransactions().isEmpty());
    }

    @Test
    void aNewTxIsAddedInTxPoolAndShouldBeAddedInCache() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);
        Account account1 = createAccount(1);
        Transaction tx = createSampleTransaction(1, 2, 1000, 0);
        transactionPool.addTransaction(tx);

        Assertions.assertNotNull(signatureCache.getSender(tx));
        Assertions.assertArrayEquals(signatureCache.getSender(tx).getBytes(), account1.getAddress().getBytes());
    }

    @Test
    void twoTxsAreAddedInTxPoolAndShouldBeAddedInCache() {
        Coin balance = Coin.valueOf(1000000);
        Account account1 = createAccount(1);
        createTestAccounts(2, balance);
        Transaction tx1 = createSampleTransaction(1, 2, 1000, 0);
        Transaction tx2 = createSampleTransaction(1, 2, 1000, 1);

        transactionPool.addTransaction(tx1);
        transactionPool.addTransaction(tx2);

        Assertions.assertNotNull(signatureCache.getSender(tx1));
        Assertions.assertNotNull(signatureCache.getSender(tx2));
        Assertions.assertArrayEquals(signatureCache.getSender(tx1).getBytes(), account1.getAddress().getBytes());
        Assertions.assertArrayEquals(signatureCache.getSender(tx2).getBytes(), account1.getAddress().getBytes());
    }

    @Test
    void invalidTxsIsSentAndShouldntBeInCache() {
        Coin balance = Coin.valueOf(0);
        createTestAccounts(2, balance);
        Transaction tx1 = createSampleTransaction(1, 2, 1000, 1);
        transactionPool.addTransaction(tx1);
        Assertions.assertNotNull(signatureCache.getSender(tx1));
    }

    @Test
    void remascTxIsReceivedAndShouldntBeInCache() {
        RemascTransaction tx = new RemascTransaction(10);
        transactionPool.addTransaction(tx);

        Assertions.assertNotNull(signatureCache.getSender(tx));
        verify(signatureCache, times(0)).storeSender(tx);

        signatureCache.storeSender(tx);
        Assertions.assertNotNull(signatureCache.getSender(tx));
    }

    @Test
    void firstTxIsRemovedWhenTheCacheLimitSizeIsExceeded() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(6005, balance);
        Transaction tx = createSampleTransaction(1, 2, 1, 0);
        transactionPool.addTransaction(tx);

        for (int i = 0; i < MAX_CACHE_SIZE; i++) {
            if (i == MAX_CACHE_SIZE - 1) {
                Assertions.assertNotNull(signatureCache.getSender(tx));
            }
            Transaction sampleTransaction = createSampleTransaction(i + 2, 2, 1, 1);
            TransactionPoolAddResult result = transactionPool.addTransaction(sampleTransaction);
            Assertions.assertTrue(result.transactionsWereAdded());
        }

        Assertions.assertNotNull(signatureCache.getSender(tx));
    }

    private void createTestAccounts(int naccounts, Coin balance) {
        Repository track = repository.startTracking();

        for (int k = 1; k <= naccounts; k++) {
            Account account = createAccount(k);
            track.createAccount(account.getAddress());
            track.addBalance(account.getAddress(), balance);
        }

        track.commit();
    }

    @Test
    void addTransaction_addTwoTransactionsUnsorted_ResultWithPendingTransactionsSortedByNonce() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);

        Transaction tx1 = createSampleTransactionWithGasPrice(1, 0, 1000, 0, 1);
        Transaction tx2 = createSampleTransactionWithGasPrice(1, 0, 2000, 1, 2);

        TransactionPoolAddResult result1 = transactionPool.addTransaction(tx2);

        Assertions.assertTrue(result1.queuedTransactionsWereAdded());
        Assertions.assertTrue(!result1.pendingTransactionsWereAdded());
        Assertions.assertEquals(1, result1.getQueuedTransactionsAdded().size());
        Assertions.assertEquals(result1.getQueuedTransactionsAdded().get(0), tx2);
        Assertions.assertEquals(1, transactionPool.getQueuedTransactions().size());
        Assertions.assertEquals(tx2, transactionPool.getQueuedTransactions().get(0));
        Assertions.assertEquals(0, transactionPool.getPendingTransactions().size());
        Assertions.assertNotNull(signatureCache.getSender(tx2));

        TransactionPoolAddResult result2 = transactionPool.addTransaction(tx1);

        Assertions.assertTrue(!result2.queuedTransactionsWereAdded());
        Assertions.assertTrue(result2.pendingTransactionsWereAdded());
        Assertions.assertEquals(2, result2.getPendingTransactionsAdded().size());
        Assertions.assertEquals(result2.getPendingTransactionsAdded().get(0), tx1);
        Assertions.assertEquals(result2.getPendingTransactionsAdded().get(1), tx2);
        Assertions.assertEquals(0, transactionPool.getQueuedTransactions().size());
        Assertions.assertEquals(2, transactionPool.getPendingTransactions().size());
        Assertions.assertEquals(tx1, transactionPool.getPendingTransactions().get(0));
        Assertions.assertEquals(tx2, transactionPool.getPendingTransactions().get(1));
        Assertions.assertNotNull(signatureCache.getSender(tx1));
        Assertions.assertNotNull(signatureCache.getSender(tx2));
    }

    @Test
    void addTransactions_addTwoTransactionsUnsorted_pendingTransactionsSortedByNonce() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);

        Transaction tx1 = createSampleTransactionWithGasPrice(1, 0, 1000, 0, 1);
        Transaction tx2 = createSampleTransactionWithGasPrice(1, 0, 2000, 1, 2);

        List<Transaction> result = transactionPool.addTransactions(Arrays.asList(tx2, tx1));

        Assertions.assertEquals(2, result.size());
        Assertions.assertEquals(0, transactionPool.getQueuedTransactions().size());
        Assertions.assertEquals(2, transactionPool.getPendingTransactions().size());
        Assertions.assertEquals(tx1, transactionPool.getPendingTransactions().get(0));
        Assertions.assertEquals(tx2, transactionPool.getPendingTransactions().get(1));
        Assertions.assertNotNull(signatureCache.getSender(tx1));
        Assertions.assertNotNull(signatureCache.getSender(tx2));
    }
}
