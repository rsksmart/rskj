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

import co.rsk.net.discovery.message.*;
import co.rsk.net.discovery.table.KademliaOptions;
import co.rsk.net.discovery.table.NodeDistanceTable;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.ethereum.crypto.ECKey;
import org.ethereum.net.rlpx.Node;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.bouncycastle.util.encoders.Hex;

import java.net.InetSocketAddress;
import java.util.*;

/**
 * Created by mario on 15/02/17.
 */
public class PeerExplorerTest {

    private static final String KEY_1 = "bd1d20e480dfb1c9c07ba0bc8cf9052f89923d38b5128c5dbfc18d4eea38261f";
    private static final String NODE_ID_1 = "826fbe97bc03c7c09d7b7d05b871282d8ac93d4446d44b55566333b240dd06260a9505f0fd3247e63d84d557f79bb63691710e40d4d9fc39f3bfd5397bcea065";
    private static final String HOST_1 = "localhost";
    private static final int PORT_1 = 44035;

    private static final String KEY_2 = "bd2d20e480dfb1c9c07ba0bc8cf9052f89923d38b5128c5dbfc18d4eea38262f";
    private static final String NODE_ID_2 = "3c7931f323989425a1e56164043af0dff567f33df8c67d4c6918647535f88798d54bc864b936d8c77d4096e8b8485b6061b0d0d2b708cd9154e6dcf981533261";
    private static final String HOST_2 = "localhost";
    private static final int PORT_2 = 44036;

    private static final String KEY_3 = "bd3d20e480dfb1c9c07ba0bc8cf9052f89923d38b5128c5dbfc18d4eea38263f";
    private static final String NODE_ID_3 = "e229918d45c131e130c91c4ea51c97ab4f66cfbd0437b35c92392b5c2b3d44b28ea15b84a262459437c955f6cc7f10ad1290132d3fc866bfaf4115eac0e8e860";
    private static final String HOST_3 = "localhost";
    private static final int PORT_3 = 44037;

    private static final long TIMEOUT = 30000;
    private static final long UPDATE = 60000;
    private static final long CLEAN = 60000;

    private static final int NETWORK_ID1 = 1;
    private static final int NETWORK_ID2 = 2;

    @Test
    public void sendInitialMessageToNodesNoNodes() {
        Node node = new Node(new ECKey().getNodeId(), HOST_2, PORT_2);
        NodeDistanceTable distanceTable = new NodeDistanceTable(KademliaOptions.BINS, KademliaOptions.BUCKET_SIZE, node);
        PeerExplorer peerExplorer = new PeerExplorer(new ArrayList<>(), node, distanceTable, new ECKey(), TIMEOUT, UPDATE, CLEAN, NETWORK_ID1);

        peerExplorer.setUDPChannel(Mockito.mock(UDPChannel.class));

        Set<String> nodesWithMessage = peerExplorer.startConversationWithNewNodes();

        Assert.assertTrue(CollectionUtils.isEmpty(nodesWithMessage));

        peerExplorer = new PeerExplorer(null, node, distanceTable, new ECKey(), TIMEOUT, UPDATE, CLEAN, NETWORK_ID1);
        peerExplorer.setUDPChannel(Mockito.mock(UDPChannel.class));

        nodesWithMessage = peerExplorer.startConversationWithNewNodes();

        Assert.assertTrue(CollectionUtils.isEmpty(nodesWithMessage));
    }


