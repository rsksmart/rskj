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
import co.rsk.test.World;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * Created by ajlopez on 9/3/2017.
 */
public class TwoAsyncNodeUsingSyncProcessorTest {
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

    private static SimpleAsyncNode createNodeWithUncles(int size) {
        final World world = new World();
        final BlockStore store = new BlockStore();
        final Blockchain blockchain = world.getBlockChain();

        List<Block> blocks = BlockGenerator.getBlockChain(blockchain.getBestBlock(), size, 0, true);

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

    private static Block getGenesis() {
        final World world = new World();

        return world.getBlockChain().getBestBlock();
    }

    @Test
    public void buildBlockchainAndSynchronize() throws InterruptedException {
        SimpleAsyncNode node1 = createNode(100);
        SimpleAsyncNode node2 = createNode(0);

        node1.sendFullStatus(node2);
        node1.waitUntilNTasksWithTimeout(100);

        node1.joinWithTimeout();
        node2.joinWithTimeout();

        Assert.assertEquals(100, node1.getBestBlock().getNumber());
        Assert.assertEquals(100, node2.getBestBlock().getNumber());
        Assert.assertArrayEquals(node1.getBestBlock().getHash(), node2.getBestBlock().getHash());

        Assert.assertTrue(node1.getExpectedResponses().isEmpty());
        Assert.assertTrue(node2.getExpectedResponses().isEmpty());

        Assert.assertFalse(node1.getSyncProcessor().getPeerStatus(node2.getNodeID()).isSyncing());
        Assert.assertFalse(node2.getSyncProcessor().getPeerStatus(node1.getNodeID()).isSyncing());
    }

    @Test
    public void buildBlockchainAndSynchronize400Blocks() throws InterruptedException {
        SimpleAsyncNode node1 = createNode(400);
        SimpleAsyncNode node2 = createNode(0);

        node1.sendFullStatus(node2);
        node2.waitUntilNTasksWithTimeout(16);
        node2.waitUntilNTasksWithTimeout(400);

        node1.joinWithTimeout();
        node2.joinWithTimeout();

        Assert.assertEquals(400, node1.getBestBlock().getNumber());
        Assert.assertEquals(400, node2.getBestBlock().getNumber());
        Assert.assertArrayEquals(node1.getBestBlock().getHash(), node2.getBestBlock().getHash());

        Assert.assertTrue(node1.getExpectedResponses().isEmpty());
        Assert.assertTrue(node2.getExpectedResponses().isEmpty());

        Assert.assertFalse(node1.getSyncProcessor().getPeerStatus(node2.getNodeID()).isSyncing());
        Assert.assertFalse(node2.getSyncProcessor().getPeerStatus(node1.getNodeID()).isSyncing());
    }

    @Test
    public void buildBlockchainWithUnclesAndSynchronize() throws InterruptedException {
        SimpleAsyncNode node1 = createNodeWithUncles(10);
        SimpleAsyncNode node2 = createNode(0);

        node1.sendFullStatus(node2);
        node1.waitUntilNTasksWithTimeout(10);

        node2.sendFullStatus(node1);

        node1.joinWithTimeout();
        node2.joinWithTimeout();

        Assert.assertEquals(10, node1.getBestBlock().getNumber());
        Assert.assertEquals(10, node2.getBestBlock().getNumber());
        Assert.assertArrayEquals(node1.getBestBlock().getHash(), node2.getBestBlock().getHash());

        Assert.assertTrue(node1.getExpectedResponses().isEmpty());
        Assert.assertTrue(node2.getExpectedResponses().isEmpty());

        Assert.assertFalse(node1.getSyncProcessor().getPeerStatus(node2.getNodeID()).isSyncing());
        Assert.assertFalse(node2.getSyncProcessor().getPeerStatus(node1.getNodeID()).isSyncing());
    }

    @Test
    public void buildBlockchainPartialAndSynchronize() throws InterruptedException {
        SimpleAsyncNode node1 = createNode(0);
        SimpleAsyncNode node2 = createNode(0);

        List<Block> blocks = BlockGenerator.getBlockChain(getGenesis(), 10);

        for (Block block : blocks) {
            BlockMessage message = new BlockMessage(block);
            node1.sendMessage(null, message);
            node1.waitUntilNTasksWithTimeout(1);

            if (block.getNumber() <= 5) {
                node2.sendMessage(null, message);
                node2.waitUntilNTasksWithTimeout(1);
            }
        }

        Assert.assertEquals(10, node1.getBestBlock().getNumber());
        Assert.assertEquals(5, node2.getBestBlock().getNumber());

        node1.sendFullStatus(node2);
        node1.waitUntilNTasksWithTimeout(10);

        node2.sendFullStatus(node1);
        node2.waitUntilNTasksWithTimeout(10);

        node1.joinWithTimeout();
        node2.joinWithTimeout();

        Assert.assertEquals(10, node1.getBestBlock().getNumber());
        Assert.assertEquals(10, node2.getBestBlock().getNumber());
        Assert.assertArrayEquals(node1.getBestBlock().getHash(), node2.getBestBlock().getHash());

        Assert.assertTrue(node1.getExpectedResponses().isEmpty());
        Assert.assertTrue(node2.getExpectedResponses().isEmpty());

        Assert.assertFalse(node1.getSyncProcessor().getPeerStatus(node2.getNodeID()).isSyncing());
        Assert.assertFalse(node2.getSyncProcessor().getPeerStatus(node1.getNodeID()).isSyncing());
    }

    @Test
    public void sendNewBlock() throws InterruptedException {
        SimpleAsyncNode node1 = createNode(1);
        SimpleAsyncNode node2 = createNode(0);

        node2.sendMessage(node1, new NewBlockHashMessage(node1.getBestBlock().getHash()));

        node1.waitUntilNTasksWithTimeout(1);

        node1.joinWithTimeout();
        node2.joinWithTimeout();

        Assert.assertEquals(1, node1.getBestBlock().getNumber());
        Assert.assertEquals(1, node2.getBestBlock().getNumber());
        Assert.assertArrayEquals(node1.getBestBlock().getHash(), node2.getBestBlock().getHash());

        Assert.assertFalse(node1.getSyncProcessor().getPeerStatus(node2.getNodeID()).isSyncing());
        Assert.assertFalse(node2.getSyncProcessor().getPeerStatus(node1.getNodeID()).isSyncing());
    }
}
