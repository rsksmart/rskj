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
import org.ethereum.config.SystemProperties;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.listener.EthereumListener;
import org.ethereum.net.MessageQueue;
import org.ethereum.net.client.Capability;
import org.ethereum.net.client.ConfigCapabilities;
import org.ethereum.net.eth.message.NewBlockMessage;
import org.ethereum.net.eth.message.TransactionsMessage;
import org.ethereum.net.message.ReasonCode;
import org.ethereum.net.message.StaticMessages;
import org.ethereum.net.server.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.ethereum.net.eth.EthVersion.fromCode;
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
            Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                public Thread newThread(Runnable r) {
                    return new Thread(r, "P2pPingTimer");
                }
            });

    private MessageQueue msgQueue;

    private HelloMessage handshakeHelloMessage = null;

    private int ethInbound;
    private int ethOutbound;

    private final EthereumListener ethereumListener;
    private final ConfigCapabilities configCapabilities;
    private final SystemProperties config;

    public P2pHandler(EthereumListener ethereumListener, ConfigCapabilities configCapabilities, SystemProperties config) {
        this.ethereumListener = ethereumListener;
        this.configCapabilities = configCapabilities;
        this.config = config;
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

        if (P2pMessageCodes.inRange(msg.getCommand().asByte()))
            logger.trace("P2PHandler invoke: [{}]", msg.getCommand());

        ethereumListener.trace(String.format("P2PHandler invoke: [%s]", msg.getCommand()));

        switch (msg.getCommand()) {
            case HELLO:
                msgQueue.receivedMessage(msg);
                setHandshake((HelloMessage) msg, ctx);
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

    private void disconnect(ReasonCode reasonCode) {
        msgQueue.sendMessage(new DisconnectMessage(reasonCode));
        channel.getNodeStatistics().nodeDisconnectedLocal(reasonCode);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("channel inactive: ", ctx.toString());
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

        if (channel.getNodeStatistics().ethInbound.get() - ethInbound > 1 ||
                channel.getNodeStatistics().ethOutbound.get() - ethOutbound > 1) {

            // it means that we've been disconnected
            // after some incorrect action from our peer
            // need to log this moment
            logger.info("From: \t{}\t [DISCONNECT reason=BAD_PEER_ACTION]", channel);
        }
    }

    private void sendGetPeers() {
        msgQueue.sendMessage(StaticMessages.GET_PEERS_MESSAGE);
    }

    public void setHandshake(HelloMessage msg, ChannelHandlerContext ctx) {

        channel.getNodeStatistics().setClientId(msg.getClientId());

        this.ethInbound = channel.getNodeStatistics().ethInbound.get();
        this.ethOutbound = channel.getNodeStatistics().ethOutbound.get();

        this.handshakeHelloMessage = msg;
        if (!isProtocolVersionSupported(msg.getP2PVersion())) {
            disconnect(ReasonCode.INCOMPATIBLE_PROTOCOL);
        }
        else {
            List<Capability> capInCommon = getSupportedCapabilities(msg);
            channel.initMessageCodes(capInCommon);
            for (Capability capability : capInCommon) {
                if (capability.getName().equals(Capability.RSK)) {

                    // Activate EthHandler for this peer
                    channel.activateEth(ctx, fromCode(capability.getVersion()));
                }
            }

            ethereumListener.onHandShakePeer(channel, msg);

        }
    }

    /**
     * submit transaction to the network
     *
     * @param tx - fresh transaction object
     */
    public void sendTransaction(Transaction tx) {

        TransactionsMessage msg = new TransactionsMessage(tx);
        msgQueue.sendMessage(msg);
    }

    public void sendNewBlock(Block block) {

        NewBlockMessage msg = new NewBlockMessage(block, block.getDifficulty());
        msgQueue.sendMessage(msg);
    }

    public void sendDisconnect() {
        msgQueue.disconnect();
    }

    public HelloMessage getHandshakeHelloMessage() {
        return handshakeHelloMessage;
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
        }, 2, config.getProperty("peer.p2p.pingInterval", 5), TimeUnit.SECONDS);
    }

    public void killTimers() {
        pingTask.cancel(false);
        msgQueue.close();
    }


    public void setMsgQueue(MessageQueue msgQueue) {
        this.msgQueue = msgQueue;
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

    public List<Capability> getSupportedCapabilities(HelloMessage hello) {
        List<Capability> configCaps = configCapabilities.getConfigCapabilities();
        List<Capability> supported = new ArrayList<>();

        List<Capability> eths = new ArrayList<>();

        for (Capability cap : hello.getCapabilities()) {
            if (configCaps.contains(cap)) {
                if (cap.isRSK()) {
                    eths.add(cap);
                } else {
                    supported.add(cap);
                }
            }
        }

        if (eths.isEmpty()) {
            return supported;
        }

        // we need to pick up
        // the most recent Eth version
        Capability highest = null;
        for (Capability eth : eths) {
            if (highest == null || highest.getVersion() < eth.getVersion()) {
                highest = eth;
            }
        }

        supported.add(highest);
        return supported;
    }

}