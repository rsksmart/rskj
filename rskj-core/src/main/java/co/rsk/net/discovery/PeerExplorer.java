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

import co.rsk.net.NodeID;
import co.rsk.net.discovery.message.DiscoveryMessageType;
import co.rsk.net.discovery.message.FindNodePeerMessage;
import co.rsk.net.discovery.message.NeighborsPeerMessage;
import co.rsk.net.discovery.message.PingPeerMessage;
import co.rsk.net.discovery.message.PongPeerMessage;
import co.rsk.net.discovery.table.NodeDistanceTable;
import co.rsk.net.discovery.table.OperationResult;
import co.rsk.net.discovery.table.PeerDiscoveryRequestBuilder;
import co.rsk.scoring.PeerScoringManager;
import co.rsk.util.ExecState;
import co.rsk.util.IpUtils;
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.StringUtils;
import org.ethereum.crypto.ECKey;
import org.ethereum.net.rlpx.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Created by mario on 10/02/17.
 */
public class PeerExplorer {
    private static final Logger logger = LoggerFactory.getLogger(PeerExplorer.class);

    private static final int MAX_NODES_PER_MSG = 20;
    private static final int MAX_NODES_TO_ASK = 24;
    private static final int MAX_NODES_TO_CHECK = 16;
    private static final int RETRIES_COUNT = 3;

    private final List<String> initialBootNodes;
    private int retryCounter = 0;
    private final long maxBootRetries;
    private final Set<InetSocketAddress> bootNodes = ConcurrentHashMap.newKeySet();

    private final Map<String, PeerDiscoveryRequest> pendingPingRequests = new ConcurrentHashMap<>();
    private final Map<String, PeerDiscoveryRequest> pendingFindNodeRequests = new ConcurrentHashMap<>();

    private final Map<NodeID, Node> establishedConnections = new ConcurrentHashMap<>();

    private final Map<String, NodeID> knownHosts;
    private final boolean allowMultipleConnectionsPerHostPort;

    private final Integer networkId;

    private final ECKey key;

    private final Node localNode;

    private final NodeDistanceTable distanceTable;

    private final Lock updateEntryLock;

    private final PeerExplorerCleaner cleaner;

    private final NodeChallengeManager challengeManager;

    private final PeerScoringManager peerScoringManager;

    private final long requestTimeout;

    private ExecState state = ExecState.CREATED;

    private UDPChannel udpChannel;

    public PeerExplorer(List<String> initialBootNodes,
                        Node localNode, NodeDistanceTable distanceTable, ECKey key,
                        long reqTimeOut, long updatePeriod, long cleanPeriod, Integer networkId,
                        PeerScoringManager peerScoringManager, boolean allowMultipleConnectionsPerHostPort, long maxBootRetries) {
        this.localNode = localNode;
        this.key = key;
        this.distanceTable = distanceTable;
        this.updateEntryLock = new ReentrantLock();
        this.networkId = networkId;
        this.initialBootNodes = Collections.unmodifiableList(new ArrayList<>(initialBootNodes));
        loadInitialBootNodes(initialBootNodes);

        this.cleaner = new PeerExplorerCleaner(updatePeriod, cleanPeriod, this);
        this.challengeManager = new NodeChallengeManager();
        this.requestTimeout = reqTimeOut;

        this.peerScoringManager = peerScoringManager;

        this.knownHosts = new ConcurrentHashMap<>();
        this.allowMultipleConnectionsPerHostPort = allowMultipleConnectionsPerHostPort;

        this.maxBootRetries = maxBootRetries;
    }

    void start() {
        start(true);
    }

    synchronized void start(boolean startConversation) {
        if (state != ExecState.CREATED) {
            logger.warn("Cannot start peer explorer as current state is {}", state);
            return;
        }
        state = ExecState.RUNNING;

        this.cleaner.start();

        if (startConversation) {
            this.startConversationWithNewNodes();
        }
    }

