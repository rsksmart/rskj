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

package org.ethereum.net.eth.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.EthereumListener;
import org.ethereum.listener.EthereumListenerAdapter;
import org.ethereum.net.MessageQueue;
import org.ethereum.net.eth.EthVersion;
import org.ethereum.net.eth.message.EthMessage;
import org.ethereum.net.eth.message.EthMessageCodes;
import org.ethereum.net.eth.message.StatusMessage;
import org.ethereum.net.message.ReasonCode;
import org.ethereum.net.server.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Process the messages between peers with 'eth' capability on the network<br>
 * Contains common logic to all supported versions
 * delegating version specific stuff to its descendants
 *
 */
public abstract class EthHandler extends SimpleChannelInboundHandler<EthMessage> implements Eth {

    private static final Logger logger = LoggerFactory.getLogger("net");

    protected final CompositeEthereumListener ethereumListener;
    protected Channel channel;

    private MessageQueue msgQueue = null;

    protected EthVersion version;

    protected Block bestBlock;
    protected EthereumListener listener = new EthereumListenerAdapter() {
        @Override
        public void onBlock(Block block, List<TransactionReceipt> receipts) {
            bestBlock = block;
        }
    };

    protected int maxHashesAsk;

    protected boolean processTransactions = false;

    protected EthHandler(Blockchain blockchain, SystemProperties config, CompositeEthereumListener ethereumListener, EthVersion version) {
        this.ethereumListener = ethereumListener;
        this.version = version;
        maxHashesAsk = config.maxHashesAsk();
        bestBlock = blockchain.getBestBlock();
        ethereumListener.addListener(listener);
        // when sync enabled we delay transactions processing until sync is complete
        processTransactions = !config.isSyncEnabled();
    }

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, EthMessage msg) throws InterruptedException {
        logger.debug("Read message: " + msg.toString());

        if (EthMessageCodes.inRange(msg.getCommand().asByte(), version)) {
            logger.trace("EthHandler invoke: [{}]", msg.getCommand());
        }

        ethereumListener.trace(String.format("EthHandler invoke: [%s]", msg.getCommand()));

        channel.getNodeStatistics().ethInbound.add();

        msgQueue.receivedMessage(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("Eth handling failed", cause);
        ctx.close();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        logger.debug("handlerRemoved: kill timers in EthHandler");
        ethereumListener.removeListener(listener);
        onShutdown();
    }

    public void activate() {
        logger.info("RSK protocol activated");
        ethereumListener.trace("RSK protocol activated");
        sendStatus();
    }

    protected void disconnect(ReasonCode reason) {
        msgQueue.disconnect(reason);
        channel.getNodeStatistics().nodeDisconnectedLocal(reason);
    }

    @Override
    public void sendMessage(EthMessage message) {
        logger.debug("Send message: " + message.toString());

        msgQueue.sendMessage(message);
        channel.getNodeStatistics().ethOutbound.add();
    }

    public StatusMessage getHandshakeStatusMessage() {
        return channel.getNodeStatistics().getEthLastInboundStatusMsg();
    }

    public void setMsgQueue(MessageQueue msgQueue) {
        this.msgQueue = msgQueue;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }
}