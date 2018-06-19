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
import co.rsk.net.discovery.message.*;
import co.rsk.net.discovery.table.NodeDistanceTable;
import co.rsk.net.discovery.table.OperationResult;
import co.rsk.net.discovery.table.PeerDiscoveryRequestBuilder;
import co.rsk.util.IpUtils;
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.ethereum.crypto.ECKey;
import org.ethereum.net.rlpx.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
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

    private final Set<InetSocketAddress> bootNodes = ConcurrentHashMap.newKeySet();
    private final Map<String, PeerDiscoveryRequest> pendingPingRequests = new ConcurrentHashMap<>();
    private final Map<String, PeerDiscoveryRequest> pendingFindNodeRequests = new ConcurrentHashMap<>();

    private final Map<NodeID, Node> establishedConnections = new ConcurrentHashMap<>();
    private final Integer networkId;

    private UDPChannel udpChannel;

    private final ECKey key;

    private final Node localNode;

    private final NodeDistanceTable distanceTable;

    private final Lock updateEntryLock;

    private final PeerExplorerCleaner cleaner;

    private final NodeChallengeManager challengeManager;

    private long requestTimeout;

    public PeerExplorer(List<String> initialBootNodes, Node localNode, NodeDistanceTable distanceTable, ECKey key, long reqTimeOut, long updatePeriod, long cleanPeriod, Integer networkId) {
        this.localNode = localNode;
        this.key = key;
        this.distanceTable = distanceTable;
        this.updateEntryLock = new ReentrantLock();
        this.networkId = networkId;
        loadInitialBootNodes(initialBootNodes);

        this.cleaner = new PeerExplorerCleaner(this, updatePeriod, cleanPeriod);
        this.challengeManager = new NodeChallengeManager();
        this.requestTimeout = reqTimeOut;
    }

    public void start() {
        this.cleaner.run();
        this.startConversationWithNewNodes();
    }

    public Set<String> startConversationWithNewNodes() {
        Set<String> sentAddresses = new HashSet<>();

        for (InetSocketAddress nodeAddress : this.bootNodes) {
            sendPing(nodeAddress, 1);
            sentAddresses.add(nodeAddress.toString());
        }

        this.bootNodes.removeAll(pendingPingRequests.values().stream()
                .map(PeerDiscoveryRequest::getAddress).collect(Collectors.toList()));

        return sentAddresses;
    }

    public void setUDPChannel(UDPChannel udpChannel) {
        this.udpChannel = udpChannel;
    }


    public void handleMessage(DiscoveryEvent event) {
        DiscoveryMessageType type = event.getMessage().getMessageType();
        //If this is not from my network ignore it. But if the messages do not
        //have a networkId in the message yet, then just let them through, for now.
        if (event.getMessage().getNetworkId().isPresent() &&
                event.getMessage().getNetworkId().getAsInt() != this.networkId) {
            return;
        }
        if (type == DiscoveryMessageType.PING) {
            this.handlePingMessage(event.getAddressIp(), (PingPeerMessage) event.getMessage());
        }

        if (type == DiscoveryMessageType.PONG) {
            this.handlePong(event.getAddressIp(), (PongPeerMessage) event.getMessage());
        }

        if (type == DiscoveryMessageType.FIND_NODE) {
            this.handleFindNode((FindNodePeerMessage) event.getMessage());
        }

        if (type == DiscoveryMessageType.NEIGHBORS) {
            this.handleNeighborsMessage((NeighborsPeerMessage) event.getMessage());
        }
    }

    public void handlePingMessage(String ip, PingPeerMessage message) {
        this.sendPong(ip, message);

        Node connectedNode = this.establishedConnections.get(message.getNodeId());

        if (connectedNode == null) {
            this.sendPing(new InetSocketAddress(ip, message.getPort()), 1);
        } else {
            updateEntry(connectedNode);
        }
    }

    public void handlePong(String ip, PongPeerMessage message) {
        PeerDiscoveryRequest request = this.pendingPingRequests.get(message.getMessageId());

        if (request != null && request.validateMessageResponse(message)) {
            this.pendingPingRequests.remove(message.getMessageId());
            NodeChallenge challenge = this.challengeManager.removeChallenge(message.getMessageId());
            if (challenge == null) {
                this.addConnection(message, ip, message.getPort());
            }
        }
    }

    public void handleFindNode(FindNodePeerMessage message) {
        NodeID nodeId = message.getNodeId();
        Node connectedNode = this.establishedConnections.get(nodeId);

        if (connectedNode != null) {
            List<Node> nodesToSend = this.distanceTable.getClosestNodes(nodeId);
            logger.debug("About to send [{}] neighbors to ip[{}] port[{}] nodeId[{}]", nodesToSend.size(), connectedNode.getHost(), connectedNode.getPort(), connectedNode.getHexIdShort());
            this.sendNeighbors(connectedNode.getAddress(), nodesToSend, message.getMessageId());
            updateEntry(connectedNode);
        }
    }

    public void handleNeighborsMessage(NeighborsPeerMessage message) {
        Node connectedNode = this.establishedConnections.get(message.getNodeId());

        if (connectedNode != null) {
            logger.debug("Neighbors received from [{}]", connectedNode.getHexIdShort());
            PeerDiscoveryRequest request = this.pendingFindNodeRequests.remove(message.getMessageId());

            if (request != null && request.validateMessageResponse(message)) {
                List<Node> nodes = (message.countNodes() > MAX_NODES_PER_MSG) ? message.getNodes().subList(0, MAX_NODES_PER_MSG -1) : message.getNodes();
                nodes.stream().filter(n -> !StringUtils.equals(n.getHexId(), this.localNode.getHexId()))
                        .forEach(node -> this.bootNodes.add(node.getAddress()));
                this.startConversationWithNewNodes();
            }
            updateEntry(connectedNode);
        }
    }

    public List<Node> getNodes() {
        return  new ArrayList<>(this.establishedConnections.values());
    }

    public PingPeerMessage sendPing(InetSocketAddress nodeAddress, int attempt) {
        return sendPing(nodeAddress, attempt, null);
    }

    public PingPeerMessage sendPing(InetSocketAddress nodeAddress, int attempt, Node node) {
        PingPeerMessage nodeMessage = checkPendingPeerToAddress(nodeAddress);

        if (nodeMessage != null) {
            return nodeMessage;
        }

        InetSocketAddress localAddress = this.localNode.getAddress();
        String id = UUID.randomUUID().toString();
        nodeMessage = PingPeerMessage.create(
                localAddress.getAddress().getHostAddress(),
                localAddress.getPort(),
                id, this.key, this.networkId);
        udpChannel.write(new DiscoveryEvent(nodeMessage, nodeAddress));

        PeerDiscoveryRequest request = PeerDiscoveryRequestBuilder.builder().messageId(id)
                .message(nodeMessage).address(nodeAddress).expectedResponse(DiscoveryMessageType.PONG).relatedNode(node)
                .expirationPeriod(requestTimeout).attemptNumber(attempt).build();

        pendingPingRequests.put(nodeMessage.getMessageId(), request);

        return nodeMessage;
    }

    private void updateEntry(Node connectedNode) {
        try{
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

    public PongPeerMessage sendPong(String ip, PingPeerMessage message) {
        InetSocketAddress localAddress = this.localNode.getAddress();
        PongPeerMessage pongPeerMessage = PongPeerMessage.create(localAddress.getHostName(), localAddress.getPort(), message.getMessageId(), this.key, this.networkId);
        InetSocketAddress nodeAddress = new InetSocketAddress(ip, message.getPort());
        udpChannel.write(new DiscoveryEvent(pongPeerMessage, nodeAddress));

        return pongPeerMessage;
    }

    public FindNodePeerMessage sendFindNode(Node node) {
        InetSocketAddress nodeAddress = node.getAddress();
        String id = UUID.randomUUID().toString();
        FindNodePeerMessage findNodePeerMessage = FindNodePeerMessage.create(this.key.getNodeId(), id, this.key, this.networkId);
        udpChannel.write(new DiscoveryEvent(findNodePeerMessage, nodeAddress));
        PeerDiscoveryRequest request = PeerDiscoveryRequestBuilder.builder().messageId(id).relatedNode(node)
                .message(findNodePeerMessage).address(nodeAddress).expectedResponse(DiscoveryMessageType.NEIGHBORS)
                .expirationPeriod(requestTimeout).build();
        pendingFindNodeRequests.put(findNodePeerMessage.getMessageId(), request);

        return findNodePeerMessage;
    }

    public NeighborsPeerMessage sendNeighbors(InetSocketAddress nodeAddress, List<Node> nodes, String id) {
        List<Node> nodesToSend = getRandomizeLimitedList(nodes, MAX_NODES_PER_MSG, 5);
        NeighborsPeerMessage sendNodesMessage = NeighborsPeerMessage.create(nodesToSend, id, this.key, networkId);
        udpChannel.write(new DiscoveryEvent(sendNodesMessage, nodeAddress));
        logger.debug(" [{}] Neighbors Sent to ip:[{}] port:[{}]", nodesToSend.size(), nodeAddress.getAddress().getHostAddress(), nodeAddress.getPort());

        return sendNodesMessage;
    }

    public void purgeRequests() {
        List<PeerDiscoveryRequest> oldPingRequests = removeExpiredRequests(this.pendingPingRequests);
        removeExpiredChallenges(oldPingRequests);
        resendExpiredPing(oldPingRequests);
        removeConnections(oldPingRequests.stream().
                filter(r -> r.getAttemptNumber() >= RETRIES_COUNT).collect(Collectors.toList()));

        removeExpiredRequests(this.pendingFindNodeRequests);
    }

    public void clean() {
        this.purgeRequests();
    }

    public void update() {
        List<Node> closestNodes = this.distanceTable.getClosestNodes(this.localNode.getId());
        this.askForMoreNodes(closestNodes);
        this.checkPeersPulse(closestNodes);
    }

    private void checkPeersPulse(List<Node> closestNodes) {
        List<Node> nodesToCheck = this.getRandomizeLimitedList(closestNodes, MAX_NODES_TO_CHECK, 10);
        nodesToCheck.forEach(node -> sendPing(node.getAddress(), 1, node));
    }

    private void askForMoreNodes(List<Node> closestNodes) {
        List<Node> nodesToAsk = getRandomizeLimitedList(closestNodes, MAX_NODES_TO_ASK, 5);
        nodesToAsk.forEach(this::sendFindNode);
    }

    private List<PeerDiscoveryRequest> removeExpiredRequests(Map<String, PeerDiscoveryRequest> pendingRequests) {
        List<PeerDiscoveryRequest> requests = pendingRequests.values().stream()
                .filter(PeerDiscoveryRequest::hasExpired).collect(Collectors.toList());
        requests.forEach(r -> pendingRequests.remove(r.getMessageId()));

        return requests;
    }

    private void removeExpiredChallenges(List<PeerDiscoveryRequest> peerDiscoveryRequests) {
        peerDiscoveryRequests.stream().forEach(r -> challengeManager.removeChallenge(r.getMessageId()));
    }

    private void resendExpiredPing(List<PeerDiscoveryRequest> peerDiscoveryRequests) {
        peerDiscoveryRequests.stream().filter(r -> r.getAttemptNumber() < RETRIES_COUNT)
                .forEach(r -> sendPing(r.getAddress(), r.getAttemptNumber() + 1));
    }

    private void removeConnections(List<PeerDiscoveryRequest> expiredRequests) {
        if (CollectionUtils.isNotEmpty(expiredRequests)) {
            for (PeerDiscoveryRequest req : expiredRequests) {
                Node node = req.getRelatedNode();

                if (node != null) {
                    this.establishedConnections.remove(node.getId());
                    this.distanceTable.removeNode(node);
                }
            }
        }
    }

    private void addConnection(PongPeerMessage message, String ip, int port) {
        Node senderNode = new Node(message.getNodeId().getID(), ip, port);
        if (!StringUtils.equals(senderNode.getHexId(), this.localNode.getHexId())) {
            OperationResult result = this.distanceTable.addNode(senderNode);

            if (result.isSuccess()) {
                NodeID senderId = senderNode.getId();
                this.establishedConnections.put(senderId, senderNode);
                logger.debug("New Peer found ip:[{}] port[{}]", ip, port);
            } else {
                this.challengeManager.startChallenge(result.getAffectedEntry().getNode(), senderNode, this);
            }
        }
    }

    private void loadInitialBootNodes(List<String> nodes) {
        if (CollectionUtils.isNotEmpty(nodes)) {
            bootNodes.addAll(IpUtils.parseAddresses(nodes));
        }
    }

    private List<Node> getRandomizeLimitedList(List<Node> nodes, int maxNumber, int randomElements) {
        if (CollectionUtils.size(nodes) <= maxNumber) {
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
    public NodeChallengeManager getChallengeManager() {
        return challengeManager;
    }
}
