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

package org.ethereum.net;

import co.rsk.net.discovery.PeerExplorer;
import org.apache.commons.collections4.CollectionUtils;
import org.ethereum.config.SystemProperties;
import org.ethereum.net.rlpx.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * The central class for Peer Discovery machinery.
 * <p>
 * The NodeManager manages info on all the Nodes discovered by the peer discovery
 * protocol, routes protocol messages to the corresponding NodeHandlers and
 * supplies the info about discovered Nodes and their usage statistics
 * <p>
 * Created by Anton Nashatyrev on 16.07.2015.
 */
@Component
public class NodeManager {
    private static final Logger logger = LoggerFactory.getLogger("discover");

    private static final int MAX_NODES = 2000;
    protected static final int NODES_TRIM_THRESHOLD = 3000;


    // to avoid checking for null
    private static final NodeStatistics DUMMY_STAT = new NodeStatistics(new Node(new byte[0], "dummy.node", 0));

    private final PeerExplorer peerExplorer;
    private final SystemProperties config;

    private Map<String, NodeHandler> nodeHandlerMap = new ConcurrentHashMap<>();
    private Set<NodeHandler> initialNodes = new HashSet<>();
    private Node homeNode;

    private boolean discoveryEnabled;

    private boolean inited = false;

    @Autowired
    public NodeManager(PeerExplorer peerExplorer, SystemProperties config) {
        this.peerExplorer = peerExplorer;
        this.config = config;
        discoveryEnabled = config.isPeerDiscoveryEnabled();

        homeNode = new Node(config.nodeId(), config.getPublicIp(), config.getPeerPort());

        for (Node node : config.peerActive()) {
            NodeHandler handler = new NodeHandler(node, this);
            handler.getNodeStatistics().setPredefined(true);
            createNodeHandler(node);
        }
    }

    private synchronized NodeHandler getNodeHandler(Node n) {
        String key = n.getHexId();
        NodeHandler handler = nodeHandlerMap.get(key);
        return (handler != null) ? handler : createNodeHandler(n);
    }

    private NodeHandler createNodeHandler(Node n) {
        NodeHandler handler = new NodeHandler(n, this);
        purgeNodeHandlers();
        nodeHandlerMap.put(n.getHexId(), handler);
        return handler;
    }

    public NodeStatistics getNodeStatistics(Node n) {
        return discoveryEnabled ? getNodeHandler(n).getNodeStatistics() : DUMMY_STAT;
    }

    public synchronized List<NodeHandler> getNodes(Set<String> nodesInUse) {
        List<NodeHandler> handlers = new ArrayList<>();
        handlers.addAll(initialNodes);

        List<Node> foundNodes = this.peerExplorer.getNodes();
        if (this.discoveryEnabled && CollectionUtils.isNotEmpty(foundNodes)) {
            logger.debug("{} Nodes retrieved from the PE.", CollectionUtils.size(foundNodes));
            foundNodes.stream().filter(n -> !nodeHandlerMap.containsKey(n.getHexId())).forEach(this::createNodeHandler);
        }

        for(NodeHandler handler : this.nodeHandlerMap.values()) {
            if(!nodesInUse.contains(handler.getNode().getHexId())) {
                handlers.add(handler);
            }
        }
        return handlers;
    }

    private void purgeNodeHandlers() {
        if (nodeHandlerMap.size() > NODES_TRIM_THRESHOLD) {
            //I create a stream
            List<NodeHandler> toRemove = nodeHandlerMap.values().stream()
                    //sort by reputation
                    .sorted(Comparator.comparingInt(o -> o.getNodeStatistics().getReputation()))
                    //and just keep the ones that exceeds the MAX_NODES
                    .limit(nodeHandlerMap.size() - MAX_NODES)
                    .collect(Collectors.toList());
            //Remove them
            nodeHandlerMap.values().removeAll(toRemove);
        }
    }

}
