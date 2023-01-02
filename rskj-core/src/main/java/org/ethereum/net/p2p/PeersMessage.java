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

import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Wrapper around an Ethereum Peers message on the network
 *
 * @see org.ethereum.net.p2p.P2pMessageCodes#PEERS
 */
public class PeersMessage extends P2pMessage {

    private boolean pmParsed = false;

    private Set<PeerConnectionData> peers;

    public PeersMessage(byte[] payload) {
        super(payload);
    }

    public PeersMessage(Set<PeerConnectionData> peers) {
        this.peers = peers;
        pmParsed = true;
    }

    private void parse() {
        RLPList paramsList = (RLPList) RLP.decode2(encoded).get(0);

        peers = new LinkedHashSet<>();
        for (int i = 1; i < paramsList.size(); ++i) {
            RLPList peerParams = (RLPList) paramsList.get(i);
            byte[] ipBytes = peerParams.get(0).getRLPData();
            byte[] portBytes = peerParams.get(1).getRLPData();
            byte[] peerIdRaw = peerParams.get(2).getRLPData();

            try {
                int peerPort = ByteUtil.byteArrayToInt(portBytes);
                InetAddress address = InetAddress.getByAddress(ipBytes);

                String peerId = peerIdRaw == null ? "" : ByteUtil.toHexString(peerIdRaw);
                PeerConnectionData peer = new PeerConnectionData(address, peerPort, peerId);
                peers.add(peer);
            } catch (UnknownHostException e) {
                throw new RuntimeException("Malformed ip", e);
            }
        }
        this.pmParsed = true;
    }

    private void encode() {
        byte[][] encodedByteArrays = new byte[this.peers.size() + 1][];
        encodedByteArrays[0] = RLP.encodeByte(this.getCommand().asByte());
        List<PeerConnectionData> peerList = new ArrayList<>(this.peers);
        for (int i = 0; i < peerList.size(); i++) {
            encodedByteArrays[i + 1] = peerList.get(i).getEncoded();
        }
        this.encoded = RLP.encodeList(encodedByteArrays);
    }

    @Override
    public byte[] getEncoded() {
        if (encoded == null) {
            encode();
        }
        return encoded;
    }

    public Set<PeerConnectionData> getPeers() {
        if (!pmParsed) {
            this.parse();
        }
        return peers;
    }

    @Override
    public P2pMessageCodes getCommand() {
        return P2pMessageCodes.PEERS;
    }

    @Override
    public Class<?> getAnswerMessage() {
        return null;
    }

    public String toString() {
        if (!pmParsed) {
            this.parse();
        }

        StringBuilder sb = new StringBuilder();
        for (PeerConnectionData peerData : peers) {
            sb.append("\n       ").append(peerData);
        }
        return "[" + this.getCommand().name() + sb.toString() + "]";
    }
}