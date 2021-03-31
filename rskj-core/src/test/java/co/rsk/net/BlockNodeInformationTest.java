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

import co.rsk.crypto.Keccak256;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Set;

public class BlockNodeInformationTest {

    // createBlockHash is a convenience function to create a ByteArrayWrapper wrapping an int.
    private Keccak256 createBlockHash(int i) {
        return new Keccak256(ByteBuffer.allocate(32).putInt(i).array());
    }

    // createNodeID is a convenience function to create a NodeID based on an int.
    private NodeID createNodeID(int i) {
        return new NodeID(ByteBuffer.allocate(4).putInt(i).array());
    }

    @Test
    public void blockEvictionPolicy() {
        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final NodeID nodeID1 = new NodeID(new byte[]{2});

        // Add a few blocks, without exceeding the block limit. NodeID1 should contain them all.
        for (int i = 0; i < 500; i++) {
            final Keccak256 hash1 = createBlockHash(i);
            nodeInformation.addBlockToNode(hash1, nodeID1);
        }

        Assert.assertTrue(nodeInformation.getNodesByBlock(createBlockHash(15)).contains(nodeID1));
        Assert.assertTrue(nodeInformation.getNodesByBlock(createBlockHash(200)).contains(nodeID1));

        Assert.assertTrue(nodeInformation.getNodesByBlock(createBlockHash(15)).contains(nodeID1));
        Assert.assertTrue(nodeInformation.getNodesByBlock(createBlockHash(300)).contains(nodeID1));

        // Add more blocks, exceeding MAX_NODES. All previous blocks should be evicted.
        // Except from block 10, which is being constantly accessed.
        for (int i = 500; i < 2000; i++) {
            final Keccak256 hash1 = createBlockHash(i);
            nodeInformation.addBlockToNode(hash1, nodeID1);

            nodeInformation.getNodesByBlock(createBlockHash(10));
        }

        Assert.assertFalse(nodeInformation.getNodesByBlock(createBlockHash(1)).contains(nodeID1));
        Assert.assertFalse(nodeInformation.getNodesByBlock(createBlockHash(700)).contains(nodeID1));
        Assert.assertFalse(nodeInformation.getNodesByBlock(createBlockHash(200)).contains(nodeID1));

        Assert.assertTrue(nodeInformation.getNodesByBlock(createBlockHash(1900)).contains(nodeID1));
        Assert.assertTrue(nodeInformation.getNodesByBlock(createBlockHash(10)).contains(nodeID1));

        Assert.assertFalse(nodeInformation.getNodesByBlock(createBlockHash(25)).contains(nodeID1));
        Assert.assertFalse(nodeInformation.getNodesByBlock(createBlockHash(70)).contains(nodeID1));

        Assert.assertTrue(nodeInformation.getNodesByBlock(createBlockHash(1901)).contains(nodeID1));
    }

    @Test
    public void getIsEmptyIfNotPresent() {
        final BlockNodeInformation nodeInformation = new BlockNodeInformation();

        Assert.assertTrue(nodeInformation.getNodesByBlock(createBlockHash(0)).size() == 0);
        Assert.assertTrue(nodeInformation.getNodesByBlock(createBlockHash(0)).size() == 0);
    }

    @Test
    public void getIsNotEmptyIfPresent() {
        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final Keccak256 hash1 = createBlockHash(1);
        final NodeID nodeID1 = new NodeID(new byte[]{2});

        final Keccak256 badHash = createBlockHash(3);
        final NodeID badNode = new NodeID(new byte[]{4});

        nodeInformation.addBlockToNode(hash1, nodeID1);

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
        final Keccak256 hash1 = createBlockHash(1);
        final NodeID nodeID1 = new NodeID(new byte[]{2});

        final Keccak256 hash2 = createBlockHash(3);
        final NodeID nodeID2 = new NodeID(new byte[]{4});

        nodeInformation.addBlockToNode(hash1, nodeID1);
        nodeInformation.addBlockToNode(hash2, nodeID1);
        nodeInformation.addBlockToNode(hash2, nodeID2);

        Set<NodeID> nodes1 = nodeInformation.getNodesByBlock(hash1);
        Set<NodeID> nodes2 = nodeInformation.getNodesByBlock(hash2);

        Assert.assertTrue(nodes1.size() == 1);
        Assert.assertTrue(nodes2.size() == 2);


        Assert.assertTrue(nodes1.contains(nodeID1));
        Assert.assertTrue(nodes2.contains(nodeID1));
        Assert.assertTrue(nodes2.contains(nodeID2));
    }

}

