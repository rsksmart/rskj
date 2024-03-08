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

package co.rsk.net.discovery;

import co.rsk.net.discovery.table.KademliaOptions;
import co.rsk.net.discovery.table.NodeDistanceTable;
import co.rsk.util.ExecState;
import co.rsk.scoring.PeerScoringManager;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.crypto.ECKey;
import org.ethereum.net.rlpx.Node;
import org.ethereum.net.server.Channel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import static org.mockito.Mockito.mock;

/**
 * Created by mario on 15/02/17.
 */
class UDPServerTest {

    private static final String KEY_1 = "bd1d20e480dfb1c9c07ba0bc8cf9052f89923d38b5128c5dbfc18d4eea38261f";
    private static final String NODE_ID_1 = "826fbe97bc03c7c09d7b7d05b871282d8ac93d4446d44b55566333b240dd06260a9505f0fd3247e63d84d557f79bb63691710e40d4d9fc39f3bfd5397bcea065";

    private static final String KEY_2 = "bd2d20e480dfb1c9c07ba0bc8cf9052f89923d38b5128c5dbfc18d4eea38262f";
    private static final String NODE_ID_2 = "3c7931f323989425a1e56164043af0dff567f33df8c67d4c6918647535f88798d54bc864b936d8c77d4096e8b8485b6061b0d0d2b708cd9154e6dcf981533261";

    private static final String KEY_3 = "bd3d20e480dfb1c9c07ba0bc8cf9052f89923d38b5128c5dbfc18d4eea38263f";
    private static final String NODE_ID_3 = "e229918d45c131e130c91c4ea51c97ab4f66cfbd0437b35c92392b5c2b3d44b28ea15b84a262459437c955f6cc7f10ad1290132d3fc866bfaf4115eac0e8e860";

    private static final String HOST = "localhost";
    private static final int PORT_1 = 40305;
    private static final int PORT_2 = 40306;
    private static final int PORT_3 = 40307;
    private static final int NETWORK_ID = 1;

    private static final long TIMEOUT = 30000;
    private static final long UPDATE = 60000;
    private static final long CLEAN = 60000;

    @Test
    @SuppressWarnings("squid:S2925") // Thread.sleep() used
    void port0DoesntCreateANewChannel() throws InterruptedException {
        UDPServer udpServer = new UDPServer(HOST, 0, mock(PeerExplorer.class));
        Channel channel = TestUtils.getInternalState(udpServer, "channel");
        udpServer.start();
        TimeUnit.SECONDS.sleep(2);
        Assertions.assertNull(channel);
    }

