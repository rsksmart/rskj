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
import co.rsk.core.BlockDifficulty;
import co.rsk.core.bc.BlockChainStatus;
import co.rsk.crypto.Keccak256;
import co.rsk.net.*;
import co.rsk.net.messages.BlockMessage;
import co.rsk.net.messages.GetBlockMessage;
import co.rsk.net.messages.Message;
import co.rsk.net.messages.StatusMessage;
import co.rsk.scoring.EventType;
import co.rsk.scoring.PeerScoringManager;
import io.netty.channel.ChannelHandlerContext;
import org.ethereum.core.*;
import org.ethereum.core.genesis.GenesisLoader;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.net.eth.EthVersion;
import org.ethereum.net.eth.handler.EthHandler;
import org.ethereum.net.eth.message.EthMessage;
import org.ethereum.net.eth.message.TransactionsMessage;
import org.ethereum.net.message.ReasonCode;
import org.ethereum.net.server.Channel;
import org.ethereum.sync.SyncState;
import org.ethereum.sync.SyncStatistics;
import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bouncycastle.util.encoders.Hex;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.NoSuchElementException;

import static org.ethereum.net.eth.EthVersion.V62;
import static org.ethereum.net.message.ReasonCode.USELESS_PEER;

public class RskWireProtocol extends EthHandler {

    private static final Logger logger = LoggerFactory.getLogger("sync");
    private static final Logger loggerNet = LoggerFactory.getLogger("net");
    /**
     * Header list sent in GET_BLOCK_BODIES message,
     * used to create blocks from headers and bodies
     * also, is useful when returned BLOCK_BODIES msg doesn't cover all sent hashes
     * or in case when peer is disconnected
     */
    private final PeerScoringManager peerScoringManager;
    protected final SyncStatistics syncStats = new SyncStatistics();
    protected EthState ethState = EthState.INIT;
    protected SyncState syncState = SyncState.IDLE;
    protected boolean syncDone = false;

    private final RskSystemProperties config;
    private final MessageChannel messageSender;
    private final MessageHandler messageHandler;
    private final Blockchain blockchain;
    private final MessageRecorder messageRecorder;
    private final Genesis genesis;

    public RskWireProtocol(RskSystemProperties config, PeerScoringManager peerScoringManager, MessageHandler messageHandler, Blockchain blockchain, CompositeEthereumListener ethereumListener) {
        super(blockchain, config, ethereumListener, V62);
        this.peerScoringManager = peerScoringManager;
        this.messageHandler = messageHandler;
        this.blockchain = blockchain;
        this.config = config;
        this.messageSender = new EthMessageSender(this);
        this.messageRecorder = config.getMessageRecorder();
        this.genesis = GenesisLoader.loadGenesis(config, config.genesisInfo(), config.getBlockchainConfig().getCommonConstants().getInitialNonce(), true);

    }

