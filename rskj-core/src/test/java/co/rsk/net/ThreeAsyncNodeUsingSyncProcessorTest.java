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

import co.rsk.net.messages.BodyResponseMessage;
import co.rsk.net.messages.NewBlockHashMessage;
import co.rsk.net.simples.SimpleAsyncNode;
import co.rsk.net.sync.SyncConfiguration;
import co.rsk.net.utils.SyncUtils;
import co.rsk.test.builders.BlockChainBuilder;
import org.ethereum.core.Blockchain;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Random;

public class ThreeAsyncNodeUsingSyncProcessorTest {

    @Test
    public void synchronizeNewNodesInAChain() throws InterruptedException {
        SimpleAsyncNode node1 = SimpleAsyncNode.createNodeWithWorldBlockChain(100,false, false);
        SimpleAsyncNode node2 = SimpleAsyncNode.createNodeWithWorldBlockChain(0,false, false);
        SimpleAsyncNode node3 = SimpleAsyncNode.createNodeWithWorldBlockChain(0,false, false);

        Assert.assertEquals(100, node1.getBestBlock().getNumber());
        Assert.assertEquals(0, node2.getBestBlock().getNumber());
        Assert.assertEquals(0, node3.getBestBlock().getNumber());

        node1.sendFullStatusTo(node2);
        // sync setup
        node2.waitUntilNTasksWithTimeout(SyncUtils.syncSetupRequests(100, 0, SyncConfiguration.IMMEDIATE_FOR_TESTING));
        // synchronize 100 new blocks from node 1
        node2.waitExactlyNTasksWithTimeout(100);

        Assert.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node2.getSyncProcessor().getExpectedResponses().isEmpty());

        Assert.assertEquals(100, node1.getBestBlock().getNumber());
        Assert.assertEquals(100, node2.getBestBlock().getNumber());
        Assert.assertEquals(0, node3.getBestBlock().getNumber());

        node2.sendFullStatusTo(node3);
        // sync setup
        node3.waitUntilNTasksWithTimeout(SyncUtils.syncSetupRequests(100, 0, SyncConfiguration.IMMEDIATE_FOR_TESTING));
        // synchronize 100 new blocks from node 2
        node3.waitExactlyNTasksWithTimeout(100);

        Assert.assertEquals(100, node1.getBestBlock().getNumber());
        Assert.assertEquals(100, node2.getBestBlock().getNumber());
        Assert.assertEquals(100, node3.getBestBlock().getNumber());