    public synchronized void dispose() {
        if (state == ExecState.FINISHED) {
            logger.warn("Cannot dispose peer explorer as current state is {}", state);
            return;
        }
        state = ExecState.FINISHED;

        this.cleaner.dispose();
    }

    @VisibleForTesting
    ExecState getState() {
        return state;
    }

    @VisibleForTesting
    int getRetryCounter() {
        return retryCounter;
    }

    @VisibleForTesting
    Set<String> startConversationWithNewNodes() {
        Set<String> sentAddresses = new HashSet<>();

        for (InetSocketAddress nodeAddress : this.bootNodes) {
            sendPing(nodeAddress, 1);
            sentAddresses.add(nodeAddress.toString());
        }

        this.bootNodes.removeAll(pendingPingRequests.values().stream()
                .map(PeerDiscoveryRequest::getAddress).collect(Collectors.toList()));

        return sentAddresses;
    }

    void setUDPChannel(UDPChannel udpChannel) {
        this.udpChannel = udpChannel;
    }

    synchronized void handleMessage(DiscoveryEvent event) {
        logger.debug("handleMessage - Handling message with " +
                        "type: [{}], " +
                        "networkId: [{}]",
                event.getMessage().getMessageType(),
                event.getMessage().getNetworkId());

        if (state != ExecState.RUNNING) {
            logger.warn("Cannot handle message as current state is {}", state);
            return;
        }

        DiscoveryMessageType type = event.getMessage().getMessageType();
        //If this is not from my network ignore it. But if the messages do not
        //have a networkId in the message yet, then just let them through, for now.
        if (event.getMessage().getNetworkId().isPresent() &&
                event.getMessage().getNetworkId().getAsInt() != this.networkId) {
            logger.warn("handleMessage - Message ignored because remote peer's network id: [{}] is different from local network id: [{}]",
                    event.getMessage().getNetworkId(), this.networkId);
            return;
        }
        if (type == DiscoveryMessageType.PING) {
            this.handlePingMessage(event.getAddress(), (PingPeerMessage) event.getMessage());
        }

        if (type == DiscoveryMessageType.PONG) {
            this.handlePong(event.getAddress(), (PongPeerMessage) event.getMessage());
        }

        if (type == DiscoveryMessageType.FIND_NODE) {
            this.handleFindNode((FindNodePeerMessage) event.getMessage());
        }

        if (type == DiscoveryMessageType.NEIGHBORS) {
            this.handleNeighborsMessage(event.getAddress(), (NeighborsPeerMessage) event.getMessage());
        }
    }

    private void handlePingMessage(InetSocketAddress address, PingPeerMessage message) {
        this.sendPong(address, message);
        Node connectedNode = this.establishedConnections.get(message.getNodeId());

        logger.debug("handlePingMessage - Handling ping message with " +
                        "address: [{}/{}], " +
                        "nodeId: [{}], " +
                        "connectedNode: [{}]"
                , address.getHostName(), address.getPort(), message.getNodeId(), connectedNode);

        if (connectedNode == null) {
            this.sendPing(address, 1);
        } else {
            updateEntry(connectedNode);
        }
    }

    private void handlePong(InetSocketAddress pongAddress, PongPeerMessage message) {
        PeerDiscoveryRequest request = this.pendingPingRequests.get(message.getMessageId());

        logger.debug("handlePong - Handling pong message with " +
                        "address: [{}/{}], " +
                        "messageId: [{}], " +
                        "request: [{}]"
                , pongAddress.getHostName(), pongAddress.getPort(), message.getMessageId(), request);

        if (request != null && request.validateMessageResponse(pongAddress, message)) {
            this.pendingPingRequests.remove(message.getMessageId());
            NodeChallenge challenge = this.challengeManager.removeChallenge(message.getMessageId());
            if (challenge == null) {
                this.addConnection(message, request.getAddress().getHostString(), request.getAddress().getPort());
            }
        } else {
            logger.debug("handlePong - Peer discovery request with id [{}] is either null or invalid", message.getMessageId());
        }
    }

