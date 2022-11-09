/*
 * This file is part of RskJ
 * Copyright (C) 2022 RSK Labs Ltd.
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
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.*;

class NodeFilterTest {

    private static final String NODE_ID_1 = "826fbe97bc03c7c09d7b7d05b871282d8ac93d4446d44b55566333b240dd06260a9505f0fd3247e63d84d557f79bb63691710e40d4d9fc39f3bfd5397bcea065";
    private static final String NODE_ID_2 = "3c7931f323989425a1e56164043af0dff567f33df8c67d4c6918647535f88798d54bc864b936d8c77d4096e8b8485b6061b0d0d2b708cd9154e6dcf981533261";
    private static final String NODE_ID_3 = "e229918d45c131e130c91c4ea51c97ab4f66cfbd0437b35c92392b5c2b3d44b28ea15b84a262459437c955f6cc7f10ad1290132d3fc866bfaf4115eac0e8e860";

    private static final String HOST_1 = "127.0.0.1";
    private static final String HOST_2_PATTERN = "192.168.0.*";

    @Test
    void accept() throws UnknownHostException {
        NodeFilter nodeFilter = new NodeFilter();

        assertFalse(nodeFilter.accept(new NodeID(NODE_ID_1.getBytes()), InetAddress.getByName(HOST_1)));
        assertFalse(nodeFilter.accept(new NodeID(NODE_ID_2.getBytes()), InetAddress.getByName("192.168.0.1")));
        assertFalse(nodeFilter.accept(new NodeID(NODE_ID_3.getBytes()), InetAddress.getByName("192.168.1.1")));

        nodeFilter.add(NODE_ID_1.getBytes(), HOST_1);
        nodeFilter.add(NODE_ID_2.getBytes(), HOST_2_PATTERN);
        nodeFilter.add(NODE_ID_3.getBytes(), null);

        assertTrue(nodeFilter.accept(new NodeID(NODE_ID_1.getBytes()), InetAddress.getByName(HOST_1)));
        assertTrue(nodeFilter.accept(new NodeID(NODE_ID_2.getBytes()), InetAddress.getByName("192.168.0.1")));
        assertTrue(nodeFilter.accept(new NodeID(NODE_ID_2.getBytes()), InetAddress.getByName("192.168.0.2")));
        assertTrue(nodeFilter.accept(new NodeID(NODE_ID_3.getBytes()), InetAddress.getByName("192.168.1.1")));

        assertFalse(nodeFilter.accept(new NodeID(NODE_ID_1.getBytes()), InetAddress.getByName("127.0.0.2")));
        assertFalse(nodeFilter.accept(new NodeID(NODE_ID_1.getBytes()), InetAddress.getByName("192.168.0.1")));
        assertFalse(nodeFilter.accept(new NodeID(NODE_ID_2.getBytes()), InetAddress.getByName(HOST_1)));
        assertFalse(nodeFilter.accept(new NodeID(NODE_ID_2.getBytes()), InetAddress.getByName("192.168.1.1")));
    }
}