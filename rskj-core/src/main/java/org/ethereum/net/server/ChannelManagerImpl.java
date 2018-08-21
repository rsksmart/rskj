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
import co.rsk.net.Metrics;
import co.rsk.net.NodeID;
import co.rsk.net.Status;
import co.rsk.net.eth.RskMessage;
import co.rsk.net.messages.*;
import co.rsk.scoring.InetAddressBlock;
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.collections4.map.LRUMap;
import org.ethereum.config.NodeFilter;
import org.ethereum.core.Block;
import org.ethereum.core.BlockIdentifier;
import org.ethereum.core.Transaction;
import org.ethereum.net.eth.message.EthMessage;
import org.ethereum.net.message.ReasonCode;
import org.ethereum.sync.SyncPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * @author Roman Mandeleil
 * @since 11.11.2014
 */
@Component("ChannelManager")
public class ChannelManagerImpl implements ChannelManager {

    private static final Logger logger = LoggerFactory.getLogger("net");

    // If the inbound peer connection was dropped by us with a reason message
    // then we ban that peer IP on any connections for some time to protect from
    // too active peers
    private static final int INBOUND_CONNECTION_BAN_TIMEOUT = 10 * 1000;
    private final Map<NodeID, Channel> activePeers = new ConcurrentHashMap<>();

    // Using a concurrent list
    // (the add and remove methods copy an internal array,
    // but the iterator directly use the internal array)
    private final List<Channel> newPeers = new CopyOnWriteArrayList<>();

    private final ScheduledExecutorService mainWorker;
    private final Map<InetAddress, Date> recentlyDisconnected = Collections.synchronizedMap(new LRUMap<>(500));

    private final SyncPool syncPool;
    private final NodeFilter trustedPeers;
    private final int maxActivePeers;
    private final int maxConnectionsPerBlock;
    private final int bitsToIgnore;

    @Autowired
    public ChannelManagerImpl(RskSystemProperties config, SyncPool syncPool) {
        this.mainWorker = Executors.newSingleThreadScheduledExecutor(target -> new Thread(target, "newPeersProcessor"));
        this.syncPool = syncPool;
        this.maxActivePeers = config.maxActivePeers();
        this.trustedPeers = config.peerTrusted();
        // TODO(lsebrie): move values to configuration
        this.maxConnectionsPerBlock = 5;
        this.bitsToIgnore = 24;
    }

    @Override
    public void start() {
        mainWorker.scheduleWithFixedDelay(() -> tryProcessNewPeers(), 0, 1, TimeUnit.SECONDS);
    }

    @Override
    public void stop() {
        mainWorker.shutdown();
    }

    private void tryProcessNewPeers() {
        if (newPeers.isEmpty()) {
            return;
        }

        try {
            processNewPeers();
        } catch (Exception e) {
            logger.error("Error", e);
        }
    }

    private void processNewPeers() {
        List<Channel> initialized = newPeers.stream().filter(Channel::isProtocolsInitialized).collect(Collectors.toList());
        initialized.forEach(channel -> {
            ReasonCode reason = getNewPeerDisconnectionReason(channel);
            if (reason != null) {
                disconnect(channel, reason);
            } else {
                 process(channel);
            }
        });
        newPeers.removeAll(initialized);
    }

    private ReasonCode getNewPeerDisconnectionReason(Channel channel) {
        if (activePeers.containsKey(channel.getNodeId())) {
            return ReasonCode.DUPLICATE_PEER;
        }

        if (!channel.isActive() &&
                activePeers.size() >= maxActivePeers &&
                !trustedPeers.accept(channel.getNode())) {
            return ReasonCode.TOO_MANY_PEERS;
        }

        return null;
    }

    private void disconnect(Channel peer, ReasonCode reason) {
        logger.debug("Disconnecting peer with reason {} : {}", reason, peer);
        peer.disconnect(reason);
        recentlyDisconnected.put(peer.getInetSocketAddress().getAddress(), new Date());
    }

    public boolean isRecentlyDisconnected(InetAddress peerAddr) {
        Date disconnectTime = recentlyDisconnected.get(peerAddr);
        if (disconnectTime != null &&
                System.currentTimeMillis() - disconnectTime.getTime() < INBOUND_CONNECTION_BAN_TIMEOUT) {
            return true;
        } else {
            recentlyDisconnected.remove(peerAddr);
            return false;
        }
    }

    private void process(Channel peer) {
        if (peer.isUsingNewProtocol() || peer.hasEthStatusSucceeded()) {
            syncPool.add(peer);
            synchronized (activePeers) {
                activePeers.put(peer.getNodeId(), peer);
            }
        }
    }

