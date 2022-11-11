/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package org.ethereum.net;

import co.rsk.net.discovery.PeerExplorer;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.config.SystemProperties;
import org.ethereum.net.rlpx.Node;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.*;

/**
 * Created by mario on 20/02/17.
 */
class NodeManagerTest {

    private static final String NODE_ID_1 = "826fbe97bc03c7c09d7b7d05b871282d8ac93d4446d44b55566333b240dd06260a9505f0fd3247e63d84d557f79bb63691710e40d4d9fc39f3bfd5397bcea065";
    private static final String NODE_ID_2 = "3c7931f323989425a1e56164043af0dff567f33df8c67d4c6918647535f88798d54bc864b936d8c77d4096e8b8485b6061b0d0d2b708cd9154e6dcf981533261";
    private static final String NODE_ID_3 = "e229918d45c131e130c91c4ea51c97ab4f66cfbd0437b35c92392b5c2b3d44b28ea15b84a262459437c955f6cc7f10ad1290132d3fc866bfaf4115eac0e8e860";

    private PeerExplorer peerExplorer;
    private SystemProperties config;

    @BeforeEach
    void initMocks(){
        peerExplorer = Mockito.mock(PeerExplorer.class);
        config = Mockito.mock(SystemProperties.class);

        Mockito.when(config.nodeId()).thenReturn(Hex.decode(NODE_ID_1));
        Mockito.when(config.getPublicIp()).thenReturn("127.0.0.1");
        Mockito.when(config.getPeerPort()).thenReturn(8080);
    }

    @Test
    void getNodesPeerDiscoveryDisable() {
        List<Node> activePeers = new ArrayList<>();
        activePeers.add(new Node(Hex.decode(NODE_ID_2), "127.0.0.2", 8081));

        List<Node> bootNodes = new ArrayList<>();
        bootNodes.add(new Node(Hex.decode(NODE_ID_3), "127.0.0.3", 8083));

        Mockito.when(config.peerActive()).thenReturn(activePeers);
        Mockito.when(peerExplorer.getNodes()).thenReturn(bootNodes);
        Mockito.when(config.isPeerDiscoveryEnabled()).thenReturn(false);

        NodeManager nodeManager = new NodeManager(peerExplorer, config);

        Set<String> nodesInUse = new HashSet<>();

        List<NodeHandler> availableNodes = nodeManager.getNodes(nodesInUse);

        Assertions.assertEquals(1, availableNodes.size());
        Assertions.assertEquals(NODE_ID_2, availableNodes.get(0).getNode().getHexId());

        //With nodes in use
        nodesInUse.add(NODE_ID_2);
        availableNodes = nodeManager.getNodes(nodesInUse);
        Assertions.assertEquals(0, availableNodes.size());

    }

    @Test
    void getNodesPeerDiscoveryEnableNoPeersFound() {
        List<Node> activePeers = new ArrayList<>();
        List<Node> bootNodes = new ArrayList<>();

        Mockito.when(config.peerActive()).thenReturn(activePeers);
        Mockito.when(peerExplorer.getNodes()).thenReturn(bootNodes);
        Mockito.when(config.isPeerDiscoveryEnabled()).thenReturn(true);

        NodeManager nodeManager = new NodeManager(peerExplorer, config);

        Set<String> nodesInUse = new HashSet<>();

        List<NodeHandler> availableNodes = nodeManager.getNodes(nodesInUse);

        Assertions.assertEquals(0, availableNodes.size());

    }

    @Test
    void getNodesPeerDiscoveryEnable() {
        List<Node> activePeers = new ArrayList<>();
        activePeers.add(new Node(Hex.decode(NODE_ID_2), "127.0.0.2", 8081));

        List<Node> bootNodes = new ArrayList<>();
        bootNodes.add(new Node(Hex.decode(NODE_ID_3), "127.0.0.3", 8083));

        Mockito.when(config.peerActive()).thenReturn(activePeers);
        Mockito.when(peerExplorer.getNodes()).thenReturn(bootNodes);
        Mockito.when(config.isPeerDiscoveryEnabled()).thenReturn(true);

        NodeManager nodeManager = new NodeManager(peerExplorer, config);

        Set<String> nodesInUse = new HashSet<>();

        List<NodeHandler> availableNodes = nodeManager.getNodes(nodesInUse);

        Assertions.assertEquals(2, availableNodes.size());
    }

    @Test
    void purgeNodesTest() {
        Random random = new Random(NodeManagerTest.class.hashCode());
        Mockito.when(config.isPeerDiscoveryEnabled()).thenReturn(true);
        NodeManager nodeManager = new NodeManager(peerExplorer, config);
        Set<String> keys = new HashSet<>();
        for (int i = 0; i <= NodeManager.NODES_TRIM_THRESHOLD+1;i++) {
            byte[] nodeId =TestUtils.generateBytesFromRandom(random,32);
            Node node = new Node(nodeId, "127.0.0.1", 8080);
            keys.add(node.getHexId());
            nodeManager.getNodeStatistics(node);
        }
        Map<String, NodeHandler> nodeHandlerMap = TestUtils.getInternalState(nodeManager, "nodeHandlerMap");
        Assertions.assertTrue(nodeHandlerMap.size() <= NodeManager.NODES_TRIM_THRESHOLD);
    }

}
