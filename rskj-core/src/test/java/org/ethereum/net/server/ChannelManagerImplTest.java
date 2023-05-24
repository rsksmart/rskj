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

package org.ethereum.net.server;

import co.rsk.config.RskSystemProperties;
import co.rsk.config.TestSystemProperties;
import co.rsk.crypto.Keccak256;
import co.rsk.net.NodeID;
import org.ethereum.TestUtils;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.net.NodeManager;
import org.ethereum.sync.SyncPool;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * @author Roman Mandeleil
 * @since 15.10.2014
 */
class ChannelManagerImplTest {


    @Test
    void getNumberOfPeersToSendStatusTo() {
        ChannelManagerImpl channelManagerImpl = new ChannelManagerImpl(new TestSystemProperties(), null, null);;

        assertEquals(1, channelManagerImpl.getNumberOfPeersToSendStatusTo(1));
        assertEquals(2, channelManagerImpl.getNumberOfPeersToSendStatusTo(2));
        assertEquals(3, channelManagerImpl.getNumberOfPeersToSendStatusTo(3));
        assertEquals(3, channelManagerImpl.getNumberOfPeersToSendStatusTo(5));
        assertEquals(3, channelManagerImpl.getNumberOfPeersToSendStatusTo(9));
        assertEquals(3, channelManagerImpl.getNumberOfPeersToSendStatusTo(12));
        assertEquals(4, channelManagerImpl.getNumberOfPeersToSendStatusTo(20));
        assertEquals(5, channelManagerImpl.getNumberOfPeersToSendStatusTo(25));
        assertEquals(10, channelManagerImpl.getNumberOfPeersToSendStatusTo(1000));
    }

    @Test
    void blockAddressIsAvailable() throws UnknownHostException {
        ChannelManagerImpl channelManagerImpl = new ChannelManagerImpl(new TestSystemProperties(), null, null);;
        Assertions.assertTrue(channelManagerImpl.isAddressBlockAvailable(InetAddress.getLocalHost()));
    }

    @Test
    void blockAddressIsNotAvailable() {
        TestSystemProperties config = mock(TestSystemProperties.class);
        when(config.maxConnectionsAllowed()).thenReturn(1);
        when(config.networkCIDR()).thenReturn(32);

        SyncPool syncPool = mock(SyncPool.class);
        ChannelManagerImpl channelManager = new ChannelManagerImpl(config, syncPool, null);

        String remoteId = "remoteId";
        NodeManager nodeManager = new NodeManager(null, config);

        Channel peer = spy(new Channel(null, null, nodeManager, null, null, null, remoteId));
        peer.setInetSocketAddress(new InetSocketAddress("127.0.0.1",5554));
        peer.setNode(new NodeID(TestUtils.generatePeerId("peer")).getID());
        when(peer.isProtocolsInitialized()).thenReturn(true);
        when(peer.isActive()).thenReturn(true);
        when(peer.isUsingNewProtocol()).thenReturn(true);

        Channel otherPeer = new Channel(null, null, nodeManager, null, null, null, remoteId);
        otherPeer.setInetSocketAddress(new InetSocketAddress("127.0.0.1",5554));

        channelManager.add(peer);
        channelManager.tryProcessNewPeers();

        Assertions.assertFalse(channelManager.isAddressBlockAvailable(otherPeer.getInetSocketAddress().getAddress()));
    }

    @Test
    void broadcastBlock() {
        ChannelManager target = new ChannelManagerImpl(mock(RskSystemProperties.class), mock(SyncPool.class), null);

        Block block = mock(Block.class);
        when(block.getHash()).thenReturn(new Keccak256(new byte[32]));
        Set<NodeID> nodeIds = target.broadcastBlock(block);

        assertTrue(nodeIds.isEmpty());
    }

    @Test
    void broadcastTransactions_broadcastToAllActivePeers() {
        final Transaction transaction = mock(Transaction.class);
        when(transaction.getHash()).thenReturn(TestUtils.generateHash("txHash"));
        final List<Transaction> transactions = Collections.singletonList(transaction);
        final Map<NodeID,Channel> activePeers = peersForTests(2);
        final ChannelManager channelManager = new ChannelManagerImpl(mock(RskSystemProperties.class), mock(SyncPool.class), null);
        channelManager.setActivePeers(activePeers);

        final Set<NodeID> broadcastedTo = channelManager.broadcastTransactions(transactions, Collections.emptySet());

        Assertions.assertTrue(activePeers.keySet().stream().allMatch(activePeer -> broadcastedTo.contains(activePeer)));
        Assertions.assertEquals(2, broadcastedTo.size());
    }

