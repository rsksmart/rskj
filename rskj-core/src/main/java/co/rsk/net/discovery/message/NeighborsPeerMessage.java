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
import org.ethereum.net.rlpx.Node;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPItem;
import org.ethereum.util.RLPList;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import static org.ethereum.util.ByteUtil.intToBytes;
import static org.ethereum.util.ByteUtil.stripLeadingZeroes;

/**
 * Created by mario on 16/02/17.
 */
public class NeighborsPeerMessage extends PeerDiscoveryMessage {
    public static final String MORE_DATA = "NeighborsPeerMessage needs more data";
    private List<Node> nodes;
    private String messageId;

    private NeighborsPeerMessage(byte[] wire, byte[] mdc, byte[] signature, byte[] type, byte[] data) {
        super(wire, mdc, signature, type, data);
        this.nodes = new ArrayList<>();
        this.parse(data);
    }

    private NeighborsPeerMessage() {
    }

    public static NeighborsPeerMessage buildFromReceived(byte[] wire, byte[] mdc, byte[] signature, byte[] type, byte[] data) {
        return new NeighborsPeerMessage(wire, mdc, signature, type, data);
    }

    public static NeighborsPeerMessage create(List<Node> nodes, String check, ECKey privKey, Integer networkId) {
        byte[][] nodeRLPs = null;

        if (nodes != null) {
            nodeRLPs = new byte[nodes.size()][];
            int i = 0;
            for (Node node : nodes) {
                nodeRLPs[i] = node.getRLP();
                ++i;
            }
        }

        byte[] rlpListNodes = RLP.encodeList(nodeRLPs);
        byte[] rlpCheck = RLP.encodeElement(check.getBytes(StandardCharsets.UTF_8));

        byte[] type = new byte[]{(byte) DiscoveryMessageType.NEIGHBORS.getTypeValue()};
        byte[] data;
        byte[] tmpNetworkId = intToBytes(networkId);
        byte[] rlpNetworkId = RLP.encodeElement(stripLeadingZeroes(tmpNetworkId));
        data = RLP.encodeList(rlpListNodes, rlpCheck, rlpNetworkId);

        NeighborsPeerMessage neighborsMessage = new NeighborsPeerMessage();
        neighborsMessage.encode(type, data, privKey);
        neighborsMessage.setNetworkId(OptionalInt.of(networkId));
        neighborsMessage.nodes = nodes;
        neighborsMessage.messageId = check;

        return neighborsMessage;
    }

    @Override
    protected final void parse(byte[] data) {
        RLPList list = RLP.decodeList(data);

        if (list.size() < 2) {
            throw new PeerDiscoveryException(MORE_DATA);
        }

        RLPList nodesRLP = (RLPList) list.get(0);

        for (int i = 0; i < nodesRLP.size(); ++i) {
            RLPList nodeRLP = (RLPList) nodesRLP.get(i);
            Node node = new Node(nodeRLP.getRLPData());
            nodes.add(node);
        }

        RLPItem chk = (RLPItem) list.get(1);
        this.messageId = extractMessageId(chk);

        this.setNetworkIdWithRLP(list.size() > 2 ? list.get(2) : null);
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public String getMessageId() {
        return this.messageId;
    }

    @Override
    public DiscoveryMessageType getMessageType() {
        return DiscoveryMessageType.NEIGHBORS;
    }

    public int countNodes() {
        return this.nodes.size();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append(this.nodes)
                .append(this.messageId)
                .append(this.getNetworkId()).toString();
    }

}
