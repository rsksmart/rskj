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

import co.rsk.config.TestSystemProperties;
import co.rsk.net.Peer;
import co.rsk.net.NodeID;
import co.rsk.net.messages.Message;
import co.rsk.net.messages.MessageType;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.BlockFactory;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.net.InetAddress;
import java.util.Objects;

import static org.mockito.Mockito.mock;

public class SimpleNodeChannel implements Peer {
    private SimpleNode sender;
    private SimpleNode receiver;
    private NodeID nodeID = new NodeID(new byte[]{});

    public SimpleNodeChannel(SimpleNode sender, SimpleNode receiver) {
        this.sender = sender;
        this.receiver = receiver;

        if (receiver != null) {
            this.nodeID = receiver.getNodeID();
        }
    }

    public void sendMessage(Message message) {
        if (this.receiver != null)
            // this.receiver.receiveMessageFrom(this.sender, message);
            this.receiver.receiveMessageFrom(this.sender, Message.create(
                    new BlockFactory(ActivationConfigsForTest.all()), RLP.decodeList(message.getEncoded())
            ));
    }

    public NodeID getPeerNodeID() {
        return this.nodeID;
    }

    @Override
    public InetAddress getAddress() { return null; }

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

        SimpleNodeChannel channel = (SimpleNodeChannel) o;

        return Objects.equals(nodeID, channel.nodeID);

    }

    @Override
    public int hashCode() {
        return nodeID != null ? nodeID.hashCode() : 0;
    }
}
