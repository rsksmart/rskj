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

package co.rsk.net.discovery.message;

import co.rsk.net.discovery.PeerDiscoveryException;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.*;

import java.nio.charset.StandardCharsets;
import java.util.OptionalInt;

import static org.ethereum.util.ByteUtil.intToBytes;
import static org.ethereum.util.ByteUtil.longToBytes;
import static org.ethereum.util.ByteUtil.stripLeadingZeroes;

/**
 * Created by mario on 16/02/17.
 */
public class PongPeerMessage extends PeerDiscoveryMessage {
    public static final String MORE_DATA = "PongPeerMessage needs more data";
    public static final String MORE_FROM_DATA = "PongPeerMessage needs more data in the from";
    private String host;
    private int port;
    private String messageId;

    private PongPeerMessage(byte[] wire, byte[] mdc, byte[] signature, byte[] type, byte[] data) {
        super(wire, mdc, signature, type, data);
        this.parse(data);
    }

    private PongPeerMessage() {
    }

    public static PongPeerMessage buildFromReceived(byte[] wire, byte[] mdc, byte[] signature, byte[] type, byte[] data) {
        return new PongPeerMessage(wire, mdc, signature, type, data);
    }

    public static PongPeerMessage create(String host, int port, String check, ECKey privKey, Integer networkId) {
        /* RLP Encode data */
        byte[] rlpIp = RLP.encodeElement(host.getBytes(StandardCharsets.UTF_8));

        byte[] tmpPort = longToBytes(port);
        byte[] rlpPort = RLP.encodeElement(stripLeadingZeroes(tmpPort));

        byte[] rlpIpTo = RLP.encodeElement(host.getBytes(StandardCharsets.UTF_8));
        byte[] tmpPortTo = longToBytes(port);
        byte[] rlpPortTo = RLP.encodeElement(stripLeadingZeroes(tmpPortTo));

        byte[] rlpCheck = RLP.encodeElement(check.getBytes(StandardCharsets.UTF_8));

        byte[] type = new byte[]{(byte) DiscoveryMessageType.PONG.getTypeValue()};
        byte[] rlpFromList = RLP.encodeList(rlpIp, rlpPort, rlpPort);
        byte[] rlpToList = RLP.encodeList(rlpIpTo, rlpPortTo, rlpPortTo);
        byte[] data;

        byte[] tmpNetworkId = intToBytes(networkId);
        byte[] rlpNetworkID = RLP.encodeElement(stripLeadingZeroes(tmpNetworkId));
        data = RLP.encodeList(rlpFromList, rlpToList, rlpCheck, rlpNetworkID);

        PongPeerMessage message = new PongPeerMessage();
        message.encode(type, data, privKey);

        message.setNetworkId(OptionalInt.of(networkId));
        message.messageId = check;
        message.host = host;
        message.port = port;

        return message;
    }

    public String getHost() {
        return this.host;
    }

    public int getPort() {
        return this.port;
    }

    @Override
    protected final void parse(byte[] data) {
        RLPList dataList = (RLPList) RLP.decode2OneItem(data, 0);
        if (dataList.size() < 3) {
            throw new PeerDiscoveryException(MORE_DATA);
        }
        RLPList fromList = (RLPList) dataList.get(1);

        if (fromList.size() != 3) {
            throw new PeerDiscoveryException(MORE_FROM_DATA);
        }

        byte[] ipB = fromList.get(0).getRLPData();
        this.host = new String(ipB, StandardCharsets.UTF_8);
        this.port = ByteUtil.byteArrayToInt(fromList.get(1).getRLPData());

        RLPItem chk = (RLPItem) dataList.get(2);
        this.messageId = extractMessageId(chk);

        //Message from nodes that do not have this
        this.setNetworkIdWithRLP(dataList.size() > 3 ? dataList.get(3) : null);
    }

    public String getMessageId() {
        return this.messageId;
    }

    @Override
    public DiscoveryMessageType getMessageType() {
        return DiscoveryMessageType.PONG;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append(this.host)
                .append(this.port)
                .append(this.messageId).toString();
    }
}
