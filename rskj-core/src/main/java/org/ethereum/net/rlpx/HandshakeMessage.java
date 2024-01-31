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

package org.ethereum.net.rlpx;

import org.ethereum.net.client.Capability;
import org.ethereum.util.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.ethereum.util.ByteUtil.longToBytes;

/**
 * Created by devrandom on 2015-04-12.
 */
public class HandshakeMessage {
    public static final int HANDSHAKE_MESSAGE_TYPE = 0x00;
    long version;
    String name;
    List<Capability> caps;
    long listenPort;
    byte[] nodeId;

    public static final int NODE_ID_BITS = 512;

    public HandshakeMessage(long version, String name, List<Capability> caps, long listenPort, byte[] nodeId) {
        this.version = version;
        this.name = name;
        this.caps = caps;
        this.listenPort = listenPort;
        this.nodeId = nodeId;
    }

    HandshakeMessage() {
    }

    static HandshakeMessage parse(byte[] wire) {
        RLPList list = RLP.decodeList(wire);
        HandshakeMessage message = new HandshakeMessage();
        message.version = ByteUtil.byteArrayToInt(list.get(0).getRLPData()); // FIXME long
        message.name = new String(list.get(1).getRLPData(), StandardCharsets.UTF_8);
        // caps
        message.caps = new ArrayList<>();

        RLPList capElements = (RLPList)list.get(2);

        for (int k = 0; k < capElements.size(); k++) {
            RLPElement capEl = capElements.get(k);
            RLPList capElList = (RLPList)capEl;
            String name = new String(capElList.get(0).getRLPData(), StandardCharsets.UTF_8);
            long version = ByteUtil.byteArrayToInt(capElList.get(1).getRLPData());

            message.caps.add(new Capability(name, (byte)version)); // FIXME long
        }

        message.listenPort = ByteUtil.byteArrayToInt(list.get(3).getRLPData());
        message.nodeId = list.get(4).getRLPData();

        return message;
    }

    public byte[] encode() {
        List<byte[]> capsItemBytes = new ArrayList<>();
        for (Capability cap : caps) {
            capsItemBytes.add(RLP.encodeList(
                    RLP.encodeElement(cap.getName().getBytes(StandardCharsets.UTF_8)),
                    RLP.encodeElement(ByteUtil.stripLeadingZeroes(longToBytes(cap.getVersion())))
            ));
        }
        return RLP.encodeList(
                RLP.encodeElement(ByteUtil.stripLeadingZeroes(longToBytes(version))),
                RLP.encodeElement(name.getBytes(StandardCharsets.UTF_8)),
                RLP.encodeList(capsItemBytes.toArray(new byte[0][])),
                RLP.encodeElement(ByteUtil.stripLeadingZeroes(longToBytes(listenPort))),
                RLP.encodeElement(nodeId)
        );
    }
}
