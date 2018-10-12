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

package org.ethereum.sync;

import co.rsk.net.NodeID;
import co.rsk.core.BlockDifficulty;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.Blockchain;
import org.ethereum.listener.EthereumListener;
import org.ethereum.net.NodeHandler;
import org.ethereum.net.NodeManager;
import org.ethereum.net.client.PeerClient;
import org.ethereum.net.rlpx.Node;
import org.ethereum.net.server.Channel;
import org.ethereum.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bouncycastle.util.encoders.Hex;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.min;
import static org.ethereum.util.BIUtil.isIn20PercentRange;
import static org.ethereum.util.TimeUtils.secondsToMillis;
import static org.ethereum.util.TimeUtils.timeAfterMillis;

/**
 * <p>Encapsulates logic which manages peers involved in blockchain sync</p>
 *
 * Holds connections, bans, disconnects and other peers logic<br>
 * The pool is completely threadsafe<br>
 * Implements {@link Iterable} and can be used in "foreach" loop<br>
 *
 * @author Mikhail Kalinin
 * @since 10.08.2015
 */
public class SyncPool implements Iterable<Channel> {

    public static final Logger logger = LoggerFactory.getLogger("sync");

    private static final long WORKER_TIMEOUT = 3; // 3 seconds

    private static final long CONNECTION_TIMEOUT = secondsToMillis(30);

    private final Map<NodeID, Channel> peers = new HashMap<>();
    private final List<Channel> activePeers = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Long> pendingConnections = new HashMap<>();

    private BlockDifficulty lowerUsefulDifficulty = BlockDifficulty.ZERO;

    private final EthereumListener ethereumListener;
    private final Blockchain blockchain;
    private final SystemProperties config;
    private final NodeManager nodeManager;
    private final ScheduledExecutorService syncPoolExecutor;

    public SyncPool(EthereumListener ethereumListener, Blockchain blockchain, SystemProperties config, NodeManager nodeManager) {
        this.ethereumListener = ethereumListener;
        this.blockchain = blockchain;
        this.config = config;
        this.nodeManager = nodeManager;
        this.syncPoolExecutor = Executors.newSingleThreadScheduledExecutor(target -> new Thread(target, "syncPool"));
    }

    public void start(PeerClientFactory peerClientFactory) {
        syncPoolExecutor.scheduleWithFixedDelay(
            () -> {
                try {
                    heartBeat();
                    processConnections();
                    updateLowerUsefulDifficulty();
                    fillUp(peerClientFactory);
                    prepareActive();
                } catch (Throwable t) {
                    logger.error("Unhandled exception", t);
                }
            }, WORKER_TIMEOUT, WORKER_TIMEOUT, TimeUnit.SECONDS
        );
    }

    public void stop() {
        syncPoolExecutor.shutdown();
    }

    public void add(Channel peer) {

        if (!config.isSyncEnabled()) {
            return;
        }

        String shortPeerId = peer.getPeerIdShort();
        logger.trace("Peer {}: adding", shortPeerId);

        synchronized (peers) {
            peers.put(peer.getNodeId(), peer);
        }

        synchronized (pendingConnections) {
            pendingConnections.remove(peer.getPeerId());
        }

        ethereumListener.onPeerAddedToSyncPool(peer);
        logger.info("Peer {}: added to pool", shortPeerId);
    }

    public void remove(Channel peer) {
        synchronized (peers) {
            peers.values().remove(peer);
        }
    }

    @Nullable
    public Channel getMaster() {

        synchronized (peers) {

            for (Channel peer : peers.values()) {
                if (peer.isMaster()) {
                    return peer;
                }
            }

            return null;
        }
    }

    @Nullable
    public Channel getMasterCandidate() {
        synchronized (activePeers) {
            if (activePeers.isEmpty()) {
                return null;
            }

            return activePeers.get(0);
        }
    }

    @Nullable
    public Channel getBestIdle() {
        synchronized (activePeers) {

            for (Channel peer : activePeers) {
                if (peer.isIdle()) {
                    return peer;
                }
            }
        }

        return null;
    }

    public void onDisconnect(Channel peer) {

        if (peer.getNodeId() == null) {
            return;
        }

        boolean existed;

        synchronized (peers) {
            existed = peers.values().remove(peer);
            synchronized (activePeers) {
                activePeers.remove(peer);
            }
        }

        // do not count disconnects for nodeId
        // if exact peer is not an active one
        if (!existed) {
            return;
        }

        logger.info("Peer {}: disconnected", peer.getPeerIdShort());
    }

    private void connect(Node node, PeerClientFactory peerClientFactory) {
        if (logger.isTraceEnabled()) {
            logger.trace(
                "Peer {}: initiate connection",
                node.getHexIdShort()
            );
        }

        if (isInUse(node.getHexId())) {
            if (logger.isTraceEnabled()) {
                logger.trace(
                    "Peer {}: connection already initiated",
                    node.getHexIdShort()
                );
            }

            return;
        }

        synchronized (pendingConnections) {
            String ip = node.getHost();
            int port = node.getPort();
            String remoteId = Hex.toHexString(node.getId().getID());
            logger.info("Connecting to: {}:{}", ip, port);
            PeerClient peerClient = peerClientFactory.newInstance();
            peerClient.connectAsync(ip, port, remoteId);
            pendingConnections.put(node.getHexId(), timeAfterMillis(CONNECTION_TIMEOUT));
        }
    }

