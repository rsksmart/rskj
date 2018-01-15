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
import co.rsk.config.ConfigHelper;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.BlockBuilder;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.test.builders.TransactionBuilder;
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

/**
 * Created by ajlopez on 08/08/2016.
 */
public class PendingStateImplTest {
    @Test
    public void usingRepository() {
        PendingStateImpl pendingState = createSampleNewPendingState();

        Repository repository = pendingState.getRepository();

        Assert.assertNotNull(repository);
        Assert.assertTrue(repository instanceof RepositoryTrack);
    }

    @Test
    public void usingInit() {
        PendingStateImpl pendingState = createSampleNewPendingState();

        Assert.assertFalse(pendingState.hasCleanerFuture());
        Assert.assertNotEquals(0, pendingState.getOutdatedThreshold());
        Assert.assertNotEquals(0, pendingState.getOutdatedTimeout());
    }

    @Test
    public void usingCleanUp() {
        PendingStateImpl pendingState = createSampleNewPendingState();

        pendingState.cleanUp();

        Assert.assertTrue(pendingState.getPendingTransactions().isEmpty());
    }

    @Test
    public void usingStart() {
        PendingStateImpl pendingState = createSampleNewPendingState();

        pendingState.start();

        Assert.assertTrue(pendingState.hasCleanerFuture());

        pendingState.stop();

        Assert.assertFalse(pendingState.hasCleanerFuture());
    }

    @Test
    public void usingAccountsWithInitialBalance() {
        PendingStateImpl pendingState = createSampleNewPendingStateWithAccounts(2, BigInteger.TEN);

        Repository repository = pendingState.getRepository();

        Assert.assertNotNull(repository);

        Account account1 = createAccount(1);
        Account account2 = createAccount(2);

        Assert.assertEquals(BigInteger.TEN, repository.getBalance(account1.getAddress()));
        Assert.assertEquals(BigInteger.TEN, repository.getBalance(account2.getAddress()));
    }

    @Test
    public void getEmptyPendingTransactionList() {
        PendingStateImpl pendingState = createSampleNewPendingState();

        List<Transaction> transactions = pendingState.getPendingTransactions();

        Assert.assertNotNull(transactions);
        Assert.assertTrue(transactions.isEmpty());
    }

    @Test
    public void addAndGetPendingTransaction() {
        PendingStateImpl pendingState = createSampleNewPendingState();
        Transaction tx = createSampleTransaction();

        pendingState.addPendingTransaction(tx);
        List<Transaction> transactions = pendingState.getPendingTransactions();

        Assert.assertNotNull(transactions);
        Assert.assertFalse(transactions.isEmpty());
        Assert.assertTrue(transactions.contains(tx));
    }

    @Test
    public void addAndExecutePendingTransaction() {
        PendingStateImpl pendingState = createSampleNewPendingStateWithAccounts(2, new BigInteger("1000000"));
        Transaction tx = createSampleTransaction(1, 2, 1000, 0);
        Account receiver = createAccount(2);

        pendingState.addPendingTransaction(tx);

        Repository repository = pendingState.getRepository();
        Assert.assertEquals(BigInteger.valueOf(1001000), repository.getBalance(receiver.getAddress()));
    }

    @Test
    public void addAndExecuteTwoPendingTransaction() {
        PendingStateImpl pendingState = createSampleNewPendingStateWithAccounts(2, new BigInteger("1000000"));
        Transaction tx1 = createSampleTransaction(1, 2, 1000, 0);
        Transaction tx2 = createSampleTransaction(1, 2, 3000, 1);
        Account receiver = createAccount(2);

        pendingState.addPendingTransaction(tx1);
        pendingState.addPendingTransaction(tx2);

        Repository repository = pendingState.getRepository();
        Assert.assertEquals(BigInteger.valueOf(1004000), repository.getBalance(receiver.getAddress()));
    }

    @Test
    public void rejectPendingStateTransaction() {
        PendingStateImpl pendingState = createSampleNewPendingStateWithAccounts(2, new BigInteger("1000000"));
        Transaction tx = createSampleTransaction(1, 2, 1000, 0);
        tx.setGasLimit(BigInteger.valueOf(3000001).toByteArray());
        Account receiver = createAccount(2);

        pendingState.addPendingTransaction(tx);

        Repository repository = pendingState.getRepository();
        Assert.assertEquals(BigInteger.valueOf(1000000), repository.getBalance(receiver.getAddress()));
    }