    @Test
    public void sendInitialMessageToNodes() {
        List<String> nodes = new ArrayList<>();
        nodes.add("localhost:3306");
        nodes.add("localhost:3307");
        nodes.add("localhost:3306:abd");
        nodes.add("");
        nodes.add(null);

        Node node = new Node(new ECKey().getNodeId(), HOST_1, PORT_1);
        NodeDistanceTable distanceTable = new NodeDistanceTable(KademliaOptions.BINS, KademliaOptions.BUCKET_SIZE, node);

        PeerExplorer peerExplorer = new PeerExplorer(nodes, node, distanceTable, new ECKey(), TIMEOUT, UPDATE, CLEAN, NETWORK_ID1);

        UDPChannel channel = new UDPChannel(Mockito.mock(Channel.class), peerExplorer);
        peerExplorer.setUDPChannel(channel);

        Set<String> nodesWithMessage = peerExplorer.startConversationWithNewNodes();

        Assert.assertTrue(CollectionUtils.size(nodesWithMessage) == 2);
        Assert.assertTrue(nodesWithMessage.contains("localhost/127.0.0.1:3307"));
        Assert.assertTrue(nodesWithMessage.contains("localhost/127.0.0.1:3306"));
    }

    @Test
    public void handlePingMessageFromDifferentNetwork() throws Exception {
        List<String> nodes = new ArrayList<>();

        ECKey key2 = ECKey.fromPrivate(Hex.decode(KEY_2)).decompress();

        Node node = new Node(key2.getNodeId(), HOST_2, PORT_2);
        NodeDistanceTable distanceTable = new NodeDistanceTable(KademliaOptions.BINS, KademliaOptions.BUCKET_SIZE, node);
        PeerExplorer peerExplorer = new PeerExplorer(nodes, node, distanceTable, key2, TIMEOUT, UPDATE, CLEAN, NETWORK_ID1);

        Channel internalChannel = Mockito.mock(Channel.class);
        UDPTestChannel channel = new UDPTestChannel(internalChannel, peerExplorer);
        ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        peerExplorer.setUDPChannel(channel);

        Assert.assertTrue(CollectionUtils.isEmpty(peerExplorer.getNodes()));

        ECKey key1 = ECKey.fromPrivate(Hex.decode(KEY_1)).decompress();
        String check = UUID.randomUUID().toString();
        PingPeerMessage pingPeerMessageWithDifferentNetwork = PingPeerMessage.create(HOST_1, PORT_1, check, key1, this.NETWORK_ID2);
        DiscoveryEvent incomingPingEvent = new DiscoveryEvent(pingPeerMessageWithDifferentNetwork, new InetSocketAddress(HOST_1, PORT_1));

        //A message is received
        channel.channelRead0(ctx, incomingPingEvent);
        //There should be no response, since they are from different networks.
        List<DiscoveryEvent> sentEvents = channel.getEventsWritten();
        Assert.assertEquals(0, sentEvents.size());
    }

