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

import co.rsk.config.RskSystemProperties;
import co.rsk.crypto.Keccak256;
import co.rsk.net.MessageHandler;
import co.rsk.net.NodeID;
import co.rsk.net.Status;
import co.rsk.net.StatusResolver;
import co.rsk.net.messages.BlockMessage;
import co.rsk.net.messages.GetBlockMessage;
import co.rsk.net.messages.Message;
import co.rsk.net.messages.StatusMessage;
import co.rsk.scoring.EventType;
import co.rsk.scoring.PeerScoringManager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.ethereum.core.Genesis;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.net.MessageQueue;
import org.ethereum.net.eth.EthVersion;
import org.ethereum.net.eth.handler.Eth;
import org.ethereum.net.eth.message.EthMessage;
import org.ethereum.net.eth.message.EthMessageCodes;
import org.ethereum.net.message.ReasonCode;
import org.ethereum.net.server.Channel;
import org.ethereum.sync.SyncStatistics;
import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.NoSuchElementException;

import static org.ethereum.net.eth.EthVersion.V62;
import static org.ethereum.net.message.ReasonCode.USELESS_PEER;

public class RskWireProtocol extends SimpleChannelInboundHandler<EthMessage> implements Eth {

    private static final Logger logger = LoggerFactory.getLogger("sync");
    private static final Logger loggerNet = LoggerFactory.getLogger("net");

    private final CompositeEthereumListener ethereumListener;
    /**
     * Header list sent in GET_BLOCK_BODIES message,
     * used to create blocks from headers and bodies
     * also, is useful when returned BLOCK_BODIES msg doesn't cover all sent hashes
     * or in case when peer is disconnected
     */
    private final PeerScoringManager peerScoringManager;
    private final SyncStatistics syncStats = new SyncStatistics();
    private final Channel channel;
    private final EthVersion version;
    private EthState ethState = EthState.INIT;

    private final RskSystemProperties config;
    private final StatusResolver statusResolver;
    private final MessageHandler messageHandler;
    private final MessageRecorder messageRecorder;
    private final Genesis genesis;
    private final MessageQueue msgQueue;