    /**
     * broadcastBlock Propagates a block message across active peers
     *
     * @param block new Block to be sent
     * @return a set containing the ids of the peers that received the block.
     */
    @Nonnull
    public Set<NodeID> broadcastBlock(@Nonnull final Block block) {
        Metrics.broadcastBlock(block);

        final Set<NodeID> nodesIdsBroadcastedTo = new HashSet<>();
        final BlockIdentifier bi = new BlockIdentifier(block.getHash().getBytes(), block.getNumber());
        final EthMessage newBlock = new RskMessage(new BlockMessage(block));
        final EthMessage newBlockHashes = new RskMessage(new NewBlockHashesMessage(Arrays.asList(bi)));
        synchronized (activePeers) {
            // Get a randomized list with all the peers that don't have the block yet.
            activePeers.values().forEach(c -> logger.trace("RSK activePeers: {}", c));
            List<Channel> peers = new ArrayList<>(activePeers.values());
            Collections.shuffle(peers);

            int sqrt = (int) Math.floor(Math.sqrt(peers.size()));
            for (int i = 0; i < sqrt; i++) {
                Channel peer = peers.get(i);
                nodesIdsBroadcastedTo.add(peer.getNodeId());
                logger.trace("RSK propagate: {}", peer);
                peer.sendMessage(newBlock);
            }
            for (int i = sqrt; i < peers.size(); i++) {
                Channel peer = peers.get(i);
                logger.trace("RSK announce: {}", peer);
                peer.sendMessage(newBlockHashes);
            }
        }

        return nodesIdsBroadcastedTo;
    }

    @Nonnull
    public Set<NodeID> broadcastBlockHash(@Nonnull final List<BlockIdentifier> identifiers, final Set<NodeID> targets) {
        final Set<NodeID> nodesIdsBroadcastedTo = new HashSet<>();
        final EthMessage newBlockHash = new RskMessage(new NewBlockHashesMessage(identifiers));

        synchronized (activePeers) {
            activePeers.values().forEach(c -> logger.trace("RSK activePeers: {}", c));

            activePeers.values().stream()
                    .filter(p -> targets.contains(p.getNodeId()))
                    .forEach(peer -> {
                        logger.trace("RSK announce hash: {}", peer);
                        peer.sendMessage(newBlockHash);
                    });
        }

        return nodesIdsBroadcastedTo;
    }

    /**
     * broadcastTransaction Propagates a transaction message across active peers with exclusion of
     * the peers with an id belonging to the skip set.
     *
     * @param transaction new Transaction to be sent
     * @param skip  the set of peers to avoid sending the message.
     * @return a set containing the ids of the peers that received the transaction.
     */
    @Nonnull
    public Set<NodeID> broadcastTransaction(@Nonnull final Transaction transaction, final Set<NodeID> skip) {
        Metrics.broadcastTransaction(transaction);
        List<Transaction> transactions = Collections.singletonList(transaction);

        final Set<NodeID> nodesIdsBroadcastedTo = new HashSet<>();
        final EthMessage newTransactions = new RskMessage(new TransactionsMessage(transactions));

        activePeers.values().stream()
            .filter(p -> !skip.contains(p.getNodeId()))
            .forEach(peer -> {
                peer.sendMessage(newTransactions);
                nodesIdsBroadcastedTo.add(peer.getNodeId());
            });

        return nodesIdsBroadcastedTo;
    }

    @Override
    public int broadcastStatus(Status status) {
        final EthMessage message = new RskMessage(new StatusMessage(status));
        synchronized (activePeers) {
            if (activePeers.isEmpty()) {
                return 0;
            }

            int numberOfPeersToSendStatusTo = getNumberOfPeersToSendStatusTo(activePeers.size());
            List<Channel> shuffledPeers = new ArrayList<>(activePeers.values());
            Collections.shuffle(shuffledPeers);
            shuffledPeers.stream()
                    .limit(numberOfPeersToSendStatusTo)
                    .forEach(c -> c.sendMessage(message));
            return numberOfPeersToSendStatusTo;
        }
    }

    @VisibleForTesting
    int getNumberOfPeersToSendStatusTo(int peerCount) {
        // Send to the sqrt of number of peers.
        // Make sure the number is between 3 and 10 (unless there are less than 3 peers).
        int peerCountSqrt = (int) Math.sqrt(peerCount);
        return Math.min(10, Math.min(Math.max(3, peerCountSqrt), peerCount));
    }


    public void add(Channel peer) {
        newPeers.add(peer);
    }

    public void notifyDisconnect(Channel channel) {
        logger.debug("Peer {}: notifies about disconnect", channel.getPeerIdShort());
        channel.onDisconnect();
        syncPool.onDisconnect(channel);
        synchronized (activePeers) {
            activePeers.values().remove(channel);
        }
        if(newPeers.remove(channel)) {
            logger.info("Peer removed from active peers: {}", channel.getPeerId());
        }
    }

    public void onSyncDone(boolean done) {
        activePeers.values().forEach(channel -> channel.onSyncDone(done));
    }

    public Collection<Channel> getActivePeers() {
        return Collections.unmodifiableCollection(activePeers.values());
    }

    @Override
    public boolean sendMessageTo(NodeID nodeID, MessageWithId message) {
        Channel channel = activePeers.get(nodeID);
        if (channel == null){
            return false;
        }
        EthMessage msg = new RskMessage(message);
        channel.sendMessage(msg);
        return true;
    }

    public boolean isAddressBlockAvailable(InetAddress inetAddress) {
        //TODO(lsebrie): save block address in a data structure and keep updated on each channel add/remove
        //TODO(lsebrie): check if we need to use a different cidr for ipv6
        return activePeers.values().stream()
                .map(ch -> new InetAddressBlock(ch.getInetSocketAddress().getAddress(), bitsToIgnore))
                .filter(block -> block.contains(inetAddress))
                .count() < maxConnectionsPerBlock;
    }

}