    private void handleFindNode(FindNodePeerMessage message) {
        NodeID nodeId = message.getNodeId();
        Node connectedNode = this.establishedConnections.get(nodeId);

        if (connectedNode != null) {
            List<Node> nodesToSend = this.distanceTable.getClosestNodes(nodeId);
            logger.debug("handleFindNode - About to send [{}] neighbors to address: [{}/{}], nodeId: [{}] with messageId: [{}]",
                    nodesToSend.size(), connectedNode.getHost(), connectedNode.getPort(), connectedNode.getHexId(), message.getMessageId());
            this.sendNeighbors(connectedNode.getAddress(), nodesToSend, message.getMessageId());
            updateEntry(connectedNode);
        } else {
            logger.debug("handleFindNode - Node with id: [{}] is not connected. Ignored", nodeId);
        }
    }

    private void handleNeighborsMessage(InetSocketAddress neighborsResponseAddress, NeighborsPeerMessage message) {
        NodeID nodeId = message.getNodeId();
        Node connectedNode = this.establishedConnections.get(nodeId);

        if (connectedNode != null) {
            logger.debug("handleNeighborsMessage - Neighbors received from id: [{}], address: [{}/{}] with " +
                            "nodeId: [{}]," +
                            "messageId: [{}], " +
                            "nodesCount: [{}], " +
                            "nodes: [{}], " +
                            "connectedNode: [{}]",
                    connectedNode.getHexId(), neighborsResponseAddress.getHostName(), neighborsResponseAddress.getPort(), message.getNodeId(),
                    message.getMessageId(), message.countNodes(), message.getNodes(), connectedNode);

            PeerDiscoveryRequest request = this.pendingFindNodeRequests.remove(message.getMessageId());

            if (request != null && request.validateMessageResponse(neighborsResponseAddress, message)) {
                List<Node> nodes = (message.countNodes() > MAX_NODES_PER_MSG) ? message.getNodes().subList(0, MAX_NODES_PER_MSG - 1) : message.getNodes();
                nodes.stream().filter(n -> !StringUtils.equals(n.getHexId(), this.localNode.getHexId()) && !isBanned(n))
                        .forEach(node -> this.bootNodes.add(node.getAddress()));
                this.startConversationWithNewNodes();
            }
            updateEntry(connectedNode);
        } else {
            logger.debug("handleNeighborsMessage - Node with id: [{}] is not connected. Ignored", nodeId);
        }
    }

    public List<Node> getNodes() {
        return new ArrayList<>(this.establishedConnections.values());
    }

    private PingPeerMessage sendPing(InetSocketAddress nodeAddress, int attempt) {
        return sendPing(nodeAddress, attempt, null);
    }

    synchronized PingPeerMessage sendPing(InetSocketAddress nodeAddress, int attempt, Node node) {
        PingPeerMessage nodeMessage = checkPendingPeerToAddress(nodeAddress);

        if (nodeMessage != null) {
            logger.trace("sendPing - No ping message has been sent to address: [{}/{}], as there's pending one", nodeAddress.getHostName(), nodeAddress.getPort());

            return nodeMessage;
        }

        InetSocketAddress localAddress = this.localNode.getAddress();
        String id = UUID.randomUUID().toString();
        nodeMessage = PingPeerMessage.create(
                localAddress.getAddress().getHostAddress(),
                localAddress.getPort(),
                id, this.key, this.networkId);

        logger.debug("sendPing - Sending ping message to " +
                        "address: [{}/{}], " +
                        "attempt: [{}], " +
                        "nodeMessage: [{}]"
                , nodeAddress.getHostName(), nodeAddress.getPort(), attempt, nodeMessage);

        udpChannel.write(new DiscoveryEvent(nodeMessage, nodeAddress));

        PeerDiscoveryRequest request = PeerDiscoveryRequestBuilder.builder().messageId(id)
                .message(nodeMessage).address(nodeAddress).expectedResponse(DiscoveryMessageType.PONG).relatedNode(node)
                .expirationPeriod(requestTimeout).attemptNumber(attempt).build();

        pendingPingRequests.put(nodeMessage.getMessageId(), request);

        return nodeMessage;
    }

