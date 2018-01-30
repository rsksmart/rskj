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

import co.rsk.core.commons.Keccak256;
import co.rsk.net.MessageChannel;
import co.rsk.net.NodeID;
import co.rsk.net.messages.GetBlockMessage;
import co.rsk.net.messages.Message;
import co.rsk.net.messages.MessageType;
import org.junit.Assert;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Created by ajlopez on 5/11/2016.
 */
public class SimpleMessageChannel implements MessageChannel {
    private static Random random = new Random();
    private List<Message> messages = new ArrayList<>();
    private NodeID nodeID;
    private InetAddress address;

    public SimpleMessageChannel() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        this.nodeID = new NodeID(bytes);

        try {
            byte[] addressBytes = new byte[4];
            random.nextBytes(bytes);
            this.address = InetAddress.getByAddress(addressBytes);
        } catch (UnknownHostException e) {
            Assert.fail("SimpleMessageChannel creation failed");
        }
    }

    public SimpleMessageChannel(byte[] nodeID) {
        this.nodeID = new NodeID(nodeID);
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
                .map(message -> ((GetBlockMessage) message).getBlockHash())
                .collect(Collectors.toList());
    }

    public NodeID getPeerNodeID() {
        return nodeID;
    }

    @Override
    public void setPeerNodeID(byte[] peerNodeId) {
        this.nodeID = new NodeID(peerNodeId);
    }

    @Override
    public InetAddress getAddress() { return this.address; }

    @Override
    public void setAddress(InetAddress address) { this.address = address; }
}
