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
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPItem;
import org.ethereum.util.RLPList;

import java.nio.charset.StandardCharsets;
import java.util.OptionalInt;

import static org.ethereum.util.ByteUtil.intToBytes;
import static org.ethereum.util.ByteUtil.stripLeadingZeroes;

/**
 * Created by mario on 16/02/17.
 */
public class FindNodePeerMessage extends PeerDiscoveryMessage {

    public static final String MORE_DATA = "FindNodePeerMessage needs more data";
    private byte[] nodeId;
    private String messageId;

    private FindNodePeerMessage(byte[] wire, byte[] mdc, byte[] signature, byte[] type, byte[] data) {
        super(wire, mdc, signature, type, data);
        this.parse(data);
    }

    private FindNodePeerMessage() {
    }
    
    public static FindNodePeerMessage buildFromReceived(byte[] wire, byte[] mdc, byte[] signature, byte[] type, byte[] data) {
        return new FindNodePeerMessage(wire, mdc, signature, type, data);
    }

    public static FindNodePeerMessage create(byte[] nodeId, String check, ECKey privKey, Integer networkId) {

        /* RLP Encode data */
        byte[] rlpCheck = RLP.encodeElement(check.getBytes(StandardCharsets.UTF_8));
        byte[] rlpNodeId = RLP.encodeElement(nodeId);

        byte[] type = new byte[]{(byte) DiscoveryMessageType.FIND_NODE.getTypeValue()};

        byte[] data;
        byte[] rlpNetworkId = RLP.encodeElement(stripLeadingZeroes(intToBytes(networkId)));
        data = RLP.encodeList(rlpNodeId, rlpCheck, rlpNetworkId);

        FindNodePeerMessage message = new FindNodePeerMessage();
        message.encode(type, data, privKey);

        message.messageId = check;
        message.nodeId = nodeId;
        message.setNetworkId(OptionalInt.of(networkId));

        return message;
    }

    @Override
    protected final void parse(byte[] data) {
        RLPList dataList = RLP.decodeList(data);
        if (dataList.size() < 2) {
            throw new PeerDiscoveryException(MORE_DATA);
        }
        RLPItem chk = (RLPItem) dataList.get(1);

        this.messageId = extractMessageId(chk);

        RLPItem nodeRlp = (RLPItem) dataList.get(0);

        this.nodeId = nodeRlp.getRLPData();

        this.setNetworkIdWithRLP(dataList.size() > 2 ? dataList.get(2) : null);
    }

    public String getMessageId() {
        return this.messageId;
    }

    @Override
    public DiscoveryMessageType getMessageType() {
        return DiscoveryMessageType.FIND_NODE;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append(ByteUtil.toHexString(this.nodeId))
                .append(this.getNetworkId())
                .append(this.messageId).toString();
    }

}
