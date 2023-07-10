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
import co.rsk.net.Peer;
import co.rsk.net.NodeID;
import co.rsk.net.Status;
import co.rsk.net.messages.*;
import co.rsk.scoring.InetAddressUtils;
import co.rsk.scoring.InvalidInetAddressException;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.config.NodeFilter;
import org.ethereum.core.Block;
import org.ethereum.core.BlockIdentifier;
import org.ethereum.core.Transaction;
import org.ethereum.net.message.ReasonCode;
import org.ethereum.sync.SyncPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * @author Roman Mandeleil
 * @since 11.11.2014
 */
public class ChannelManagerImpl implements ChannelManager {

    private static final Logger logger = LoggerFactory.getLogger("net");

    // If the inbound peer connection was dropped by us with a reason message
    // then we ban that peer IP on any connections for some time to protect from
    // too active peers
    private static final Duration INBOUND_CONNECTION_BAN_TIMEOUT = Duration.ofSeconds(10);
    private static final int LOG_ACTIVE_PEERS_PERIOD = 60; // every minute
    private final Object activePeersLock = new Object();
    private final Map<NodeID, Channel> activePeers;

    // Using a concurrent list
    // (the add and remove methods copy an internal array,
    // but the iterator directly use the internal array)
    private final List<Channel> newPeers;

    private final ScheduledExecutorService mainWorker;
    private final Map<InetAddress, Instant> disconnectionsTimeouts;
    private final Object disconnectionTimeoutsLock = new Object();

    private final SyncPool syncPool;
    private final NodeFilter trustedPeers;
    private final int maxActivePeers;
    private final int maxConnectionsAllowed;
    private final int networkCIDR;

    private long timeLastLoggedPeers = System.currentTimeMillis();

    public ChannelManagerImpl(RskSystemProperties config, SyncPool syncPool) {
        this.mainWorker = Executors.newSingleThreadScheduledExecutor(target -> new Thread(target, "newPeersProcessor"));
        this.syncPool = syncPool;
        this.maxActivePeers = config.maxActivePeers();
        this.trustedPeers = config.trustedPeers();
        this.disconnectionsTimeouts = new HashMap<>();
        this.activePeers = new ConcurrentHashMap<>();
        this.newPeers = new CopyOnWriteArrayList<>();
        this.maxConnectionsAllowed = config.maxConnectionsAllowed();
        this.networkCIDR = config.networkCIDR();
    }

    @Override
    public void start() {
        mainWorker.scheduleWithFixedDelay(this::handleNewPeersAndDisconnections, 0, 1, TimeUnit.SECONDS);
    }

    @Override
    public void stop() {
        mainWorker.shutdown();
    }

    private void handleNewPeersAndDisconnections() {
        this.tryProcessNewPeers();
        this.cleanDisconnections();
        this.logActivePeers(System.currentTimeMillis());
    }

    @VisibleForTesting
    public void tryProcessNewPeers() {
        if (newPeers.isEmpty()) {
            return;
        }

        try {
            processNewPeers();
        } catch (Exception e) {
            logger.error("Error", e);
        }
    }

    private void cleanDisconnections() {
        synchronized (disconnectionTimeoutsLock) {
            Instant now = Instant.now();
            disconnectionsTimeouts.values().removeIf(v -> !isRecent(v, now));
        }
    }

    private void processNewPeers() {
        synchronized (newPeers) {
            List<Channel> processedChannels = new ArrayList<>();
            newPeers.stream().filter(Channel::isProtocolsInitialized).forEach(c -> processNewPeer(c, processedChannels));
            newPeers.removeAll(processedChannels);
        }
    }

    private void processNewPeer(Channel channel, List<Channel> processedChannels) {
        ReasonCode reason = getNewPeerDisconnectionReason(channel);
        if (reason != null) {
            disconnect(channel, reason);
        } else {
            addToActives(channel);
        }
        processedChannels.add(channel);
    }

    private ReasonCode getNewPeerDisconnectionReason(Channel channel) {
        if (activePeers.containsKey(channel.getNodeId())) {
            return ReasonCode.DUPLICATE_PEER;
        }

        if (!channel.isActive() && activePeers.size() >= maxActivePeers && !trustedPeers.accept(channel.getNode())) {
            return ReasonCode.TOO_MANY_PEERS;
        }

        return null;
    }

    private void disconnect(Channel peer, ReasonCode reason) {
        logger.debug("Disconnecting peer with reason {} : {}", reason, peer);
        peer.disconnect(reason);
        synchronized (disconnectionTimeoutsLock) {
            disconnectionsTimeouts.put(peer.getInetSocketAddress().getAddress(),
                    Instant.now().plus(INBOUND_CONNECTION_BAN_TIMEOUT));
        }
    }

    public boolean isRecentlyDisconnected(InetAddress peerAddress) {
        synchronized (disconnectionTimeoutsLock) {
            return isRecent(disconnectionsTimeouts.getOrDefault(peerAddress, Instant.EPOCH), Instant.now());
        }
    }