    @Test
    public void handlePingMessage() throws Exception {
        List<String> nodes = new ArrayList<>();

        ECKey key2 = ECKey.fromPrivate(Hex.decode(KEY_2)).decompress();

        Node node = new Node(key2.getNodeId(), HOST_2, PORT_2);
        NodeDistanceTable distanceTable = new NodeDistanceTable(KademliaOptions.BINS, KademliaOptions.BUCKET_SIZE, node);

        PeerExplorer peerExplorer = new PeerExplorer(nodes, node, distanceTable, key2, TIMEOUT, UPDATE, CLEAN, NETWORK_ID1);

        Channel internalChannel = Mockito.mock(Channel.class);
        UDPTestChannel channel = new UDPTestChannel(internalChannel, peerExplorer);
        ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        peerExplorer.setUDPChannel(channel);

        Assert.assertTrue(CollectionUtils.isEmpty(peerExplorer.getNodes()));

        ECKey key1 = ECKey.fromPrivate(Hex.decode(KEY_1)).decompress();
        String check = UUID.randomUUID().toString();
        PingPeerMessage nodeMessage = PingPeerMessage.create(HOST_1, PORT_1, check, key1, this.NETWORK_ID1);
        DiscoveryEvent incomingPingEvent = new DiscoveryEvent(nodeMessage, new InetSocketAddress(HOST_1, PORT_1));

        //A message is received
        channel.channelRead0(ctx, incomingPingEvent);
        //As part of the ping response, a Ping and a Pong are sent to the sender.
        List<DiscoveryEvent> sentEvents = channel.getEventsWritten();
        Assert.assertEquals(2, sentEvents.size());
        DiscoveryEvent pongEvent = sentEvents.get(0);
        PongPeerMessage toSenderPong = (PongPeerMessage) pongEvent.getMessage();
        Assert.assertEquals(DiscoveryMessageType.PONG, toSenderPong.getMessageType());
        Assert.assertEquals(new InetSocketAddress(HOST_1, PORT_1), pongEvent.getAddress());

        DiscoveryEvent pingEvent = sentEvents.get(1);
        PingPeerMessage toSenderPing = (PingPeerMessage) pingEvent.getMessage();
        Assert.assertEquals(DiscoveryMessageType.PING, toSenderPing.getMessageType());
        Assert.assertEquals(new InetSocketAddress(HOST_1, PORT_1), pingEvent.getAddress());

        //After a pong returns from a node, when we receive a ping from that node, we only answer with a pong (no additional ping)
        PongPeerMessage pongResponseFromSender = PongPeerMessage.create(HOST_1, PORT_1, toSenderPing.getMessageId(), key1, NETWORK_ID1);
        DiscoveryEvent incomingPongEvent = new DiscoveryEvent(pongResponseFromSender, new InetSocketAddress(HOST_1, PORT_1));
        channel.channelRead0(ctx, incomingPongEvent);
        channel.clearEvents();
        channel.channelRead0(ctx, incomingPingEvent);
        sentEvents = channel.getEventsWritten();
        Assert.assertEquals(1, sentEvents.size());
        pongEvent = sentEvents.get(0);
        toSenderPong = (PongPeerMessage) pongEvent.getMessage();
        Assert.assertEquals(DiscoveryMessageType.PONG, toSenderPong.getMessageType());
        Assert.assertEquals(new InetSocketAddress(HOST_1, PORT_1), pongEvent.getAddress());
        Assert.assertEquals(NODE_ID_2, Hex.toHexString(toSenderPong.getKey().getNodeId()));
    }

    @Test
    public void handlePongMessage() throws Exception {
        List<String> nodes = new ArrayList<>();
        nodes.add(HOST_1 + ":" + PORT_1);
        nodes.add(HOST_3 + ":" + PORT_3);

        ECKey key1 = ECKey.fromPrivate(Hex.decode(KEY_1)).decompress();
        ECKey key2 = ECKey.fromPrivate(Hex.decode(KEY_2)).decompress();

        Node node = new Node(key2.getNodeId(), HOST_2, PORT_2);
        NodeDistanceTable distanceTable = new NodeDistanceTable(KademliaOptions.BINS, KademliaOptions.BUCKET_SIZE, node);
        PeerExplorer peerExplorer = new PeerExplorer(nodes, node, distanceTable, key2, TIMEOUT, UPDATE, CLEAN, NETWORK_ID1);

        Channel internalChannel = Mockito.mock(Channel.class);
        UDPTestChannel channel = new UDPTestChannel(internalChannel, peerExplorer);
        ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        peerExplorer.setUDPChannel(channel);
        Assert.assertTrue(CollectionUtils.isEmpty(peerExplorer.getNodes()));

        //A incoming pong for a Ping we did not sent.
        String check = UUID.randomUUID().toString();
        PongPeerMessage incomingPongMessage = PongPeerMessage.create(HOST_1, PORT_1, check, key1, NETWORK_ID1);
        DiscoveryEvent incomingPongEvent = new DiscoveryEvent(incomingPongMessage, new InetSocketAddress(HOST_1, PORT_1));
        channel.clearEvents();
        channel.channelRead0(ctx, incomingPongEvent);
        List<DiscoveryEvent> sentEvents = channel.getEventsWritten();
        Assert.assertEquals(0, sentEvents.size());
        Assert.assertEquals(0, peerExplorer.getNodes().size());

        //Now we send the ping first
        peerExplorer.startConversationWithNewNodes();
        sentEvents = channel.getEventsWritten();
        Assert.assertEquals(2, sentEvents.size());
        incomingPongMessage = PongPeerMessage.create(HOST_1, PORT_1, ((PingPeerMessage) sentEvents.get(0).getMessage()).getMessageId(), key1, NETWORK_ID1);
        incomingPongEvent = new DiscoveryEvent(incomingPongMessage, new InetSocketAddress(HOST_1, PORT_1));
        channel.clearEvents();
        List<Node> addedNodes = peerExplorer.getNodes();
        Assert.assertEquals(0, addedNodes.size());
        channel.channelRead0(ctx, incomingPongEvent);
        Assert.assertEquals(1, peerExplorer.getNodes().size());
        addedNodes = peerExplorer.getNodes();
        Assert.assertEquals(1, addedNodes.size());
    }

