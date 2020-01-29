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
import co.rsk.core.Coin;
import co.rsk.core.genesis.TestGenesisLoader;
import co.rsk.db.RepositoryLocator;
import co.rsk.remasc.RemascTransaction;
import co.rsk.test.builders.BlockBuilder;
import org.ethereum.core.*;
import org.ethereum.core.genesis.GenesisLoader;
import org.ethereum.util.RskTestContext;
import org.ethereum.vm.DataWord;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.ethereum.util.TransactionFactoryHelper.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * Created by ajlopez on 08/08/2016.
 */
public class TransactionPoolImplTest {
    private Blockchain blockChain;
    private TransactionPoolImpl transactionPool;
    private Repository repository;

    @Before
    public void setUp() {
        RskTestContext rskTestContext = new RskTestContext(new String[]{"--regtest"}) {
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
        transactionPool = new TransactionPoolImpl(
                rskTestContext.getRskSystemProperties(),
                repositoryLocator,
                rskTestContext.getBlockStore(),
                rskTestContext.getBlockFactory(),
                rskTestContext.getCompositeEthereumListener(),
                rskTestContext.getTransactionExecutorFactory(),
                rskTestContext.getReceivedTxSignatureCache(),
                10,
                100);
        // don't call start to avoid creating threads
        transactionPool.processBest(blockChain.getBestBlock());

        // this is to workaround the current test structure, which abuses the Repository by
        // modifying it in place
        doReturn(repository).when(repositoryLocator).snapshotAt(any());
    }

    @Test
    public void usingInit() {
        Assert.assertFalse(transactionPool.hasCleanerFuture());
        Assert.assertNotEquals(0, transactionPool.getOutdatedThreshold());
        Assert.assertNotEquals(0, transactionPool.getOutdatedTimeout());
    }

    @Test
    public void usingCleanUp() {
        transactionPool.cleanUp();

        Assert.assertTrue(transactionPool.getPendingTransactions().isEmpty());
    }

    @Test
    public void usingStart() {
        Assert.assertFalse(transactionPool.hasCleanerFuture());
        transactionPool.start();
        Assert.assertTrue(transactionPool.hasCleanerFuture());
    }

    @Test
    public void usingAccountsWithInitialBalance() {
        createTestAccounts(2, Coin.valueOf(10L));

        PendingState pendingState = transactionPool.getPendingState();

        Assert.assertNotNull(pendingState);

        Account account1 = createAccount(1);
        Account account2 = createAccount(2);

        Assert.assertEquals(BigInteger.TEN, pendingState.getBalance(account1.getAddress()).asBigInteger());
        Assert.assertEquals(BigInteger.TEN, pendingState.getBalance(account2.getAddress()).asBigInteger());
    }

    @Test
    public void getEmptyPendingTransactionList() {
        List<Transaction> transactions = transactionPool.getPendingTransactions();

        Assert.assertNotNull(transactions);
        Assert.assertTrue(transactions.isEmpty());
    }

    @Test
    public void addAndGetPendingTransaction() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(1, balance);
        Transaction tx = createSampleTransaction(1, 2, 1000, 0);

        transactionPool.addTransaction(tx);
        List<Transaction> transactions = transactionPool.getPendingTransactions();

        Assert.assertNotNull(transactions);
        Assert.assertFalse(transactions.isEmpty());
        Assert.assertTrue(transactions.contains(tx));
    }

    @Test
    public void addAndGetQueuedTransaction() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(1, balance);
        Transaction tx = createSampleTransaction(1, 2, 1000, 4);

        transactionPool.addTransaction(tx);

        List<Transaction> pendingTransactions = transactionPool.getPendingTransactions();

        Assert.assertNotNull(pendingTransactions);
        Assert.assertTrue(pendingTransactions.isEmpty());

        List<Transaction> queuedTransactions = transactionPool.getQueuedTransactions();