    public RskWireProtocol(RskSystemProperties config,
                           PeerScoringManager peerScoringManager,
                           MessageHandler messageHandler,
                           CompositeEthereumListener ethereumListener,
                           Genesis genesis,
                           MessageRecorder messageRecorder,
                           StatusResolver statusResolver,
                           MessageQueue msgQueue,
                           Channel channel) {
        this.ethereumListener = ethereumListener;
        this.version = V62;

        this.msgQueue = msgQueue;
        this.channel = channel;
        this.peerScoringManager = peerScoringManager;
        this.messageHandler = messageHandler;
        this.config = config;
        this.statusResolver = statusResolver;
        this.messageRecorder = messageRecorder;
        this.genesis = genesis;
    }

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, EthMessage msg) throws InterruptedException {
        loggerNet.debug("Read message: {}", msg);

        if (EthMessageCodes.inRange(msg.getCommand().asByte(), version)) {
            loggerNet.trace("EthHandler invoke: [{}]", msg.getCommand());
        }

        ethereumListener.trace(String.format("EthHandler invoke: [%s]", msg.getCommand()));

        channel.getNodeStatistics().getEthInbound().add();

        msgQueue.receivedMessage(msg);

        if (this.messageRecorder != null) {
            this.messageRecorder.recordMessage(channel.getPeerNodeID(), msg);
        }

        if (!checkGoodReputation(ctx)) {
            ctx.disconnect();
            return;
        }

        switch (msg.getCommand()) {
            case STATUS:
                processStatus((org.ethereum.net.eth.message.StatusMessage) msg, ctx);
                break;
            case RSK_MESSAGE:
                RskMessage rskmessage = (RskMessage)msg;
                Message message = rskmessage.getMessage();

                switch (message.getMessageType()) {
                    case BLOCK_MESSAGE:
                        loggerNet.trace("RSK Block Message: Block {} {} from {}", ((BlockMessage)message).getBlock().getNumber(), ((BlockMessage)message).getBlock().getPrintableHash(), channel.getPeerNodeID());
                        syncStats.addBlocks(1);
                        break;
                    case GET_BLOCK_MESSAGE:
                        loggerNet.trace("RSK Get Block Message: Block {} from {}", ByteUtil.toHexString(((GetBlockMessage)message).getBlockHash()), channel.getPeerNodeID());
                        syncStats.getBlock();
                        break;
                    case STATUS_MESSAGE:
                        loggerNet.trace("RSK Status Message: Block {} {} from {}", ((StatusMessage)message).getStatus().getBestBlockNumber(), ByteUtil.toHexString(((StatusMessage)message).getStatus().getBestBlockHash()), channel.getPeerNodeID());
                        syncStats.addStatus();
                        break;
                }

                if (this.messageHandler != null) {
                    this.messageHandler.postMessage(channel, rskmessage.getMessage());
                }
                break;
            default:
                break;
        }
    }

    /*************************
     *  Message Processing   *
     *************************/

    protected void processStatus(org.ethereum.net.eth.message.StatusMessage msg, ChannelHandlerContext ctx) {
        try {
            byte protocolVersion = msg.getProtocolVersion();
            byte versionCode = version.getCode();
            if (protocolVersion != versionCode) {
                loggerNet.info("Removing EthHandler for {} due to protocol incompatibility", ctx.channel().remoteAddress());
                loggerNet.info("Protocol version {} - message protocol version {}",
                        versionCode,
                        protocolVersion);
                ethState = EthState.STATUS_FAILED;
                reportEventToPeerScoring(EventType.INCOMPATIBLE_PROTOCOL);
                disconnect(ReasonCode.INCOMPATIBLE_PROTOCOL);
                ctx.pipeline().remove(this); // Peer is not compatible for the 'eth' sub-protocol
                return;
            }

            int networkId = config.networkId();
            int msgNetworkId = msg.getNetworkId();
            if (msgNetworkId != networkId) {
                loggerNet.info("Removing EthHandler for {} due to invalid network", ctx.channel().remoteAddress());
                loggerNet.info("Different network received: config network ID {} - message network ID {}",
                        networkId, msgNetworkId);
                ethState = EthState.STATUS_FAILED;
                reportEventToPeerScoring(EventType.INVALID_NETWORK);
                disconnect(ReasonCode.NULL_IDENTITY);
                ctx.pipeline().remove(this);
                return;
            }

            Keccak256 genesisHash = genesis.getHash();
            Keccak256 msgGenesisHash = new Keccak256(msg.getGenesisHash());
            if (!msgGenesisHash.equals(genesisHash)) {
                loggerNet.info("Removing EthHandler for {} due to unexpected genesis", ctx.channel().remoteAddress());
                loggerNet.info("Config genesis hash {} - message genesis hash {}",
                        genesisHash, msgGenesisHash);
                ethState = EthState.STATUS_FAILED;
                reportEventToPeerScoring(EventType.UNEXPECTED_GENESIS);
                disconnect(ReasonCode.UNEXPECTED_GENESIS);
                ctx.pipeline().remove(this);
                return;
            }

            // basic checks passed, update statistics
            channel.getNodeStatistics().ethHandshake(msg);
            ethereumListener.onEthStatusUpdated(channel, msg);
        } catch (NoSuchElementException e) {
            loggerNet.debug("EthHandler already removed");
        }
    }

    private boolean checkGoodReputation(ChannelHandlerContext ctx) {
        SocketAddress socketAddress = ctx.channel().remoteAddress();

        //TODO(mmarquez): and if not ???
        if (socketAddress instanceof InetSocketAddress) {

            InetAddress address = ((InetSocketAddress)socketAddress).getAddress();

            if (!peerScoringManager.hasGoodReputation(address)) {
                return false;
            }

            NodeID nodeID = channel.getNodeId();

            if (nodeID != null && !peerScoringManager.hasGoodReputation(nodeID)) {
                return false;
            }

        }

        return true; //TODO(mmarquez): ugly
    }

    private void reportEventToPeerScoring(EventType event) {
        peerScoringManager.recordEvent(
                        channel.getPeerNodeID(),
                        channel.getAddress(),
                        event);
    }


    /*************************
     *    Message Sending    *
     *************************/

    @Override
    public void sendStatus() {
        byte protocolVersion = version.getCode();
        int networkId = config.networkId();

        Status status = statusResolver.currentStatusLenient(); // TODO(iago:4) undo, just for testing!!!

        // Original status
        org.ethereum.net.eth.message.StatusMessage msg = new org.ethereum.net.eth.message.StatusMessage(
                protocolVersion,
                networkId,
                ByteUtil.bigIntegerToBytes(status.getTotalDifficulty().asBigInteger()),
                status.getBestBlockHash(),
                genesis.getHash().getBytes());
        sendMessage(msg);

        // RSK new protocol send status
        RskMessage rskmessage = new RskMessage(new StatusMessage(status));
        loggerNet.trace("Sending status best block {} to {}",
                status.getBestBlockNumber(),
                channel.getPeerNodeID());
        sendMessage(rskmessage);

        ethState = EthState.STATUS_SENT;
    }

    @Override
    public boolean hasStatusPassed() {
        return ethState.ordinal() > EthState.STATUS_SENT.ordinal();
    }

    @Override
    public boolean hasStatusSucceeded() {
        return ethState == EthState.STATUS_SUCCEEDED;
    }

    @Override
    public SyncStatistics getStats() {
        return syncStats;
    }

    @Override
    public EthVersion getVersion() {
        return version;
    }

    @Override
    public void dropConnection() {

        // todo: reduce reputation

        logger.info("Peer {}: is a bad one, drop", channel.getPeerId());
        disconnect(USELESS_PEER);
    }

    @Override
    public boolean isUsingNewProtocol() {
        return true;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        loggerNet.error("Eth handling failed", cause);
        ctx.close();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        loggerNet.debug("handlerRemoved: kill timers in EthHandler");
    }

    public void activate() {
        loggerNet.info("RSK protocol activated");
        ethereumListener.trace("RSK protocol activated");
        sendStatus();
    }

    protected void disconnect(ReasonCode reason) {
        msgQueue.disconnect(reason);
        channel.getNodeStatistics().nodeDisconnectedLocal(reason);
    }

    @Override
    public void sendMessage(EthMessage message) {
        loggerNet.debug("Send message: {}", message);

        msgQueue.sendMessage(message);
        channel.getNodeStatistics().getEthOutbound().add();
    }

    private enum EthState {
        INIT,
        STATUS_SENT,
        STATUS_SUCCEEDED,
        STATUS_FAILED
    }

    public interface Factory {
        RskWireProtocol newInstance(MessageQueue messageQueue, Channel channel);
    }
}