    @Test
    public void handleFindNodeMessage() throws Exception {
        List<String> nodes = new ArrayList<>();
        nodes.add(HOST_1 + ":" + PORT_1);
        nodes.add(HOST_3 + ":" + PORT_3);

        ECKey key1 = ECKey.fromPrivate(Hex.decode(KEY_1)).decompress();
        ECKey key2 = ECKey.fromPrivate(Hex.decode(KEY_2)).decompress();

        Node node = new Node(key2.getNodeId(), HOST_2, PORT_2);
        NodeDistanceTable distanceTable = new NodeDistanceTable(KademliaOptions.BINS, KademliaOptions.BUCKET_SIZE, node);

        PeerExplorer peerExplorer = new PeerExplorer(nodes, node, distanceTable, key2, TIMEOUT, UPDATE, CLEAN, NETWORK_ID1);

        Channel internalChannel = Mockito.mock(Channel.class);
        UDPTestChannel channel = new UDPTestChannel(internalChannel, peerExplorer);
        ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        peerExplorer.setUDPChannel(channel);

        //We try to handle a findNode message from an unkown sender, no message should be send as a response
        String check = UUID.randomUUID().toString();
        FindNodePeerMessage findNodePeerMessage = FindNodePeerMessage.create(key1.getNodeId(), check, key1, NETWORK_ID1);
        channel.clearEvents();
        channel.channelRead0(ctx, new DiscoveryEvent(findNodePeerMessage, new InetSocketAddress(HOST_1, PORT_1)));
        List<DiscoveryEvent> sentEvents = channel.getEventsWritten();
        Assert.assertEquals(0, sentEvents.size());

        //Now we send the ping first
        peerExplorer.startConversationWithNewNodes();
        PongPeerMessage incomingPongMessage = PongPeerMessage.create(HOST_1, PORT_1, ((PingPeerMessage) sentEvents.get(0).getMessage()).getMessageId(), key1, NETWORK_ID1);
        DiscoveryEvent incomingPongEvent = new DiscoveryEvent(incomingPongMessage, new InetSocketAddress(HOST_1, PORT_1));
        channel.channelRead0(ctx, incomingPongEvent);

        incomingPongMessage = PongPeerMessage.create(HOST_3, PORT_3, ((PingPeerMessage) sentEvents.get(0).getMessage()).getMessageId(), key1, NETWORK_ID1);
        incomingPongEvent = new DiscoveryEvent(incomingPongMessage, new InetSocketAddress(HOST_3, PORT_3));
        channel.channelRead0(ctx, incomingPongEvent);

        check = UUID.randomUUID().toString();
        findNodePeerMessage = FindNodePeerMessage.create(key1.getNodeId(), check, key1, NETWORK_ID1);
        channel.clearEvents();
        channel.channelRead0(ctx, new DiscoveryEvent(findNodePeerMessage, new InetSocketAddress(HOST_1, PORT_1)));
        sentEvents = channel.getEventsWritten();
        Assert.assertEquals(1, sentEvents.size());
        NeighborsPeerMessage neighborsPeerMessage = (NeighborsPeerMessage) sentEvents.get(0).getMessage();
        Assert.assertEquals(1, neighborsPeerMessage.getNodes().size());
    }

