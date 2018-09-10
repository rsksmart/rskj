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

import org.ethereum.net.rlpx.Node;
import org.bouncycastle.util.encoders.Hex;

/**
 * The instance of this class responsible for discovery messages exchange with the specified Node
 * It also manages itself regarding inclusion/eviction from Kademlia table
 *
 * Created by Anton Nashatyrev on 14.07.2015.
 */
public class NodeHandler {

    private Node node;
    private NodeStatistics nodeStatistics;

    public NodeHandler(Node node) {
        this.node = node;
    }

    public Node getNode() {
        return node;
    }

    public NodeStatistics getNodeStatistics() {
        if (nodeStatistics == null) {
            nodeStatistics = new NodeStatistics();
        }
        return nodeStatistics;
    }

    @Override
    public String toString() {
        return "NodeHandler[node: " + node.getHost() + ":" + node.getPort() + ", id="
                + (node.getId().getID().length > 0 ? Hex.toHexString(node.getId().getID(), 0, 4) : "empty") + "]";
    }


}
