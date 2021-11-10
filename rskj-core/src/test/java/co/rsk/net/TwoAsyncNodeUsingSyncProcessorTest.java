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

package co.rsk.net;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.net.messages.BlockMessage;
import co.rsk.net.messages.NewBlockHashMessage;
import co.rsk.net.simples.SimpleAsyncNode;
import co.rsk.net.sync.SyncConfiguration;
import co.rsk.net.utils.SyncUtils;
import co.rsk.test.World;
import co.rsk.test.builders.BlockChainBuilder;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.db.BlockStore;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * Created by ajlopez on 9/3/2017.
 */
public class TwoAsyncNodeUsingSyncProcessorTest {

    private static Block getGenesis() {
        final World world = new World();

        return world.getBlockChain().getBestBlock();
    }

    @Test
    public void buildBlockchainAndSynchronize() {
        SimpleAsyncNode node1 = SimpleAsyncNode.createNodeWithWorldBlockChain(100, false, true);
        SimpleAsyncNode node2 = SimpleAsyncNode.createNodeWithWorldBlockChain(0, false, true);

        node1.sendFullStatusTo(node2);
        // sync setup
        node2.waitUntilNTasksWithTimeout(SyncUtils.syncSetupRequests(100, 0, SyncConfiguration.IMMEDIATE_FOR_TESTING));
        // get blocks
        node2.waitExactlyNTasksWithTimeout(100);

        node1.joinWithTimeout();
        node2.joinWithTimeout();

        Assert.assertEquals(100, node1.getBestBlock().getNumber());
        Assert.assertEquals(100, node2.getBestBlock().getNumber());
        Assert.assertEquals(node1.getBestBlock().getHash(), node2.getBestBlock().getHash());

        Assert.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node2.getSyncProcessor().getExpectedResponses().isEmpty());

