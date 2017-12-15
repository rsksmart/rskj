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
import org.ethereum.config.SystemProperties;
import org.ethereum.net.rlpx.Node;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.spongycastle.util.encoders.Hex;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by mario on 20/02/17.
 */
public class NodeManagerTest {

    private static final String NODE_ID_1 = "826fbe97bc03c7c09d7b7d05b871282d8ac93d4446d44b55566333b240dd06260a9505f0fd3247e63d84d557f79bb63691710e40d4d9fc39f3bfd5397bcea065";
    private static final String NODE_ID_2 = "3c7931f323989425a1e56164043af0dff567f33df8c67d4c6918647535f88798d54bc864b936d8c77d4096e8b8485b6061b0d0d2b708cd9154e6dcf981533261";
    private static final String NODE_ID_3 = "e229918d45c131e130c91c4ea51c97ab4f66cfbd0437b35c92392b5c2b3d44b28ea15b84a262459437c955f6cc7f10ad1290132d3fc866bfaf4115eac0e8e860";


    @InjectMocks
    private NodeManager nodeManager;

    @Mock
    private PeerExplorer peerExplorer;

    @Mock
    private SystemProperties config;

    @Before
    public void initMocks(){
        nodeManager = new NodeManager();
        peerExplorer = Mockito.mock(PeerExplorer.class);
        config = Mockito.mock(SystemProperties.class);
        MockitoAnnotations.initMocks(this);

        Mockito.when(config.nodeId()).thenReturn(Hex.decode(NODE_ID_1));
        Mockito.when(config.getExternalIp()).thenReturn("127.0.0.1");
        Mockito.when(config.listenPort()).thenReturn(8080);
    }

    @Test
    public void getNodesPeerDiscoveryDisable() {
        List<Node> activePeers = new ArrayList<>();
        activePeers.add(new Node(Hex.decode(NODE_ID_2), "127.0.0.2", 8081));

        List<Node> bootNodes = new ArrayList<>();
        bootNodes.add(new Node(Hex.decode(NODE_ID_3), "127.0.0.3", 8083));

        Mockito.when(config.peerActive()).thenReturn(activePeers);
        Mockito.when(peerExplorer.getNodes()).thenReturn(bootNodes);
        Mockito.when(config.peerDiscovery()).thenReturn(false);

        nodeManager.init();

        Set<String> nodesInUse = new HashSet<>();

        List<NodeHandler> availableNodes = nodeManager.getNodes(nodesInUse);

        Assert.assertEquals(1, availableNodes.size());
        Assert.assertEquals(NODE_ID_2, availableNodes.get(0).getNode().getHexId());

        //With nodes in use
        nodesInUse.add(NODE_ID_2);
        availableNodes = nodeManager.getNodes(nodesInUse);
        Assert.assertEquals(0, availableNodes.size());

    }

    @Test
    public void getNodesPeerDiscoveryEnableNoPeersFound() {
        List<Node> activePeers = new ArrayList<>();
        List<Node> bootNodes = new ArrayList<>();

        Mockito.when(config.peerActive()).thenReturn(activePeers);
        Mockito.when(peerExplorer.getNodes()).thenReturn(bootNodes);
        Mockito.when(config.peerDiscovery()).thenReturn(true);

        nodeManager.init();

        Set<String> nodesInUse = new HashSet<>();

        List<NodeHandler> availableNodes = nodeManager.getNodes(nodesInUse);

        Assert.assertEquals(0, availableNodes.size());

    }

    @Test
    public void getNodesPeerDiscoveryEnable() {
        List<Node> activePeers = new ArrayList<>();
        activePeers.add(new Node(Hex.decode(NODE_ID_2), "127.0.0.2", 8081));

        List<Node> bootNodes = new ArrayList<>();
        bootNodes.add(new Node(Hex.decode(NODE_ID_3), "127.0.0.3", 8083));

        Mockito.when(config.peerActive()).thenReturn(activePeers);
        Mockito.when(peerExplorer.getNodes()).thenReturn(bootNodes);
        Mockito.when(config.peerDiscovery()).thenReturn(true);

        nodeManager.init();

        Set<String> nodesInUse = new HashSet<>();

        List<NodeHandler> availableNodes = nodeManager.getNodes(nodesInUse);

        Assert.assertEquals(2, availableNodes.size());
    }

}
