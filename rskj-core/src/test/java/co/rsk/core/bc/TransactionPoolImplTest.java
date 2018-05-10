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
import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.test.builders.BlockBuilder;
import org.ethereum.core.Account;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.db.RepositoryTrack;
import org.ethereum.listener.TestCompositeEthereumListener;
import org.ethereum.util.RskTestFactory;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.ethereum.util.TransactionFactoryHelper.*;

/**
 * Created by ajlopez on 08/08/2016.
 */
public class TransactionPoolImplTest {
    private static final TestSystemProperties config = new TestSystemProperties();

    private TransactionPoolImpl transactionPool;
    private BlockChainImpl blockChain;

    @Before
    public void setUp() {
        RskTestFactory factory = new RskTestFactory();
        blockChain = factory.getBlockchain();
        Block genesis = BlockChainImplTest.getGenesisBlock(blockChain);
        blockChain.setStatus(genesis, genesis.getCumulativeDifficulty());
        transactionPool = new TransactionPoolImpl(config, factory.getRepository(), null, null, new ProgramInvokeFactoryImpl(), new TestCompositeEthereumListener(), 10, 100);
        // don't call start to avoid creating threads
        transactionPool.processBest(blockChain.getBestBlock());
    }

    @Test
    public void usingRepository() {
        Repository repository = transactionPool.getRepository();

        Assert.assertNotNull(repository);
        Assert.assertTrue(repository instanceof RepositoryTrack);
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
        transactionPool.start(blockChain.getBestBlock());

        Assert.assertTrue(transactionPool.hasCleanerFuture());

        transactionPool.stop();

        Assert.assertFalse(transactionPool.hasCleanerFuture());
    }

    @Test
    public void usingAccountsWithInitialBalance() {
        createTestAccounts(2, Coin.valueOf(10L));

        Repository repository = transactionPool.getRepository();

        Assert.assertNotNull(repository);

        Account account1 = createAccount(1);
        Account account2 = createAccount(2);

        Assert.assertEquals(BigInteger.TEN, repository.getBalance(account1.getAddress()).asBigInteger());
        Assert.assertEquals(BigInteger.TEN, repository.getBalance(account2.getAddress()).asBigInteger());
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

        Assert.assertFalse(transactionPool.addTransaction(tx1));
        Assert.assertFalse(transactionPool.addTransaction(tx2));

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

        Repository repository = transactionPool.getRepository();
        Assert.assertEquals(BigInteger.valueOf(1001000), repository.getBalance(receiver.getAddress()).asBigInteger());
    }

    @Test
    public void addAndExecuteTwoPendingTransaction() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);
        Transaction tx1 = createSampleTransaction(1, 2, 1000, 0);
        Transaction tx2 = createSampleTransaction(1, 2, 3000, 1);
        Account receiver = createAccount(2);

        transactionPool.addTransaction(tx1);
        transactionPool.addTransaction(tx2);

        Repository repository = transactionPool.getRepository();
        Assert.assertEquals(BigInteger.valueOf(1004000), repository.getBalance(receiver.getAddress()).asBigInteger());
    }

    @Test
    public void rejectTransactionPoolTransaction() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);
        Transaction tx = createSampleTransaction(1, 2, 1000, 0, BigInteger.valueOf(3000001));
        Account receiver = createAccount(2);

        transactionPool.addTransaction(tx);

        Repository repository = transactionPool.getRepository();
        Assert.assertEquals(BigInteger.valueOf(1000000), repository.getBalance(receiver.getAddress()).asBigInteger());
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
        Block block = new BlockBuilder().parent(genesis).transactions(btxs).build();

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

        Block block = new BlockBuilder().parent(new BlockGenerator().getGenesisBlock()).transactions(txs).build();

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

        transactionPool.updateState();

        Repository repository = transactionPool.getRepository();
        Assert.assertEquals(BigInteger.valueOf(1004000), repository.getBalance(receiver.getAddress()).asBigInteger());
    }

    @Test
    public void addTwiceAndGetPendingTransaction() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(1, balance);
        Transaction tx = createSampleTransaction(1, 2, 1000, 0);

        transactionPool.addTransaction(tx);
        transactionPool.addTransaction(tx);
        List<Transaction> transactions = transactionPool.getPendingTransactions();

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
        Assert.assertEquals(DataWord.ONE, transactionPool.getRepository().getStorageValue(tx.getContractAddress(), DataWord.ZERO));
    }

    private void createTestAccounts(int naccounts, Coin balance) {
        Repository repository = blockChain.getRepository();

        Repository track = repository.startTracking();

        for (int k = 1; k <= naccounts; k++) {
            Account account = createAccount(k);
            track.createAccount(account.getAddress());
            track.addBalance(account.getAddress(), balance);
        }

        track.commit();
    }

}
