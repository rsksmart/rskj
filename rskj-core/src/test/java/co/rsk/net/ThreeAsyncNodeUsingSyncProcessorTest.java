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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;


@SuppressWarnings("squid:S1607") // many @Disabled annotations for diverse reasons
class ThreeAsyncNodeUsingSyncProcessorTest {

    @Test
    void synchronizeNewNodesInAChain() {
        SimpleAsyncNode node1 = SimpleAsyncNode.createNodeWithWorldBlockChain(100,false, false);
        SimpleAsyncNode node2 = SimpleAsyncNode.createNodeWithWorldBlockChain(0,false, false);
        SimpleAsyncNode node3 = SimpleAsyncNode.createNodeWithWorldBlockChain(0,false, false);

        Assertions.assertEquals(100, node1.getBestBlock().getNumber());
        Assertions.assertEquals(0, node2.getBestBlock().getNumber());
        Assertions.assertEquals(0, node3.getBestBlock().getNumber());

        node1.sendFullStatusTo(node2);
        // sync setup
        node2.waitUntilNTasksWithTimeout(SyncUtils.syncSetupRequests(100, 0, SyncConfiguration.IMMEDIATE_FOR_TESTING));
        // synchronize 100 new blocks from node 1
        node2.waitExactlyNTasksWithTimeout(100);

        Assertions.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assertions.assertTrue(node2.getSyncProcessor().getExpectedResponses().isEmpty());

        Assertions.assertEquals(100, node1.getBestBlock().getNumber());
        Assertions.assertEquals(100, node2.getBestBlock().getNumber());
        Assertions.assertEquals(0, node3.getBestBlock().getNumber());

        node2.sendFullStatusTo(node3);
        // sync setup
        node3.waitUntilNTasksWithTimeout(SyncUtils.syncSetupRequests(100, 0, SyncConfiguration.IMMEDIATE_FOR_TESTING));
        // synchronize 100 new blocks from node 2
        node3.waitExactlyNTasksWithTimeout(100);

        Assertions.assertEquals(100, node1.getBestBlock().getNumber());
        Assertions.assertEquals(100, node2.getBestBlock().getNumber());
        Assertions.assertEquals(100, node3.getBestBlock().getNumber());

        Assertions.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assertions.assertTrue(node2.getSyncProcessor().getExpectedResponses().isEmpty());
        Assertions.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());

        Assertions.assertEquals(node1.getBestBlock().getHash(), node2.getBestBlock().getHash());
        Assertions.assertEquals(node2.getBestBlock().getHash(), node3.getBestBlock().getHash());

        node1.joinWithTimeout();
        node2.joinWithTimeout();
        node3.joinWithTimeout();