    private void updateEntry(Node connectedNode) {
        logger.trace("updateEntry - Updating node [{}]", connectedNode.getHexId());
        try {
            updateEntryLock.lock();
            this.distanceTable.updateEntry(connectedNode);
        } finally {
            updateEntryLock.unlock();
        }
    }

    private PingPeerMessage checkPendingPeerToAddress(InetSocketAddress address) {
        for (PeerDiscoveryRequest req : this.pendingPingRequests.values()) {
            if (req.getAddress().equals(address)) {
                return (PingPeerMessage) req.getMessage();
            }
        }

        return null;
    }

    private PongPeerMessage sendPong(InetSocketAddress nodeAddress, PingPeerMessage message) {
        InetSocketAddress localAddress = this.localNode.getAddress();
        PongPeerMessage pongPeerMessage = PongPeerMessage.create(localAddress.getHostName(), localAddress.getPort(), message.getMessageId(), this.key, this.networkId);

        logger.debug("sendPong - Sending pong message to " +
                        "address: [{}/{}], " +
                        "messageId: [{}], " +
                        "pongPeerMessage: [{}]"
                , nodeAddress.getHostName(), nodeAddress.getPort(), message.getMessageId(), pongPeerMessage);

        udpChannel.write(new DiscoveryEvent(pongPeerMessage, nodeAddress));

        return pongPeerMessage;
    }

    @VisibleForTesting
    FindNodePeerMessage sendFindNode(Node node) {
        InetSocketAddress nodeAddress = node.getAddress();
        String id = UUID.randomUUID().toString();
        FindNodePeerMessage findNodePeerMessage = FindNodePeerMessage.create(this.key.getNodeId(), id, this.key, this.networkId);

        udpChannel.write(new DiscoveryEvent(findNodePeerMessage, nodeAddress));
        PeerDiscoveryRequest request = PeerDiscoveryRequestBuilder.builder().messageId(id).relatedNode(node)
                .message(findNodePeerMessage).address(nodeAddress).expectedResponse(DiscoveryMessageType.NEIGHBORS)
                .expirationPeriod(requestTimeout).build();

        logger.debug("sendFindNode - Sending find node message to " +
                        "address: [{}/{}], " +
                        "findNodePeerMessage: [{}], " +
                        "request: [{}]"
                , nodeAddress.getHostName(), nodeAddress.getPort(), findNodePeerMessage, request);

        pendingFindNodeRequests.put(findNodePeerMessage.getMessageId(), request);

        return findNodePeerMessage;
    }

    private NeighborsPeerMessage sendNeighbors(InetSocketAddress nodeAddress, List<Node> nodes, String id) {
        List<Node> nodesToSend = getRandomizeLimitedList(nodes, MAX_NODES_PER_MSG, 5);
        NeighborsPeerMessage sendNodesMessage = NeighborsPeerMessage.create(nodesToSend, id, this.key, networkId);

        logger.debug("sendNeighbors - Sending neighbors message to " +
                        "address: [{}/{}], " +
                        "id: [{}], " +
                        "nodes: [{}], " +
                        "nodesToSend: [{}], " +
                        "sendNodesMessage: [{}]"
                , nodeAddress.getHostName(), nodeAddress.getPort(), id, nodes, nodesToSend, sendNodesMessage);

        udpChannel.write(new DiscoveryEvent(sendNodesMessage, nodeAddress));

        return sendNodesMessage;
    }

    private void purgeRequests() {
        List<PeerDiscoveryRequest> oldPingRequests = removeExpiredRequests(this.pendingPingRequests);
        removeExpiredChallenges(oldPingRequests);
        resendExpiredPing(oldPingRequests);
        removeConnections(oldPingRequests.stream().
                filter(r -> r.getAttemptNumber() >= RETRIES_COUNT).collect(Collectors.toList()));

        removeExpiredRequests(this.pendingFindNodeRequests);
    }

