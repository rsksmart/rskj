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

import co.rsk.net.MessageChannel;
import co.rsk.net.NodeID;
import co.rsk.net.messages.GetBlockMessage;
import co.rsk.net.messages.Message;
import co.rsk.net.messages.MessageType;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.ByteArrayWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by ajlopez on 5/11/2016.
 */
public class SimpleMessageChannel implements MessageChannel {
    private List<Message> messages = new ArrayList<>();
    private NodeID nodeID = new NodeID(HashUtil.randomPeerId());

    public SimpleMessageChannel() {

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

    public List<ByteArrayWrapper> getGetBlockMessagesHashes() {
        return this.messages.stream()
                .filter(message -> message.getMessageType() == MessageType.GET_BLOCK_MESSAGE)
                .map(message -> new ByteArrayWrapper(((GetBlockMessage) message).getBlockHash()))
                .collect(Collectors.toList());
    }

    public NodeID getPeerNodeID() {
        return nodeID;
    }

    @Override
    public void setPeerNodeID(byte[] peerNodeId) {
        this.nodeID = new NodeID(peerNodeId);
    }
}
