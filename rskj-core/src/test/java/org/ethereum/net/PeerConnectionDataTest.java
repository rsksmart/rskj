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

import org.ethereum.net.client.Capability;
import org.ethereum.net.p2p.PeerConnectionData;
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PeerConnectionDataTest {

    /* PEER */

    @Test
    public void testPeer() {

        //Init
        InetAddress address = InetAddress.getLoopbackAddress();
        List<Capability> capabilities = new ArrayList<>();
        int port = 1010;
        String peerId = "1010";
        PeerConnectionData peerCopy = new PeerConnectionData(address, port, peerId);

        //PeerConnectionData
        PeerConnectionData peer = new PeerConnectionData(address, port, peerId);

        //getAddress
        assertEquals("127.0.0.1", peer.getAddress().getHostAddress());

        //getPort
        assertEquals(port, peer.getPort());

        //getPeerId
        assertEquals(peerId, peer.getPeerId());

        //getCapabilities
        assertEquals(capabilities, peer.getCapabilities());

        //getEncoded
        assertEquals("CC847F0000018203F2821010C0", ByteUtil.toHexString(peer.getEncoded()).toUpperCase());

        //toString
        assertEquals("[ip=" + address.getHostAddress() + " port=" + Integer.toString(port) + " peerId=" + peerId + "]", peer.toString());

        //equals
        assertEquals(true, peer.equals(peerCopy));
        assertEquals(false, peer.equals(null));

        //hashCode
        assertEquals(-1218913009, peer.hashCode());
    }
}