    public Set<String> nodesInUse() {
        Set<String> ids = new HashSet<>();

        synchronized (peers) {
            for (Channel peer : peers.values()) {
                ids.add(peer.getPeerId());
            }
        }

        synchronized (pendingConnections) {
            ids.addAll(pendingConnections.keySet());
        }

        return ids;
    }

    public boolean isInUse(String nodeId) {
        return nodesInUse().contains(nodeId);
    }

    public boolean isEmpty() {
        synchronized (peers) {
            return peers.isEmpty();
        }
    }

    @Override
    public Iterator<Channel> iterator() {
        synchronized (peers) {
            return new ArrayList<>(peers.values()).iterator();
        }
    }

    void logActivePeers() {
        synchronized (activePeers) {
            if (activePeers.isEmpty()) {
                return;
            }

            logger.info("\n");
            logger.info("Active peers");
            logger.info("============");

            for (Channel peer : activePeers) {
                peer.logSyncStats();
            }
        }
    }

    private void processConnections() {
        synchronized (pendingConnections) {
            Set<String> exceeded = getTimeoutExceeded(pendingConnections);
            pendingConnections.keySet().removeAll(exceeded);
        }
    }

    private Set<String> getTimeoutExceeded(Map<String, Long> map) {
        Set<String> exceeded = new HashSet<>();
        final Long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> e : map.entrySet()) {
            if (now >= e.getValue()) {
                exceeded.add(e.getKey());
            }
        }
        return exceeded;
    }

    private void fillUp(PeerClientFactory peerClientFactory) {
        int lackSize = config.maxActivePeers() - peers.size();
        if(lackSize <= 0) {
            return;
        }

        Set<String> nodesInUse = nodesInUse();

        List<NodeHandler> newNodes = nodeManager.getNodes(nodesInUse);

        if (logger.isTraceEnabled()) {
            logDiscoveredNodes(newNodes);
        }

        for(NodeHandler n : newNodes) {
            connect(n.getNode(), peerClientFactory);
        }
    }

    private void prepareActive() {
        synchronized (peers) {

            List<Channel> active = new ArrayList<>(peers.values());

            if (active.isEmpty()) {
                return;
            }

            // filtering by 20% from top difficulty
            Collections.sort(active, new Comparator<Channel>() {
                @Override
                public int compare(Channel c1, Channel c2) {
                    return c2.getTotalDifficulty().compareTo(c1.getTotalDifficulty());
                }
            });

            BigInteger highestDifficulty = active.get(0).getTotalDifficulty();
            int thresholdIdx = min(config.syncPeerCount(), active.size()) - 1;

            for (int i = thresholdIdx; i >= 0; i--) {
                if (isIn20PercentRange(active.get(i).getTotalDifficulty(), highestDifficulty)) {
                    thresholdIdx = i;
                    break;
                }
            }

            List<Channel> filtered = active.subList(0, thresholdIdx + 1);

            // sorting by latency in asc order
            Collections.sort(filtered, new Comparator<Channel>() {
                @Override
                public int compare(Channel c1, Channel c2) {
                    return Double.valueOf(c1.getPeerStats().getAvgLatency()).compareTo(c2.getPeerStats().getAvgLatency());
                }
            });

            synchronized (activePeers) {
                activePeers.clear();
                activePeers.addAll(filtered);
            }
        }
    }

    private void logDiscoveredNodes(List<NodeHandler> nodes) {
        StringBuilder sb = new StringBuilder();

        for(NodeHandler n : nodes) {
            sb.append(Utils.getNodeIdShort(Hex.toHexString(n.getNode().getId().getID())));
            sb.append(", ");
        }

        if(sb.length() > 0) {
            sb.delete(sb.length() - 2, sb.length());
        }

        logger.trace(
                "Node list obtained from discovery: {}",
                nodes.isEmpty() ? "empty" : sb.toString()
        );
    }

    public void updateLowerUsefulDifficulty() {
        BlockDifficulty td = blockchain.getTotalDifficulty();

        if (td.compareTo(lowerUsefulDifficulty) > 0) {
            lowerUsefulDifficulty = td;
        }
    }

    private void heartBeat() {
        for (Channel peer : this) {
            if (!peer.isIdle() && peer.getSyncStats().secondsSinceLastUpdate() > config.peerChannelReadTimeout()) {
                logger.info("Peer {}: no response after %d seconds", peer.getPeerIdShort(), config.peerChannelReadTimeout());
                peer.dropConnection();
            }
        }
    }

    public interface PeerClientFactory {
        PeerClient newInstance();
    }
}
