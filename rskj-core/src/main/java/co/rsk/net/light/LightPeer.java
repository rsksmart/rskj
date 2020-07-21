/*
 * This file is part of RskJ
 * Copyright (C) 2020 RSK Labs Ltd.
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

package co.rsk.net.light;

import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.BlockHeader;
import org.ethereum.net.MessageQueue;
import org.ethereum.net.message.Message;
import org.ethereum.net.message.ReasonCode;
import org.ethereum.net.server.Channel;

import java.util.LinkedList;
import java.util.List;

public class LightPeer {

    private final LinkedList<BlockHeader> blockHeaders;
    private final Channel channel;
    private final MessageQueue msgQueue;

    public LightPeer(Channel channel, MessageQueue msgQueue) {
        this.channel = channel;
        this.msgQueue = msgQueue;
        this.blockHeaders = new LinkedList<>(); //This is a dummy chain of headers. It is going to be replaced by an unique header chain
    }

    public String getPeerIdShort() {
        return channel.getPeerIdShort();
    }

    public void sendMessage(Message message) {
        msgQueue.sendMessage(message);
    }

    public void disconnect(ReasonCode reasonCode) {
        msgQueue.disconnect(reasonCode);
    }

    public void receivedBlockHeaders(List<BlockHeader> blockHeaders) {
        this.blockHeaders.addAll(blockHeaders);
    }

    @VisibleForTesting
    public List<BlockHeader> getBlocks() {
        return new LinkedList<>(blockHeaders);
    }
}