    @Test
    void broadcastTransactions_skipSender() {
        final Transaction transaction = mock(Transaction.class);
        when(transaction.getHash()).thenReturn(TestUtils.generateHash("txHash"));
        final List<Transaction> transactions = Collections.singletonList(transaction);
        final Map<NodeID,Channel> activePeers = peersForTests(2);
        final Channel sender = mock(Channel.class);
        when(sender.getNodeId()).thenReturn(new NodeID(TestUtils.generatePeerId("sender")));
        activePeers.put(sender.getNodeId(), sender);
        final ChannelManager channelManager = new ChannelManagerImpl(mock(RskSystemProperties.class), mock(SyncPool.class), null);
        channelManager.setActivePeers(activePeers);

        final Set<NodeID> broadcastedNodeIDS = channelManager.broadcastTransactions(transactions, Collections.singleton(sender.getNodeId()));

        broadcastedNodeIDS.forEach(broadcastedNodeID -> Assertions.assertTrue(activePeers.keySet().contains(broadcastedNodeID) && !broadcastedNodeID.equals(sender.getNodeId())));
        Assertions.assertEquals(2, broadcastedNodeIDS.size());
    }

    @Test
    void broadcastTransaction_broadcastToAllActivePeers() {
        final Transaction transaction = mock(Transaction.class);
        when(transaction.getHash()).thenReturn(TestUtils.generateHash("txHash"));
        final Map<NodeID,Channel> activePeers = peersForTests(2);
        final ChannelManager channelManager = new ChannelManagerImpl(mock(RskSystemProperties.class), mock(SyncPool.class), null);
        channelManager.setActivePeers(activePeers);

        final Set<NodeID> broadcastedTo = channelManager.broadcastTransaction(transaction, Collections.emptySet());

        Assertions.assertTrue(activePeers.keySet().stream().allMatch(activePeer -> broadcastedTo.contains(activePeer)));
        Assertions.assertEquals(2, broadcastedTo.size());
    }

    public Map<NodeID,Channel> peersForTests(int count) {
        Map<NodeID,Channel> peers = new ConcurrentHashMap<>();
        TestSystemProperties config = mock(TestSystemProperties.class);
        when(config.maxConnectionsAllowed()).thenReturn(1);
        when(config.networkCIDR()).thenReturn(32);

        for(int i  = 0; i < count; i++) {
            Channel peer = mock(Channel.class);
            when(peer.getNodeId()).thenReturn(new NodeID(TestUtils.generatePeerId("peer"+i)));
            peers.put(peer.getNodeId(),peer);
        }

        return peers;
    }

    @Test
    void testLogActivePeers() {
        final Map<NodeID,Channel> activePeers = peersForTests(2);
        final ChannelManagerImpl channelManager = new ChannelManagerImpl(mock(RskSystemProperties.class), mock(SyncPool.class), null);
        channelManager.setActivePeers(activePeers);

        Logger logger = mock(Logger.class);
        TestUtils.setFinalStatic(channelManager, "logger", logger);
        when(logger.isDebugEnabled()).thenReturn(true);

        LongSupplier timeLastLoggedPeersSupplier = () -> TestUtils.getInternalState(channelManager, "timeLastLoggedPeers");

        // not enough time passed to log peers again, timeLastLoggedPeers should remain intact
        long timeLastLoggedPeers = timeLastLoggedPeersSupplier.getAsLong();
        channelManager.logActivePeers(System.currentTimeMillis());
        Assertions.assertEquals(timeLastLoggedPeers, timeLastLoggedPeersSupplier.getAsLong());

        // fake time passed for peers to be logged, timeLastLoggedPeers should be updated
        long runTime = System.currentTimeMillis() + Duration.ofSeconds(61).toMillis();
        channelManager.logActivePeers(runTime);
        Assertions.assertEquals(runTime, timeLastLoggedPeersSupplier.getAsLong());
    }
}
