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

package org.ethereum.net.p2p;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.ethereum.listener.EthereumListener;
import org.ethereum.net.MessageQueue;
import org.ethereum.net.message.ReasonCode;
import org.ethereum.net.server.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

import static org.ethereum.net.message.StaticMessages.PING_MESSAGE;
import static org.ethereum.net.message.StaticMessages.PONG_MESSAGE;

/**
 * Process the basic protocol messages between every peer on the network.
 *
 * Peers can send/receive
 * <ul>
 *  <li>HELLO       :   Announce themselves to the network</li>
 *  <li>DISCONNECT  :   Disconnect themselves from the network</li>
 *  <li>GET_PEERS   :   Request a list of other knows peers</li>
 *  <li>PEERS       :   Send a list of known peers</li>
 *  <li>PING        :   Check if another peer is still alive</li>
 *  <li>PONG        :   Confirm that they themselves are still alive</li>
 * </ul>
 */
public class P2pHandler extends SimpleChannelInboundHandler<P2pMessage> {

    public static final byte VERSION = 4;

    private static final byte[] SUPPORTED_VERSIONS = {4, 5};

    private static final Logger logger = LoggerFactory.getLogger("net");

    private static ScheduledExecutorService pingTimer =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "P2pPingTimer"));

    private int ethInbound;
    private int ethOutbound;

    private final EthereumListener ethereumListener;
    private final MessageQueue msgQueue;
    private final int pingInterval;

    public P2pHandler(
            EthereumListener ethereumListener,
            MessageQueue msgQueue,
            int pingInterval) {
        this.ethereumListener = ethereumListener;
        this.msgQueue = msgQueue;
        this.pingInterval = pingInterval;
    }

    private Channel channel;
    private ScheduledFuture<?> pingTask;


    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        logger.info("P2P protocol activated");
        msgQueue.activate(ctx);
        ethereumListener.trace("P2P protocol activated");
        startTimers();
    }


    @Override
    public void channelRead0(final ChannelHandlerContext ctx, P2pMessage msg) throws InterruptedException {

        if (P2pMessageCodes.inRange(msg.getCommand().asByte())) {
            logger.trace("P2PHandler invoke: [{}]", msg.getCommand());
        }

        ethereumListener.trace(String.format("P2PHandler invoke: [%s]", msg.getCommand()));

        switch (msg.getCommand()) {
            case HELLO:
                logger.trace("Received unexpected HELLO message, channel {}", channel);
                msgQueue.receivedMessage(msg);
                sendDisconnect();
                break;
            case DISCONNECT:
                msgQueue.receivedMessage(msg);
                channel.getNodeStatistics().nodeDisconnectedRemote(((DisconnectMessage) msg).getReason());
                processDisconnect((DisconnectMessage) msg);
                break;
            case PING:
                logger.trace("Receive PING message, channel {}", channel);
                msgQueue.receivedMessage(msg);
                ctx.writeAndFlush(PONG_MESSAGE);
                break;
            case PONG:
                logger.trace("Receive PONG message, channel {}", channel);
                msgQueue.receivedMessage(msg);
                break;
            default:
                ctx.fireChannelRead(msg);
                break;
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("channel inactive: {}", ctx);
        this.killTimers();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("P2p handling failed", cause);
        ctx.close();
        killTimers();
    }

    private void processDisconnect(DisconnectMessage msg) {

        if (!logger.isInfoEnabled() || msg.getReason() != ReasonCode.USELESS_PEER) {
            return;
        }

        if (channel.getNodeStatistics().getEthInbound().get() - ethInbound > 1 ||
                channel.getNodeStatistics().getEthOutbound().get() - ethOutbound > 1) {

            // it means that we've been disconnected
            // after some incorrect action from our peer
            // need to log this moment
            logger.info("From: \t{}\t [DISCONNECT reason=BAD_PEER_ACTION]", channel);
        }
    }

    public void setHandshake(HelloMessage msg) {

        channel.getNodeStatistics().setClientId(msg.getClientId());

        this.ethInbound = channel.getNodeStatistics().getEthInbound().get();
        this.ethOutbound = channel.getNodeStatistics().getEthOutbound().get();

        ethereumListener.onHandShakePeer(channel, msg);
    }

    public void sendDisconnect() {
        msgQueue.disconnect();
    }

    private void startTimers() {
        // sample for pinging in background
        pingTask = pingTimer.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    msgQueue.sendMessage(PING_MESSAGE);
                } catch (Throwable t) {
                    logger.error("Unhandled exception", t);
                }
            }
        }, 2, pingInterval, TimeUnit.SECONDS);
    }

    public void killTimers() {
        pingTask.cancel(false);
        msgQueue.close();
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public static boolean isProtocolVersionSupported(byte ver) {
        for (byte v : SUPPORTED_VERSIONS) {
            if (v == ver) {
                return true;
            }
        }
        return false;
    }

}