    @Test
    public void removeObsoletePendingTransactionsByBlock() {
        PendingStateImpl pendingState = createSampleNewPendingStateWithAccounts(2, new BigInteger("1000000"));
        Transaction tx1 = createSampleTransaction(1, 2, 1000, 0);
        Transaction tx2 = createSampleTransaction(1, 2, 3000, 1);

        pendingState.addPendingTransaction(tx1);
        pendingState.addPendingTransaction(tx2);

        Assert.assertEquals(10, pendingState.getOutdatedThreshold());

        List<Transaction> list = pendingState.getPendingTransactions();

        Assert.assertNotNull(list);
        Assert.assertFalse(list.isEmpty());
        Assert.assertEquals(2, list.size());

        pendingState.removeObsoleteTransactions(1, 1, 100);

        list = pendingState.getPendingTransactions();

        Assert.assertNotNull(list);
        Assert.assertFalse(list.isEmpty());
        Assert.assertEquals(2, list.size());

        pendingState.removeObsoleteTransactions(20, pendingState.getOutdatedThreshold(), 100);

        list = pendingState.getPendingTransactions();

        Assert.assertNotNull(list);
        Assert.assertTrue(list.isEmpty());
    }

    @Test
    public void removeObsoletePendingTransactionsByTimeout() throws InterruptedException {
        PendingStateImpl pendingState = createSampleNewPendingStateWithAccounts(2, new BigInteger("1000000"));
        Transaction tx1 = createSampleTransaction(1, 2, 1000, 0);
        Transaction tx2 = createSampleTransaction(1, 2, 3000, 1);

        pendingState.addPendingTransaction(tx1);
        pendingState.addPendingTransaction(tx2);

        Assert.assertEquals(10, pendingState.getOutdatedThreshold());

        List<Transaction> list = pendingState.getPendingTransactions();

        Assert.assertNotNull(list);
        Assert.assertFalse(list.isEmpty());
        Assert.assertEquals(2, list.size());

        Thread.sleep(3000);

        pendingState.removeObsoleteTransactions(1, 1, 1);

        list = pendingState.getPendingTransactions();

        Assert.assertNotNull(list);
        Assert.assertTrue(list.isEmpty());
    }

    @Test
    public void removeObsoleteWireTransactions() {
        PendingStateImpl pendingState = createSampleNewPendingStateWithAccounts(2, new BigInteger("1000000"));
        Transaction tx1 = createSampleTransaction(1, 2, 1000, 0);
        Transaction tx2 = createSampleTransaction(1, 2, 3000, 1);
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx1);
        txs.add(tx2);

        pendingState.addWireTransactions(txs);

        Assert.assertEquals(10, pendingState.getOutdatedThreshold());

        List<Transaction> list = pendingState.getWireTransactions();

        Assert.assertNotNull(list);
        Assert.assertFalse(list.isEmpty());
        Assert.assertEquals(2, list.size());

        pendingState.removeObsoleteTransactions(1, 1, 0);

        list = pendingState.getWireTransactions();

        Assert.assertNotNull(list);
        Assert.assertFalse(list.isEmpty());
        Assert.assertEquals(2, list.size());

        pendingState.removeObsoleteTransactions(20, pendingState.getOutdatedThreshold(), pendingState.getOutdatedTimeout());

        list = pendingState.getWireTransactions();

