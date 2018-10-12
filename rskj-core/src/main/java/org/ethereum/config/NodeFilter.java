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

package org.ethereum.config;

import co.rsk.net.NodeID;
import org.ethereum.net.rlpx.Node;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Anton Nashatyrev on 14.01.2016.
 */
public class NodeFilter {
    private List<Entry> entries = new ArrayList<>();

    public void add(byte[] nodeId, String hostIpPattern) {
        entries.add(new Entry(nodeId, hostIpPattern));
    }

    public boolean accept(Node node) {
        return entries.stream().anyMatch(entry -> entry.accept(node));
    }

    private class Entry {
        private NodeID nodeId;
        private String hostIpPattern;

        public Entry(byte[] nodeId, String hostIpPattern) {
            this.nodeId = new NodeID(nodeId);
            if (hostIpPattern != null) {
                int idx = hostIpPattern.indexOf("*");
                if (idx > 0) {
                    hostIpPattern = hostIpPattern.substring(0, idx);
                }
            }
            this.hostIpPattern = hostIpPattern;
        }

        public boolean accept(InetAddress nodeAddr) {
            String ip = nodeAddr.getHostAddress();
            return hostIpPattern != null && ip.startsWith(hostIpPattern);
        }

        public boolean accept(Node node) {
            try {
                return (nodeId == null || nodeId.equals(node.getId()))
                        && (hostIpPattern == null || accept(InetAddress.getByName(node.getHost())));
            } catch (UnknownHostException e) {
                return false;
            }
        }
    }
}
