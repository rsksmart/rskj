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

import org.bouncycastle.util.encoders.Hex;
import org.ethereum.net.client.Capability;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

/**
 * Wrapper around an Ethereum HelloMessage on the network
 *
 * @see org.ethereum.net.p2p.P2pMessageCodes#HELLO
 */
public class HelloMessage extends P2pMessage {

    /**
     * The implemented version of the P2P protocol.
     */
    private byte p2pVersion;
    /**
     * The underlying client. A user-readable string.
     */
    private String clientId;
    /**
     * A peer-network capability code, readable ASCII and 3 letters.
     * Currently only "eth", "shh" and "bzz" are known.
     */
    private List<Capability> capabilities = Collections.emptyList();
    /**
     * The port on which the peer is listening for an incoming connection
     */
    private int listenPort;
    /**
     * The identity and public key of the peer
     */
    private String peerId;

    public HelloMessage(byte[] encoded) {
        super(encoded);
    }

    public HelloMessage(byte p2pVersion, String clientId,
                        List<Capability> capabilities, int listenPort, String peerId) {
        this.p2pVersion = p2pVersion;
        this.clientId = clientId;
        this.capabilities = capabilities;
        this.listenPort = listenPort;
        this.peerId = peerId;
        this.parsed = true;
    }

    private void parse() {
        RLPList paramsList = RLP.decodeList(encoded);

        byte[] p2pVersionBytes = paramsList.get(0).getRLPData();
        this.p2pVersion = p2pVersionBytes != null ? p2pVersionBytes[0] : 0;

        byte[] clientIdBytes = paramsList.get(1).getRLPData();
        this.clientId = new String(clientIdBytes != null ? clientIdBytes : EMPTY_BYTE_ARRAY);

        RLPList capabilityList = (RLPList) paramsList.get(2);
        this.capabilities = new ArrayList<>();

        for (int k = 0; k < capabilityList.size(); k++) {
            Object aCapabilityList = capabilityList.get(k);

            RLPElement capId = ((RLPList) aCapabilityList).get(0);
            RLPElement capVersion = ((RLPList) aCapabilityList).get(1);

            String name = new String(capId.getRLPData());
            byte version = capVersion.getRLPData() == null ? 0 : capVersion.getRLPData()[0];

            Capability cap = new Capability(name, version);
            this.capabilities.add(cap);
        }

        byte[] peerPortBytes = paramsList.get(3).getRLPData();
        this.listenPort = ByteUtil.byteArrayToInt(peerPortBytes);

        byte[] peerIdBytes = paramsList.get(4).getRLPData();
        this.peerId = ByteUtil.toHexString(peerIdBytes);
        this.parsed = true;
    }

    private void encode() {
        byte[] p2pVersion = RLP.encodeByte(this.p2pVersion);
        byte[] clientId = RLP.encodeString(this.clientId);
        byte[][] capabilities = new byte[this.capabilities.size()][];
        for (int i = 0; i < this.capabilities.size(); i++) {
            Capability capability = this.capabilities.get(i);
            capabilities[i] = RLP.encodeList(
                    RLP.encodeString(capability.getName()),
                    RLP.encodeInt(capability.getVersion()));
        }
        byte[] capabilityList = RLP.encodeList(capabilities);
        byte[] peerPort = RLP.encodeInt(this.listenPort);
        byte[] peerId = RLP.encodeElement(Hex.decode(this.peerId));

        this.encoded = RLP.encodeList(p2pVersion, clientId,
                capabilityList, peerPort, peerId);
    }

    @Override
    public byte[] getEncoded() {
        if (encoded == null) {
            encode();
        }
        return encoded;
    }

    public byte getP2PVersion() {
        if (!parsed) {
            parse();
        }
        return p2pVersion;
    }

    public String getClientId() {
        if (!parsed) {
            parse();
        }
        return clientId;
    }

    public List<Capability> getCapabilities() {
        if (!parsed) {
            parse();
        }
        return capabilities;
    }

    public int getListenPort() {
        if (!parsed) {
            parse();
        }
        return listenPort;
    }

    public String getPeerId() {
        if (!parsed) {
            parse();
        }
        return peerId;
    }

    @Override
    public P2pMessageCodes getCommand() {
        return P2pMessageCodes.HELLO;
    }

    public void setPeerId(String peerId) {
        this.peerId = peerId;
    }

    public void setP2pVersion(byte p2pVersion) {
        this.p2pVersion = p2pVersion;
    }

    @Override
    public Class<?> getAnswerMessage() {
        return null;
    }

    public String toString() {
        if (!parsed) {
            parse();
        }

        return String.format(
                "[%s p2pVersion=%s clientId=%s capabilities=[%s] getPeerPort=%d peerId=%s]",
                this.getCommand().name(),
                this.p2pVersion,
                this.clientId,
                this.capabilities.stream().map(Capability::toString).collect(Collectors.joining(" ")),
                this.listenPort,
                this.peerId
        );
    }
}