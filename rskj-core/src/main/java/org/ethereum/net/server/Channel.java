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

package org.ethereum.net.server;

import co.rsk.net.NodeID;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.net.MessageQueue;
import org.ethereum.net.NodeManager;
import org.ethereum.net.NodeStatistics;
import org.ethereum.net.client.Capability;
import org.ethereum.net.eth.EthVersion;
import org.ethereum.net.eth.handler.Eth;
import org.ethereum.net.eth.handler.EthAdapter;
import org.ethereum.net.eth.handler.EthHandler;
import org.ethereum.net.eth.handler.EthHandlerFactory;
import org.ethereum.net.eth.message.Eth62MessageFactory;
import org.ethereum.net.eth.message.EthMessage;
import org.ethereum.net.message.MessageFactory;
import org.ethereum.net.message.ReasonCode;
import org.ethereum.net.message.StaticMessages;
import org.ethereum.net.p2p.HelloMessage;
import org.ethereum.net.p2p.P2pHandler;
import org.ethereum.net.rlpx.FrameCodec;
import org.ethereum.net.rlpx.MessageCodec;
import org.ethereum.net.rlpx.Node;
import org.ethereum.sync.SyncStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.util.List;

public class Channel {

    private static final Logger logger = LoggerFactory.getLogger("net");

    private final MessageQueue msgQueue;
    private final MessageCodec messageCodec;
    private final NodeManager nodeManager;
    private final EthHandlerFactory ethHandlerFactory;
    private final StaticMessages staticMessages;
    private final boolean isActive;

    private Eth eth = new EthAdapter();

    private InetSocketAddress inetSocketAddress;

    private Node node;
    private NodeStatistics nodeStatistics;

    private final PeerStatistics peerStats = new PeerStatistics();

    public Channel(MessageQueue msgQueue,
                   MessageCodec messageCodec,
                   NodeManager nodeManager,
                   EthHandlerFactory ethHandlerFactory,
                   StaticMessages staticMessages,
                   String remoteId) {
        this.msgQueue = msgQueue;
        this.messageCodec = messageCodec;
        this.nodeManager = nodeManager;
        this.ethHandlerFactory = ethHandlerFactory;
        this.staticMessages = staticMessages;
        this.isActive = remoteId != null && !remoteId.isEmpty();
    }

    public void sendHelloMessage(ChannelHandlerContext ctx, FrameCodec frameCodec, String nodeId,
                                 HelloMessage inboundHelloMessage) throws IOException, InterruptedException {

        // in discovery mode we are supplying fake port along with fake nodeID to not receive
        // incoming connections with fake public key
        HelloMessage helloMessage = staticMessages.createHelloMessage(nodeId);

        if (inboundHelloMessage != null && P2pHandler.isProtocolVersionSupported(inboundHelloMessage.getP2PVersion())) {
            // the p2p version can be downgraded if requested by peer and supported by us
            helloMessage.setP2pVersion(inboundHelloMessage.getP2PVersion());
        }

        byte[] payload = helloMessage.getEncoded();

        ByteBuf byteBufMsg = ctx.alloc().buffer();
        frameCodec.writeFrame(new FrameCodec.Frame(helloMessage.getCode(), payload), byteBufMsg);
        ctx.writeAndFlush(byteBufMsg).sync();

        if (logger.isInfoEnabled()) {
            logger.info("To: \t{} \tSend: \t{}", ctx.channel().remoteAddress(), helloMessage);
        }
        getNodeStatistics().rlpxOutHello.add();
    }

    public void activateEth(ChannelHandlerContext ctx, EthVersion version) {
        EthHandler handler = ethHandlerFactory.create(version);
        MessageFactory messageFactory = createEthMessageFactory(version);
        messageCodec.setEthVersion(version);
        messageCodec.setEthMessageFactory(messageFactory);

        logger.info("Eth{} [ address = {} | id = {} ]", handler.getVersion(), inetSocketAddress, getPeerIdShort());

        ctx.pipeline().addLast(Capability.RSK, handler);

        handler.setMsgQueue(msgQueue);
        handler.setChannel(this);

        handler.activate();

        eth = handler;
    }

