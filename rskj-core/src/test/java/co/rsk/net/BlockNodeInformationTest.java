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

import org.ethereum.db.ByteArrayWrapper;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Set;

public class BlockNodeInformationTest {

    // createBlockHash is a convenience function to create a ByteArrayWrapper wrapping an int.
    private ByteArrayWrapper createBlockHash(int i) {
        return new ByteArrayWrapper(ByteBuffer.allocate(4).putInt(i).array());
    }

    // createNodeID is a convenience function to create a NodeID based on an int.
    private NodeID createNodeID(int i) {
        return new NodeID(ByteBuffer.allocate(4).putInt(i).array());
    }

    @Test
    public void nodeEvictionPolicy() {
        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final ByteArrayWrapper block = createBlockHash(10);

        // Add a few nodes, not exceeding the node limit.
        // These nodes should contain the block when we call getBlocksByNode
        for (int i = 0; i < 30; i++) {
            final NodeID node = createNodeID(i);
            nodeInformation.addBlockToNode(block, node);
        }

        Assert.assertTrue(nodeInformation.getBlocksByNode(createNodeID(10)).contains(block));
        Assert.assertTrue(nodeInformation.getBlocksByNode(createNodeID(20)).contains(block));
        Assert.assertFalse(nodeInformation.getBlocksByNode(createNodeID(80)).contains(block));

        // Add more nodes, exceeding the node limit. The previous nodes should be evicted.
        // Except for node 10, which is being constantly accessed.
        for (int i = 30; i < 100; i++) {
            final NodeID node = createNodeID(i);
            nodeInformation.addBlockToNode(block, node);
            nodeInformation.getBlocksByNode(createNodeID(10));
        }

        Assert.assertTrue(nodeInformation.getBlocksByNode(createNodeID(10)).contains(block));

        Assert.assertFalse(nodeInformation.getBlocksByNode(createNodeID(20)).contains(block));
        Assert.assertFalse(nodeInformation.getBlocksByNode(createNodeID(210)).contains(block));

        Assert.assertTrue(nodeInformation.getBlocksByNode(createNodeID(80)).contains(block));
    }

    @Test
    public void blockEvictionPolicy() {
        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final NodeID nodeID1 = new NodeID(new byte[]{2});

        // Add a few blocks, without exceeding the block limit. NodeID1 should contain them all.
        for (int i = 0; i < 500; i++) {
            final ByteArrayWrapper hash1 = createBlockHash(i);
            nodeInformation.addBlockToNode(hash1, nodeID1);
        }

        Assert.assertTrue(nodeInformation.getNodesByBlock(createBlockHash(15)).contains(nodeID1));
        Assert.assertTrue(nodeInformation.getNodesByBlock(createBlockHash(200)).contains(nodeID1));

        Assert.assertTrue(nodeInformation.getBlocksByNode(nodeID1).contains(createBlockHash(15)));
        Assert.assertTrue(nodeInformation.getBlocksByNode(nodeID1).contains(createBlockHash(300)));

        // Add more blocks, exceeding MAX_NODES. All previous blocks should be evicted.
        // Except from block 10, which is being constantly accessed.
        for (int i = 500; i < 2000; i++) {
            final ByteArrayWrapper hash1 = createBlockHash(i);
            nodeInformation.addBlockToNode(hash1, nodeID1);

            nodeInformation.getNodesByBlock(createBlockHash(10));
        }

        Assert.assertFalse(nodeInformation.getNodesByBlock(createBlockHash(1)).contains(nodeID1));
        Assert.assertFalse(nodeInformation.getNodesByBlock(createBlockHash(700)).contains(nodeID1));
        Assert.assertFalse(nodeInformation.getNodesByBlock(createBlockHash(200)).contains(nodeID1));

        Assert.assertTrue(nodeInformation.getNodesByBlock(createBlockHash(1900)).contains(nodeID1));
        Assert.assertTrue(nodeInformation.getNodesByBlock(createBlockHash(10)).contains(nodeID1));

        Assert.assertFalse(nodeInformation.getBlocksByNode(nodeID1).contains(createBlockHash(25)));
        Assert.assertFalse(nodeInformation.getBlocksByNode(nodeID1).contains(createBlockHash(70)));

        Assert.assertTrue(nodeInformation.getBlocksByNode(nodeID1).contains(createBlockHash(1901)));
    }