        Assert.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node2.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());

        Assert.assertEquals(node1.getBestBlock().getHash(), node2.getBestBlock().getHash());
        Assert.assertEquals(node2.getBestBlock().getHash(), node3.getBestBlock().getHash());

        node1.joinWithTimeout();
        node2.joinWithTimeout();
        node3.joinWithTimeout();

        Assert.assertFalse(node1.getSyncProcessor().isPeerSyncing(node2.getNodeID()));
        Assert.assertFalse(node1.getSyncProcessor().isPeerSyncing(node3.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().isPeerSyncing(node1.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().isPeerSyncing(node3.getNodeID()));
        Assert.assertFalse(node3.getSyncProcessor().isPeerSyncing(node1.getNodeID()));
        Assert.assertFalse(node3.getSyncProcessor().isPeerSyncing(node2.getNodeID()));
    }

    @Test
    public void synchronizeNewNodeWithBestChain() throws InterruptedException {
        SimpleAsyncNode node1 = SimpleAsyncNode.createNodeWithWorldBlockChain(30,false, false);
        SimpleAsyncNode node2 = SimpleAsyncNode.createNodeWithWorldBlockChain(50,false, false);
        SimpleAsyncNode node3 = SimpleAsyncNode.createNodeWithWorldBlockChain(0,false, false);

        Assert.assertEquals(30, node1.getBestBlock().getNumber());
        Assert.assertEquals(50, node2.getBestBlock().getNumber());
        Assert.assertEquals(0, node3.getBestBlock().getNumber());

        node1.sendFullStatusTo(node3);
        // sync setup
        node3.waitUntilNTasksWithTimeout(SyncUtils.syncSetupRequests(30, 0, SyncConfiguration.IMMEDIATE_FOR_TESTING));
        // synchronize 30 new blocks from node 1
        node3.waitExactlyNTasksWithTimeout(30);

        Assert.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());

        Assert.assertEquals(30, node1.getBestBlock().getNumber());
        Assert.assertEquals(50, node2.getBestBlock().getNumber());
        Assert.assertEquals(30, node3.getBestBlock().getNumber());
        Assert.assertEquals(node1.getBestBlock().getHash(), node3.getBestBlock().getHash());

        node2.sendFullStatusTo(node3);
        // sync setup
        node3.waitUntilNTasksWithTimeout(SyncUtils.syncSetupRequests(50, 30, SyncConfiguration.IMMEDIATE_FOR_TESTING));
        // synchronize 50 new blocks from node 2
        node3.waitExactlyNTasksWithTimeout(20);

        Assert.assertEquals(30, node1.getBestBlock().getNumber());
        Assert.assertEquals(50, node2.getBestBlock().getNumber());
        Assert.assertEquals(50, node3.getBestBlock().getNumber());

        Assert.assertEquals(node2.getBestBlock().getHash(), node3.getBestBlock().getHash());

        Assert.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node2.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());

        node1.joinWithTimeout();
        node2.joinWithTimeout();
        node3.joinWithTimeout();

        Assert.assertFalse(node1.getSyncProcessor().isPeerSyncing(node2.getNodeID()));
        Assert.assertFalse(node1.getSyncProcessor().isPeerSyncing(node3.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().isPeerSyncing(node1.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().isPeerSyncing(node3.getNodeID()));
        Assert.assertFalse(node3.getSyncProcessor().isPeerSyncing(node1.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().isPeerSyncing(node2.getNodeID()));
    }

    @Ignore
    public void synchronizeNewNodeWithTwoPeers() {
        Blockchain b1 = BlockChainBuilder.ofSize(30, false);
        Blockchain b2 = BlockChainBuilder.copyAndExtend(b1, 43, false);

        SimpleAsyncNode node1 = SimpleAsyncNode.createNode(b1, SyncConfiguration.IMMEDIATE_FOR_TESTING);
        SimpleAsyncNode node2 = SimpleAsyncNode.createNode(b2, SyncConfiguration.IMMEDIATE_FOR_TESTING);
        SimpleAsyncNode node3 = SimpleAsyncNode.createNodeWithBlockChainBuilder(0);

        Assert.assertEquals(30, node1.getBestBlock().getNumber());
        Assert.assertEquals(73, node2.getBestBlock().getNumber());
        Assert.assertEquals(0, node3.getBestBlock().getNumber());

        node1.sendFullStatusTo(node3);
        // sync setup
        node3.waitUntilNTasksWithTimeout(SyncUtils.syncSetupRequests(30, 0, SyncConfiguration.IMMEDIATE_FOR_TESTING));
        // synchronize 30 new blocks from node 1
        node3.waitExactlyNTasksWithTimeout(30);

        Assert.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());

        Assert.assertEquals(30, node1.getBestBlock().getNumber());
        Assert.assertEquals(73, node2.getBestBlock().getNumber());
        Assert.assertEquals(30, node3.getBestBlock().getNumber());
        Assert.assertEquals(node1.getBestBlock().getHash(), node3.getBestBlock().getHash());

        node2.sendFullStatusTo(node3);
        // sync setup
        node3.waitUntilNTasksWithTimeout(SyncUtils.syncSetupRequests(73, 30, SyncConfiguration.IMMEDIATE_FOR_TESTING));
        // synchronize 43 new blocks from node 2
        node3.waitExactlyNTasksWithTimeout(43);

        Assert.assertEquals(node2.getBestBlock().getHash(), node3.getBestBlock().getHash());

        Assert.assertEquals(30, node1.getBestBlock().getNumber());
        Assert.assertEquals(73, node2.getBestBlock().getNumber());
        Assert.assertEquals(73, node3.getBestBlock().getNumber());

        Assert.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node2.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());

        node1.joinWithTimeout();
        node2.joinWithTimeout();
        node3.joinWithTimeout();

        Assert.assertFalse(node1.getSyncProcessor().isPeerSyncing(node2.getNodeID()));
        Assert.assertFalse(node1.getSyncProcessor().isPeerSyncing(node3.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().isPeerSyncing(node1.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().isPeerSyncing(node3.getNodeID()));
        Assert.assertFalse(node3.getSyncProcessor().isPeerSyncing(node1.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().isPeerSyncing(node2.getNodeID()));
    }

    @Ignore
    public void synchronizeNewNodeWithTwoPeersDefault() {
        Blockchain b1 = BlockChainBuilder.ofSize(50, false);
        Blockchain b2 = BlockChainBuilder.ofSize(0, false);

        SimpleAsyncNode node1 = SimpleAsyncNode.createDefaultNode(b1);
        SimpleAsyncNode node2 = SimpleAsyncNode.createDefaultNode(b1);
        SyncConfiguration syncConfiguration = new SyncConfiguration(2,1,1,1,20,192);
        SimpleAsyncNode node3 = SimpleAsyncNode.createNode(b2, syncConfiguration);

        Assert.assertEquals(50, node1.getBestBlock().getNumber());
        Assert.assertEquals(50, node2.getBestBlock().getNumber());
        Assert.assertEquals(0, node3.getBestBlock().getNumber());

        node1.sendFullStatusTo(node3);
        node2.sendFullStatusTo(node3);

        // sync setup
        node3.waitUntilNTasksWithTimeout(SyncUtils.syncSetupRequests(50, 0, SyncConfiguration.IMMEDIATE_FOR_TESTING));
        // synchronize 50 new blocks from node 1
        node3.waitExactlyNTasksWithTimeout(50 + 2);

        Assert.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());

        Assert.assertEquals(50, node3.getBestBlock().getNumber());
        Assert.assertEquals(node1.getBestBlock().getHash(), node3.getBestBlock().getHash());

        Assert.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node2.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());

        node1.joinWithTimeout();
        node2.joinWithTimeout();
        node3.joinWithTimeout();

        Assert.assertFalse(node1.getSyncProcessor().isPeerSyncing(node2.getNodeID()));
        Assert.assertFalse(node1.getSyncProcessor().isPeerSyncing(node3.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().isPeerSyncing(node1.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().isPeerSyncing(node3.getNodeID()));
        Assert.assertFalse(node3.getSyncProcessor().isPeerSyncing(node1.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().isPeerSyncing(node2.getNodeID()));
    }

    @Ignore
    public void synchronizeNewNodeWithTwoPeers200Default() {
        Blockchain b1 = BlockChainBuilder.ofSize(200, false);
        Blockchain b2 = BlockChainBuilder.ofSize(0, false);

        SimpleAsyncNode node1 = SimpleAsyncNode.createDefaultNode(b1);
        SimpleAsyncNode node2 = SimpleAsyncNode.createDefaultNode(b1);
        SyncConfiguration syncConfiguration = new SyncConfiguration(2,1,1,1,20,192);
        SimpleAsyncNode node3 = SimpleAsyncNode.createNode(b2, syncConfiguration);

        Assert.assertEquals(200, node1.getBestBlock().getNumber());
        Assert.assertEquals(200, node2.getBestBlock().getNumber());
        Assert.assertEquals(0, node3.getBestBlock().getNumber());

        node1.sendFullStatusTo(node3);
        node2.sendFullStatusTo(node3);

        // sync setup
        int setupRequests = SyncUtils.syncSetupRequests(200, 0, SyncConfiguration.IMMEDIATE_FOR_TESTING);
        node3.waitUntilNTasksWithTimeout(setupRequests);
        node3.waitExactlyNTasksWithTimeout(200 + setupRequests - 10);

        Assert.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());

        Assert.assertEquals(200, node3.getBestBlock().getNumber());
        Assert.assertEquals(node1.getBestBlock().getHash(), node3.getBestBlock().getHash());

        Assert.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node2.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());

        node1.joinWithTimeout();
        node2.joinWithTimeout();
        node3.joinWithTimeout();

        Assert.assertFalse(node1.getSyncProcessor().isPeerSyncing(node2.getNodeID()));
        Assert.assertFalse(node1.getSyncProcessor().isPeerSyncing(node3.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().isPeerSyncing(node1.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().isPeerSyncing(node3.getNodeID()));
        Assert.assertFalse(node3.getSyncProcessor().isPeerSyncing(node1.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().isPeerSyncing(node2.getNodeID()));
    }

    @Ignore
    public void synchronizeWithTwoPeers200AndOneFails() {
        Blockchain b1 = BlockChainBuilder.ofSize(200, false);
        Blockchain b2 = BlockChainBuilder.ofSize(0, false);

        SimpleAsyncNode node1 = SimpleAsyncNode.createDefaultNode(b1);
        SimpleAsyncNode node2 = SimpleAsyncNode.createDefaultNode(b1);
        SyncConfiguration syncConfiguration = new SyncConfiguration(2,1,0,1,20,192);
        SimpleAsyncNode node3 = SimpleAsyncNode.createNode(b2, syncConfiguration);

        Assert.assertEquals(200, node1.getBestBlock().getNumber());
        Assert.assertEquals(200, node2.getBestBlock().getNumber());
        Assert.assertEquals(0, node3.getBestBlock().getNumber());

        node1.sendFullStatusTo(node3);
        node2.sendFullStatusTo(node3);

        // sync setup
        int setupRequests = SyncUtils.syncSetupRequests(200, 0, SyncConfiguration.IMMEDIATE_FOR_TESTING);
        node3.waitUntilNTasksWithTimeout(setupRequests);
        node3.waitUntilNTasksWithTimeout(5);
        // synchronize 200 (extra tasks are from old sync protocol messages)
        BodyResponseMessage response = new BodyResponseMessage(new Random().nextLong(), null, null);
        node3.getSyncProcessor().registerExpectedMessage(response);
        node3.getSyncProcessor().processBodyResponse(node1.getMessageChannel(node3), response);
        node3.waitExactlyNTasksWithTimeout(200 + setupRequests - 15);

        Assert.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());

        Assert.assertEquals(200, node3.getBestBlock().getNumber());
        Assert.assertEquals(node1.getBestBlock().getHash(), node3.getBestBlock().getHash());

        Assert.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node2.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());

        node1.joinWithTimeout();
        node2.joinWithTimeout();
        node3.joinWithTimeout();

        Assert.assertFalse(node1.getSyncProcessor().isPeerSyncing(node2.getNodeID()));
        Assert.assertFalse(node1.getSyncProcessor().isPeerSyncing(node3.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().isPeerSyncing(node1.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().isPeerSyncing(node3.getNodeID()));
        Assert.assertFalse(node3.getSyncProcessor().isPeerSyncing(node1.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().isPeerSyncing(node2.getNodeID()));
    }

    @Ignore
    public void synchronizeNewNodeWithTwoPeers200Different() {
        Blockchain b1 = BlockChainBuilder.ofSize(193, false);
        Blockchain b2 = BlockChainBuilder.copyAndExtend(b1,7);
        Blockchain b3 = BlockChainBuilder.ofSize(0, false);

        SimpleAsyncNode node1 = SimpleAsyncNode.createDefaultNode(b1);
        SimpleAsyncNode node2 = SimpleAsyncNode.createDefaultNode(b2);
        SyncConfiguration syncConfiguration = new SyncConfiguration(2,1,1,1,20,192);
        SimpleAsyncNode node3 = SimpleAsyncNode.createNode(b3, syncConfiguration);

        Assert.assertEquals(193, node1.getBestBlock().getNumber());
        Assert.assertEquals(200, node2.getBestBlock().getNumber());
        Assert.assertEquals(0, node3.getBestBlock().getNumber());

        node1.sendFullStatusTo(node3);
        node2.sendFullStatusTo(node3);

        // sync setup
        int setupRequests = SyncUtils.syncSetupRequests(200, 0, syncConfiguration);
        node3.waitUntilNTasksWithTimeout(setupRequests);
        // synchronize 200 new blocks (extra tasks are from old sync protocol messages)
        node3.waitExactlyNTasksWithTimeout(192 + setupRequests - 2);

        Assert.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());

        Assert.assertEquals(200, node3.getBestBlock().getNumber());
        Assert.assertEquals(node2.getBestBlock().getHash(), node3.getBestBlock().getHash());

        Assert.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node2.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());

        node1.joinWithTimeout();
        node2.joinWithTimeout();
        node3.joinWithTimeout();

        Assert.assertFalse(node1.getSyncProcessor().isPeerSyncing(node2.getNodeID()));
        Assert.assertFalse(node1.getSyncProcessor().isPeerSyncing(node3.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().isPeerSyncing(node1.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().isPeerSyncing(node3.getNodeID()));
        Assert.assertFalse(node3.getSyncProcessor().isPeerSyncing(node1.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().isPeerSyncing(node2.getNodeID()));
    }

    @Ignore
    public void synchronizeNewNodeWithThreePeers400Different() {
        Blockchain b1 = BlockChainBuilder.ofSize(0, false);
        Blockchain b2 = BlockChainBuilder.copyAndExtend(b1, 200);
        Blockchain b3 = BlockChainBuilder.copyAndExtend(b2,200);

        SimpleAsyncNode node1 = SimpleAsyncNode.createDefaultNode(b2);
        SimpleAsyncNode node2 = SimpleAsyncNode.createDefaultNode(b2);
        SimpleAsyncNode node3 = SimpleAsyncNode.createDefaultNode(b3);
        SyncConfiguration syncConfiguration = new SyncConfiguration(3,1,10,100,20,192);
        SimpleAsyncNode node4 = SimpleAsyncNode.createNode(b1, syncConfiguration);

        Assert.assertEquals(200, node1.getBestBlock().getNumber());
        Assert.assertEquals(200, node2.getBestBlock().getNumber());
        Assert.assertEquals(400, node3.getBestBlock().getNumber());
        Assert.assertEquals(0, node4.getBestBlock().getNumber());

        node1.sendFullStatusTo(node4);
        node2.sendFullStatusTo(node4);
        node3.sendFullStatusTo(node4);

        // sync setup
        int setupRequests = SyncUtils.syncSetupRequests(400, 0, syncConfiguration);
        node4.waitUntilNTasksWithTimeout(setupRequests);
        // synchronize 50 new blocks from node 1
        node4.waitExactlyNTasksWithTimeout(400 + setupRequests - 10);

        Assert.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node4.getSyncProcessor().getExpectedResponses().isEmpty());

        Assert.assertEquals(400, node4.getBestBlock().getNumber());
        Assert.assertEquals(node4.getBestBlock().getHash(), node3.getBestBlock().getHash());

        Assert.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node2.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node4.getSyncProcessor().getExpectedResponses().isEmpty());

        node1.joinWithTimeout();
        node2.joinWithTimeout();
        node3.joinWithTimeout();
        node4.joinWithTimeout();

        Assert.assertFalse(node1.getSyncProcessor().isPeerSyncing(node4.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().isPeerSyncing(node4.getNodeID()));
        Assert.assertFalse(node3.getSyncProcessor().isPeerSyncing(node4.getNodeID()));
    }

    @Ignore
    public void dontSynchronizeNodeWithShorterChain() throws InterruptedException {
        SimpleAsyncNode node1 = SimpleAsyncNode.createNodeWithWorldBlockChain(50, false, false);
        SimpleAsyncNode node2 = SimpleAsyncNode.createNodeWithWorldBlockChain(30,false, false);
        SimpleAsyncNode node3 = SimpleAsyncNode.createNodeWithWorldBlockChain(0,false, false);

        Assert.assertEquals(50, node1.getBestBlock().getNumber());
        Assert.assertEquals(30, node2.getBestBlock().getNumber());
        Assert.assertEquals(0, node3.getBestBlock().getNumber());

        node1.sendFullStatusTo(node3);
        // sync setup
        node3.waitUntilNTasksWithTimeout(SyncUtils.syncSetupRequests(50, 0, SyncConfiguration.IMMEDIATE_FOR_TESTING));
        // synchronize 50 new blocks from node 1
        node3.waitExactlyNTasksWithTimeout(50);

        Assert.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());

        Assert.assertEquals(50, node1.getBestBlock().getNumber());
        Assert.assertEquals(30, node2.getBestBlock().getNumber());
        Assert.assertEquals(50, node3.getBestBlock().getNumber());
        Assert.assertEquals(node1.getBestBlock().getHash(), node3.getBestBlock().getHash());

        node2.sendFullStatusTo(node3);
        // receive status, do nothing
        node3.waitExactlyNTasksWithTimeout(1);

        Assert.assertEquals(50, node1.getBestBlock().getNumber());
        Assert.assertEquals(30, node2.getBestBlock().getNumber());
        Assert.assertEquals(50, node3.getBestBlock().getNumber());

        Assert.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node2.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());

        node1.joinWithTimeout();
        node2.joinWithTimeout();
        node3.joinWithTimeout();

        Assert.assertFalse(node1.getSyncProcessor().isPeerSyncing(node2.getNodeID()));
        Assert.assertFalse(node1.getSyncProcessor().isPeerSyncing(node3.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().isPeerSyncing(node1.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().isPeerSyncing(node3.getNodeID()));
        Assert.assertFalse(node3.getSyncProcessor().isPeerSyncing(node1.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().isPeerSyncing(node2.getNodeID()));
    }

    @Ignore
    public void dontSynchronizeNodeWithShorterChainAndThenSynchronizeWithNewPeer() throws InterruptedException {
        Blockchain b1 = BlockChainBuilder.ofSize(30, false);
        Blockchain b2 = BlockChainBuilder.copyAndExtend(b1, 43, false);
        Blockchain b3 = BlockChainBuilder.copyAndExtend(b2, 7, false);

        SimpleAsyncNode node1 = SimpleAsyncNode.createNode(b1, SyncConfiguration.IMMEDIATE_FOR_TESTING);
        SimpleAsyncNode node2 = SimpleAsyncNode.createNode(b2, SyncConfiguration.IMMEDIATE_FOR_TESTING);
        SimpleAsyncNode node3 = SimpleAsyncNode.createNode(b3, SyncConfiguration.IMMEDIATE_FOR_TESTING);

        Assert.assertEquals(30, node1.getBestBlock().getNumber());
        Assert.assertEquals(73, node2.getBestBlock().getNumber());
        Assert.assertEquals(80, node3.getBestBlock().getNumber());

        node1.sendFullStatusTo(node2);
        // receive status, do nothing
        node2.waitExactlyNTasksWithTimeout(1);

        Assert.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node2.getSyncProcessor().getExpectedResponses().isEmpty());

        Assert.assertEquals(30, node1.getBestBlock().getNumber());
        Assert.assertEquals(73, node2.getBestBlock().getNumber());
        Assert.assertEquals(80, node3.getBestBlock().getNumber());

        node3.sendFullStatusTo(node2);
        // sync setup
        node2.waitUntilNTasksWithTimeout(SyncUtils.syncSetupRequests(80, 73, SyncConfiguration.IMMEDIATE_FOR_TESTING));
        // synchronize 7 new blocks from node 3
        node2.waitExactlyNTasksWithTimeout(7);

        Assert.assertEquals(30, node1.getBestBlock().getNumber());
        Assert.assertEquals(80, node2.getBestBlock().getNumber());
        Assert.assertEquals(80, node3.getBestBlock().getNumber());
        Assert.assertEquals(node2.getBestBlock().getHash(), node3.getBestBlock().getHash());

        Assert.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node2.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());

        node1.joinWithTimeout();
        node2.joinWithTimeout();
        node3.joinWithTimeout();

        Assert.assertFalse(node1.getSyncProcessor().isPeerSyncing(node2.getNodeID()));
        Assert.assertFalse(node1.getSyncProcessor().isPeerSyncing(node3.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().isPeerSyncing(node1.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().isPeerSyncing(node3.getNodeID()));
        Assert.assertFalse(node3.getSyncProcessor().isPeerSyncing(node1.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().isPeerSyncing(node2.getNodeID()));
    }

    @Ignore
    public void ignoreNewBlockHashesWhenSyncing() {
        Blockchain b1 = BlockChainBuilder.ofSize(30, false);
        Blockchain b2 = BlockChainBuilder.copyAndExtend(b1, 1, false);

        SimpleAsyncNode node1 = SimpleAsyncNode.createNode(b1, SyncConfiguration.IMMEDIATE_FOR_TESTING);
        SimpleAsyncNode node2 = SimpleAsyncNode.createNode(b2, SyncConfiguration.IMMEDIATE_FOR_TESTING);
        SimpleAsyncNode node3 = SimpleAsyncNode.createNodeWithBlockChainBuilder(0);

        Assert.assertEquals(30, node1.getBestBlock().getNumber());
        Assert.assertEquals(31, node2.getBestBlock().getNumber());
        Assert.assertEquals(0, node3.getBestBlock().getNumber());

        node1.sendFullStatusTo(node3);
        // receive the hash of a better block than node1's best
        // while it's syncing with node1
        node3.receiveMessageFrom(node2, new NewBlockHashMessage(node2.getBestBlock().getHash().getBytes()));
        // receive and ignore NewBlockHashMessage
        node3.waitUntilNTasksWithTimeout(1);
        // sync setup
        node3.waitUntilNTasksWithTimeout(SyncUtils.syncSetupRequests(30, 0, SyncConfiguration.IMMEDIATE_FOR_TESTING));
        // synchronize 30 new blocks from node 1
        node3.waitExactlyNTasksWithTimeout(30);

        Assert.assertEquals(30, node1.getBestBlock().getNumber());
        Assert.assertEquals(31, node2.getBestBlock().getNumber());
        Assert.assertEquals(30, node3.getBestBlock().getNumber());

        Assert.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node2.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());

        node1.joinWithTimeout();
        node2.joinWithTimeout();
        node3.joinWithTimeout();

        Assert.assertFalse(node1.getSyncProcessor().isPeerSyncing(node2.getNodeID()));
        Assert.assertFalse(node1.getSyncProcessor().isPeerSyncing(node3.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().isPeerSyncing(node1.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().isPeerSyncing(node3.getNodeID()));
        Assert.assertFalse(node3.getSyncProcessor().isPeerSyncing(node1.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().isPeerSyncing(node2.getNodeID()));
    }

    @Ignore
    public void acceptNewBlockHashWhenNotSyncing() {
        Blockchain b1 = BlockChainBuilder.ofSize(30, false);
        Blockchain b2 = BlockChainBuilder.copyAndExtend(b1, 1, false);

        SimpleAsyncNode node1 = SimpleAsyncNode.createNode(b1, SyncConfiguration.IMMEDIATE_FOR_TESTING);
        SimpleAsyncNode node2 = SimpleAsyncNode.createNode(b2, SyncConfiguration.IMMEDIATE_FOR_TESTING);
        SimpleAsyncNode node3 = SimpleAsyncNode.createNodeWithBlockChainBuilder(0);

        Assert.assertEquals(30, node1.getBestBlock().getNumber());
        Assert.assertEquals(31, node2.getBestBlock().getNumber());
        Assert.assertEquals(0, node3.getBestBlock().getNumber());

        node1.sendFullStatusTo(node3);
        // sync setup
        node3.waitUntilNTasksWithTimeout(SyncUtils.syncSetupRequests(30, 0, SyncConfiguration.IMMEDIATE_FOR_TESTING));
        // synchronize 30 new blocks from node 1
        node3.waitExactlyNTasksWithTimeout(30);

        Assert.assertEquals(30, node1.getBestBlock().getNumber());
        Assert.assertEquals(31, node2.getBestBlock().getNumber());
        Assert.assertEquals(30, node3.getBestBlock().getNumber());

        // receive the hash of a better block than node1's best
        // after syncing with node1
        node3.receiveMessageFrom(node2, new NewBlockHashMessage(node2.getBestBlock().getHash().getBytes()));
        // receive block hash, then receive block
        node3.waitExactlyNTasksWithTimeout(2);

        Assert.assertEquals(30, node1.getBestBlock().getNumber());
        Assert.assertEquals(31, node2.getBestBlock().getNumber());
        Assert.assertEquals(31, node3.getBestBlock().getNumber());

        Assert.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node2.getSyncProcessor().getExpectedResponses().isEmpty());
        Assert.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());

        node1.joinWithTimeout();
        node2.joinWithTimeout();
        node3.joinWithTimeout();

        Assert.assertFalse(node1.getSyncProcessor().isPeerSyncing(node2.getNodeID()));
        Assert.assertFalse(node1.getSyncProcessor().isPeerSyncing(node3.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().isPeerSyncing(node1.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().isPeerSyncing(node3.getNodeID()));
        Assert.assertFalse(node3.getSyncProcessor().isPeerSyncing(node1.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().isPeerSyncing(node2.getNodeID()));
    }
}