        Assert.assertNotNull(list);
        Assert.assertTrue(list.isEmpty());
    }

    @Test
    public void getAllPendingTransactions() {
        PendingStateImpl pendingState = createSampleNewPendingStateWithAccounts(2, new BigInteger("1000000"));
        Transaction tx1 = createSampleTransaction(1, 2, 1000, 0);
        Transaction tx2 = createSampleTransaction(1, 2, 3000, 1);

        pendingState.addPendingTransaction(tx1);
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx2);
        pendingState.addWireTransactions(txs);

        List<Transaction> alltxs = pendingState.getAllPendingTransactions();

        Assert.assertNotNull(alltxs);
        Assert.assertFalse(alltxs.isEmpty());
        Assert.assertEquals(2, alltxs.size());
        Assert.assertTrue(alltxs.contains(tx1));
        Assert.assertTrue(alltxs.contains(tx2));
    }

    @Test
    public void processBestBlockRemovesTransactionsInBlock() {
        PendingStateImpl pendingState = createSampleNewPendingStateWithAccounts(3, new BigInteger("1000000"));
        Transaction tx1 = createSampleTransaction(1, 2, 1000, 0);
        Transaction tx2 = createSampleTransaction(1, 2, 3000, 1);
        Transaction tx3 = createSampleTransaction(2, 3, 1000, 0);
        Transaction tx4 = createSampleTransaction(2, 3, 3000, 1);

        pendingState.addPendingTransaction(tx1);
        pendingState.addPendingTransaction(tx2);
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx3);
        txs.add(tx4);
        pendingState.addWireTransactions(txs);

        List<Transaction> btxs = new ArrayList<>();
        btxs.add(tx1);
        btxs.add(tx3);

        Block genesis = pendingState.getBlockChain().getBestBlock();
        Block block = new BlockBuilder().parent(genesis).transactions(btxs).build();

        pendingState.getBlockStore().saveBlock(genesis, BigInteger.ONE, true);
        pendingState.processBest(block);

        List<Transaction> alltxs = pendingState.getAllPendingTransactions();

        Assert.assertNotNull(alltxs);
        Assert.assertFalse(alltxs.isEmpty());
        Assert.assertEquals(2, alltxs.size());
        Assert.assertFalse(alltxs.contains(tx1));
        Assert.assertTrue(alltxs.contains(tx2));
        Assert.assertFalse(alltxs.contains(tx3));
        Assert.assertTrue(alltxs.contains(tx4));

        Assert.assertSame(block, pendingState.getBestBlock());
    }

    @Test
    public void retractBlockAddsTransactionsAsWired() {
        PendingStateImpl pendingState = createSampleNewPendingStateWithAccounts(3, new BigInteger("1000000"));
        Transaction tx1 = createSampleTransaction(1, 2, 1000, 0);
        Transaction tx2 = createSampleTransaction(1, 2, 3000, 1);
        Transaction tx3 = createSampleTransaction(2, 3, 1000, 0);
        Transaction tx4 = createSampleTransaction(2, 3, 3000, 1);

        pendingState.addPendingTransaction(tx1);
        pendingState.addPendingTransaction(tx2);

        List<Transaction> txs = new ArrayList<>();
        txs.add(tx3);
        txs.add(tx4);

        Block block = new BlockBuilder().parent(BlockGenerator.getInstance().getGenesisBlock()).transactions(txs).build();

        pendingState.retractBlock(block);

        List<Transaction> alltxs = pendingState.getAllPendingTransactions();

        Assert.assertNotNull(alltxs);
        Assert.assertFalse(alltxs.isEmpty());
        Assert.assertEquals(4, alltxs.size());
        Assert.assertTrue(alltxs.contains(tx1));
        Assert.assertTrue(alltxs.contains(tx2));
        Assert.assertTrue(alltxs.contains(tx3));
        Assert.assertTrue(alltxs.contains(tx4));

        List<Transaction> wtxs = pendingState.getWireTransactions();

        Assert.assertNotNull(wtxs);
        Assert.assertFalse(wtxs.isEmpty());
        Assert.assertEquals(2, wtxs.size());
        Assert.assertTrue(wtxs.contains(tx3));
        Assert.assertTrue(wtxs.contains(tx4));
    }

    @Test
    public void updatePendingState() {
        PendingStateImpl pendingState = createSampleNewPendingStateWithAccounts(2, new BigInteger("1000000"));
        Transaction tx1 = createSampleTransaction(1, 2, 1000, 0);
        Transaction tx2 = createSampleTransaction(1, 2, 3000, 1);
        Account receiver = createAccount(2);

        pendingState.addPendingTransaction(tx1);
        pendingState.addPendingTransaction(tx2);

        pendingState.updateState();

        Repository repository = pendingState.getRepository();
        Assert.assertEquals(BigInteger.valueOf(1004000), repository.getBalance(receiver.getAddress()));
    }

    @Test
    public void addTwiceAndGetPendingTransaction() {
        PendingStateImpl pendingState = createSampleNewPendingState();
        Transaction tx = createSampleTransaction();

        pendingState.addPendingTransaction(tx);
        pendingState.addPendingTransaction(tx);
        List<Transaction> transactions = pendingState.getPendingTransactions();

        Assert.assertNotNull(transactions);
        Assert.assertFalse(transactions.isEmpty());
        Assert.assertEquals(1, transactions.size());
        Assert.assertTrue(transactions.contains(tx));
    }

    @Test
    public void getEmptyWireTransactionList() {
        PendingStateImpl pendingState = createSampleNewPendingState();

        List<Transaction> transactions = pendingState.getWireTransactions();

        Assert.assertNotNull(transactions);
        Assert.assertTrue(transactions.isEmpty());
    }

    @Test
    public void addAndGetWireTransaction() {
        PendingStateImpl pendingState = createSampleNewPendingState();
        Transaction tx = createSampleTransaction();
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);

        pendingState.addWireTransactions(txs);
        List<Transaction> transactions = pendingState.getWireTransactions();

        Assert.assertNotNull(transactions);
        Assert.assertFalse(transactions.isEmpty());
        Assert.assertTrue(transactions.contains(tx));
    }

    @Test
    public void addTwiceAndGetWireTransaction() {
        PendingStateImpl pendingState = createSampleNewPendingState();
        Transaction tx = createSampleTransaction();

        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        txs.add(tx);

        pendingState.addWireTransactions(txs);

        List<Transaction> transactions = pendingState.getWireTransactions();

        Assert.assertNotNull(transactions);
        Assert.assertFalse(transactions.isEmpty());
        Assert.assertEquals(1, transactions.size());
        Assert.assertTrue(transactions.contains(tx));
    }

    @Test
    public void addWireTransactionThatAlreadyExistsAsPendingTransaction() {
        PendingStateImpl pendingState = createSampleNewPendingState();
        Transaction tx = createSampleTransaction();

        pendingState.addPendingTransaction(tx);
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        pendingState.addWireTransactions(txs);
        List<Transaction> transactions = pendingState.getWireTransactions();

        Assert.assertNotNull(transactions);
        Assert.assertTrue(transactions.isEmpty());
    }

    @Test
    public void executeContractWithFakeBlock() {
        PendingStateImpl pendingState = createSampleNewPendingStateWithAccounts(2, new BigInteger("1000000"));
        // "NUMBER PUSH1 0x00 SSTORE" compiled to bytecodes
        String code = "43600055";
        Transaction tx = createSampleTransactionWithData(1, 0, code);

        pendingState.addPendingTransaction(tx);

        Assert.assertNotNull(tx.getContractAddress().getBytes());
        // Stored value at 0 position should be 1, one more than the blockchain best block
        Assert.assertEquals(DataWord.ONE, pendingState.getRepository().getStorageValue(tx.getContractAddress(), DataWord.ZERO));
    }

    private static PendingStateImpl createSampleNewPendingState() {
        BlockChainImpl blockChain = createBlockchain();

        return new PendingStateImpl(ConfigHelper.CONFIG, blockChain, blockChain.getRepository(), blockChain.getBlockStore(), new ProgramInvokeFactoryImpl(), new BlockExecutorTest.SimpleEthereumListener(), 10, 100);
    }

    private static PendingStateImpl createSampleNewPendingStateWithAccounts(int naccounts, BigInteger balance) {
        BlockChainImpl blockChain = createBlockchain();

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

        PendingStateImpl pendingState = new PendingStateImpl(ConfigHelper.CONFIG, blockChain, blockChain.getRepository(), blockChain.getBlockStore(), new ProgramInvokeFactoryImpl(), new BlockExecutorTest.SimpleEthereumListener(), 10, 100);
        blockChain.setPendingState(pendingState);

        return pendingState;
    }

    private static Account createAccount(int naccount) {
        return new AccountBuilder().name("account" + naccount).build();
    }

    private static Transaction createSampleTransaction() {
        Account sender = new AccountBuilder().name("sender").build();
        Account receiver = new AccountBuilder().name("receiver").build();

        Transaction tx = new TransactionBuilder()
                .sender(sender)
                .receiver(receiver)
                .value(BigInteger.TEN)
                .build();

        return tx;
    }

    private static Transaction createSampleTransaction(int from, int to, long value, int nonce) {
        Account sender = createAccount(from);
        Account receiver = createAccount(to);

        Transaction tx = new TransactionBuilder()
                .sender(sender)
                .receiver(receiver)
                .nonce(nonce)
                .value(BigInteger.valueOf(value))
                .build();

        return tx;
    }

    private static Transaction createSampleTransactionWithData(int from, int nonce, String data) {
        Account sender = createAccount(from);

        Transaction tx = new TransactionBuilder()
                .sender(sender)
                .receiverAddress(new byte[0])
                .nonce(nonce)
                .data(data)
                .gasLimit(BigInteger.valueOf(1000000))
                .build();

        return tx;
    }

    private static BlockChainImpl createBlockchain() {
        BlockChainImpl blockChain = new BlockChainBuilder().build();
        Block genesis = BlockChainImplTest.getGenesisBlock(blockChain);
        blockChain.setStatus(genesis, genesis.getCumulativeDifficulty());
        return blockChain;
    }
}