    @Override
    public void setChannel(Channel channel) {
        super.setChannel(channel);

        if (channel == null) {
            return;
        }

        this.messageSender.setPeerNodeID(channel.getNodeId());
        this.messageSender.setAddress(channel.getInetSocketAddress().getAddress());
    }

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, EthMessage msg) throws InterruptedException {
        super.channelRead0(ctx, msg);

        if (this.messageRecorder != null) {
            this.messageRecorder.recordMessage(messageSender.getPeerNodeID(), msg);
        }

        if (!hasGoodReputation(ctx)) {
            ctx.disconnect();
            return;
        }

        Metrics.messageBytes(messageSender.getPeerNodeID(), msg.getEncoded().length);

        switch (msg.getCommand()) {
            case STATUS:
                processStatus((org.ethereum.net.eth.message.StatusMessage) msg, ctx);
                break;
            case RSK_MESSAGE:
                RskMessage rskmessage = (RskMessage)msg;
                Message message = rskmessage.getMessage();

                switch (message.getMessageType()) {
                    case BLOCK_MESSAGE:
                        loggerNet.trace("RSK Block Message: Block {} {} from {}", ((BlockMessage)message).getBlock().getNumber(), ((BlockMessage)message).getBlock().getShortHash(), this.messageSender.getPeerNodeID());
                        syncStats.addBlocks(1);
                        break;
                    case GET_BLOCK_MESSAGE:
                        loggerNet.trace("RSK Get Block Message: Block {} from {}", Hex.toHexString(((GetBlockMessage)message).getBlockHash()).substring(0, 10), this.messageSender.getPeerNodeID());
                        syncStats.getBlock();
                        break;
                    case STATUS_MESSAGE:
                        loggerNet.trace("RSK Status Message: Block {} {} from {}", ((StatusMessage)message).getStatus().getBestBlockNumber(), Hex.toHexString(((StatusMessage)message).getStatus().getBestBlockHash()).substring(0, 10), this.messageSender.getPeerNodeID());
                        syncStats.addStatus();
                        break;
                }

                if (this.messageHandler != null) {
                    this.messageHandler.postMessage(this.messageSender, rskmessage.getMessage());
                }
                break;
            default:
                break;
        }
    }

    /*************************
     *  Message Processing   *
     *************************/

    protected void processStatus(org.ethereum.net.eth.message.StatusMessage msg, ChannelHandlerContext ctx) throws InterruptedException {
        try {
            byte protocolVersion = msg.getProtocolVersion();
            byte versionCode = version.getCode();
            if (protocolVersion != versionCode) {
                loggerNet.info("Removing EthHandler for {} due to protocol incompatibility", ctx.channel().remoteAddress());
                loggerNet.info("Protocol version {} - message protocol version {}",
                        versionCode,
                        protocolVersion);
                ethState = EthState.STATUS_FAILED;
                recordEvent(EventType.INCOMPATIBLE_PROTOCOL);
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
                recordEvent(EventType.INVALID_NETWORK);
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
                recordEvent(EventType.UNEXPECTED_GENESIS);
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

    private boolean hasGoodReputation(ChannelHandlerContext ctx) {
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

    private void recordEvent(EventType event) {
        peerScoringManager.recordEvent(
                        this.messageSender.getPeerNodeID(),
                        this.messageSender.getAddress(),
                        event);
    }


    /*************************
     *    Message Sending    *
     *************************/

    @Override
    public void sendStatus() {
        byte protocolVersion = version.getCode();
        int networkId = config.networkId();

        BlockChainStatus blockChainStatus = this.blockchain.getStatus();
        Block bestBlock = blockChainStatus.getBestBlock();
        BlockDifficulty totalDifficulty = blockChainStatus.getTotalDifficulty();

        // Original status
        Genesis loadGenesis = GenesisLoader.loadGenesis(config, config.genesisInfo(), config.getBlockchainConfig().getCommonConstants().getInitialNonce(), true);
        org.ethereum.net.eth.message.StatusMessage msg = new org.ethereum.net.eth.message.StatusMessage(protocolVersion, networkId,
                ByteUtil.bigIntegerToBytes(totalDifficulty.asBigInteger()), bestBlock.getHash().getBytes(), loadGenesis.getHash().getBytes());
        sendMessage(msg);

        // RSK new protocol send status
        Status status = new Status(bestBlock.getNumber(), bestBlock.getHash().getBytes(), bestBlock.getParentHash().getBytes(), totalDifficulty);
        RskMessage rskmessage = new RskMessage(new StatusMessage(status));
        loggerNet.trace("Sending status best block {} to {}", status.getBestBlockNumber(), this.messageSender.getPeerNodeID().toString());
        sendMessage(rskmessage);

        ethState = EthState.STATUS_SENT;
    }

    @Override
    public void sendTransaction(List<Transaction> txs) {
        TransactionsMessage msg = new TransactionsMessage(txs);
        sendMessage(msg);
    }

    @Override
    public void sendNewBlock(Block newBlock) {

    }

    @Override
    public void sendNewBlockHashes(Block block) {

    }

    /*************************
     *    Sync Management    *
     *************************/

    @Override
    public void onShutdown() {

    }

    /*************************
     *   Getters, setters    *
     *************************/

    @Override
    public boolean hasStatusPassed() {
        return ethState.ordinal() > EthState.STATUS_SENT.ordinal();
    }

    @Override
    public boolean hasStatusSucceeded() {
        return ethState == EthState.STATUS_SUCCEEDED;
    }

    @Override
    public boolean isHashRetrievingDone() {
        return syncState == SyncState.DONE_HASH_RETRIEVING;
    }

    @Override
    public boolean isHashRetrieving() {
        return syncState == SyncState.HASH_RETRIEVING;
    }

    @Override
    public boolean isIdle() {
        return syncState == SyncState.IDLE;
    }

    @Override
    public void enableTransactions() {
        processTransactions = true;
    }

    @Override
    public void disableTransactions() {
        processTransactions = false;
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
    public void onSyncDone(boolean done) {
        syncDone = done;
    }

    @Override
    public void dropConnection() {

        // todo: reduce reputation

        logger.info("Peer {}: is a bad one, drop", channel.getPeerIdShort());
        disconnect(USELESS_PEER);
    }

    /*************************
     *       Logging         *
     *************************/

    @Override
    public void logSyncStats() {
        if(!logger.isInfoEnabled()) {
            return;
        }
    }

    @Override
    public boolean isUsingNewProtocol() {
        return true;
    }

    protected enum EthState {
        INIT,
        STATUS_SENT,
        STATUS_SUCCEEDED,
        STATUS_FAILED
    }
}