    @Test
    @SuppressWarnings("squid:S2925") // Thread.sleep() used
    void run3NodesFullTest() throws InterruptedException {
        ECKey key1 = ECKey.fromPrivate(Hex.decode(KEY_1)).decompress();
        ECKey key2 = ECKey.fromPrivate(Hex.decode(KEY_2)).decompress();
        ECKey key3 = ECKey.fromPrivate(Hex.decode(KEY_3)).decompress();

        List<String> node1BootNode = new ArrayList<>();
        node1BootNode.add(HOST + ":5555");
        node1BootNode.add(HOST + ":" + PORT_2);

        List<String> node2BootNode = new ArrayList<>();
        node2BootNode.add(HOST + ":" + PORT_3);

        List<String> node3BootNode = new ArrayList<>();

        Node node1 = new Node(key1.getNodeId(), HOST, PORT_1);
        Node node2 = new Node(key2.getNodeId(), HOST, PORT_2);
        Node node3 = new Node(key3.getNodeId(), HOST, PORT_3);

        NodeDistanceTable distanceTable1 = new NodeDistanceTable(KademliaOptions.BINS, KademliaOptions.BUCKET_SIZE, node1);
        NodeDistanceTable distanceTable2 = new NodeDistanceTable(KademliaOptions.BINS, KademliaOptions.BUCKET_SIZE, node2);
        NodeDistanceTable distanceTable3 = new NodeDistanceTable(KademliaOptions.BINS, KademliaOptions.BUCKET_SIZE, node3);

        PeerExplorer peerExplorer1 = new PeerExplorer(node1BootNode, node1, distanceTable1, key1, TIMEOUT, UPDATE, CLEAN, NETWORK_ID, mock(PeerScoringManager.class), true, -1);
        PeerExplorer peerExplorer2 = new PeerExplorer(node2BootNode, node2, distanceTable2, key2, TIMEOUT, UPDATE, CLEAN, NETWORK_ID, mock(PeerScoringManager.class), true, -1);
        PeerExplorer peerExplorer3 = new PeerExplorer(node3BootNode, node3, distanceTable3, key3, TIMEOUT, UPDATE, CLEAN, NETWORK_ID, mock(PeerScoringManager.class), true, -1);

        assertEquals(0, peerExplorer1.getNodes().size());
        assertEquals(0, peerExplorer2.getNodes().size());
        assertEquals(0, peerExplorer3.getNodes().size());

        UDPServer udpServer1 = new UDPServer(HOST, PORT_1, peerExplorer1);
        UDPServer udpServer2 = new UDPServer(HOST, PORT_2, peerExplorer2);
        UDPServer udpServer3 = new UDPServer(HOST, PORT_3, peerExplorer3);

        udpServer3.start();
        TimeUnit.SECONDS.sleep(2);
        peerExplorer3.update();
        peerExplorer3.clean();

        udpServer2.start();
        TimeUnit.SECONDS.sleep(2);
        peerExplorer2.update();
        peerExplorer2.clean();
        peerExplorer3.update();
        peerExplorer3.clean();

        udpServer1.start();
        TimeUnit.SECONDS.sleep(2);
        peerExplorer1.update();
        peerExplorer1.clean();
        peerExplorer2.update();
        peerExplorer2.clean();
        peerExplorer3.update();
        peerExplorer3.clean();

        TimeUnit.SECONDS.sleep(2);

        List<Node> foundNodes1 = peerExplorer1.getNodes();
        List<Node> foundNodes2 = peerExplorer2.getNodes();
        List<Node> foundNodes3 = peerExplorer3.getNodes();

        assertEquals(2, foundNodes1.size());
        assertEquals(2, foundNodes2.size());
        assertEquals(2, foundNodes3.size());

        udpServer1.stop();
        udpServer2.stop();
        udpServer3.stop();
        TimeUnit.SECONDS.sleep(5);

        assertTrue(checkNodeIds(foundNodes1, NODE_ID_2, NODE_ID_3));
        assertTrue(checkNodeIds(foundNodes2, NODE_ID_1, NODE_ID_3));
        assertTrue(checkNodeIds(foundNodes3, NODE_ID_2, NODE_ID_1));
    }

    @Test
    void startAndStopUDPServer() {
        //noinspection unchecked
        Function<Runnable, Thread> threadFactory = (Function<Runnable, Thread>) mock(Function.class);
        UDPServer udpServer = new UDPServer(HOST, PORT_1, mock(PeerExplorer.class), threadFactory);
        assertEquals(ExecState.CREATED, udpServer.getState());

        Thread thread = mock(Thread.class);
        doReturn(thread).when(threadFactory).apply(any());

        udpServer.start();
        assertEquals(ExecState.RUNNING, udpServer.getState());
        verify(thread, times(1)).start();

        udpServer.stop();
        assertEquals(ExecState.FINISHED, udpServer.getState());

        udpServer.start(); // re-start is not allowed
        assertEquals(ExecState.FINISHED, udpServer.getState());
        verify(thread, times(1)).start();
    }

    private boolean checkNodeIds(List<Node> nodes, String... idsToCheck) {
        boolean check = true;
        for (String id : idsToCheck) {
            if (!check) {
                return check;
            } else {
                check = false;
                for (Node n : nodes) {
                    if (StringUtils.equals(n.getHexId(), id))
                        check = true;
                }
            }
        }
        return check;
    }
}
