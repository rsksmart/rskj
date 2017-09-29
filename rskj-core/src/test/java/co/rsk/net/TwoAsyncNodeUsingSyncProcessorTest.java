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
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * Created by ajlopez on 9/3/2017.
 */
public class TwoAsyncNodeUsingSyncProcessorTest {

    private static Block getGenesis() {
        final World world = new World();

        return world.getBlockChain().getBestBlock();
    }

    @Test
    public void buildBlockchainAndSynchronize() throws InterruptedException {
        SimpleAsyncNode node1 = SimpleAsyncNode.createNodeWithWorldBlockChain(100, false);
        SimpleAsyncNode node2 = SimpleAsyncNode.createNodeWithWorldBlockChain(0, false);

        node1.sendFullStatusTo(node2);
        // find connection point
        node1.waitUntilNTasksWithTimeout(11);
        // get blocks
        node1.waitExactlyNTasksWithTimeout(100);

        node1.joinWithTimeout();
        node2.joinWithTimeout();

        Assert.assertEquals(100, node1.getBestBlock().getNumber());
        Assert.assertEquals(100, node2.getBestBlock().getNumber());
        Assert.assertArrayEquals(node1.getBestBlock().getHash(), node2.getBestBlock().getHash());

        Assert.assertTrue(node1.getExpectedResponses().isEmpty());
        Assert.assertTrue(node2.getExpectedResponses().isEmpty());

        Assert.assertFalse(node1.getSyncProcessor().isPeerSyncing(node2.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().isPeerSyncing(node1.getNodeID()));
    }

    @Test
    public void buildBlockchainAndSynchronize400Blocks() throws InterruptedException {
        SimpleAsyncNode node1 = SimpleAsyncNode.createNodeWithWorldBlockChain(400, false);
        SimpleAsyncNode node2 = SimpleAsyncNode.createNodeWithWorldBlockChain(0, false);

        node1.sendFullStatusTo(node2);
        // find connection point
        node2.waitUntilNTasksWithTimeout(16);
        // get blocks
        node2.waitExactlyNTasksWithTimeout(400);

        node1.joinWithTimeout();
        node2.joinWithTimeout();

        Assert.assertEquals(400, node1.getBestBlock().getNumber());
        Assert.assertEquals(400, node2.getBestBlock().getNumber());
        Assert.assertArrayEquals(node1.getBestBlock().getHash(), node2.getBestBlock().getHash());

        Assert.assertTrue(node1.getExpectedResponses().isEmpty());
        Assert.assertTrue(node2.getExpectedResponses().isEmpty());

        Assert.assertFalse(node1.getSyncProcessor().isPeerSyncing(node2.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().isPeerSyncing(node1.getNodeID()));
    }

    @Test
    public void buildBlockchainWithUnclesAndSynchronize() throws InterruptedException {
        SimpleAsyncNode node1 = SimpleAsyncNode.createNodeWithWorldBlockChain(10, true);
        SimpleAsyncNode node2 = SimpleAsyncNode.createNodeWithWorldBlockChain(0, false);

        node1.sendFullStatusTo(node2);
        // find connection point
        node2.waitUntilNTasksWithTimeout(8);
        // get blocks
        node2.waitExactlyNTasksWithTimeout(10);

        node2.sendFullStatusTo(node1);

        node1.joinWithTimeout();
        node2.joinWithTimeout();

        Assert.assertEquals(10, node1.getBestBlock().getNumber());
        Assert.assertEquals(10, node2.getBestBlock().getNumber());
        Assert.assertArrayEquals(node1.getBestBlock().getHash(), node2.getBestBlock().getHash());

        Assert.assertTrue(node1.getExpectedResponses().isEmpty());
        Assert.assertTrue(node2.getExpectedResponses().isEmpty());

        Assert.assertFalse(node1.getSyncProcessor().isPeerSyncing(node2.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().isPeerSyncing(node1.getNodeID()));
    }

    @Test
    public void buildBlockchainPartialAndSynchronize() throws InterruptedException {
        SimpleAsyncNode node1 = SimpleAsyncNode.createNodeWithWorldBlockChain(0, false);
        SimpleAsyncNode node2 = SimpleAsyncNode.createNodeWithWorldBlockChain(0, false);

        List<Block> blocks = BlockGenerator.getBlockChain(getGenesis(), 10, 0, false, true);

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
        node2.waitUntilNTasksWithTimeout(7);
        // get blocks
        node2.waitExactlyNTasksWithTimeout(5);
        // drain node 1 for next test
        node1.waitExactlyNTasksWithTimeout(11);

        node2.sendFullStatusTo(node1);
        // receive status, do nothing
        node1.waitExactlyNTasksWithTimeout(1);

        node1.joinWithTimeout();
        node2.joinWithTimeout();

        Assert.assertEquals(10, node1.getBestBlock().getNumber());
        Assert.assertEquals(10, node2.getBestBlock().getNumber());
        Assert.assertArrayEquals(node1.getBestBlock().getHash(), node2.getBestBlock().getHash());

        Assert.assertTrue(node1.getExpectedResponses().isEmpty());
        Assert.assertTrue(node2.getExpectedResponses().isEmpty());

        Assert.assertFalse(node1.getSyncProcessor().isPeerSyncing(node2.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().isPeerSyncing(node1.getNodeID()));
    }

    @Test
    public void sendNewBlock() throws InterruptedException {
        SimpleAsyncNode node1 = SimpleAsyncNode.createNodeWithWorldBlockChain(1, false);
        SimpleAsyncNode node2 = SimpleAsyncNode.createNodeWithWorldBlockChain(0, false);

        node2.receiveMessageFrom(node1, new NewBlockHashMessage(node1.getBestBlock().getHash()));

        // send hash
        node2.waitUntilNTasksWithTimeout(1);
        // request header
        node1.waitExactlyNTasksWithTimeout(1);
        // respond header
        node2.waitExactlyNTasksWithTimeout(1);

        node1.joinWithTimeout();
        node2.joinWithTimeout();

        Assert.assertEquals(1, node1.getBestBlock().getNumber());
        Assert.assertEquals(1, node2.getBestBlock().getNumber());
        Assert.assertArrayEquals(node1.getBestBlock().getHash(), node2.getBestBlock().getHash());

        Assert.assertFalse(node1.getSyncProcessor().isPeerSyncing(node2.getNodeID()));
        Assert.assertFalse(node2.getSyncProcessor().isPeerSyncing(node1.getNodeID()));
    }
}