    @Test
    public void handleFindNodeMessageWithExtraNodes() throws Exception {
        List<String> nodes = new ArrayList<>();
        nodes.add(HOST_1 + ":" + PORT_1);
        nodes.add(HOST_3 + ":" + PORT_3);

        ECKey key1 = ECKey.fromPrivate(Hex.decode(KEY_1)).decompress();
        ECKey key2 = ECKey.fromPrivate(Hex.decode(KEY_2)).decompress();
        ECKey key3 = ECKey.fromPrivate(Hex.decode(KEY_3)).decompress();

        Node node = new Node(key2.getNodeId(), HOST_2, PORT_2);
        NodeDistanceTable distanceTable = new NodeDistanceTable(KademliaOptions.BINS, KademliaOptions.BUCKET_SIZE, node);
        PeerExplorer peerExplorer = new PeerExplorer(nodes, node, distanceTable, key2, TIMEOUT, UPDATE, CLEAN, NETWORK_ID1);

        Channel internalChannel = Mockito.mock(Channel.class);
        UDPTestChannel channel = new UDPTestChannel(internalChannel, peerExplorer);
        ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        peerExplorer.setUDPChannel(channel);
        Assert.assertTrue(CollectionUtils.isEmpty(peerExplorer.getNodes()));

        //we send the ping first
        peerExplorer.startConversationWithNewNodes();
        List<DiscoveryEvent> sentEvents = channel.getEventsWritten();
        Assert.assertEquals(2, sentEvents.size());
        PongPeerMessage incomingPongMessage1 = PongPeerMessage.create(HOST_1, PORT_1, ((PingPeerMessage) sentEvents.get(0).getMessage()).getMessageId(), key1, NETWORK_ID1);
        DiscoveryEvent incomingPongEvent1 = new DiscoveryEvent(incomingPongMessage1, new InetSocketAddress(HOST_1, PORT_1));
        PongPeerMessage incomingPongMessage2 = PongPeerMessage.create(HOST_3, PORT_3, ((PingPeerMessage) sentEvents.get(1).getMessage()).getMessageId(), key3, NETWORK_ID1);
        DiscoveryEvent incomingPongEvent2 = new DiscoveryEvent(incomingPongMessage2, new InetSocketAddress(HOST_3, PORT_3));

        channel.clearEvents();
        channel.channelRead0(ctx, incomingPongEvent1);
        channel.channelRead0(ctx, incomingPongEvent2);

        List<Node> foundNodes = peerExplorer.getNodes();
        Assert.assertEquals(2, foundNodes.size());
        Assert.assertEquals(NODE_ID_3, Hex.toHexString(foundNodes.get(0).getId().getID()));
        Assert.assertEquals(NODE_ID_1, Hex.toHexString(foundNodes.get(1).getId().getID()));

        String check = UUID.randomUUID().toString();
        FindNodePeerMessage findNodePeerMessage = FindNodePeerMessage.create(key1.getNodeId(), check, key1, NETWORK_ID1);
        channel.clearEvents();
        channel.channelRead0(ctx, new DiscoveryEvent(findNodePeerMessage, new InetSocketAddress(HOST_1, PORT_1)));
        sentEvents = channel.getEventsWritten();
        Assert.assertEquals(1, sentEvents.size());
        NeighborsPeerMessage neighborsPeerMessage = (NeighborsPeerMessage) sentEvents.get(0).getMessage();
        Assert.assertEquals(2, neighborsPeerMessage.getNodes().size());
        Assert.assertTrue(cotainsNode(NODE_ID_1, neighborsPeerMessage.getNodes()));
        Assert.assertTrue(cotainsNode(NODE_ID_3, neighborsPeerMessage.getNodes()));
    }