        Assertions.assertFalse(node1.getSyncProcessor().isSyncing());
        Assertions.assertFalse(node2.getSyncProcessor().isSyncing());
        Assertions.assertFalse(node3.getSyncProcessor().isSyncing());
    }

    @Test
    void synchronizeNewNodeWithBestChain() {
        SimpleAsyncNode node1 = SimpleAsyncNode.createNodeWithWorldBlockChain(30,false, false);
        SimpleAsyncNode node2 = SimpleAsyncNode.createNodeWithWorldBlockChain(50,false, false);
        SimpleAsyncNode node3 = SimpleAsyncNode.createNodeWithWorldBlockChain(0,false, false);

        Assertions.assertEquals(30, node1.getBestBlock().getNumber());
        Assertions.assertEquals(50, node2.getBestBlock().getNumber());
        Assertions.assertEquals(0, node3.getBestBlock().getNumber());

        node1.sendFullStatusTo(node3);
        // sync setup
        node3.waitUntilNTasksWithTimeout(SyncUtils.syncSetupRequests(30, 0, SyncConfiguration.IMMEDIATE_FOR_TESTING));
        // synchronize 30 new blocks from node 1
        node3.waitExactlyNTasksWithTimeout(30);

        Assertions.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assertions.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());

        Assertions.assertEquals(30, node1.getBestBlock().getNumber());
        Assertions.assertEquals(50, node2.getBestBlock().getNumber());
        Assertions.assertEquals(30, node3.getBestBlock().getNumber());
        Assertions.assertEquals(node1.getBestBlock().getHash(), node3.getBestBlock().getHash());

        node2.sendFullStatusTo(node3);
        // sync setup
        node3.waitUntilNTasksWithTimeout(SyncUtils.syncSetupRequests(50, 30, SyncConfiguration.IMMEDIATE_FOR_TESTING));
        // synchronize 50 new blocks from node 2
        node3.waitExactlyNTasksWithTimeout(20);

        Assertions.assertEquals(30, node1.getBestBlock().getNumber());
        Assertions.assertEquals(50, node2.getBestBlock().getNumber());
        Assertions.assertEquals(50, node3.getBestBlock().getNumber());

        Assertions.assertEquals(node2.getBestBlock().getHash(), node3.getBestBlock().getHash());

        Assertions.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assertions.assertTrue(node2.getSyncProcessor().getExpectedResponses().isEmpty());
        Assertions.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());

        node1.joinWithTimeout();
        node2.joinWithTimeout();
        node3.joinWithTimeout();

        Assertions.assertFalse(node1.getSyncProcessor().isSyncing());
        Assertions.assertFalse(node2.getSyncProcessor().isSyncing());
        Assertions.assertFalse(node3.getSyncProcessor().isSyncing());
    }

    @Disabled
    public void synchronizeNewNodeWithTwoPeers() {
        Blockchain b1 = new BlockChainBuilder().ofSize(30, false);
        Blockchain b2 = BlockChainBuilder.copyAndExtend(b1, 43, false);

        SimpleAsyncNode node1 = SimpleAsyncNode.createNode(b1, SyncConfiguration.IMMEDIATE_FOR_TESTING);
        SimpleAsyncNode node2 = SimpleAsyncNode.createNode(b2, SyncConfiguration.IMMEDIATE_FOR_TESTING);
        SimpleAsyncNode node3 = SimpleAsyncNode.createNodeWithBlockChainBuilder(0);

        Assertions.assertEquals(30, node1.getBestBlock().getNumber());
        Assertions.assertEquals(73, node2.getBestBlock().getNumber());
        Assertions.assertEquals(0, node3.getBestBlock().getNumber());

        node1.sendFullStatusTo(node3);
        // sync setup
        node3.waitUntilNTasksWithTimeout(SyncUtils.syncSetupRequests(30, 0, SyncConfiguration.IMMEDIATE_FOR_TESTING));
        // synchronize 30 new blocks from node 1
        node3.waitExactlyNTasksWithTimeout(30);

        Assertions.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assertions.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());

        Assertions.assertEquals(30, node1.getBestBlock().getNumber());
        Assertions.assertEquals(73, node2.getBestBlock().getNumber());
        Assertions.assertEquals(30, node3.getBestBlock().getNumber());
        Assertions.assertEquals(node1.getBestBlock().getHash(), node3.getBestBlock().getHash());

        node2.sendFullStatusTo(node3);
        // sync setup
        node3.waitUntilNTasksWithTimeout(SyncUtils.syncSetupRequests(73, 30, SyncConfiguration.IMMEDIATE_FOR_TESTING));
        // synchronize 43 new blocks from node 2
        node3.waitExactlyNTasksWithTimeout(43);

        Assertions.assertEquals(node2.getBestBlock().getHash(), node3.getBestBlock().getHash());

        Assertions.assertEquals(30, node1.getBestBlock().getNumber());
        Assertions.assertEquals(73, node2.getBestBlock().getNumber());
        Assertions.assertEquals(73, node3.getBestBlock().getNumber());

        Assertions.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assertions.assertTrue(node2.getSyncProcessor().getExpectedResponses().isEmpty());
        Assertions.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());

        node1.joinWithTimeout();
        node2.joinWithTimeout();
        node3.joinWithTimeout();

        Assertions.assertFalse(node1.getSyncProcessor().isSyncing());
        Assertions.assertFalse(node2.getSyncProcessor().isSyncing());
        Assertions.assertFalse(node3.getSyncProcessor().isSyncing());
    }

    @Disabled
    public void synchronizeNewNodeWithTwoPeersDefault() {
        Blockchain b1 = new BlockChainBuilder().ofSize(50, false);
        Blockchain b2 = new BlockChainBuilder().ofSize(0, false);

        SimpleAsyncNode node1 = SimpleAsyncNode.createDefaultNode(b1);
        SimpleAsyncNode node2 = SimpleAsyncNode.createDefaultNode(b1);
        SyncConfiguration syncConfiguration = new SyncConfiguration(2,1,1,1,20,192, 20, 10, 0, false, 60);
        SimpleAsyncNode node3 = SimpleAsyncNode.createNode(b2, syncConfiguration);

        Assertions.assertEquals(50, node1.getBestBlock().getNumber());
        Assertions.assertEquals(50, node2.getBestBlock().getNumber());
        Assertions.assertEquals(0, node3.getBestBlock().getNumber());

        node1.sendFullStatusTo(node3);
        node2.sendFullStatusTo(node3);

        // sync setup
        node3.waitUntilNTasksWithTimeout(SyncUtils.syncSetupRequests(50, 0, SyncConfiguration.IMMEDIATE_FOR_TESTING));
        // synchronize 50 new blocks from node 1
        node3.waitExactlyNTasksWithTimeout(50 + 2);

        Assertions.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assertions.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());

        Assertions.assertEquals(50, node3.getBestBlock().getNumber());
        Assertions.assertEquals(node1.getBestBlock().getHash(), node3.getBestBlock().getHash());

        Assertions.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assertions.assertTrue(node2.getSyncProcessor().getExpectedResponses().isEmpty());
        Assertions.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());

        node1.joinWithTimeout();
        node2.joinWithTimeout();
        node3.joinWithTimeout();

        Assertions.assertFalse(node1.getSyncProcessor().isSyncing());
        Assertions.assertFalse(node2.getSyncProcessor().isSyncing());
        Assertions.assertFalse(node3.getSyncProcessor().isSyncing());
    }

    @Disabled
    public void synchronizeNewNodeWithTwoPeers200Default() {
        Blockchain b1 = new BlockChainBuilder().ofSize(200, false);
        Blockchain b2 = new BlockChainBuilder().ofSize(0, false);

        SimpleAsyncNode node1 = SimpleAsyncNode.createDefaultNode(b1);
        SimpleAsyncNode node2 = SimpleAsyncNode.createDefaultNode(b1);
        SyncConfiguration syncConfiguration = new SyncConfiguration(2,1,1,1,20,192, 20, 10, 0, false, 60);
        SimpleAsyncNode node3 = SimpleAsyncNode.createNode(b2, syncConfiguration);

        Assertions.assertEquals(200, node1.getBestBlock().getNumber());
        Assertions.assertEquals(200, node2.getBestBlock().getNumber());
        Assertions.assertEquals(0, node3.getBestBlock().getNumber());

        node1.sendFullStatusTo(node3);
        node2.sendFullStatusTo(node3);

        // sync setup
        int setupRequests = SyncUtils.syncSetupRequests(200, 0, SyncConfiguration.IMMEDIATE_FOR_TESTING);
        node3.waitUntilNTasksWithTimeout(setupRequests);
        node3.waitExactlyNTasksWithTimeout(200 + setupRequests - 10);

        Assertions.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assertions.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());

        Assertions.assertEquals(200, node3.getBestBlock().getNumber());
        Assertions.assertEquals(node1.getBestBlock().getHash(), node3.getBestBlock().getHash());

        Assertions.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assertions.assertTrue(node2.getSyncProcessor().getExpectedResponses().isEmpty());
        Assertions.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());

        node1.joinWithTimeout();
        node2.joinWithTimeout();
        node3.joinWithTimeout();

        Assertions.assertFalse(node1.getSyncProcessor().isSyncing());
        Assertions.assertFalse(node2.getSyncProcessor().isSyncing());
        Assertions.assertFalse(node3.getSyncProcessor().isSyncing());
    }

    @Disabled
    public void synchronizeWithTwoPeers200AndOneFails() {
        Blockchain b1 = new BlockChainBuilder().ofSize(200, false);
        Blockchain b2 = new BlockChainBuilder().ofSize(0, false);

        SimpleAsyncNode node1 = SimpleAsyncNode.createDefaultNode(b1);
        SimpleAsyncNode node2 = SimpleAsyncNode.createDefaultNode(b1);
        SyncConfiguration syncConfiguration = new SyncConfiguration(2,1,0,1,20,192, 20, 10, 0, false, 60);
        SimpleAsyncNode node3 = SimpleAsyncNode.createNode(b2, syncConfiguration);

        Assertions.assertEquals(200, node1.getBestBlock().getNumber());
        Assertions.assertEquals(200, node2.getBestBlock().getNumber());
        Assertions.assertEquals(0, node3.getBestBlock().getNumber());

        node1.sendFullStatusTo(node3);
        node2.sendFullStatusTo(node3);

        // sync setup
        int setupRequests = SyncUtils.syncSetupRequests(200, 0, SyncConfiguration.IMMEDIATE_FOR_TESTING);
        node3.waitUntilNTasksWithTimeout(setupRequests);
        node3.waitUntilNTasksWithTimeout(5);
        // synchronize 200 (extra tasks are from old sync protocol messages)
        BodyResponseMessage response = new BodyResponseMessage(123123123123L, null, null);
        node3.getSyncProcessor().registerExpectedMessage(response);
        node3.getSyncProcessor().processBodyResponse(node1.getMessageChannel(node3), response);
        node3.waitExactlyNTasksWithTimeout(200 + setupRequests - 15);

        Assertions.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assertions.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());

        Assertions.assertEquals(200, node3.getBestBlock().getNumber());
        Assertions.assertEquals(node1.getBestBlock().getHash(), node3.getBestBlock().getHash());

        Assertions.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assertions.assertTrue(node2.getSyncProcessor().getExpectedResponses().isEmpty());
        Assertions.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());

        node1.joinWithTimeout();
        node2.joinWithTimeout();
        node3.joinWithTimeout();

        Assertions.assertFalse(node1.getSyncProcessor().isSyncing());
        Assertions.assertFalse(node2.getSyncProcessor().isSyncing());
        Assertions.assertFalse(node3.getSyncProcessor().isSyncing());
    }

    @Disabled
    public void synchronizeNewNodeWithTwoPeers200Different() {
        Blockchain b1 = new BlockChainBuilder().ofSize(193, false);
        Blockchain b2 = BlockChainBuilder.copyAndExtend(b1,7);
        Blockchain b3 = new BlockChainBuilder().ofSize(0, false);

        SimpleAsyncNode node1 = SimpleAsyncNode.createDefaultNode(b1);
        SimpleAsyncNode node2 = SimpleAsyncNode.createDefaultNode(b2);
        SyncConfiguration syncConfiguration = new SyncConfiguration(2,1,1,1,20,192, 20, 10, 0, false, 60);
        SimpleAsyncNode node3 = SimpleAsyncNode.createNode(b3, syncConfiguration);

        Assertions.assertEquals(193, node1.getBestBlock().getNumber());
        Assertions.assertEquals(200, node2.getBestBlock().getNumber());
        Assertions.assertEquals(0, node3.getBestBlock().getNumber());

        node1.sendFullStatusTo(node3);
        node2.sendFullStatusTo(node3);

        // sync setup
        int setupRequests = SyncUtils.syncSetupRequests(200, 0, syncConfiguration);
        node3.waitUntilNTasksWithTimeout(setupRequests);
        // synchronize 200 new blocks (extra tasks are from old sync protocol messages)
        node3.waitExactlyNTasksWithTimeout(192 + setupRequests - 2);

        Assertions.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assertions.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());

        Assertions.assertEquals(200, node3.getBestBlock().getNumber());
        Assertions.assertEquals(node2.getBestBlock().getHash(), node3.getBestBlock().getHash());

        Assertions.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assertions.assertTrue(node2.getSyncProcessor().getExpectedResponses().isEmpty());
        Assertions.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());

        node1.joinWithTimeout();
        node2.joinWithTimeout();
        node3.joinWithTimeout();

        Assertions.assertFalse(node1.getSyncProcessor().isSyncing());
        Assertions.assertFalse(node2.getSyncProcessor().isSyncing());
        Assertions.assertFalse(node3.getSyncProcessor().isSyncing());
    }

    @Disabled
    public void synchronizeNewNodeWithThreePeers400Different() {
        Blockchain b1 = new BlockChainBuilder().ofSize(0, false);
        Blockchain b2 = BlockChainBuilder.copyAndExtend(b1, 200);
        Blockchain b3 = BlockChainBuilder.copyAndExtend(b2,200);

        SimpleAsyncNode node1 = SimpleAsyncNode.createDefaultNode(b2);
        SimpleAsyncNode node2 = SimpleAsyncNode.createDefaultNode(b2);
        SimpleAsyncNode node3 = SimpleAsyncNode.createDefaultNode(b3);
        SyncConfiguration syncConfiguration = new SyncConfiguration(3,1,10,100,20,192, 20, 10, 0, false, 60);
        SimpleAsyncNode node4 = SimpleAsyncNode.createNode(b1, syncConfiguration);

        Assertions.assertEquals(200, node1.getBestBlock().getNumber());
        Assertions.assertEquals(200, node2.getBestBlock().getNumber());
        Assertions.assertEquals(400, node3.getBestBlock().getNumber());
        Assertions.assertEquals(0, node4.getBestBlock().getNumber());

        node1.sendFullStatusTo(node4);
        node2.sendFullStatusTo(node4);
        node3.sendFullStatusTo(node4);

        // sync setup
        int setupRequests = SyncUtils.syncSetupRequests(400, 0, syncConfiguration);
        node4.waitUntilNTasksWithTimeout(setupRequests);
        // synchronize 50 new blocks from node 1
        node4.waitExactlyNTasksWithTimeout(400 + setupRequests - 10);

        Assertions.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());
        Assertions.assertTrue(node4.getSyncProcessor().getExpectedResponses().isEmpty());

        Assertions.assertEquals(400, node4.getBestBlock().getNumber());
        Assertions.assertEquals(node4.getBestBlock().getHash(), node3.getBestBlock().getHash());

        Assertions.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assertions.assertTrue(node2.getSyncProcessor().getExpectedResponses().isEmpty());
        Assertions.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());
        Assertions.assertTrue(node4.getSyncProcessor().getExpectedResponses().isEmpty());

        node1.joinWithTimeout();
        node2.joinWithTimeout();
        node3.joinWithTimeout();
        node4.joinWithTimeout();

        Assertions.assertFalse(node1.getSyncProcessor().isSyncing());
        Assertions.assertFalse(node2.getSyncProcessor().isSyncing());
        Assertions.assertFalse(node3.getSyncProcessor().isSyncing());
    }

    @Disabled
    public void dontSynchronizeNodeWithShorterChain() throws InterruptedException {
        SimpleAsyncNode node1 = SimpleAsyncNode.createNodeWithWorldBlockChain(50, false, false);
        SimpleAsyncNode node2 = SimpleAsyncNode.createNodeWithWorldBlockChain(30,false, false);
        SimpleAsyncNode node3 = SimpleAsyncNode.createNodeWithWorldBlockChain(0,false, false);

        Assertions.assertEquals(50, node1.getBestBlock().getNumber());
        Assertions.assertEquals(30, node2.getBestBlock().getNumber());
        Assertions.assertEquals(0, node3.getBestBlock().getNumber());

        node1.sendFullStatusTo(node3);
        // sync setup
        node3.waitUntilNTasksWithTimeout(SyncUtils.syncSetupRequests(50, 0, SyncConfiguration.IMMEDIATE_FOR_TESTING));
        // synchronize 50 new blocks from node 1
        node3.waitExactlyNTasksWithTimeout(50);

        Assertions.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assertions.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());

        Assertions.assertEquals(50, node1.getBestBlock().getNumber());
        Assertions.assertEquals(30, node2.getBestBlock().getNumber());
        Assertions.assertEquals(50, node3.getBestBlock().getNumber());
        Assertions.assertEquals(node1.getBestBlock().getHash(), node3.getBestBlock().getHash());

        node2.sendFullStatusTo(node3);
        // receive status, do nothing
        node3.waitExactlyNTasksWithTimeout(1);

        Assertions.assertEquals(50, node1.getBestBlock().getNumber());
        Assertions.assertEquals(30, node2.getBestBlock().getNumber());
        Assertions.assertEquals(50, node3.getBestBlock().getNumber());

        Assertions.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assertions.assertTrue(node2.getSyncProcessor().getExpectedResponses().isEmpty());
        Assertions.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());

        node1.joinWithTimeout();
        node2.joinWithTimeout();
        node3.joinWithTimeout();

        Assertions.assertFalse(node1.getSyncProcessor().isSyncing());
        Assertions.assertFalse(node2.getSyncProcessor().isSyncing());
        Assertions.assertFalse(node3.getSyncProcessor().isSyncing());
    }

    @Disabled
    public void dontSynchronizeNodeWithShorterChainAndThenSynchronizeWithNewPeer() throws InterruptedException {
        Blockchain b1 = new BlockChainBuilder().ofSize(30, false);
        Blockchain b2 = BlockChainBuilder.copyAndExtend(b1, 43, false);
        Blockchain b3 = BlockChainBuilder.copyAndExtend(b2, 7, false);

        SimpleAsyncNode node1 = SimpleAsyncNode.createNode(b1, SyncConfiguration.IMMEDIATE_FOR_TESTING);
        SimpleAsyncNode node2 = SimpleAsyncNode.createNode(b2, SyncConfiguration.IMMEDIATE_FOR_TESTING);
        SimpleAsyncNode node3 = SimpleAsyncNode.createNode(b3, SyncConfiguration.IMMEDIATE_FOR_TESTING);

        Assertions.assertEquals(30, node1.getBestBlock().getNumber());
        Assertions.assertEquals(73, node2.getBestBlock().getNumber());
        Assertions.assertEquals(80, node3.getBestBlock().getNumber());

        node1.sendFullStatusTo(node2);
        // receive status, do nothing
        node2.waitExactlyNTasksWithTimeout(1);

        Assertions.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assertions.assertTrue(node2.getSyncProcessor().getExpectedResponses().isEmpty());

        Assertions.assertEquals(30, node1.getBestBlock().getNumber());
        Assertions.assertEquals(73, node2.getBestBlock().getNumber());
        Assertions.assertEquals(80, node3.getBestBlock().getNumber());

        node3.sendFullStatusTo(node2);
        // sync setup
        node2.waitUntilNTasksWithTimeout(SyncUtils.syncSetupRequests(80, 73, SyncConfiguration.IMMEDIATE_FOR_TESTING));
        // synchronize 7 new blocks from node 3
        node2.waitExactlyNTasksWithTimeout(7);

        Assertions.assertEquals(30, node1.getBestBlock().getNumber());
        Assertions.assertEquals(80, node2.getBestBlock().getNumber());
        Assertions.assertEquals(80, node3.getBestBlock().getNumber());
        Assertions.assertEquals(node2.getBestBlock().getHash(), node3.getBestBlock().getHash());

        Assertions.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assertions.assertTrue(node2.getSyncProcessor().getExpectedResponses().isEmpty());
        Assertions.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());

        node1.joinWithTimeout();
        node2.joinWithTimeout();
        node3.joinWithTimeout();

        Assertions.assertFalse(node1.getSyncProcessor().isSyncing());
        Assertions.assertFalse(node2.getSyncProcessor().isSyncing());
        Assertions.assertFalse(node3.getSyncProcessor().isSyncing());
    }

    @Disabled
    public void ignoreNewBlockHashesWhenSyncing() {
        Blockchain b1 = new BlockChainBuilder().ofSize(30, false);
        Blockchain b2 = BlockChainBuilder.copyAndExtend(b1, 1, false);

        SimpleAsyncNode node1 = SimpleAsyncNode.createNode(b1, SyncConfiguration.IMMEDIATE_FOR_TESTING);
        SimpleAsyncNode node2 = SimpleAsyncNode.createNode(b2, SyncConfiguration.IMMEDIATE_FOR_TESTING);
        SimpleAsyncNode node3 = SimpleAsyncNode.createNodeWithBlockChainBuilder(0);

        Assertions.assertEquals(30, node1.getBestBlock().getNumber());
        Assertions.assertEquals(31, node2.getBestBlock().getNumber());
        Assertions.assertEquals(0, node3.getBestBlock().getNumber());

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

        Assertions.assertEquals(30, node1.getBestBlock().getNumber());
        Assertions.assertEquals(31, node2.getBestBlock().getNumber());
        Assertions.assertEquals(30, node3.getBestBlock().getNumber());

        Assertions.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assertions.assertTrue(node2.getSyncProcessor().getExpectedResponses().isEmpty());
        Assertions.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());

        node1.joinWithTimeout();
        node2.joinWithTimeout();
        node3.joinWithTimeout();

        Assertions.assertFalse(node1.getSyncProcessor().isSyncing());
        Assertions.assertFalse(node2.getSyncProcessor().isSyncing());
        Assertions.assertFalse(node3.getSyncProcessor().isSyncing());
    }

    @Disabled
    public void acceptNewBlockHashWhenNotSyncing() {
        Blockchain b1 = new BlockChainBuilder().ofSize(30, false);
        Blockchain b2 = BlockChainBuilder.copyAndExtend(b1, 1, false);

        SimpleAsyncNode node1 = SimpleAsyncNode.createNode(b1, SyncConfiguration.IMMEDIATE_FOR_TESTING);
        SimpleAsyncNode node2 = SimpleAsyncNode.createNode(b2, SyncConfiguration.IMMEDIATE_FOR_TESTING);
        SimpleAsyncNode node3 = SimpleAsyncNode.createNodeWithBlockChainBuilder(0);

        Assertions.assertEquals(30, node1.getBestBlock().getNumber());
        Assertions.assertEquals(31, node2.getBestBlock().getNumber());
        Assertions.assertEquals(0, node3.getBestBlock().getNumber());

        node1.sendFullStatusTo(node3);
        // sync setup
        node3.waitUntilNTasksWithTimeout(SyncUtils.syncSetupRequests(30, 0, SyncConfiguration.IMMEDIATE_FOR_TESTING));
        // synchronize 30 new blocks from node 1
        node3.waitExactlyNTasksWithTimeout(30);

        Assertions.assertEquals(30, node1.getBestBlock().getNumber());
        Assertions.assertEquals(31, node2.getBestBlock().getNumber());
        Assertions.assertEquals(30, node3.getBestBlock().getNumber());

        // receive the hash of a better block than node1's best
        // after syncing with node1
        node3.receiveMessageFrom(node2, new NewBlockHashMessage(node2.getBestBlock().getHash().getBytes()));
        // receive block hash, then receive block
        node3.waitExactlyNTasksWithTimeout(2);

        Assertions.assertEquals(30, node1.getBestBlock().getNumber());
        Assertions.assertEquals(31, node2.getBestBlock().getNumber());
        Assertions.assertEquals(31, node3.getBestBlock().getNumber());

        Assertions.assertTrue(node1.getSyncProcessor().getExpectedResponses().isEmpty());
        Assertions.assertTrue(node2.getSyncProcessor().getExpectedResponses().isEmpty());
        Assertions.assertTrue(node3.getSyncProcessor().getExpectedResponses().isEmpty());

        node1.joinWithTimeout();
        node2.joinWithTimeout();
        node3.joinWithTimeout();

        Assertions.assertFalse(node1.getSyncProcessor().isSyncing());
        Assertions.assertFalse(node2.getSyncProcessor().isSyncing());
        Assertions.assertFalse(node3.getSyncProcessor().isSyncing());
    }
}
