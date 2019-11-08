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

package org.ethereum.net.p2p;

import org.ethereum.net.client.Capability;
import org.ethereum.util.RLP;

import org.bouncycastle.util.encoders.Hex;

import java.net.InetAddress;

import java.util.ArrayList;
import java.util.List;

/**
 * This class models a peer in the network
 */
public class PeerConnectionData {

    private final InetAddress address;
    private final int port;
    private final String peerId;
    private final List<Capability> capabilities;

    public PeerConnectionData(InetAddress ip, int port, String peerId) {
        this.address = ip;
        this.port = port;
        this.peerId = peerId;
        this.capabilities = new ArrayList<>();
    }

    public InetAddress getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public String getPeerId() {
        return peerId == null ? "" : peerId;
    }

    public List<Capability> getCapabilities() {
        return new ArrayList<>(capabilities);
    }

    public byte[] getEncoded() {
        byte[] ip = RLP.encodeElement(this.address.getAddress());
        byte[] port = RLP.encodeInt(this.port);
        byte[] peerId = RLP.encodeElement(Hex.decode(this.peerId));
        byte[][] encodedCaps = new byte[this.capabilities.size()][];
        for (int i = 0; i < this.capabilities.size() * 2; i++) {
            encodedCaps[i] = RLP.encodeByte(this.capabilities.get(i).getVersion());
        }
        byte[] capabilities = RLP.encodeList(encodedCaps);
        return RLP.encodeList(ip, port, peerId, capabilities);
    }

    @Override
    public String toString() {
        return "[ip=" + getAddress().getHostAddress() +
                " port=" + getPort()
                + " peerId=" + getPeerId() + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (this.getClass() != obj.getClass()) {
            return false;
        }

        PeerConnectionData peerData = (PeerConnectionData) obj;
        return peerData.peerId.equals(this.peerId)
                || this.getAddress().equals(peerData.getAddress());
    }

    @Override
    public int hashCode() {
        int result = peerId.hashCode();
        result = 31 * result + address.hashCode();
        result = 31 * result + port;
        return result;
    }
}
