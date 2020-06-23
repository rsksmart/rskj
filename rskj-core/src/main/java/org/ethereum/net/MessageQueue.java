/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package org.ethereum.net;

import io.netty.channel.ChannelHandlerContext;
import co.rsk.panic.PanicProcessor;
import org.ethereum.net.message.Message;
import org.ethereum.net.message.ReasonCode;
import org.ethereum.net.p2p.DisconnectMessage;
import org.ethereum.net.server.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.ethereum.net.message.StaticMessages.DISCONNECT_MESSAGE;

/**
 * This class contains the logic for sending messages in a queue
 *
 * Messages open by send and answered by receive of appropriate message
 *      PING by PONG
 *      GET_PEERS by PEERS
 *      GET_TRANSACTIONS by TRANSACTIONS
 *      GET_BLOCK_HASHES by BLOCK_HASHES
 *      GET_BLOCKS by BLOCKS
 *
 * The following messages will not be answered:
 *      PONG, PEERS, HELLO, STATUS, TRANSACTIONS, BLOCKS
 *
 * @author Roman Mandeleil
 */
public class MessageQueue {

    private static final Logger logger = LoggerFactory.getLogger("net");
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    private ChannelHandlerContext ctx = null;

    public MessageQueue() {
    }

    public void activate(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    public void sendMessage(Message msg) {
        ctx.writeAndFlush(msg);
    }

    public void disconnect() {
        disconnect(DISCONNECT_MESSAGE);
    }

    public void disconnect(ReasonCode reason) {
        disconnect(new DisconnectMessage(reason));
    }

    private void disconnect(DisconnectMessage msg) {
        ctx.writeAndFlush(msg);
        ctx.close();
    }

    public void close() {
    }
}
