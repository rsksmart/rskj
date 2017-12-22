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

import co.rsk.net.Metrics;
import co.rsk.net.NodeID;
import co.rsk.net.Status;
import co.rsk.net.eth.RskMessage;
import co.rsk.net.messages.BlockMessage;
import co.rsk.net.messages.NewBlockHashesMessage;
import co.rsk.net.messages.StatusMessage;
import co.rsk.net.messages.TransactionsMessage;
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.map.LRUMap;
import org.ethereum.config.NodeFilter;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.Block;
import org.ethereum.core.BlockIdentifier;
import org.ethereum.core.Transaction;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.net.eth.message.EthMessage;
import org.ethereum.net.message.ReasonCode;
import org.ethereum.sync.SyncPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Roman Mandeleil
 * @since 11.11.2014
 */
@Component("ChannelManager")
public class ChannelManagerImpl implements ChannelManager {

    private static final Logger logger = LoggerFactory.getLogger("net");
    private static final Logger mlogger = LoggerFactory.getLogger("metrics");

    // If the inbound peer connection was dropped by us with a reason message
    // then we ban that peer IP on any connections for some time to protect from
    // too active peers
    private static final int INBOUND_CONNECTION_BAN_TIMEOUT = 10 * 1000;
    private final Map<ByteArrayWrapper, Channel> activePeers = Collections.synchronizedMap(new HashMap<>());
    @Autowired
    SystemProperties config;
    @Autowired
    SyncPool syncPool;

    // Using a concurrent list
    // (the add and remove methods copy an internal array,
    // but the iterator directly use the internal array)
    private List<Channel> newPeers = new CopyOnWriteArrayList<>();

    private ScheduledExecutorService mainWorker = Executors.newSingleThreadScheduledExecutor();
    private int maxActivePeers;
    private Map<InetAddress, Date> recentlyDisconnected = Collections.synchronizedMap(new LRUMap<InetAddress, Date>(500));
    private NodeFilter trustedPeers;