        Assert.assertFalse(node1.getSyncProcessor().isSyncing());
        Assert.assertFalse(node2.getSyncProcessor().isSyncing());
    }

    @Test
    public void buildBlockchainAndSynchronize400Blocks() {
        SimpleAsyncNode node1 = SimpleAsyncNode.createNodeWithWorldBlockChain(400, false, true);
        SimpleAsyncNode node2 = SimpleAsyncNode.createNodeWithWorldBlockChain(0, false, true);

        node1.sendFullStatusTo(node2);
        // sync setup
        node2.waitUntilNTasksWithTimeout(SyncUtils.syncSetupRequests(400, 0, SyncConfiguration.IMMEDIATE_FOR_TESTING));
        // get blocks
        node2.waitExactlyNTasksWithTimeout(400);

        node1.joinWithTimeout();
        node2.joinWithTimeout();

        Assert.assertEquals(400, node1.getBestBlock().getNumber());
        Assert.assertEquals(400, node2.getBestBlock().getNumber());
        Assert.assertEquals(node1.getBestBlock().getHash(), node2.getBestBlock().getHash());

        Assert.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node2.getSyncProcessor().getExpectedResponses().isEmpty());

        Assert.assertFalse(node1.getSyncProcessor().isSyncing());
        Assert.assertFalse(node2.getSyncProcessor().isSyncing());
    }

    @Test
    public void buildBlockchainWithUnclesAndSynchronize() {
        SimpleAsyncNode node1 = SimpleAsyncNode.createNodeWithWorldBlockChain(10, true, true);
        SimpleAsyncNode node2 = SimpleAsyncNode.createNodeWithWorldBlockChain(0, false, true);

        node1.sendFullStatusTo(node2);
        // find connection point
        node2.waitUntilNTasksWithTimeout(SyncUtils.syncSetupRequests(10, 0, SyncConfiguration.IMMEDIATE_FOR_TESTING));
        // get blocks
        node2.waitExactlyNTasksWithTimeout(10);

        node2.sendFullStatusTo(node1);

        node1.joinWithTimeout();
        node2.joinWithTimeout();

        Assert.assertEquals(10, node1.getBestBlock().getNumber());
        Assert.assertEquals(10, node2.getBestBlock().getNumber());
        Assert.assertEquals(node1.getBestBlock().getHash(), node2.getBestBlock().getHash());

        Assert.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node2.getSyncProcessor().getExpectedResponses().isEmpty());

        Assert.assertFalse(node1.getSyncProcessor().isSyncing());
        Assert.assertFalse(node2.getSyncProcessor().isSyncing());
    }

    @Test
    public void buildBlockchainPartialAndSynchronize() {
        SimpleAsyncNode node1 = SimpleAsyncNode.createNodeWithWorldBlockChain(0, false, true);
        SimpleAsyncNode node2 = SimpleAsyncNode.createNodeWithWorldBlockChain(0, false, true);

        List<Block> blocks = new BlockGenerator().getBlockChain(getGenesis(), 10, 0, false, true, null);

        for (Block block : blocks) {
            BlockMessage message = new BlockMessage(block);
            node1.receiveMessageFrom(null, message);
            node1.waitExactlyNTasksWithTimeout(1);

            if (block.getNumber() <= 5) {
                node2.receiveMessageFrom(null, message);
                node2.waitExactlyNTasksWithTimeout(1);
            }
        }

        Assert.assertEquals(10, node1.getBestBlock().getNumber());
        Assert.assertEquals(5, node2.getBestBlock().getNumber());

        node1.sendFullStatusTo(node2);
        // find connection point
        node2.waitUntilNTasksWithTimeout(SyncUtils.syncSetupRequests(10, 5, SyncConfiguration.IMMEDIATE_FOR_TESTING));
        // get blocks
        node2.waitExactlyNTasksWithTimeout(5);
        // drain node 1 for next test
        node1.clearQueue();

        node2.sendFullStatusTo(node1);
        // receive status, do nothing
        node1.waitExactlyNTasksWithTimeout(1);

        node1.joinWithTimeout();
        node2.joinWithTimeout();

        Assert.assertEquals(10, node1.getBestBlock().getNumber());
        Assert.assertEquals(10, node2.getBestBlock().getNumber());
        Assert.assertEquals(node1.getBestBlock().getHash(), node2.getBestBlock().getHash());

        Assert.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node2.getSyncProcessor().getExpectedResponses().isEmpty());

        Assert.assertFalse(node1.getSyncProcessor().isSyncing());
        Assert.assertFalse(node2.getSyncProcessor().isSyncing());
    }

    @Test
    public void sendNewBlock() throws InterruptedException {
        SimpleAsyncNode node1 = SimpleAsyncNode.createNodeWithWorldBlockChain(1, false, true);
        SimpleAsyncNode node2 = SimpleAsyncNode.createNodeWithWorldBlockChain(0, false, true);

        Assert.assertFalse(node2.getSyncProcessor().isSyncing());

        node2.receiveMessageFrom(node1, new NewBlockHashMessage(node1.getBestBlock().getHash().getBytes()));

        // process new block hash
        node2.waitUntilNTasksWithTimeout(1);
        // process block response
        node2.waitExactlyNTasksWithTimeout(1);

        node1.joinWithTimeout();
        node2.joinWithTimeout();

        Assert.assertEquals(1, node1.getBestBlock().getNumber());
        Assert.assertEquals(1, node2.getBestBlock().getNumber());
        Assert.assertEquals(node1.getBestBlock().getHash(), node2.getBestBlock().getHash());

        Assert.assertFalse(node1.getSyncProcessor().isSyncing());
        Assert.assertFalse(node2.getSyncProcessor().isSyncing());
    }

    @Test
    public void stopSyncingAfter5SkeletonChunks() {
        int longSyncLimit = SyncConfiguration.IMMEDIATE_FOR_TESTING.getLongSyncLimit();
        int fiveChunksSize = 960;
        int b1Size = 30;
        int b2Size = fiveChunksSize + longSyncLimit;

        BlockChainBuilder builder = new BlockChainBuilder();
        Blockchain b1 = builder.ofSize(b1Size, false);
        Blockchain b2 = BlockChainBuilder.copyAndExtend(b1, b2Size - b1Size, false);

        BlockStore blockStore = spy(builder.getBlockStore());
        doReturn(1L).when(blockStore).getMinNumber();

        SimpleAsyncNode node1 = SimpleAsyncNode.createNode(b1,
                SyncConfiguration.IMMEDIATE_FOR_TESTING,
                blockStore);

        SimpleAsyncNode node2 = SimpleAsyncNode.createNode(b2, SyncConfiguration.IMMEDIATE_FOR_TESTING);

        Assert.assertEquals(b1Size, node1.getBestBlock().getNumber());
        Assert.assertEquals(b2Size, node2.getBestBlock().getNumber());

        node2.sendFullStatusTo(node1);
        // sync setup
        node1.waitUntilNTasksWithTimeout(SyncUtils.syncSetupRequests(b2Size, b1Size, SyncConfiguration.IMMEDIATE_FOR_TESTING));
        // request bodies
        node1.waitExactlyNTasksWithTimeout(b2Size - b1Size - longSyncLimit + 1);

        Assert.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node2.getSyncProcessor().getExpectedResponses().isEmpty());

        Assert.assertEquals(fiveChunksSize, node1.getBestBlock().getNumber());
        Assert.assertEquals(b2Size, node2.getBestBlock().getNumber());

        node1.joinWithTimeout();
        node2.joinWithTimeout();

        Assert.assertFalse(node1.getSyncProcessor().isSyncing());
        Assert.assertFalse(node2.getSyncProcessor().isSyncing());
    }

    @Test
    public void syncInMultipleStepsWithLongBlockchain() {
        BlockChainBuilder builder = new BlockChainBuilder();
        Blockchain b1 = builder.ofSize(300, false);
        Blockchain b2 = BlockChainBuilder.copyAndExtend(b1, 4000, false);

        SimpleAsyncNode node1 = SimpleAsyncNode.createNode(b1, SyncConfiguration.IMMEDIATE_FOR_TESTING, builder.getBlockStore());
        SimpleAsyncNode node2 = SimpleAsyncNode.createNode(b2, SyncConfiguration.IMMEDIATE_FOR_TESTING);

        Assert.assertEquals(300, node1.getBestBlock().getNumber());
        Assert.assertEquals(4300, node2.getBestBlock().getNumber());

        for (int i = 0; i < 5; i++) {
            int skippedChunks = 300 / 192;
            int expectedBestBlockNumber = Math.min(4300, 192 * skippedChunks + 192 * 5 * (i + 1));
            long currentBestBlock = node1.getBestBlock().getNumber();
            // at the beginning and the end we might have different number of blocks to download
            int blocksToDownload = Math.toIntExact(expectedBestBlockNumber - currentBestBlock);

            node2.sendFullStatusTo(node1);
            node1.waitUntilNTasksWithTimeout(SyncUtils.syncSetupRequests(4300, currentBestBlock, SyncConfiguration.IMMEDIATE_FOR_TESTING));

            // request bodies
            node1.waitExactlyNTasksWithTimeout(blocksToDownload);

            Assert.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
            Assert.assertTrue(node2.getSyncProcessor().getExpectedResponses().isEmpty());

            Assert.assertEquals(expectedBestBlockNumber, node1.getBestBlock().getNumber());
            Assert.assertEquals(4300, node2.getBestBlock().getNumber());

            // this prevents node2's queue to get full
            node2.clearQueue();
        }

        node1.joinWithTimeout();
        node2.joinWithTimeout();

        Assert.assertFalse(node1.getSyncProcessor().isSyncing());
        Assert.assertFalse(node2.getSyncProcessor().isSyncing());
    }

}
