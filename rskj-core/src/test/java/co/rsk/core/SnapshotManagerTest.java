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

import co.rsk.core.bc.BlockChainStatus;
import co.rsk.mine.MinerClient;
import co.rsk.mine.MinerServer;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import org.ethereum.core.*;
import org.ethereum.db.BlockStore;
import org.ethereum.util.RskTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.mockito.Mockito.mock;

/**
 * Created by ajlopez on 15/04/2017.
 */
class SnapshotManagerTest {

    private RskTestContext testContext;
    private Blockchain blockchain;
    private TransactionPool transactionPool;
    private SnapshotManager manager;
    private MinerServer minerServer;
    private MinerClient minerClient;

    @BeforeEach
    void setUp() {
        testContext = new RskTestContext(new String[]{"--regtest"});
        blockchain = testContext.getBlockchain();
        minerServer = testContext.getMinerServer();
        minerClient = testContext.getMinerClient();
        transactionPool = testContext.getTransactionPool();
        BlockStore blockStore = testContext.getBlockStore();
        // don't call start to avoid creating threads
        transactionPool.processBest(blockchain.getBestBlock());
        manager = new SnapshotManager(blockchain, blockStore, transactionPool, mock(MinerServer.class));
    }

    @AfterEach
    void tearDown() {
        testContext.close();
    }

    @Test
    void createWithNoSnapshot() {
        Assertions.assertNotNull(manager.getSnapshots());
        Assertions.assertTrue(manager.getSnapshots().isEmpty());
    }

    @Test
    void takeSnapshotOnGenesis() {
        int result = manager.takeSnapshot();

        Assertions.assertEquals(1, result);

        Assertions.assertNotNull(manager.getSnapshots());
        Assertions.assertFalse(manager.getSnapshots().isEmpty());
        Assertions.assertEquals(1, manager.getSnapshots().size());
        Assertions.assertEquals(0, manager.getSnapshots().get(0).longValue());
    }

    @Test
    void takeSnapshotOnManyBlocks() {
        addBlocks(10);

        int result = manager.takeSnapshot();

        Assertions.assertEquals(1, result);

        Assertions.assertNotNull(manager.getSnapshots());
        Assertions.assertFalse(manager.getSnapshots().isEmpty());
        Assertions.assertEquals(1, manager.getSnapshots().size());
        Assertions.assertEquals(10, manager.getSnapshots().get(0).longValue());
    }

    @Test
    void takeTwoSnapshots() {
        addBlocks(10);

        int result1 = manager.takeSnapshot();

        Assertions.assertEquals(1, result1);

        addBlocks(10);

        int result2 = manager.takeSnapshot();

        Assertions.assertEquals(2, result2);

        Assertions.assertNotNull(manager.getSnapshots());
        Assertions.assertFalse(manager.getSnapshots().isEmpty());
        Assertions.assertEquals(2, manager.getSnapshots().size());
        Assertions.assertEquals(10, manager.getSnapshots().get(0).longValue());
        Assertions.assertEquals(20, manager.getSnapshots().get(1).longValue());
    }

    @Test
    void revertToNegativeSnapshot() {
        Assertions.assertFalse(manager.revertToSnapshot(-1));
    }

    @Test
    void revertToNonExistentSnapshot() {
        Assertions.assertFalse(manager.revertToSnapshot(0));
        Assertions.assertFalse(manager.revertToSnapshot(1));
        Assertions.assertFalse(manager.revertToSnapshot(10));
    }

    @Test
    void revertToSnapshot() {
        addBlocks(10);

        BlockChainStatus status = blockchain.getStatus();

        int snapshotId = manager.takeSnapshot();

        addBlocks(20);

        Assertions.assertEquals(30, blockchain.getStatus().getBestBlockNumber());

        Assertions.assertTrue(manager.revertToSnapshot(snapshotId));

        BlockChainStatus newStatus = blockchain.getStatus();

        Assertions.assertEquals(status.getBestBlockNumber(), newStatus.getBestBlockNumber());
        Assertions.assertEquals(status.getTotalDifficulty(), newStatus.getTotalDifficulty());
        Assertions.assertEquals(status.getBestBlock().getHash(), newStatus.getBestBlock().getHash());

        for (int k = 11; k <= 30; k++)
            Assertions.assertTrue(blockchain.getBlocksByNumber(k).isEmpty());
    }

    @Test
    void revertToSnapshotClearingTransactionPool() {
        addBlocks(10);

        BlockChainStatus status = blockchain.getStatus();

        int snapshotId = manager.takeSnapshot();

        addBlocks(20);

        manager.takeSnapshot();

        Assertions.assertEquals(2, manager.getSnapshots().size());

        Assertions.assertNotNull(transactionPool);

        transactionPool.addTransaction(createSampleTransaction());
        Assertions.assertFalse(transactionPool.getPendingTransactions().isEmpty());
        Assertions.assertFalse(transactionPool.getPendingTransactions().isEmpty());

        Assertions.assertEquals(30, blockchain.getStatus().getBestBlockNumber());

        Assertions.assertTrue(manager.revertToSnapshot(snapshotId));

        BlockChainStatus newStatus = blockchain.getStatus();

        Assertions.assertEquals(status.getBestBlockNumber(), newStatus.getBestBlockNumber());
        Assertions.assertEquals(status.getTotalDifficulty(), newStatus.getTotalDifficulty());
        Assertions.assertEquals(status.getBestBlock().getHash(), newStatus.getBestBlock().getHash());

        Assertions.assertTrue(transactionPool.getPendingTransactions().isEmpty());

        Assertions.assertEquals(1, manager.getSnapshots().size());

        for (int k = 11; k <= 30; k++)
            Assertions.assertTrue(blockchain.getBlocksByNumber(k).isEmpty());
    }

    @Test
    void resetSnapshotClearingTransactionPool() {
        Block genesis = blockchain.getBestBlock();
        BlockDifficulty genesisDifficulty = blockchain.getStatus().getTotalDifficulty();

        addBlocks(10);

        BlockChainStatus status = blockchain.getStatus();

        Assertions.assertEquals(10, status.getBestBlockNumber());

        transactionPool.addTransaction(createSampleTransaction());
        Assertions.assertFalse(transactionPool.getPendingTransactions().isEmpty());
        Assertions.assertFalse(transactionPool.getPendingTransactions().isEmpty());

        manager.takeSnapshot();
        Assertions.assertFalse(manager.getSnapshots().isEmpty());
        Assertions.assertTrue(manager.resetSnapshots());
        Assertions.assertTrue(manager.getSnapshots().isEmpty());

        Assertions.assertTrue(manager.resetSnapshots());

        BlockChainStatus newStatus = blockchain.getStatus();

        Assertions.assertEquals(0, newStatus.getBestBlockNumber());
        Assertions.assertEquals(genesisDifficulty, newStatus.getTotalDifficulty());
        Assertions.assertEquals(genesis.getHash(), newStatus.getBestBlock().getHash());

        Assertions.assertTrue(transactionPool.getPendingTransactions().isEmpty());

        Assertions.assertTrue(manager.getSnapshots().isEmpty());

        for (int k = 1; k <= 10; k++)
            Assertions.assertTrue(blockchain.getBlocksByNumber(k).isEmpty());
    }

    private void addBlocks(int size) {
        for (int i = 0; i < size; i++) {
            minerServer.buildBlockToMine(blockchain.getBestBlock(), false);
            Assertions.assertTrue(minerClient.mineBlock());
        }
    }

    private Transaction createSampleTransaction() {
        Account sender = new AccountBuilder().name("cow").build();
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