    synchronized void clean() {
        if (state != ExecState.RUNNING) {
            logger.warn("Cannot clean as current state is {}", state);
            return;
        }

        logger.trace("clean - Cleaning obsolete requests");
        this.purgeRequests();
    }

    synchronized void update() {
        if (state != ExecState.RUNNING) {
            logger.warn("Cannot update as current state is {}", state);
            return;
        }

        List<Node> closestNodes = this.distanceTable.getClosestNodes(this.localNode.getId());

        if (shouldRetryConnection(closestNodes)) {
            if (maxBootRetries == -1 || retryCounter < maxBootRetries) {
                retryCounter++;
                logger.info("retrying connection to bootstrap nodes. Attempt: {}", retryCounter);
                loadInitialBootNodes(initialBootNodes);
                startConversationWithNewNodes();
            } else {
                logger.warn("max retry attempts reached.");
            }
        } else {
            logger.trace("update - closestNodes: [{}]", closestNodes);
            this.askForMoreNodes(closestNodes);
            this.checkPeersPulse(closestNodes);
        }
    }

    private boolean shouldRetryConnection(List<Node> closestNodes) {
        return closestNodes.isEmpty() && bootNodes.isEmpty() && pendingPingRequests.isEmpty() &&
                pendingFindNodeRequests.isEmpty() && establishedConnections.isEmpty();
    }

    private void checkPeersPulse(List<Node> closestNodes) {
        List<Node> nodesToCheck = this.getRandomizeLimitedList(closestNodes, MAX_NODES_TO_CHECK, 10);

        logger.trace("checkPeersPulse - Checking peers pulse for nodes: [{}], nodesToCheck: [{}]"
                , closestNodes, nodesToCheck);

        nodesToCheck.forEach(node -> sendPing(node.getAddress(), 1, node));
    }

    private void askForMoreNodes(List<Node> closestNodes) {
        List<Node> nodesToAsk = getRandomizeLimitedList(closestNodes, MAX_NODES_TO_ASK, 5);

        logger.trace("askForMoreNodes - Asking for more nodes from closestNodes: [{}], nodesToAsk: [{}]"
                , closestNodes, nodesToAsk);

        nodesToAsk.forEach(this::sendFindNode);
    }

    private List<PeerDiscoveryRequest> removeExpiredRequests(Map<String, PeerDiscoveryRequest> pendingRequests) {
        List<PeerDiscoveryRequest> requests = pendingRequests.values().stream()
                .filter(PeerDiscoveryRequest::hasExpired).collect(Collectors.toList());
        requests.forEach(r -> pendingRequests.remove(r.getMessageId()));

        logger.trace("removeExpiredRequests - Removing expired requests from pendingRequests: [{}], requests: [{}]"
                , pendingRequests, requests);

        return requests;
    }

    private void removeExpiredChallenges(List<PeerDiscoveryRequest> peerDiscoveryRequests) {
        peerDiscoveryRequests.stream().forEach(r -> challengeManager.removeChallenge(r.getMessageId()));
    }

    private void resendExpiredPing(List<PeerDiscoveryRequest> peerDiscoveryRequests) {
        logger.trace("resendExpiredPing - Resending expired pings form peerDiscoveryRequests: [{}]", peerDiscoveryRequests);

        peerDiscoveryRequests.stream().filter(r -> r.getAttemptNumber() < RETRIES_COUNT)
                .forEach(r -> sendPing(r.getAddress(), r.getAttemptNumber() + 1, r.getRelatedNode()));
    }

    private void removeConnections(List<PeerDiscoveryRequest> expiredRequests) {
        for (PeerDiscoveryRequest req : expiredRequests) {
            Node node = req.getRelatedNode();

            if (node != null) {
                removeConnection(node);
            }
        }
    }

