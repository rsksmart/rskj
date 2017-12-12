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

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPItem;
import org.ethereum.util.RLPList;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.ethereum.util.ByteUtil.longToBytes;
import static org.ethereum.util.ByteUtil.stripLeadingZeroes;

/**
 * Created by mario on 16/02/17.
 */
public class PongPeerMessage extends PeerDiscoveryMessage {
    private String host;
    private int port;
    private String messageId;

    public PongPeerMessage(byte[] wire, byte[] mdc, byte[] signature, byte[] type, byte[] data) {
        super(wire, mdc, signature, type, data);
        this.parse(data);
    }

    private PongPeerMessage() {
    }

    public static PongPeerMessage create(String host, int port, String check, ECKey privKey) {
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
        byte[] data = RLP.encodeList(rlpFromList, rlpToList, rlpCheck);

        PongPeerMessage message = new PongPeerMessage();
        message.encode(type, data, privKey);

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
    public final void parse(byte[] data) {
        RLPList dataList = (RLPList) RLP.decode2OneItem(data, 0);
        RLPList fromList = (RLPList) dataList.get(1);

        byte[] ipB = fromList.get(0).getRLPData();
        this.host = new String(ipB, Charset.forName("UTF-8"));
        this.port = ByteUtil.byteArrayToInt(fromList.get(1).getRLPData());

        RLPItem chk = (RLPItem) dataList.get(2);

        this.messageId = new String(chk.getRLPData(), Charset.forName("UTF-8"));
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
