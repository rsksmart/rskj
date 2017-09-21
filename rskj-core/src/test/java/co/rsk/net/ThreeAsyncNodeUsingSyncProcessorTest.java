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

import co.rsk.net.simples.SimpleAsyncNode;
import co.rsk.test.builders.BlockChainBuilder;
import org.ethereum.core.Blockchain;
import org.junit.Assert;
import org.junit.Test;

public class ThreeAsyncNodeUsingSyncProcessorTest {

    @Test
    public void synchronizeNewNodesInAChain() throws InterruptedException {
        SimpleAsyncNode node1 = SimpleAsyncNode.createNodeWithBlockChainBuilder(100);
        SimpleAsyncNode node2 = SimpleAsyncNode.createNodeWithBlockChainBuilder(0);
        SimpleAsyncNode node3 = SimpleAsyncNode.createNodeWithBlockChainBuilder(0);

        Assert.assertEquals(100, node1.getBestBlock().getNumber());
        Assert.assertEquals(0, node2.getBestBlock().getNumber());
        Assert.assertEquals(0, node3.getBestBlock().getNumber());

        node1.sendFullStatusTo(node2);
        // sync setup
        node2.waitUntilNTasksWithTimeout(12);
        // synchronize 100 new blocks from node 1
        node2.waitExactlyNTasksWithTimeout(100);

        Assert.assertTrue(node1.getExpectedResponses().isEmpty());
        Assert.assertTrue(node2.getExpectedResponses().isEmpty());

        Assert.assertEquals(100, node1.getBestBlock().getNumber());
        Assert.assertEquals(100, node2.getBestBlock().getNumber());
        Assert.assertEquals(0, node3.getBestBlock().getNumber());

        node2.sendFullStatusTo(node3);
        // sync setup
        node3.waitUntilNTasksWithTimeout(12);
        // synchronize 100 new blocks from node 2
        node3.waitExactlyNTasksWithTimeout(100);

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

        Assert.assertFalse(node1.getSyncProcessor().peerIsSyncing(node2.getNodeID()));
        Assert.assertFalse(node1.getSyncProcessor().peerIsSyncing(node3.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().peerIsSyncing(node1.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().peerIsSyncing(node3.getNodeID()));
        Assert.assertFalse(node3.getSyncProcessor().peerIsSyncing(node1.getNodeID()));
        Assert.assertFalse(node3.getSyncProcessor().peerIsSyncing(node2.getNodeID()));
    }

    @Test
    public void synchronizeNewNodeWithBestChain() throws InterruptedException {
        SimpleAsyncNode node1 = SimpleAsyncNode.createNodeWithBlockChainBuilder(30);
        SimpleAsyncNode node2 = SimpleAsyncNode.createNodeWithBlockChainBuilder(50);
        SimpleAsyncNode node3 = SimpleAsyncNode.createNodeWithBlockChainBuilder(0);

        Assert.assertEquals(30, node1.getBestBlock().getNumber());
        Assert.assertEquals(50, node2.getBestBlock().getNumber());
        Assert.assertEquals(0, node3.getBestBlock().getNumber());

        node1.sendFullStatusTo(node3);
        // sync setup
        node3.waitUntilNTasksWithTimeout(11);
        // synchronize 30 new blocks from node 1
        node3.waitExactlyNTasksWithTimeout(30);

        Assert.assertTrue(node1.getExpectedResponses().isEmpty());
        Assert.assertTrue(node3.getExpectedResponses().isEmpty());

        Assert.assertEquals(30, node1.getBestBlock().getNumber());
        Assert.assertEquals(50, node2.getBestBlock().getNumber());
        Assert.assertEquals(30, node3.getBestBlock().getNumber());
        Assert.assertArrayEquals(node1.getBestBlock().getHash(), node3.getBestBlock().getHash());

        node2.sendFullStatusTo(node3);
        // sync setup
        node3.waitUntilNTasksWithTimeout(11);
        // synchronize 50 new blocks from node 2
        node3.waitExactlyNTasksWithTimeout(50);

        Assert.assertEquals(30, node1.getBestBlock().getNumber());
        Assert.assertEquals(50, node2.getBestBlock().getNumber());
        Assert.assertEquals(50, node3.getBestBlock().getNumber());

        Assert.assertArrayEquals(node2.getBestBlock().getHash(), node3.getBestBlock().getHash());

        Assert.assertTrue(node1.getExpectedResponses().isEmpty());
        Assert.assertTrue(node2.getExpectedResponses().isEmpty());
        Assert.assertTrue(node3.getExpectedResponses().isEmpty());

        node1.joinWithTimeout();
        node2.joinWithTimeout();
        node3.joinWithTimeout();

        Assert.assertFalse(node1.getSyncProcessor().peerIsSyncing(node2.getNodeID()));
        Assert.assertFalse(node1.getSyncProcessor().peerIsSyncing(node3.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().peerIsSyncing(node1.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().peerIsSyncing(node3.getNodeID()));
        Assert.assertFalse(node3.getSyncProcessor().peerIsSyncing(node1.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().peerIsSyncing(node2.getNodeID()));
    }

    @Test
    public void synchronizeNewNodeWithTwoPeers() throws InterruptedException {
        Blockchain b1 = BlockChainBuilder.ofSize(30, true);
        Blockchain b2 = BlockChainBuilder.copyAndExtend(b1, 43, true);

        SimpleAsyncNode node1 = SimpleAsyncNode.createNode(b1);
        SimpleAsyncNode node2 = SimpleAsyncNode.createNode(b2);
        SimpleAsyncNode node3 = SimpleAsyncNode.createNodeWithBlockChainBuilder(0);

        Assert.assertEquals(30, node1.getBestBlock().getNumber());
        Assert.assertEquals(73, node2.getBestBlock().getNumber());
        Assert.assertEquals(0, node3.getBestBlock().getNumber());

        node1.sendFullStatusTo(node3);
        // sync setup
        node3.waitUntilNTasksWithTimeout(11);
        // synchronize 30 new blocks from node 1
        node3.waitExactlyNTasksWithTimeout(30);

        Assert.assertTrue(node1.getExpectedResponses().isEmpty());
        Assert.assertTrue(node3.getExpectedResponses().isEmpty());

        Assert.assertEquals(30, node1.getBestBlock().getNumber());
        Assert.assertEquals(73, node2.getBestBlock().getNumber());
        Assert.assertEquals(30, node3.getBestBlock().getNumber());
        Assert.assertArrayEquals(node1.getBestBlock().getHash(), node3.getBestBlock().getHash());

        node2.sendFullStatusTo(node3);
        // find connection point
        node3.waitUntilNTasksWithTimeout(11);
        // synchronize 43 new blocks from node 2
        node3.waitExactlyNTasksWithTimeout(43);

        Assert.assertArrayEquals(node2.getBestBlock().getHash(), node3.getBestBlock().getHash());

        Assert.assertEquals(30, node1.getBestBlock().getNumber());
        Assert.assertEquals(73, node2.getBestBlock().getNumber());
        Assert.assertEquals(73, node3.getBestBlock().getNumber());

        Assert.assertTrue(node1.getExpectedResponses().isEmpty());
        Assert.assertTrue(node2.getExpectedResponses().isEmpty());
        Assert.assertTrue(node3.getExpectedResponses().isEmpty());

        node1.joinWithTimeout();
        node2.joinWithTimeout();
        node3.joinWithTimeout();

        Assert.assertFalse(node1.getSyncProcessor().peerIsSyncing(node2.getNodeID()));
        Assert.assertFalse(node1.getSyncProcessor().peerIsSyncing(node3.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().peerIsSyncing(node1.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().peerIsSyncing(node3.getNodeID()));
        Assert.assertFalse(node3.getSyncProcessor().peerIsSyncing(node1.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().peerIsSyncing(node2.getNodeID()));
    }

    @Test
    public void dontSynchronizeNodeWithShorterChain() throws InterruptedException {
        SimpleAsyncNode node1 = SimpleAsyncNode.createNodeWithBlockChainBuilder(50);
        SimpleAsyncNode node2 = SimpleAsyncNode.createNodeWithBlockChainBuilder(30);
        SimpleAsyncNode node3 = SimpleAsyncNode.createNodeWithBlockChainBuilder(0);

        Assert.assertEquals(50, node1.getBestBlock().getNumber());
        Assert.assertEquals(30, node2.getBestBlock().getNumber());
        Assert.assertEquals(0, node3.getBestBlock().getNumber());

        node1.sendFullStatusTo(node3);
        // sync setup
        node3.waitUntilNTasksWithTimeout(11);
        // synchronize 50 new blocks from node 1
        node3.waitExactlyNTasksWithTimeout(50);

        Assert.assertTrue(node1.getExpectedResponses().isEmpty());
        Assert.assertTrue(node3.getExpectedResponses().isEmpty());

        Assert.assertEquals(50, node1.getBestBlock().getNumber());
        Assert.assertEquals(30, node2.getBestBlock().getNumber());
        Assert.assertEquals(50, node3.getBestBlock().getNumber());
        Assert.assertArrayEquals(node1.getBestBlock().getHash(), node3.getBestBlock().getHash());

        node2.sendFullStatusTo(node3);
        // receive status, do nothing
        node3.waitExactlyNTasksWithTimeout(1);

        Assert.assertEquals(50, node1.getBestBlock().getNumber());
        Assert.assertEquals(30, node2.getBestBlock().getNumber());
        Assert.assertEquals(50, node3.getBestBlock().getNumber());

        Assert.assertTrue(node1.getExpectedResponses().isEmpty());
        Assert.assertTrue(node2.getExpectedResponses().isEmpty());
        Assert.assertTrue(node3.getExpectedResponses().isEmpty());

        node1.joinWithTimeout();
        node2.joinWithTimeout();
        node3.joinWithTimeout();

        Assert.assertFalse(node1.getSyncProcessor().peerIsSyncing(node2.getNodeID()));
        Assert.assertFalse(node1.getSyncProcessor().peerIsSyncing(node3.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().peerIsSyncing(node1.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().peerIsSyncing(node3.getNodeID()));
        Assert.assertFalse(node3.getSyncProcessor().peerIsSyncing(node1.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().peerIsSyncing(node2.getNodeID()));
    }
}