        Assert.assertNotNull(queuedTransactions);
        Assert.assertFalse(queuedTransactions.isEmpty());
        Assert.assertEquals(1, queuedTransactions.size());
        Assert.assertTrue(queuedTransactions.contains(tx));
    }

    @Test
    public void addAndGetTwoQueuedTransaction() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(1, balance);
        Transaction tx1 = createSampleTransaction(1, 2, 1000, 1);
        Transaction tx2 = createSampleTransaction(1, 2, 1000, 2);

        transactionPool.addTransaction(tx1);
        transactionPool.addTransaction(tx2);

        List<Transaction> pendingTransactions = transactionPool.getPendingTransactions();

        Assert.assertNotNull(pendingTransactions);
        Assert.assertTrue(pendingTransactions.isEmpty());

        List<Transaction> queuedTransactions = transactionPool.getQueuedTransactions();

        Assert.assertNotNull(queuedTransactions);
        Assert.assertFalse(queuedTransactions.isEmpty());
        Assert.assertEquals(2, queuedTransactions.size());
        Assert.assertTrue(queuedTransactions.contains(tx1));
        Assert.assertTrue(queuedTransactions.contains(tx2));
    }

    @Test
    public void addAndGetTwoQueuedTransactionAsPendingOnes() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(1, balance);
        Transaction tx0 = createSampleTransaction(1, 2, 1000, 0);
        Transaction tx1 = createSampleTransaction(1, 2, 1000, 1);
        Transaction tx2 = createSampleTransaction(1, 2, 1000, 2);

        Assert.assertTrue(transactionPool.addTransaction(tx1).transactionWasAdded());
        Assert.assertTrue(transactionPool.addTransaction(tx2).transactionWasAdded());

        List<Transaction> transactionsToProcess = new ArrayList<>();
        transactionsToProcess.add(tx0);

        List<Transaction> processedTransactions = transactionPool.addTransactions(transactionsToProcess);

        Assert.assertNotNull(processedTransactions);
        Assert.assertFalse(processedTransactions.isEmpty());
        Assert.assertEquals(3, processedTransactions.size());
        Assert.assertTrue(processedTransactions.contains(tx0));
        Assert.assertTrue(processedTransactions.contains(tx1));
        Assert.assertTrue(processedTransactions.contains(tx2));

        List<Transaction> pendingTransactions = transactionPool.getPendingTransactions();

        Assert.assertNotNull(pendingTransactions);
        Assert.assertFalse(pendingTransactions.isEmpty());
        Assert.assertEquals(3, pendingTransactions.size());
        Assert.assertTrue(pendingTransactions.contains(tx0));
        Assert.assertTrue(pendingTransactions.contains(tx1));
        Assert.assertTrue(pendingTransactions.contains(tx2));

        List<Transaction> queuedTransactions = transactionPool.getQueuedTransactions();

        Assert.assertNotNull(queuedTransactions);
        Assert.assertTrue(queuedTransactions.isEmpty());
    }

    @Test
    public void addAndExecutePendingTransaction() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);
        Transaction tx = createSampleTransaction(1, 2, 1000, 0);
        Account receiver = createAccount(2);

        transactionPool.addTransaction(tx);

        PendingState pendingState = transactionPool.getPendingState();
        Assert.assertEquals(BigInteger.valueOf(1001000), pendingState.getBalance(receiver.getAddress()).asBigInteger());
    }

    @Test
    public void removeObsoletePendingTransactionsByBlock() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);
        Transaction tx1 = createSampleTransaction(1, 2, 1000, 0);
        Transaction tx2 = createSampleTransaction(1, 2, 3000, 1);

        transactionPool.addTransaction(tx1);
        transactionPool.addTransaction(tx2);

        Assert.assertEquals(10, transactionPool.getOutdatedThreshold());

        List<Transaction> list = transactionPool.getPendingTransactions();

        Assert.assertNotNull(list);
        Assert.assertFalse(list.isEmpty());
        Assert.assertEquals(2, list.size());

        transactionPool.removeObsoleteTransactions(1, 1, 100);

        list = transactionPool.getPendingTransactions();

        Assert.assertNotNull(list);
        Assert.assertFalse(list.isEmpty());
        Assert.assertEquals(2, list.size());

        transactionPool.removeObsoleteTransactions(20, transactionPool.getOutdatedThreshold(), 100);

        list = transactionPool.getPendingTransactions();

        Assert.assertNotNull(list);
        Assert.assertTrue(list.isEmpty());
    }

    @Test
    public void removeObsoleteQueuedTransactionsByBlock() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);
        Transaction tx1 = createSampleTransaction(1, 2, 1000, 1);
        Transaction tx2 = createSampleTransaction(1, 2, 3000, 2);

        transactionPool.addTransaction(tx1);
        transactionPool.addTransaction(tx2);

        Assert.assertEquals(10, transactionPool.getOutdatedThreshold());

        List<Transaction> list = transactionPool.getQueuedTransactions();

        Assert.assertNotNull(list);
        Assert.assertFalse(list.isEmpty());
        Assert.assertEquals(2, list.size());

        transactionPool.removeObsoleteTransactions(1, 1, 100);

        list = transactionPool.getQueuedTransactions();

        Assert.assertNotNull(list);
        Assert.assertFalse(list.isEmpty());
        Assert.assertEquals(2, list.size());

        transactionPool.removeObsoleteTransactions(20, transactionPool.getOutdatedThreshold(), 100);

        list = transactionPool.getQueuedTransactions();

        Assert.assertNotNull(list);
        Assert.assertTrue(list.isEmpty());
    }

    @Test
    public void removeObsoletePendingTransactionsByTimeout() throws InterruptedException {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);
        Transaction tx1 = createSampleTransaction(1, 2, 1000, 0);
        Transaction tx2 = createSampleTransaction(1, 2, 3000, 1);

        transactionPool.addTransaction(tx1);
        transactionPool.addTransaction(tx2);

        Assert.assertEquals(10, transactionPool.getOutdatedThreshold());

        List<Transaction> list = transactionPool.getPendingTransactions();

        Assert.assertNotNull(list);
        Assert.assertFalse(list.isEmpty());
        Assert.assertEquals(2, list.size());

        Thread.sleep(3000);

        transactionPool.removeObsoleteTransactions(1, 1, 1);

        list = transactionPool.getPendingTransactions();

        Assert.assertNotNull(list);
        Assert.assertTrue(list.isEmpty());
    }

    @Test
    public void removeObsoleteQueuedTransactionsByTimeout() throws InterruptedException {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);
        Transaction tx1 = createSampleTransaction(1, 2, 1000, 1);
        Transaction tx2 = createSampleTransaction(1, 2, 3000, 2);

        transactionPool.addTransaction(tx1);
        transactionPool.addTransaction(tx2);

        Assert.assertEquals(10, transactionPool.getOutdatedThreshold());

        List<Transaction> list = transactionPool.getQueuedTransactions();

        Assert.assertNotNull(list);
        Assert.assertFalse(list.isEmpty());
        Assert.assertEquals(2, list.size());

        Thread.sleep(3000);

        transactionPool.removeObsoleteTransactions(1, 1, 1);

        list = transactionPool.getQueuedTransactions();

        Assert.assertNotNull(list);
        Assert.assertTrue(list.isEmpty());
    }

    @Test
    public void getAllPendingTransactions() {
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

        Assert.assertNotNull(alltxs);
        Assert.assertFalse(alltxs.isEmpty());
        Assert.assertEquals(2, alltxs.size());
        Assert.assertTrue(alltxs.contains(tx1));
        Assert.assertTrue(alltxs.contains(tx2));
    }

    @Test
    public void processBestBlockRemovesTransactionsInBlock() {
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

        Assert.assertNotNull(alltxs);
        Assert.assertFalse(alltxs.isEmpty());
        Assert.assertEquals(2, alltxs.size());
        Assert.assertFalse(alltxs.contains(tx1));
        Assert.assertTrue(alltxs.contains(tx2));
        Assert.assertFalse(alltxs.contains(tx3));
        Assert.assertTrue(alltxs.contains(tx4));

        Assert.assertSame(block, transactionPool.getBestBlock());
    }

    @Test
    public void retractBlockAddsTransactionsAsPending() {
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

        Block block = new BlockBuilder(null, null,null)
                .parent(new BlockGenerator().getGenesisBlock()).transactions(txs).build();

        transactionPool.retractBlock(block);

        List<Transaction> alltxs = transactionPool.getPendingTransactions();

        Assert.assertNotNull(alltxs);
        Assert.assertFalse(alltxs.isEmpty());
        Assert.assertEquals(4, alltxs.size());
        Assert.assertTrue(alltxs.contains(tx1));
        Assert.assertTrue(alltxs.contains(tx2));
        Assert.assertTrue(alltxs.contains(tx3));
        Assert.assertTrue(alltxs.contains(tx4));

        List<Transaction> ptxs = transactionPool.getPendingTransactions();

        Assert.assertNotNull(ptxs);
        Assert.assertFalse(ptxs.isEmpty());
        Assert.assertEquals(4, ptxs.size());
        Assert.assertTrue(ptxs.contains(tx1));
        Assert.assertTrue(ptxs.contains(tx2));
        Assert.assertTrue(ptxs.contains(tx3));
        Assert.assertTrue(ptxs.contains(tx4));
    }

    @Test
    public void updateTransactionPool() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);
        Transaction tx1 = createSampleTransaction(1, 2, 1000, 0);
        Transaction tx2 = createSampleTransaction(1, 2, 3000, 1);
        Account receiver = createAccount(2);

        transactionPool.addTransaction(tx1);
        transactionPool.addTransaction(tx2);

        PendingState pendingState = transactionPool.getPendingState();
        Assert.assertEquals(BigInteger.valueOf(1004000), pendingState.getBalance(receiver.getAddress()).asBigInteger());
    }

    @Test
    public void addTwiceAndGetPendingTransaction() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(1, balance);
        Transaction tx = createSampleTransaction(1, 2, 1000, 0);

        transactionPool.addTransaction(tx);

        TransactionPoolAddResult result = transactionPool.addTransaction(tx);
        Assert.assertFalse(result.transactionWasAdded());
        result.ifTransactionWasNotAdded(msg -> Assert.assertEquals("pending transaction with same hash already exists", msg));

        List<Transaction> transactions = transactionPool.getPendingTransactions();

        Assert.assertNotNull(transactions);
        Assert.assertFalse(transactions.isEmpty());
        Assert.assertEquals(1, transactions.size());
        Assert.assertTrue(transactions.contains(tx));
    }

    @Test
    public void addTwiceAndGetQueuedTransaction() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(1, balance);
        Transaction tx = createSampleTransaction(1, 2, 1000, 1);

        transactionPool.addTransaction(tx);

        TransactionPoolAddResult result = transactionPool.addTransaction(tx);
        Assert.assertFalse(result.transactionWasAdded());
        result.ifTransactionWasNotAdded(msg -> Assert.assertEquals("queued transaction with same hash already exists", msg));

        List<Transaction> transactions = transactionPool.getQueuedTransactions();

        Assert.assertNotNull(transactions);
        Assert.assertFalse(transactions.isEmpty());
        Assert.assertEquals(1, transactions.size());
        Assert.assertTrue(transactions.contains(tx));
    }

    @Test
    public void getEmptyTransactionList() {
        List<Transaction> transactions = transactionPool.getPendingTransactions();

        Assert.assertNotNull(transactions);
        Assert.assertTrue(transactions.isEmpty());
    }

    @Test
    public void executeContractWithFakeBlock() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);
        // "NUMBER PUSH1 0x00 SSTORE" compiled to bytecodes
        String code = "43600055";
        Transaction tx = createSampleTransactionWithData(1, 0, code);

        transactionPool.addTransaction(tx);

        Assert.assertNotNull(tx.getContractAddress().getBytes());
        // Stored value at 0 position should be 1, one more than the blockChain best block
        Assert.assertEquals(DataWord.ONE, transactionPool.getPendingState().getStorageValue(tx.getContractAddress(), DataWord.ZERO));
    }

    @Test
    public void checkTxWithSameNonceIsRejected() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);
        Transaction tx = createSampleTransaction(1, 0, 1000, 0);
        Transaction tx2 = createSampleTransaction(1, 0, 2000, 0);

        transactionPool.addTransaction(tx);

        TransactionPoolAddResult result = transactionPool.addTransaction(tx2);

        Assert.assertFalse(result.transactionWasAdded());
        result.ifTransactionWasNotAdded(msg -> Assert.assertEquals("gas price not enough to bump transaction", msg));
    }

    @Test
    public void checkTxWithSameNonceBumpedIsAccepted() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);
        Transaction tx1 = createSampleTransactionWithGasPrice(1, 0, 1000, 0, 1);
        Transaction tx2 = createSampleTransactionWithGasPrice(1, 0, 2000, 0, 2);

        transactionPool.addTransaction(tx1);
        Assert.assertTrue(transactionPool.addTransaction(tx2).transactionWasAdded());
        Assert.assertTrue(transactionPool.getPendingTransactions().stream().anyMatch(tx -> tx.getHash().equals(tx2.getHash())));
        Assert.assertFalse(transactionPool.getPendingTransactions().stream().anyMatch(tx -> tx.getHash().equals(tx1.getHash())));
    }

    @Test
    public void checkTxWithHighGasLimitIsRejected() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);
        Transaction tx = createSampleTransaction(1, 2, 1000, 0, BigInteger.valueOf(3000001));
        Account receiver = createAccount(2);

        TransactionPoolAddResult result = transactionPool.addTransaction(tx);

        Assert.assertFalse(result.transactionWasAdded());
        result.ifTransactionWasNotAdded(msg -> Assert.assertEquals("transaction's gas limit of 3000001 is higher than the block's gas limit of 3000000", msg));

        List<Transaction> pending = transactionPool.getPendingTransactions();
        Assert.assertTrue(pending.isEmpty());
    }

    @Test
    public void checkTxWithHighNonceIsRejected() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);
        Transaction tx = createSampleTransaction(1, 2, 1000, 5);

        TransactionPoolAddResult result = transactionPool.addTransaction(tx);

        Assert.assertFalse(result.transactionWasAdded());
        result.ifTransactionWasNotAdded(msg -> Assert.assertEquals("transaction nonce too high", msg));

        Assert.assertTrue(transactionPool.getPendingTransactions().isEmpty());
        Assert.assertTrue(transactionPool.getQueuedTransactions().isEmpty());
    }

    @Test
    public void checkTxWithLowNonceIsRejected() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);

        Transaction tx = createSampleTransaction(1, 2, 3000, 0);

        repository.increaseNonce(tx.getSender());

        TransactionPoolAddResult result = transactionPool.addTransaction(tx);

        Assert.assertFalse(result.transactionWasAdded());
        result.ifTransactionWasNotAdded(msg -> Assert.assertEquals("transaction nonce too low", msg));

        Assert.assertTrue(transactionPool.getPendingTransactions().isEmpty());
        Assert.assertTrue(transactionPool.getQueuedTransactions().isEmpty());
    }

    @Test
    public void checkRemascTxIsRejected() {
        RemascTransaction tx = new RemascTransaction(10);

        TransactionPoolAddResult result = transactionPool.addTransaction(tx);

        Assert.assertFalse(result.transactionWasAdded());
        result.ifTransactionWasNotAdded(msg -> Assert.assertEquals("transaction is a remasc transaction", msg));
    }

    @Test
    public void checkTxWithLowGasPriceIsRejected() {
        Block newBest = new BlockBuilder(null, null,null)
                .parent(transactionPool.getBestBlock()).minGasPrice(BigInteger.valueOf(100)).build();
        transactionPool.processBest(newBest);

        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);
        Transaction tx = createSampleTransactionWithGasPrice(1, 2, 1000, 0, 1);

        TransactionPoolAddResult result = transactionPool.addTransaction(tx);

        Assert.assertFalse(result.transactionWasAdded());
        result.ifTransactionWasNotAdded(msg -> Assert.assertEquals("transaction's gas price lower than block's minimum", msg));

        Assert.assertTrue(transactionPool.getPendingTransactions().isEmpty());
    }

    @Test
    public void checkTxFromAccountWithLowBalanceIsRejected() {
        Coin balance = Coin.valueOf(1000);
        createTestAccounts(2, balance);
        Transaction tx = createSampleTransaction(1, 2, 10000, 0);

        TransactionPoolAddResult result = transactionPool.addTransaction(tx);

        Assert.assertFalse(result.transactionWasAdded());
        result.ifTransactionWasNotAdded(msg -> Assert.assertEquals("insufficient funds", msg));

        Assert.assertTrue(transactionPool.getPendingTransactions().isEmpty());
    }

    @Test
    public void checkTxFromNullStateIsRejected() {
        Transaction tx = createSampleTransaction(1, 2, 1000, 0);

        TransactionPoolAddResult result = transactionPool.addTransaction(tx);

        Assert.assertFalse(result.transactionWasAdded());
        result.ifTransactionWasNotAdded(msg -> Assert.assertEquals("the sender account doesn't exist", msg));

        Assert.assertTrue(transactionPool.getPendingTransactions().isEmpty());
    }

    @Test
    public void checkTxWithHighIntrinsicGasIsRejected() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);

        // basic tx cost is 21000, set the gas limit below that
        Transaction tx = createSampleTransaction(1, 2, 1000, 0, BigInteger.valueOf(20000));

        TransactionPoolAddResult result = transactionPool.addTransaction(tx);

        Assert.assertFalse(result.transactionWasAdded());
        result.ifTransactionWasNotAdded(msg -> Assert.assertEquals("transaction's basic cost is above the gas limit", msg));

        Assert.assertTrue(transactionPool.getPendingTransactions().isEmpty());
    }

    @Test
    public void checkTxWhichCanNotBePaidIsRejected() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);

        // basic tx cost is 21000
        Transaction tx1 = createSampleTransaction(1, 2, 500000 - 21000, 0);
        Transaction tx2 = createSampleTransaction(1, 2, 500001 - 21000, 1);

        transactionPool.addTransaction(tx1);
        TransactionPoolAddResult result = transactionPool.addTransaction(tx2);

        Assert.assertFalse(result.transactionWasAdded());
        result.ifTransactionWasNotAdded(msg -> Assert.assertEquals("insufficient funds to pay for pending and new transaction", msg));

        Assert.assertEquals(1, transactionPool.getPendingTransactions().size());
        Assert.assertTrue(transactionPool.getQueuedTransactions().isEmpty());
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
}