    @PostConstruct
    public void init() {
        maxActivePeers = config.maxActivePeers();
        trustedPeers = config.peerTrusted();
        mainWorker.scheduleWithFixedDelay((Runnable) () -> {
            try {
                processNewPeers();
            } catch (Exception e) {
                logger.error("Error", e);
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    private void processNewPeers() {
        if (!CollectionUtils.isEmpty(newPeers)) {
            final List<Channel>  processed = new ArrayList<>();
            newPeers.stream().filter(channel -> channel.isProtocolsInitialized()).forEach(channel -> {
                ReasonCode reason = getNewPeerDisconnectionReason(channel);
               if(reason == null) {
                    process(channel);
                } else {
                   disconnect(channel, reason);
                }
                processed.add(channel);
            });
            newPeers.removeAll(processed);
        }
    }

    private ReasonCode getNewPeerDisconnectionReason(Channel peer) {
        if(activePeers.containsKey(peer.getNodeIdWrapper())) {
            return ReasonCode.DUPLICATE_PEER;
        }

        if (!peer.isActive() &&
                activePeers.size() >= maxActivePeers &&
                !trustedPeers.accept(peer.getNode())) {
            return ReasonCode.TOO_MANY_PEERS;
        }

        return null;
    }

    private void disconnect(Channel peer, ReasonCode reason) {
        logger.debug("Disconnecting peer with reason " + reason + ": " + peer);
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
            activePeers.put(peer.getNodeIdWrapper(), peer);
        }
    }

    /**
     * Propagates the transactions message across active peers with exclusion of
     * 'receivedFrom' peer.
     *
     * @param tx           transactions to be sent
     * @param receivedFrom the peer which sent original message or null if
     *                     the transactions were originated by this peer
     */
    public void sendTransaction(List<Transaction> tx, Channel receivedFrom) {
        tx.stream().forEach(t -> Metrics.broadcastTransaction(t));

        synchronized (activePeers) {
            TransactionsMessage txsmsg = new TransactionsMessage(tx);
            EthMessage msg = new RskMessage(txsmsg);
            for (Channel channel : activePeers.values()) {
                if (channel != receivedFrom) {
                    channel.sendMessage(msg);
                }
            }
        }
    }

    /**
     * broadcastBlock Propagates a block message across active peers with exclusion of
     * the peers with an id belonging to the skip set.
     *
     * @param block new Block to be sent
     * @param skip  the set of peers to avoid sending the message.
     * @return a set containing the ids of the peers that received the block.
     */
    @Nonnull
    public Set<NodeID> broadcastBlock(@Nonnull final Block block, @Nullable final Set<NodeID> skip) {
        Metrics.broadcastBlock(block);

        final Set<NodeID> res = new HashSet<>();
        final BlockIdentifier bi = new BlockIdentifier(block.getHash(), block.getNumber());
        final EthMessage newBlock = new RskMessage(new BlockMessage(block));
        final EthMessage newBlockHashes = new RskMessage(new NewBlockHashesMessage(Arrays.asList(bi)));
        synchronized (activePeers) {
            // Get a randomized list with all the peers that don't have the block yet.
            activePeers.values().forEach(c -> logger.trace("RSK activePeers: {}", c));
            final Vector<Channel> peers = activePeers.values().stream()
                    .filter(p -> skip == null || !skip.contains(new NodeID(p.getNodeId())))
                    .collect(Collectors.toCollection(() -> new Vector<>()));
            Collections.shuffle(peers);

            int sqrt = (int) Math.floor(Math.sqrt(peers.size()));
            for (int i = 0; i < sqrt; i++) {
                Channel peer = peers.get(i);
                res.add(new NodeID(peer.getNodeId()));
                logger.trace("RSK propagate: {}", peer);
                peer.sendMessage(newBlock);
            }
            for (int i = sqrt; i < peers.size(); i++) {
                Channel peer = peers.get(i);
                logger.trace("RSK announce: {}", peer);
                peer.sendMessage(newBlockHashes);
            }
        }

        return res;
    }

    @Nonnull
    public Set<NodeID> broadcastBlockHash(@Nonnull final List<BlockIdentifier> identifiers, @Nullable final Set<NodeID> targets) {
        final Set<NodeID> res = new HashSet<>();
        final EthMessage newBlockHash = new RskMessage(new NewBlockHashesMessage(identifiers));

        synchronized (activePeers) {
            activePeers.values().forEach(c -> logger.trace("RSK activePeers: {}", c));

            activePeers.values().stream()
                    .filter(p -> targets == null || targets.contains(new NodeID(p.getNodeId())))
                    .forEach(peer -> {
                        logger.trace("RSK announce hash: {}", peer);
                        peer.sendMessage(newBlockHash);
                    });
        }

        return res;
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
    public Set<NodeID> broadcastTransaction(@Nonnull final Transaction transaction, @Nullable final Set<NodeID> skip) {
        Metrics.broadcastTransaction(transaction);
        List<Transaction> transactions = new ArrayList<>();
        transactions.add(transaction);

        final Set<NodeID> res = new HashSet<>();
        final EthMessage newTransactions = new RskMessage(new TransactionsMessage(transactions));

        synchronized (activePeers) {
            final Vector<Channel> peers = activePeers.values().stream()
                    .filter(p -> skip == null || !skip.contains(new NodeID(p.getNodeId())))
                    .collect(Collectors.toCollection(() -> new Vector<>()));

            for (Channel peer : peers) {
                res.add(new NodeID(peer.getNodeId()));
                peer.sendMessage(newTransactions);
            }
        }
        return res;
    }

    @Override
    public int broadcastStatus(Status status) {
        final EthMessage message = new RskMessage(new StatusMessage(status));

        int npeers = 0;

        synchronized (activePeers) {
            int peerCount = activePeers.size();
            if (peerCount > 0) {
                int numberOfPeersToSendStatusTo = getNumberOfPeersToSendStatusTo(peerCount);
                List<Channel> shuffledPeers = new ArrayList<>(activePeers.values());
                Collections.shuffle(shuffledPeers);
                for (int i = 0; i < numberOfPeersToSendStatusTo; i++) {
                    shuffledPeers.get(i).sendMessage(message);
                    npeers++;
                }
            }
        }
        return npeers;
    }

    @VisibleForTesting
    int getNumberOfPeersToSendStatusTo(int peerCount) {
        // Send to the sqrt of number of peers.
        // Make sure the number is between 3 and 10 (unless there are less than 3 peers).
        int peerCountSqrt = (int) Math.sqrt(peerCount);
        int numberOfPeersToSendStatusTo = Math.min(10, Math.min(Math.max(3, peerCountSqrt), peerCount));
        return numberOfPeersToSendStatusTo;
    }

    /**
     * Propagates the new block message across active peers with exclusion of
     * 'receivedFrom' peer.
     * @deprecated
     * @param block        new Block to be sent
     * @param receivedFrom the peer which sent original message or null if
     *                     the block has been mined by us
     */
    @Deprecated // Use broadcastBlock
    public void sendNewBlock(Block block, Channel receivedFrom) {
        EthMessage message = new RskMessage(new BlockMessage(block));

        synchronized (activePeers) {
            for (Channel channel : activePeers.values()) {
                if (channel != receivedFrom) {
                    channel.sendMessage(message);
                }
            }
        }
    }


    public void add(Channel peer) {
        newPeers.add(peer);
    }

    public void notifyDisconnect(Channel channel) {
        logger.debug("Peer {}: notifies about disconnect", channel.getPeerIdShort());
        channel.onDisconnect();
        syncPool.onDisconnect(channel);
        activePeers.values().remove(channel);
        if(newPeers.remove(channel)) {
            logger.debug("Peer removed from active peers: {}", channel);
            mlogger.info("Peer removed from active peers: {}", channel);
        }
    }

    public void onSyncDone(boolean done) {

        synchronized (activePeers) {
            for (Channel channel : activePeers.values()) {
                channel.onSyncDone(done);
            }
        }
    }

    public Collection<Channel> getActivePeers() {
        return Collections.unmodifiableCollection(activePeers.values());
    }

}
