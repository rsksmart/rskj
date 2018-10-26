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

package co.rsk.net.eth;

import co.rsk.net.MessageChannel;
import co.rsk.net.NodeID;
import co.rsk.net.messages.Message;
import org.ethereum.net.eth.handler.Eth;

import javax.annotation.Nonnull;
import java.net.InetAddress;

/**
 * EthMessageSender implements the MessageChannel interface.
 * <p>
 * Created by ajlopez on 5/16/2016.
 */
public class EthMessageSender implements MessageChannel {
    private final Eth eth;
    private NodeID nodeID;
    private InetAddress address;

    /**
     * EthMessageSender creates a new message sender.
     *
     * @param eth the underlying ethereum peer interface
     */
    public EthMessageSender(@Nonnull final Eth eth) {
        this.eth = eth;
    }


    /**
     * sendMessage sends a message to this node.
     *
     * @param message the message to be sent.
     */
    public void sendMessage(@Nonnull final Message message) {
        this.eth.sendMessage(new RskMessage(message));
    }

    /**
     * getNodeID returns the messageSender's nodeID
     *
     * @return the corresponding NodeID.
     */
    @Nonnull
    public NodeID getPeerNodeID() {
        return this.nodeID;
    }

    @Override
    public void setPeerNodeID(@Nonnull final NodeID peerNodeId) {
        this.nodeID = peerNodeId;
    }

    @Override
    public InetAddress getAddress() { return this.address; }

    @Override
    public void setAddress(InetAddress address) { this.address = address; }

    @Override
    public String toString() {
        return "EthMessageSender{" +
                "nodeID=" + nodeID +
                '}';
    }
}
