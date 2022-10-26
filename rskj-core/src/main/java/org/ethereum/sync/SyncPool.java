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

import co.rsk.config.InternalService;
import co.rsk.config.RskSystemProperties;
import co.rsk.core.BlockDifficulty;
import co.rsk.net.NodeBlockProcessor;
import co.rsk.net.NodeID;
import org.ethereum.core.Blockchain;
import org.ethereum.listener.EthereumListener;
import org.ethereum.net.NodeHandler;
import org.ethereum.net.NodeManager;
import org.ethereum.net.client.PeerClient;
import org.ethereum.net.rlpx.Node;
import org.ethereum.net.server.Channel;
import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.min;
import static org.ethereum.util.BIUtil.isIn20PercentRange;

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
public class SyncPool implements InternalService {

    public static final Logger logger = LoggerFactory.getLogger("sync");

    private static final long WORKER_TIMEOUT = 3; // 3 seconds

    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(30);
    private final Map<NodeID, Channel> peers = new HashMap<>();
    private final List<Channel> activePeers = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Instant> pendingConnections = new HashMap<>();

    private BlockDifficulty lowerUsefulDifficulty = BlockDifficulty.ZERO;

    private final EthereumListener ethereumListener;
    private final Blockchain blockchain;
    private final RskSystemProperties config;
    private final NodeManager nodeManager;
    private final NodeBlockProcessor nodeBlockProcessor;
    private final PeerClientFactory peerClientFactory;

    private ScheduledExecutorService syncPoolExecutor;

    public SyncPool(
            EthereumListener ethereumListener,
            Blockchain blockchain,
            RskSystemProperties config,
            NodeManager nodeManager,
            NodeBlockProcessor nodeBlockProcessor,
            PeerClientFactory peerClientFactory) {
        this.ethereumListener = ethereumListener;
        this.blockchain = blockchain;
        this.config = config;
        this.nodeManager = nodeManager;
        this.nodeBlockProcessor = nodeBlockProcessor;
        this.peerClientFactory = peerClientFactory;
    }

    @Override
    public void start() {
        this.syncPoolExecutor = Executors.newSingleThreadScheduledExecutor(target -> new Thread(target, "syncPool"));

        updateLowerUsefulDifficulty();

        syncPoolExecutor.scheduleWithFixedDelay(
            () -> {
                try {
                    if (config.getIsHeartBeatEnabled()) {
                        heartBeat();
                    }
                    processConnections();
                    updateLowerUsefulDifficulty();
                    fillUp();
                    prepareActive();
                } catch (Throwable t) {
                    logger.error("Unhandled exception", t);
                }
            }, WORKER_TIMEOUT, WORKER_TIMEOUT, TimeUnit.SECONDS
        );

        if (config.waitForSync()) {
            try {
                while (nodeBlockProcessor.getBestBlockNumber() == 0 || nodeBlockProcessor.hasBetterBlockToSync()) {
                    Thread.sleep(10000);
                }
            } catch (InterruptedException e) {
                logger.error("The SyncPool service couldn't be started", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void stop() {
        syncPoolExecutor.shutdown();
    }

    public void add(Channel peer) {

        if (!config.isSyncEnabled()) {
            return;
        }

        String peerId = peer.getPeerId();
        logger.trace("Peer {}: adding", peerId);

        synchronized (peers) {
            peers.put(peer.getNodeId(), peer);
        }

        synchronized (pendingConnections) {
            pendingConnections.remove(peer.getPeerId());
        }

        ethereumListener.onPeerAddedToSyncPool(peer);
        logger.info("Peer {}: added to pool", peerId);
    }

    public void remove(Channel peer) {
        synchronized (peers) {
            peers.values().remove(peer);
        }
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

        logger.info("Peer {}: disconnected", peer.getPeerId());
    }

    private void connect(Node node) {
        if (logger.isTraceEnabled()) {
            logger.trace(
                "Peer {}: initiate connection",
                node.getHexId()
            );
        }

        if (isInUse(node.getHexId())) {
            if (logger.isTraceEnabled()) {
                logger.trace(
                    "Peer {}: connection already initiated",
                    node.getHexId()
                );
            }

            return;
        }

        synchronized (pendingConnections) {
            String ip = node.getHost();
            int port = node.getPort();
            String remoteId = ByteUtil.toHexString(node.getId().getID());
            logger.info("Connecting to: {}:{}", ip, port);
            PeerClient peerClient = peerClientFactory.newInstance();
            peerClient.connectAsync(ip, port, remoteId);
            pendingConnections.put(node.getHexId(), Instant.now());
        }
    }

    private Set<String> nodesInUse() {
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

    private boolean isInUse(String nodeId) {
        return nodesInUse().contains(nodeId);
    }

    private void processConnections() {
        synchronized (pendingConnections) {
            Instant earliestAcceptableTime = Instant.now().minus(CONNECTION_TIMEOUT);
            pendingConnections.values().removeIf(e -> e.isBefore(earliestAcceptableTime));
        }
    }

    private void fillUp() {
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
            connect(n.getNode());
        }
    }

    private void prepareActive() {
        synchronized (peers) {

            List<Channel> active = new ArrayList<>(peers.values());

            if (active.isEmpty()) {
                return;
            }

            // filtering by 20% from top difficulty
            active.sort(Comparator.comparing(Channel::getTotalDifficulty).reversed());

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
            filtered.sort(Comparator.comparingDouble(c -> c.getPeerStats().getAvgLatency()));

            synchronized (activePeers) {
                activePeers.clear();
                activePeers.addAll(filtered);
            }
        }
    }

    private void logDiscoveredNodes(List<NodeHandler> nodes) {
        StringBuilder sb = new StringBuilder();

        for(NodeHandler n : nodes) {
            sb.append(ByteUtil.toHexString(n.getNode().getId().getID()));
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

    private void updateLowerUsefulDifficulty() {
        BlockDifficulty td = blockchain.getTotalDifficulty();

        if (td.compareTo(lowerUsefulDifficulty) > 0) {
            lowerUsefulDifficulty = td;
        }
    }

    private void heartBeat() {
        synchronized (peers) {
            for (Channel peer : peers.values()) {
                if (peer.getSyncStats().secondsSinceLastUpdate() > config.peerChannelReadTimeout()) {
                    logger.info("Peer {}: no response after {} seconds", peer.getPeerId(), config.peerChannelReadTimeout());
                    peer.dropConnection();
                }
            }
        }
    }

    public interface PeerClientFactory {
        PeerClient newInstance();
    }
}