    @Test
    public void getIsEmptyIfNotPresent() {
        final BlockNodeInformation nodeInformation = new BlockNodeInformation();

        Assert.assertTrue(nodeInformation.getBlocksByNode(new NodeID(new byte[]{})).size() == 0);
        Assert.assertTrue(nodeInformation.getNodesByBlock(new ByteArrayWrapper(new byte[]{})).size() == 0);
        Assert.assertTrue(nodeInformation.getBlocksByNode(new byte[]{}).size() == 0);
        Assert.assertTrue(nodeInformation.getNodesByBlock(new byte[]{}).size() == 0);
    }

    @Test
    public void getIsNotEmptyIfPresent() {
        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final ByteArrayWrapper hash1 = new ByteArrayWrapper(new byte[]{1});
        final NodeID nodeID1 = new NodeID(new byte[]{2});

        final ByteArrayWrapper badHash = new ByteArrayWrapper(new byte[]{3});
        final NodeID badNode = new NodeID(new byte[]{4});

        nodeInformation.addBlockToNode(hash1, nodeID1);
        Set<ByteArrayWrapper> blocks = nodeInformation.getBlocksByNode(nodeID1);
        Assert.assertTrue(blocks.size() == 1);
        Assert.assertTrue(blocks.contains(hash1));
        Assert.assertFalse(blocks.contains(badHash));

        blocks = nodeInformation.getBlocksByNode(badNode);
        Assert.assertTrue(blocks.size() == 0);

        Set<NodeID> nodes = nodeInformation.getNodesByBlock(hash1);
        Assert.assertTrue(nodes.size() == 1);
        Assert.assertTrue(nodes.contains(nodeID1));
        Assert.assertFalse(nodes.contains(badNode));

        nodes = nodeInformation.getNodesByBlock(badHash);
        Assert.assertTrue(nodes.size() == 0);
    }

    @Test
    public void twoNodesTwoBlocks() {
        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final ByteArrayWrapper hash1 = new ByteArrayWrapper(new byte[]{1});
        final NodeID nodeID1 = new NodeID(new byte[]{2});

        final ByteArrayWrapper hash2 = new ByteArrayWrapper(new byte[]{3});
        final NodeID nodeID2 = new NodeID(new byte[]{4});

        nodeInformation.addBlockToNode(hash1, nodeID1);
        nodeInformation.addBlockToNode(hash2, nodeID1);
        nodeInformation.addBlockToNode(hash2, nodeID2);

        Set<ByteArrayWrapper> blocks1 = nodeInformation.getBlocksByNode(nodeID1);
        Set<ByteArrayWrapper> blocks2 = nodeInformation.getBlocksByNode(nodeID2);
        Set<NodeID> nodes1 = nodeInformation.getNodesByBlock(hash1);
        Set<NodeID> nodes2 = nodeInformation.getNodesByBlock(hash2);

        Assert.assertTrue(blocks1.size() == 2);
        Assert.assertTrue(blocks2.size() == 1);
        Assert.assertTrue(nodes1.size() == 1);
        Assert.assertTrue(nodes2.size() == 2);

        Assert.assertTrue(blocks1.contains(hash1));
        Assert.assertTrue(blocks1.contains(hash2));
        Assert.assertTrue(blocks2.contains(hash2));

        Assert.assertTrue(nodes1.contains(nodeID1));
        Assert.assertTrue(nodes2.contains(nodeID1));
        Assert.assertTrue(nodes2.contains(nodeID2));
    }

}