    private boolean cotainsNode(String nodeId, List<Node> nodes) {
        for(Node n : nodes) {
            if(StringUtils.equals(n.getHexId(), nodeId))
                return true;
        }
        return false;
    }

    @Test
    public void handleNeighbors() throws Exception {
        List<String> nodes = new ArrayList<>();
        nodes.add(HOST_1 + ":" + PORT_1);

        ECKey key1 = ECKey.fromPrivate(Hex.decode(KEY_1)).decompress();
        ECKey key2 = ECKey.fromPrivate(Hex.decode(KEY_2)).decompress();

        Node node1 = new Node(key1.getNodeId(), HOST_1, PORT_1);
        Node node2 = new Node(key2.getNodeId(), HOST_2, PORT_2);
        NodeDistanceTable distanceTable = new NodeDistanceTable(KademliaOptions.BINS, KademliaOptions.BUCKET_SIZE, node2);

        PeerExplorer peerExplorer = new PeerExplorer(nodes, node2, distanceTable, key2, TIMEOUT, UPDATE, CLEAN, NETWORK_ID1);

        Channel internalChannel = Mockito.mock(Channel.class);
        UDPTestChannel channel = new UDPTestChannel(internalChannel, peerExplorer);
        ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        peerExplorer.setUDPChannel(channel);
        Assert.assertTrue(CollectionUtils.isEmpty(peerExplorer.getNodes()));

        //We try to process a Message without previous connection
        List<Node> newNodes = new ArrayList<>();
        newNodes.add(new Node(Hex.decode(NODE_ID_3), HOST_3, PORT_3));
        NeighborsPeerMessage neighborsPeerMessage = NeighborsPeerMessage.create(newNodes, UUID.randomUUID().toString(), key1, NETWORK_ID1);
        DiscoveryEvent neighborsEvent = new DiscoveryEvent(neighborsPeerMessage, new InetSocketAddress(HOST_1, PORT_1));
        channel.clearEvents();
        channel.channelRead0(ctx, neighborsEvent);
        List<DiscoveryEvent> sentEvents = channel.getEventsWritten();
        Assert.assertEquals(0, sentEvents.size());

        //we establish a connection but we dont send the findnode message.
        peerExplorer.startConversationWithNewNodes();
        PongPeerMessage incomingPongMessage = PongPeerMessage.create(HOST_1, PORT_1, ((PingPeerMessage) sentEvents.get(0).getMessage()).getMessageId(), key1, NETWORK_ID1);
        DiscoveryEvent incomingPongEvent = new DiscoveryEvent(incomingPongMessage, new InetSocketAddress(HOST_1, PORT_1));
        channel.channelRead0(ctx, incomingPongEvent);
        channel.clearEvents();
        channel.channelRead0(ctx, neighborsEvent);
        sentEvents = channel.getEventsWritten();
        Assert.assertEquals(0, sentEvents.size());


        //We send a findNode first
        channel.clearEvents();
        peerExplorer.sendFindNode(node1);
        FindNodePeerMessage findNodePeerMessage = (FindNodePeerMessage) channel.getEventsWritten().get(0).getMessage();
        neighborsPeerMessage = NeighborsPeerMessage.create(newNodes, findNodePeerMessage.getMessageId(), key1, NETWORK_ID1);
        neighborsEvent = new DiscoveryEvent(neighborsPeerMessage, new InetSocketAddress(HOST_1, PORT_1));
        channel.clearEvents();
        channel.channelRead0(ctx, neighborsEvent);

        sentEvents = channel.getEventsWritten();
        Assert.assertEquals(1, sentEvents.size());
        DiscoveryEvent discoveryEvent = sentEvents.get(0);
        Assert.assertEquals(new InetSocketAddress(HOST_3, PORT_3), discoveryEvent.getAddress());
        Assert.assertEquals(DiscoveryMessageType.PING, discoveryEvent.getMessage().getMessageType());
    }

