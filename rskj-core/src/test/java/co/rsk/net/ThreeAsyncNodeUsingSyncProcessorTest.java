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
import co.rsk.net.simples.SimpleAsyncNode;
import co.rsk.test.World;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class ThreeAsyncNodeUsingSyncProcessorTest {
    private static SimpleAsyncNode createNode(int size) {
        final World world = new World();
        final BlockStore store = new BlockStore();
        final Blockchain blockchain = world.getBlockChain();

        List<Block> blocks = BlockGenerator.getBlockChain(blockchain.getBestBlock(), size);

        for (Block b: blocks)
            blockchain.tryToConnect(b);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);
        SyncProcessor syncProcessor = new SyncProcessor(blockchain, blockSyncService);
        NodeMessageHandler handler = new NodeMessageHandler(processor, syncProcessor, null, null, null).disablePoWValidation();

        handler.disablePoWValidation();

        return new SimpleAsyncNode(handler, syncProcessor);
    }

    @Test
    public void synchronizeNewNodesInAChain() throws InterruptedException {
        SimpleAsyncNode node1 = createNode(100);
        SimpleAsyncNode node2 = createNode(0);
        SimpleAsyncNode node3 = createNode(0);

        Assert.assertEquals(100, node1.getBestBlock().getNumber());
        Assert.assertEquals(0, node2.getBestBlock().getNumber());
        Assert.assertEquals(0, node3.getBestBlock().getNumber());

        node1.sendFullStatus(node2);
        // sync setup
        node2.waitUntilNTasksWithTimeout(12);
        // synchronize 100 new blocks from node 1
        node2.waitUntilNTasksWithTimeout(100);

        Assert.assertTrue(node1.getExpectedResponses().isEmpty());
        Assert.assertTrue(node2.getExpectedResponses().isEmpty());

        Assert.assertEquals(100, node1.getBestBlock().getNumber());
        Assert.assertEquals(100, node2.getBestBlock().getNumber());
        Assert.assertEquals(0, node3.getBestBlock().getNumber());

        node2.sendFullStatus(node3);
        // sync setup
        node3.waitUntilNTasksWithTimeout(12);
        // synchronize 100 new blocks from node 2
        node3.waitUntilNTasksWithTimeout(100);

        Assert.assertEquals(100, node1.getBestBlock().getNumber());
        Assert.assertEquals(100, node2.getBestBlock().getNumber());
        Assert.assertEquals(100, node3.getBestBlock().getNumber());

        Assert.assertTrue(node1.getExpectedResponses().isEmpty());
        Assert.assertTrue(node2.getExpectedResponses().isEmpty());
        Assert.assertTrue(node3.getExpectedResponses().isEmpty());

        Assert.assertArrayEquals(node1.getBestBlock().getHash(), node2.getBestBlock().getHash());
        Assert.assertArrayEquals(node2.getBestBlock().getHash(), node3.getBestBlock().getHash());

        node1.joinWithTimeout();
        node2.joinWithTimeout();
        node3.joinWithTimeout();
    }

    @Test
    public void synchronizeNewNodeWithBestChain() throws InterruptedException {
        SimpleAsyncNode node1 = createNode(30);
        SimpleAsyncNode node2 = createNode(50);
        SimpleAsyncNode node3 = createNode(0);

        Assert.assertEquals(30, node1.getBestBlock().getNumber());
        Assert.assertEquals(50, node2.getBestBlock().getNumber());
        Assert.assertEquals(0, node3.getBestBlock().getNumber());

        node1.sendFullStatus(node3);
        // sync setup
        node3.waitUntilNTasksWithTimeout(11);
        // synchronize 30 new blocks from node 1
        node3.waitUntilNTasksWithTimeout(30);

        Assert.assertTrue(node1.getExpectedResponses().isEmpty());
        Assert.assertTrue(node3.getExpectedResponses().isEmpty());

        Assert.assertEquals(30, node1.getBestBlock().getNumber());
        Assert.assertEquals(50, node2.getBestBlock().getNumber());
        Assert.assertEquals(30, node3.getBestBlock().getNumber());
        Assert.assertArrayEquals(node1.getBestBlock().getHash(), node3.getBestBlock().getHash());

        node2.sendFullStatus(node3);
        // sync setup
        node3.waitUntilNTasksWithTimeout(3);
        // synchronize 50 new blocks from node 2
        node3.waitUntilNTasksWithTimeout(50);

        Assert.assertArrayEquals(node2.getBestBlock().getHash(), node3.getBestBlock().getHash());

        Assert.assertEquals(30, node1.getBestBlock().getNumber());
        Assert.assertEquals(50, node2.getBestBlock().getNumber());
        Assert.assertEquals(50, node3.getBestBlock().getNumber());

        Assert.assertTrue(node1.getExpectedResponses().isEmpty());
        Assert.assertTrue(node2.getExpectedResponses().isEmpty());
        Assert.assertTrue(node3.getExpectedResponses().isEmpty());

        node1.joinWithTimeout();
        node2.joinWithTimeout();
        node3.joinWithTimeout();
    }
}
