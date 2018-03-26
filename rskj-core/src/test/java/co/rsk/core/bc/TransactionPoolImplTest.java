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
import co.rsk.core.BlockDifficulty;
import co.rsk.core.Coin;
import co.rsk.test.builders.BlockBuilder;
import co.rsk.test.builders.BlockChainBuilder;
import org.ethereum.core.Account;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.db.RepositoryTrack;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.Assert;
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

    @Test
    public void usingRepository() {
        TransactionPoolImpl transactionPool = createSampleNewTransactionPool(createBlockchain());

        Repository repository = transactionPool.getRepository();

        Assert.assertNotNull(repository);
        Assert.assertTrue(repository instanceof RepositoryTrack);
    }

    @Test
    public void usingInit() {
        TransactionPoolImpl transactionPool = createSampleNewTransactionPool(createBlockchain());

        Assert.assertFalse(transactionPool.hasCleanerFuture());
        Assert.assertNotEquals(0, transactionPool.getOutdatedThreshold());
        Assert.assertNotEquals(0, transactionPool.getOutdatedTimeout());
    }

    @Test
    public void usingCleanUp() {
        TransactionPoolImpl transactionPool = createSampleNewTransactionPool(createBlockchain());

        transactionPool.cleanUp();

        Assert.assertTrue(transactionPool.getPendingTransactions().isEmpty());
    }

    @Test
    public void usingStart() {
        BlockChainImpl blockchain = createBlockchain();
        TransactionPoolImpl transactionPool = createSampleNewTransactionPool(blockchain);

        transactionPool.start(blockchain.getBestBlock());

        Assert.assertTrue(transactionPool.hasCleanerFuture());

        transactionPool.stop();

        Assert.assertFalse(transactionPool.hasCleanerFuture());
    }

    @Test
    public void usingAccountsWithInitialBalance() {
        TransactionPoolImpl transactionPool = createSampleNewTransactionPoolWithAccounts(2, Coin.valueOf(10L), createBlockchain());

        Repository repository = transactionPool.getRepository();

        Assert.assertNotNull(repository);

        Account account1 = createAccount(1);
        Account account2 = createAccount(2);

        Assert.assertEquals(BigInteger.TEN, repository.getBalance(account1.getAddress()).asBigInteger());
        Assert.assertEquals(BigInteger.TEN, repository.getBalance(account2.getAddress()).asBigInteger());
    }

    @Test
    public void getEmptyPendingTransactionList() {
        TransactionPoolImpl transactionPool = createSampleNewTransactionPool(createBlockchain());

        List<Transaction> transactions = transactionPool.getPendingTransactions();

        Assert.assertNotNull(transactions);
        Assert.assertTrue(transactions.isEmpty());
    }

    @Test
    public void addAndGetPendingTransaction() {
        TransactionPoolImpl transactionPool = createSampleNewTransactionPool(createBlockchain());
        Transaction tx = createSampleTransaction();

        transactionPool.addTransaction(tx);
        List<Transaction> transactions = transactionPool.getPendingTransactions();

        Assert.assertNotNull(transactions);
        Assert.assertFalse(transactions.isEmpty());
        Assert.assertTrue(transactions.contains(tx));
    }

    @Test
    public void addAndGetQueuedTransaction() {
        TransactionPoolImpl transactionPool = createSampleNewTransactionPool(createBlockchain());
        Transaction tx = createSampleTransaction(10);

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
        TransactionPoolImpl transactionPool = createSampleNewTransactionPool(createBlockchain());
        Transaction tx1 = createSampleTransaction(1);
        Transaction tx2 = createSampleTransaction(2);

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
        TransactionPoolImpl transactionPool = createSampleNewTransactionPool(createBlockchain());
        Transaction tx1 = createSampleTransaction(1);
        Transaction tx2 = createSampleTransaction(2);
        Transaction tx0 = createSampleTransaction(0);

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
        BlockChainImpl blockchain = createBlockchain();
        Coin balance = Coin.valueOf(1000000);
        TransactionPoolImpl transactionPool = createSampleNewTransactionPoolWithAccounts(2, balance, blockchain);
        transactionPool.processBest(blockchain.getBestBlock());
        Transaction tx = createSampleTransaction(1, 2, 1000, 0);
        Account receiver = createAccount(2);

        transactionPool.addTransaction(tx);

        Repository repository = transactionPool.getRepository();
        Assert.assertEquals(BigInteger.valueOf(1001000), repository.getBalance(receiver.getAddress()).asBigInteger());
    }

    @Test
    public void addAndExecuteTwoPendingTransaction() {
        BlockChainImpl blockchain = createBlockchain();
        Coin balance = Coin.valueOf(1000000);
        TransactionPoolImpl transactionPool = createSampleNewTransactionPoolWithAccounts(2, balance, blockchain);
        transactionPool.processBest(blockchain.getBestBlock());
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
        BlockChainImpl blockchain = createBlockchain();
        Coin balance = Coin.valueOf(1000000);
        TransactionPoolImpl transactionPool = createSampleNewTransactionPoolWithAccounts(2, balance, blockchain);
        transactionPool.processBest(blockchain.getBestBlock());
        Transaction tx = createSampleTransaction(1, 2, 1000, 0);
        tx.setGasLimit(BigInteger.valueOf(3000001).toByteArray());
        Account receiver = createAccount(2);

        transactionPool.addTransaction(tx);

        Repository repository = transactionPool.getRepository();
        Assert.assertEquals(BigInteger.valueOf(1000000), repository.getBalance(receiver.getAddress()).asBigInteger());
    }

    @Test
    public void removeObsoletePendingTransactionsByBlock() {
        BlockChainImpl blockchain = createBlockchain();
        Coin balance = Coin.valueOf(1000000);
        TransactionPoolImpl transactionPool = createSampleNewTransactionPoolWithAccounts(2, balance, blockchain);
        transactionPool.processBest(blockchain.getBestBlock());
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
        BlockChainImpl blockchain = createBlockchain();
        Coin balance = Coin.valueOf(1000000);
        TransactionPoolImpl transactionPool = createSampleNewTransactionPoolWithAccounts(2, balance, blockchain);
        transactionPool.processBest(blockchain.getBestBlock());
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
        BlockChainImpl blockchain = createBlockchain();
        Coin balance = Coin.valueOf(1000000);
        TransactionPoolImpl transactionPool = createSampleNewTransactionPoolWithAccounts(2, balance, blockchain);
        transactionPool.processBest(blockchain.getBestBlock());
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
        BlockChainImpl blockchain = createBlockchain();
        Coin balance = Coin.valueOf(1000000);
        TransactionPoolImpl transactionPool = createSampleNewTransactionPoolWithAccounts(2, balance, blockchain);
        transactionPool.processBest(blockchain.getBestBlock());
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
        BlockChainImpl blockchain = createBlockchain();
        Coin balance = Coin.valueOf(1000000);
        TransactionPoolImpl transactionPool = createSampleNewTransactionPoolWithAccounts(2, balance, blockchain);
        transactionPool.processBest(blockchain.getBestBlock());
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
        BlockChainImpl blockchain = createBlockchain();
        Coin balance = Coin.valueOf(1000000);
        TransactionPoolImpl transactionPool = createSampleNewTransactionPoolWithAccounts(3, balance, blockchain);
        transactionPool.processBest(blockchain.getBestBlock());
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

        Block genesis = blockchain.getBestBlock();
        Block block = new BlockBuilder().parent(genesis).transactions(btxs).build();

        transactionPool.getBlockStore().saveBlock(genesis, new BlockDifficulty(BigInteger.ONE), true);
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
        BlockChainImpl blockchain = createBlockchain();
        Coin balance = Coin.valueOf(1000000);
        TransactionPoolImpl transactionPool = createSampleNewTransactionPoolWithAccounts(3, balance, blockchain);
        transactionPool.processBest(blockchain.getBestBlock());
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
        BlockChainImpl blockchain = createBlockchain();
        Coin balance = Coin.valueOf(1000000);
        TransactionPoolImpl transactionPool = createSampleNewTransactionPoolWithAccounts(2, balance, blockchain);
        transactionPool.processBest(blockchain.getBestBlock());
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
        TransactionPoolImpl transactionPool = createSampleNewTransactionPool(createBlockchain());
        Transaction tx = createSampleTransaction();

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
        TransactionPoolImpl transactionPool = createSampleNewTransactionPool(createBlockchain());

        List<Transaction> transactions = transactionPool.getPendingTransactions();

        Assert.assertNotNull(transactions);
        Assert.assertTrue(transactions.isEmpty());
    }
    
    @Test
    public void executeContractWithFakeBlock() {
        BlockChainImpl blockchain = createBlockchain();
        Coin balance = Coin.valueOf(1000000);
        TransactionPoolImpl transactionPool = createSampleNewTransactionPoolWithAccounts(2, balance, blockchain);
        transactionPool.processBest(blockchain.getBestBlock());
        // "NUMBER PUSH1 0x00 SSTORE" compiled to bytecodes
        String code = "43600055";
        Transaction tx = createSampleTransactionWithData(1, 0, code);

        transactionPool.addTransaction(tx);

        Assert.assertNotNull(tx.getContractAddress().getBytes());
        // Stored value at 0 position should be 1, one more than the blockchain best block
        Assert.assertEquals(DataWord.ONE, transactionPool.getRepository().getStorageValue(tx.getContractAddress(), DataWord.ZERO));
    }

    private static TransactionPoolImpl createSampleNewTransactionPool(BlockChainImpl blockChain) {
        TransactionPoolImpl transactionPool = new TransactionPoolImpl(config, blockChain.getRepository(), blockChain.getBlockStore(), null, new ProgramInvokeFactoryImpl(), new BlockExecutorTest.SimpleEthereumListener(), 10, 100);
        transactionPool.processBest(blockChain.getBestBlock());
        return transactionPool;
    }

    private static TransactionPoolImpl createSampleNewTransactionPoolWithAccounts(int naccounts, Coin balance, BlockChainImpl blockChain) {
        Block best = blockChain.getStatus().getBestBlock();
        Repository repository = blockChain.getRepository();

        Repository track = repository.startTracking();

        for (int k = 1; k <= naccounts; k++) {
            Account account = createAccount(k);
            track.createAccount(account.getAddress());
            track.addBalance(account.getAddress(), balance);
        }

        track.commit();

        best.setStateRoot(repository.getRoot());
        best.flushRLP();

        TransactionPoolImpl transactionPool = new TransactionPoolImpl(config, blockChain.getRepository(), blockChain.getBlockStore(), null, new ProgramInvokeFactoryImpl(), new BlockExecutorTest.SimpleEthereumListener(), 10, 100);
        blockChain.setTransactionPool(transactionPool);

        return transactionPool;
    }

    private static BlockChainImpl createBlockchain() {
        BlockChainImpl blockChain = new BlockChainBuilder().build();
        Block genesis = BlockChainImplTest.getGenesisBlock(blockChain);
        blockChain.setStatus(genesis, genesis.getCumulativeDifficulty());
        return blockChain;
    }
}