    private MessageFactory createEthMessageFactory(EthVersion version) {
        switch (version) {
            case V62:   return new Eth62MessageFactory();
            default:    throw new IllegalArgumentException("Eth " + version + " is not supported");
        }
    }

    public void setInetSocketAddress(InetSocketAddress inetSocketAddress) {
        this.inetSocketAddress = inetSocketAddress;
    }

    public NodeStatistics getNodeStatistics() {
        return nodeStatistics;
    }

    public void setNode(byte[] nodeId) {
        node = new Node(nodeId, inetSocketAddress.getHostName(), inetSocketAddress.getPort());
        nodeStatistics = nodeManager.getNodeStatistics(node);
    }

    public Node getNode() {
        return node;
    }

    public void initMessageCodes(List<Capability> caps) {
        messageCodec.initMessageCodes(caps);
    }

    public boolean isProtocolsInitialized() {
        return eth.isUsingNewProtocol() || eth.hasStatusPassed();
    }

    public boolean isUsingNewProtocol() {
        return eth.isUsingNewProtocol();
    }

    public void onDisconnect() {
    }

    public void onSyncDone(boolean done) {

        if (done) {
            eth.enableTransactions();
        } else {
            eth.disableTransactions();
        }

        eth.onSyncDone(done);
    }

    public String getPeerId() {
        return node == null ? "<null>" : node.getHexId();
    }

    public String getPeerIdShort() {
        return node == null ? "<null>" : node.getHexIdShort();
    }

    /**
     * Indicates whether this connection was initiated by our peer
     */
    public boolean isActive() {
        return isActive;
    }

    public NodeID getNodeId() {
        return node == null ? null : node.getId();
    }

    public void disconnect(ReasonCode reason) {
        msgQueue.disconnect(reason);
    }

    public InetSocketAddress getInetSocketAddress() {
        return inetSocketAddress;
    }

    public PeerStatistics getPeerStats() {
        return peerStats;
    }

    // RSK sub protocol

    public boolean isEthCompatible(Channel peer) {
        return peer != null && peer.getEthVersion().isCompatible(getEthVersion());
    }

    public Eth getEthHandler() {
        return eth;
    }

    public boolean hasEthStatusSucceeded() {
        return eth.hasStatusSucceeded();
    }

    public void logSyncStats() {
        eth.logSyncStats();
    }

    public BigInteger getTotalDifficulty() {
        return nodeStatistics.getEthTotalDifficulty();
    }

    public SyncStatistics getSyncStats() {
        return eth.getStats();
    }

    public boolean isHashRetrievingDone() {
        return eth.isHashRetrievingDone();
    }

    public boolean isHashRetrieving() {
        return eth.isHashRetrieving();
    }

    public boolean isMaster() {
        return eth.isHashRetrieving() || eth.isHashRetrievingDone();
    }

    public boolean isIdle() {
        return eth.isIdle();
    }

    public void prohibitTransactionProcessing() {
        eth.disableTransactions();
    }

    public void sendTransaction(List<Transaction> tx) {
        eth.sendTransaction(tx);
    }

    public void sendNewBlock(Block block) {
        eth.sendNewBlock(block);
    }

    public EthVersion getEthVersion() {
        return eth.getVersion();
    }

    public void dropConnection() {
        eth.dropConnection();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Channel channel = (Channel) o;

        if (inetSocketAddress != null ? !inetSocketAddress.equals(channel.inetSocketAddress) : channel.inetSocketAddress != null) {
            return false;
        }
        return node != null ? node.equals(channel.node) : channel.node == null;

    }

    @Override
    public int hashCode() {
        int result = inetSocketAddress != null ? inetSocketAddress.hashCode() : 0;
        result = 31 * result + (node != null ? node.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return String.format("%s | %s", getPeerIdShort(), inetSocketAddress);
    }

    public void sendMessage(EthMessage message) {
        eth.sendMessage(message);
    }
}
