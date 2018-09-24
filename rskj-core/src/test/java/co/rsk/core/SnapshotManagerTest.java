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

package co.rsk.core;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.core.bc.BlockChainStatus;
import co.rsk.mine.MinerServer;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import org.ethereum.core.*;
import org.ethereum.util.RskTestFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.List;

import static org.mockito.Mockito.mock;

/**
 * Created by ajlopez on 15/04/2017.
 */
public class SnapshotManagerTest {

    private BlockChainImpl blockchain;
    private Repository repository;
    private TransactionPool transactionPool;
    private SnapshotManager manager;

    @Before
    public void setUp() {
        RskTestFactory factory = new RskTestFactory();
        blockchain = factory.getBlockchain();
        repository = factory.getRepository();
        transactionPool = factory.getTransactionPool();
        // don't call start to avoid creating threads
        transactionPool.processBest(blockchain.getBestBlock());
        manager = new SnapshotManager(blockchain, transactionPool, mock(MinerServer.class));
    }

    @Test
    public void createWithNoSnapshot() {
        Assert.assertNotNull(manager.getSnapshots());
        Assert.assertTrue(manager.getSnapshots().isEmpty());
    }

    @Test
    public void takeSnapshotOnGenesis() {
        int result = manager.takeSnapshot();

        Assert.assertEquals(1, result);

        Assert.assertNotNull(manager.getSnapshots());
        Assert.assertFalse(manager.getSnapshots().isEmpty());
        Assert.assertEquals(1, manager.getSnapshots().size());
        Assert.assertEquals(0, manager.getSnapshots().get(0).longValue());
    }

    @Test
    public void takeSnapshotOnManyBlocks() {
        addBlocks(blockchain, 10);

        int result = manager.takeSnapshot();

        Assert.assertEquals(1, result);

        Assert.assertNotNull(manager.getSnapshots());
        Assert.assertFalse(manager.getSnapshots().isEmpty());
        Assert.assertEquals(1, manager.getSnapshots().size());
        Assert.assertEquals(10, manager.getSnapshots().get(0).longValue());
    }

    @Test
    public void takeTwoSnapshots() {
        addBlocks(blockchain, 10);

        int result1 = manager.takeSnapshot();

        Assert.assertEquals(1, result1);

        addBlocks(blockchain, 10);

        int result2 = manager.takeSnapshot();

        Assert.assertEquals(2, result2);

        Assert.assertNotNull(manager.getSnapshots());
        Assert.assertFalse(manager.getSnapshots().isEmpty());
        Assert.assertEquals(2, manager.getSnapshots().size());
        Assert.assertEquals(10, manager.getSnapshots().get(0).longValue());
        Assert.assertEquals(20, manager.getSnapshots().get(1).longValue());
    }

    @Test
    public void revertToNegativeSnapshot() {
        Assert.assertFalse(manager.revertToSnapshot(-1));
    }

    @Test
    public void revertToNonExistentSnapshot() {
        Assert.assertFalse(manager.revertToSnapshot(0));
        Assert.assertFalse(manager.revertToSnapshot(1));
        Assert.assertFalse(manager.revertToSnapshot(10));
    }

    @Test
    public void revertToSnapshot() {
        addBlocks(blockchain, 10);

        BlockChainStatus status = blockchain.getStatus();

        int snapshotId = manager.takeSnapshot();

        addBlocks(blockchain, 20);

        Assert.assertEquals(30, blockchain.getStatus().getBestBlockNumber());

        Assert.assertTrue(manager.revertToSnapshot(snapshotId));

        BlockChainStatus newStatus = blockchain.getStatus();

        Assert.assertEquals(status.getBestBlockNumber(), newStatus.getBestBlockNumber());
        Assert.assertEquals(status.getTotalDifficulty(), newStatus.getTotalDifficulty());
        Assert.assertEquals(status.getBestBlock().getHash(), newStatus.getBestBlock().getHash());

        for (int k = 11; k <= 30; k++)
            Assert.assertTrue(blockchain.getBlocksByNumber(k).isEmpty());
    }

