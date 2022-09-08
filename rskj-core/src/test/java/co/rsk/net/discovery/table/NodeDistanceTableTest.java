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

package co.rsk.net.discovery.table;

import co.rsk.net.NodeID;
import org.ethereum.net.rlpx.Node;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.bouncycastle.util.encoders.Hex;

import java.util.List;

/**
 * Created by mario on 21/02/17.
 */
public class NodeDistanceTableTest {

    private static final String NODE_ID_1 = "826fbe97bc03c7c09d7b7d05b871282d8ac93d4446d44b55566333b240dd06260a9505f0fd3247e63d84d557f79bb63691710e40d4d9fc39f3bfd5397bcea065";
    private static final String NODE_ID_2 = "3c7931f323989425a1e56164043af0dff567f33df8c67d4c6918647535f88798d54bc864b936d8c77d4096e8b8485b6061b0d0d2b708cd9154e6dcf981533261";
    private static final String NODE_ID_3 = "e229918d45c131e130c91c4ea51c97ab4f66cfbd0437b35c92392b5c2b3d44b28ea15b84a262459437c955f6cc7f10ad1290132d3fc866bfaf4115eac0e8e860";
    private static final NodeID EMPTY_NODE_ID = new NodeID(new byte[0]);

    private static final String HOST = "localhost";
    private static final int PORT_1 = 40305;
    private static final int PORT_2 = 40306;
    private static final int PORT_3 = 40307;

    @Test
    public void creation() {
        Node localNode = new Node(Hex.decode(NODE_ID_1), HOST, PORT_1);
        NodeDistanceTable table = new NodeDistanceTable(KademliaOptions.BINS, KademliaOptions.BUCKET_SIZE, localNode);
        Assertions.assertTrue(table != null);
        Assertions.assertEquals(0, table.getClosestNodes(EMPTY_NODE_ID).size());
    }


    @Test
    public void addNode() {
        Node localNode = new Node(Hex.decode(NODE_ID_1), HOST, PORT_1);
        Node node2 = new Node(Hex.decode(NODE_ID_2), HOST, PORT_2);
        Node node3 = new Node(Hex.decode(NODE_ID_3), HOST, PORT_3);

        NodeDistanceTable table = new NodeDistanceTable(KademliaOptions.BINS, KademliaOptions.BUCKET_SIZE, localNode);

        OperationResult result = table.addNode(node3);
        Assertions.assertTrue(result.isSuccess());

        result = table.addNode(node2);
        Assertions.assertTrue(result.isSuccess());

        Assertions.assertEquals(2, table.getClosestNodes(EMPTY_NODE_ID).size());

        result = table.addNode(node2);
        Assertions.assertTrue(result.isSuccess());
        Assertions.assertEquals(2, table.getClosestNodes(EMPTY_NODE_ID).size());

        NodeDistanceTable smallerTable = new NodeDistanceTable(KademliaOptions.BINS, 1, localNode);

        //If a bucket is full, the operations fails (returns false) and we get a candidate for eviction
        result = smallerTable.addNode(node3);
        Assertions.assertTrue(result.isSuccess());
        Node sameDistanceNode = new Node(Hex.decode("00"), HOST, PORT_3);
        result = smallerTable.addNode(sameDistanceNode);
        Assertions.assertFalse(result.isSuccess());
        Assertions.assertEquals(NODE_ID_3, result.getAffectedEntry().getNode().getHexId());
    }

    @Test
    public void remove() {
        Node localNode = new Node(Hex.decode(NODE_ID_1), HOST, PORT_1);
        Node node2 = new Node(Hex.decode(NODE_ID_2), HOST, PORT_2);
        Node node3 = new Node(Hex.decode(NODE_ID_3), HOST, PORT_3);

        NodeDistanceTable table = new NodeDistanceTable(KademliaOptions.BINS, KademliaOptions.BUCKET_SIZE, localNode);

        OperationResult result = table.addNode(node2);
        Assertions.assertTrue(result.isSuccess());

        Assertions.assertEquals(1, table.getClosestNodes(EMPTY_NODE_ID).size());

        //Try to remove a node that was never added
        result = table.removeNode(node3);
        Assertions.assertFalse(result.isSuccess());
        Assertions.assertEquals(1, table.getClosestNodes(EMPTY_NODE_ID).size());

        //Add and remove node
        result = table.addNode(node3);
        Assertions.assertTrue(result.isSuccess());
        Assertions.assertEquals(2, table.getClosestNodes(EMPTY_NODE_ID).size());
        result = table.removeNode(node3);
        Assertions.assertTrue(result.isSuccess());
        Assertions.assertEquals(1, table.getClosestNodes(EMPTY_NODE_ID).size());

        //Leave the table empty
        result = table.removeNode(node2);
        Assertions.assertTrue(result.isSuccess());
        Assertions.assertEquals(0, table.getClosestNodes(EMPTY_NODE_ID).size());
    }

    @Test
    public void getCloseNodes() {
        Node node1 = new Node(Hex.decode(NODE_ID_1), HOST, PORT_1);
        Node node2 = new Node(Hex.decode(NODE_ID_2), HOST, PORT_2);
        Node node3 = new Node(Hex.decode(NODE_ID_3), HOST, PORT_3);

        NodeDistanceTable table = new NodeDistanceTable(KademliaOptions.BINS, KademliaOptions.BUCKET_SIZE, node1);

        Assertions.assertTrue(table.addNode(node1).isSuccess());
        Assertions.assertTrue(table.addNode(node2).isSuccess());
        Assertions.assertTrue(table.addNode(node3).isSuccess());

        List<Node> sortedNodes = table.getClosestNodes(node2.getId());

        Assertions.assertEquals(3, sortedNodes.size());

        DistanceCalculator calculator = new DistanceCalculator(KademliaOptions.BINS);

        int d1 = calculator.calculateDistance(node2.getId(), sortedNodes.get(0).getId());
        int d2 = calculator.calculateDistance(node2.getId(), sortedNodes.get(1).getId());
        int d3 = calculator.calculateDistance(node2.getId(), sortedNodes.get(2).getId());

        Assertions.assertTrue(d1 <= d2);
        Assertions.assertTrue(d2 <= d3);
    }

}
