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

package co.rsk.net.simples;

import co.rsk.crypto.Keccak256;
import co.rsk.net.NodeID;
import co.rsk.net.Peer;
import co.rsk.net.messages.GetBlockMessage;
import co.rsk.net.messages.Message;
import co.rsk.net.messages.MessageType;
import org.ethereum.TestUtils;
import org.junit.jupiter.api.Assertions;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Created by ajlopez on 5/11/2016.
 */
public class SimplePeer implements Peer {
    private List<Message> messages = new ArrayList<>();
    private NodeID nodeID;
    private InetAddress address;

    public SimplePeer(NodeID nodeID) {
        this.nodeID = nodeID;
        initAddress();
    }

    public SimplePeer() {
        this.nodeID = new NodeID(TestUtils.generateBytes("nodeID",32));
        initAddress();
    }

    public SimplePeer(byte[] nodeID) {
        this.nodeID = new NodeID(nodeID);
    }

    private void initAddress(){
        try {
            byte[] addressBytes = new byte[4];
            this.address = InetAddress.getByAddress(addressBytes);
        } catch (UnknownHostException e) {
            Assertions.fail("SimplePeer creation failed");
        }
    }

    public void sendMessage(Message message) {
        this.messages.add(message);
    }

    public List<Message> getMessages() {
        return this.messages;
    }

    public List<Message> getGetBlockMessages() {
        return this.messages.stream()
                .filter(message -> message.getMessageType() == MessageType.GET_BLOCK_MESSAGE)
                .collect(Collectors.toList());
    }

    public List<Keccak256> getGetBlockMessagesHashes() {
        return this.messages.stream()
                .filter(message -> message.getMessageType() == MessageType.GET_BLOCK_MESSAGE)
                .map(message -> new Keccak256(((GetBlockMessage) message).getBlockHash()))
                .collect(Collectors.toList());
    }

    public NodeID getPeerNodeID() {
        return nodeID;
    }

    @Override
    public InetAddress getAddress() { return this.address; }

    @Override
    public double score(long currentTime, MessageType type) {
        return 0;
    }

    @Override
    public void imported(boolean best) {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SimplePeer channel = (SimplePeer) o;

        return Objects.equals(address, channel.address) &&
                Objects.equals(nodeID, channel.nodeID);

    }

    @Override
    public int hashCode() {
        int result = address != null ? address.hashCode() : 0;
        result = 31 * result + (nodeID != null ? nodeID.hashCode() : 0);
        return result;
    }
}