    @Test
    public void revertToSnapshotClearingTransactionPool() {
        addBlocks(blockchain, 10);

        BlockChainStatus status = blockchain.getStatus();

        int snapshotId = manager.takeSnapshot();

        addBlocks(blockchain, 20);

        manager.takeSnapshot();

        Assert.assertEquals(2, manager.getSnapshots().size());

        Assert.assertNotNull(transactionPool);

        setUpSampleAccounts();
        repository.commit();
        transactionPool.addTransaction(createSampleTransaction());
        Assert.assertFalse(transactionPool.getPendingTransactions().isEmpty());
        Assert.assertFalse(transactionPool.getPendingTransactions().isEmpty());

        Assert.assertEquals(30, blockchain.getStatus().getBestBlockNumber());

        Assert.assertTrue(manager.revertToSnapshot(snapshotId));

        BlockChainStatus newStatus = blockchain.getStatus();

        Assert.assertEquals(status.getBestBlockNumber(), newStatus.getBestBlockNumber());
        Assert.assertEquals(status.getTotalDifficulty(), newStatus.getTotalDifficulty());
        Assert.assertEquals(status.getBestBlock().getHash(), newStatus.getBestBlock().getHash());

        Assert.assertTrue(transactionPool.getPendingTransactions().isEmpty());

        Assert.assertEquals(1, manager.getSnapshots().size());

        for (int k = 11; k <= 30; k++)
            Assert.assertTrue(blockchain.getBlocksByNumber(k).isEmpty());
    }

    @Test
    public void resetSnapshotClearingTransactionPool() {
        Block genesis = blockchain.getBestBlock();
        BlockDifficulty genesisDifficulty = blockchain.getStatus().getTotalDifficulty();

        addBlocks(blockchain, 10);

        BlockChainStatus status = blockchain.getStatus();

        Assert.assertEquals(10, status.getBestBlockNumber());

        setUpSampleAccounts();
        // Now the repository has uncommited changes. Before taking a snapshot,
        // it should commit them upstream.
        repository.commit();
        transactionPool.addTransaction(createSampleTransaction());
        Assert.assertFalse(transactionPool.getPendingTransactions().isEmpty());
        Assert.assertFalse(transactionPool.getPendingTransactions().isEmpty());

        manager.takeSnapshot();
        Assert.assertFalse(manager.getSnapshots().isEmpty());
        Assert.assertTrue(manager.resetSnapshots());
        Assert.assertTrue(manager.getSnapshots().isEmpty());

        Assert.assertTrue(manager.resetSnapshots());

        BlockChainStatus newStatus = blockchain.getStatus();

        Assert.assertEquals(0, newStatus.getBestBlockNumber());
        Assert.assertEquals(genesisDifficulty, newStatus.getTotalDifficulty());
        Assert.assertEquals(genesis.getHash(), newStatus.getBestBlock().getHash());

        Assert.assertTrue(transactionPool.getPendingTransactions().isEmpty());

        Assert.assertTrue(manager.getSnapshots().isEmpty());

        for (int k = 1; k <= 10; k++)
            Assert.assertTrue(blockchain.getBlocksByNumber(k).isEmpty());
    }

    private static void addBlocks(Blockchain blockchain, int size) {
        List<Block> blocks = new BlockGenerator().getBlockChain(blockchain.getBestBlock(), size);

        for (Block block : blocks)
            blockchain.tryToConnect(block);
    }

    private void setUpSampleAccounts() {
        Repository track = repository.startTracking();

        for (String name : new String[]{"sender", "receiver"}) {
            Account account = new AccountBuilder().name(name).build();
            track.createAccount(account.getAddress());
            track.addBalance(account.getAddress(), Coin.valueOf(5000000));
        }

        track.commit();
    }

    private static Transaction createSampleTransaction() {
        Account sender = new AccountBuilder().name("sender").build();
        Account receiver = new AccountBuilder().name("receiver").build();

        Transaction tx = new TransactionBuilder()
                .sender(sender)
                .receiver(receiver)
                .gasPrice(BigInteger.valueOf(200))
                .value(BigInteger.TEN)
                .build();

        return tx;
    }
}