    private void removeConnection(Node node) {
        this.establishedConnections.remove(node.getId());
        this.distanceTable.removeNode(node);
        this.knownHosts.remove(node.getAddressAsString());

        logger.info("removeConnection - Removed peer: [{}]. Total num of peers: [{}]", node, this.establishedConnections.size());
    }

    private void addConnection(PongPeerMessage message, String ip, int port) {
        Node senderNode = new Node(message.getNodeId().getID(), ip, port);
        boolean isLocalNode = StringUtils.equals(senderNode.getHexId(), this.localNode.getHexId());

        logger.debug("addConnection - Adding node with " +
                "address: [{}/{}], " +
                "messageId: [{}], " +
                "allowMultipleConnectionsPerHostPort: [{}], " +
                "senderNode: [{}], " +
                "isLocalNode: [{}], ", ip, port, message.getMessageId(), this.allowMultipleConnectionsPerHostPort, senderNode, isLocalNode);

        if (isLocalNode) {
            return;
        }

        if (!this.allowMultipleConnectionsPerHostPort) {
            disconnectPeerIfDuplicatedByNodeId(senderNode, ip, port);
        }

        OperationResult result = this.distanceTable.addNode(senderNode);

        logger.debug("addConnection - result: [{}]", result);

        if (result.isSuccess()) {
            this.knownHosts.put(senderNode.getAddressAsString(), senderNode.getId());
            this.establishedConnections.put(senderNode.getId(), senderNode);
            logger.info("New Peer found or id changed: ip[{}] port[{}] id [{}]. Total num of peers: [{}]", ip, port, senderNode.getId(), this.establishedConnections.size());
        } else {
            this.challengeManager.startChallenge(result.getAffectedEntry().getNode(), senderNode, this);
        }
    }

    private void disconnectPeerIfDuplicatedByNodeId(Node senderNode, String ip, int port) {
        NodeID oldNodeIdForHost = this.knownHosts.get(senderNode.getAddressAsString());
        boolean existsWithDifferentId = oldNodeIdForHost != null && !oldNodeIdForHost.equals(senderNode.getId());
        if (existsWithDifferentId) {
            logger.warn("Disconnecting peer with old id: ip[{}] port[{}] id [{}]", ip, port, oldNodeIdForHost);
            Node oldNodeForHost = new Node(oldNodeIdForHost.getID(), ip, port);
            removeConnection(oldNodeForHost);
        }
    }

    private void loadInitialBootNodes(List<String> nodes) {
        bootNodes.addAll(IpUtils.parseAddresses(nodes));
    }

    private List<Node> getRandomizeLimitedList(List<Node> nodes, int maxNumber, int randomElements) {
        if (nodes.size() <= maxNumber) {
            return nodes;
        } else {
            List<Node> ret = new ArrayList<>();
            int limit = maxNumber - randomElements;
            ret.addAll(nodes.subList(0, limit - 1));
            ret.addAll(collectRandomNodes(nodes.subList(limit, nodes.size()), randomElements));

            return ret;
        }
    }

    private Set<Node> collectRandomNodes(List<Node> originalList, int elementsNbr) {
        Set<Node> ret = new HashSet<>();
        SecureRandom rnd = new SecureRandom();

        while (ret.size() < elementsNbr) {
            int i = rnd.nextInt(originalList.size());
            ret.add(originalList.get(i));
        }

        return ret;
    }

    @VisibleForTesting
    NodeChallengeManager getChallengeManager() {
        return challengeManager;
    }

    private boolean isBanned(Node node) {
        InetAddress address;
        try {
            address = InetAddress.getByName(node.getHost());
        } catch (UnknownHostException e) {
            logger.error("Invalid node host: {}", node.getHost(), e);
            address = null;
        }

        return address != null && this.peerScoringManager.isAddressBanned(address) || this.peerScoringManager.isNodeIDBanned(node.getId());
    }
}