    @Test
    public void testCleanPeriod() throws InterruptedException, Exception{
        List<String> nodes = new ArrayList<>();
        nodes.add(HOST_1 + ":" + PORT_1);
        nodes.add(HOST_3 + ":" + PORT_3);

        ECKey key1 = ECKey.fromPrivate(Hex.decode(KEY_1)).decompress();
        ECKey key2 = ECKey.fromPrivate(Hex.decode(KEY_2)).decompress();
        ECKey key3 = ECKey.fromPrivate(Hex.decode(KEY_3)).decompress();

        Node node = new Node(key2.getNodeId(), HOST_2, PORT_2);
        NodeDistanceTable distanceTable = new NodeDistanceTable(1, 1, node);
        PeerExplorer peerExplorer = new PeerExplorer(nodes, node, distanceTable, key2, 199, UPDATE, CLEAN, NETWORK_ID1);

        Channel internalChannel = Mockito.mock(Channel.class);
        UDPTestChannel channel = new UDPTestChannel(internalChannel, peerExplorer);
        ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        peerExplorer.setUDPChannel(channel);
        Assert.assertTrue(CollectionUtils.isEmpty(peerExplorer.getNodes()));

        //A incoming pong for a Ping we did not sent.
        String check = UUID.randomUUID().toString();
        PongPeerMessage incomingPongMessage = PongPeerMessage.create(HOST_1, PORT_1, check, key1, NETWORK_ID1);
        DiscoveryEvent incomingPongEvent = new DiscoveryEvent(incomingPongMessage, new InetSocketAddress(HOST_1, PORT_1));
        channel.clearEvents();
        channel.channelRead0(ctx, incomingPongEvent);
        List<DiscoveryEvent> sentEvents = channel.getEventsWritten();
        Assert.assertEquals(0, sentEvents.size());
        Assert.assertEquals(0, peerExplorer.getNodes().size());

        //Now we send the ping first
        peerExplorer.startConversationWithNewNodes();
        sentEvents = channel.getEventsWritten();
        Assert.assertEquals(2, sentEvents.size());
        incomingPongMessage = PongPeerMessage.create(HOST_1, PORT_1, ((PingPeerMessage) sentEvents.get(0).getMessage()).getMessageId(), key1, NETWORK_ID1);
        incomingPongEvent = new DiscoveryEvent(incomingPongMessage, new InetSocketAddress(HOST_1, PORT_1));
        PongPeerMessage incomingPongMessage3 = PongPeerMessage.create(HOST_3, PORT_3, ((PingPeerMessage) sentEvents.get(1).getMessage()).getMessageId(), key3, NETWORK_ID1);
        DiscoveryEvent incomingPongEvent3 = new DiscoveryEvent(incomingPongMessage3, new InetSocketAddress(HOST_3, PORT_3));
        channel.clearEvents();
        List<Node> addedNodes = peerExplorer.getNodes();
        Assert.assertEquals(0, addedNodes.size());
        channel.channelRead0(ctx, incomingPongEvent);
        Assert.assertEquals(1, peerExplorer.getNodes().size());
        addedNodes = peerExplorer.getNodes();
        Assert.assertEquals(1, addedNodes.size());

        channel.channelRead0(ctx, incomingPongEvent3);
        Assert.assertEquals(1, peerExplorer.getNodes().size());
        addedNodes = peerExplorer.getNodes();
        Assert.assertEquals(1, addedNodes.size());

        Assert.assertEquals(1, peerExplorer.getChallengeManager().activeChallengesCount());
        Thread.sleep(200L);
        peerExplorer.clean();
        peerExplorer.clean();
        peerExplorer.clean();
        Assert.assertEquals(0, peerExplorer.getChallengeManager().activeChallengesCount());
    }

}