    private boolean isRecent(Instant disconnectionTimeout, Instant currentTime) {
        return currentTime.isBefore(disconnectionTimeout);
    }

    private void addToActives(Channel peer) {
        if (peer.isUsingNewProtocol() || peer.hasEthStatusSucceeded()) {
            syncPool.add(peer);
            synchronized (activePeersLock) {
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

        final Set<NodeID> nodesIdsBroadcastedTo = new HashSet<>();
        final BlockIdentifier bi = new BlockIdentifier(block.getHash().getBytes(), block.getNumber());
        final Message newBlock = new BlockMessage(block);
        final Message newBlockHashes = new NewBlockHashesMessage(Arrays.asList(bi));
        synchronized (activePeersLock) {
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
        final Message newBlockHash = new NewBlockHashesMessage(identifiers);

        synchronized (activePeersLock) {
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

    @Override
    public int broadcastStatus(Status status) {
        final Message message = new StatusMessage(status);
        synchronized (activePeersLock) {
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
        logger.debug("Peer {}: notifies about disconnect", channel.getPeerId());
        channel.onDisconnect();
        synchronized (newPeers) {
            if (newPeers.remove(channel)) {
                logger.info("Peer removed from new peers list: {}", channel.getPeerId());
            }
            synchronized (activePeersLock) {
                if (activePeers.values().remove(channel)) {
                    logger.info("Peer removed from active peers list: {}", channel.getPeerId());
                }
            }
            syncPool.onDisconnect(channel);
        }
    }

    public Collection<Peer> getActivePeers() {
        // from the docs: it is imperative to synchronize when iterating
        synchronized (activePeersLock) {
            return new ArrayList<>(activePeers.values());
        }
    }

    public boolean isAddressBlockAvailable(InetAddress inetAddress) {
        synchronized (activePeersLock) {
            //TODO(lsebrie): save block address in a data structure and keep updated on each channel add/remove
            //TODO(lsebrie): check if we need to use a different networkCIDR for ipv6
            return activePeers.values().stream()
                    .map(ch -> {
                        try {
                            return InetAddressUtils.parse(ch.getInetSocketAddress().getAddress(), networkCIDR);
                        } catch (InvalidInetAddressException e) {
                            logger.error(e.getMessage(), e);
                        }

                        return null;
                    })
                    .filter(block -> block != null && block.contains(inetAddress))
                    .count() < maxConnectionsAllowed;
        }
    }

    /**
     * broadcastTransaction Propagates a transaction message across active peers with exclusion of
     * the peers with an id belonging to the skip set.
     *
     * @param transaction new Transaction to be sent
     * @param skip        the set of peers to avoid sending the message.
     * @return a set containing the ids of the peers that received the transaction.
     */
    @Nonnull
    public Set<NodeID> broadcastTransaction(@Nonnull final Transaction transaction, @Nonnull final Set<NodeID> skip) {
        List<Transaction> transactions = Collections.singletonList(transaction);

        return internalBroadcastTransactions(skip, transactions);
    }

    /**
     * broadcastTransaction Propagates a transaction message across active peers with exclusion of
     * the peers with an id belonging to the skip set.
     *
     * @param transactions List of Transactions to be sent
     * @param skip         the set of peers to avoid sending the message.
     * @return a set containing the ids of the peers that received the transaction.
     */
    @Override
    public Set<NodeID> broadcastTransactions(@Nonnull final List<Transaction> transactions, @Nonnull final Set<NodeID> skip) {
        return internalBroadcastTransactions(skip, transactions);
    }

    private Set<NodeID> internalBroadcastTransactions(Set<NodeID> skip, List<Transaction> transactions) {
        final Set<NodeID> nodesIdsBroadcastedTo = new HashSet<>();
        final Message newTransactions = new TransactionsMessage(transactions);
        final List<Channel> peersToBroadcast = activePeers.values().stream().
                filter(p -> !skip.contains(p.getNodeId())).collect(Collectors.toList());

        peersToBroadcast.forEach(peer -> {
            peer.sendMessage(newTransactions);
            nodesIdsBroadcastedTo.add(peer.getNodeId());
        });

        return nodesIdsBroadcastedTo;
    }

    @VisibleForTesting
    public void setActivePeers(Map<NodeID, Channel> newActivePeers) {
        this.activePeers.clear();
        this.activePeers.putAll(newActivePeers);
    }

    @VisibleForTesting
    void logActivePeers(long refTime) {
        Duration timeFromLastLog = Duration.ofMillis(refTime - timeLastLoggedPeers);

        if (timeFromLastLog.getSeconds() > LOG_ACTIVE_PEERS_PERIOD) {
            logger.info("Active peers count: {}", activePeers.size());

            if (logger.isDebugEnabled()) {
                String activePeersStr = activePeers.values()
                        .stream()
                        .map(Channel::toString)
                        .collect(Collectors.joining(","));

                logger.debug("Active peers list: [{}]", activePeersStr);
            }

            this.timeLastLoggedPeers = refTime;
        }
    }

}