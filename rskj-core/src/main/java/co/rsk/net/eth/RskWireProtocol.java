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
import co.rsk.core.Rsk;
import co.rsk.net.MessageHandler;
import co.rsk.net.MessageSender;
import co.rsk.net.Metrics;
import co.rsk.net.Status;
import co.rsk.net.messages.BlockMessage;
import co.rsk.net.messages.GetBlockMessage;
import co.rsk.net.messages.Message;
import co.rsk.net.messages.StatusMessage;
import io.netty.channel.ChannelHandlerContext;
import org.ethereum.core.*;
import org.ethereum.core.genesis.GenesisLoader;
import org.ethereum.db.BlockStore;
import org.ethereum.net.eth.EthVersion;
import org.ethereum.net.eth.handler.EthHandler;
import org.ethereum.net.eth.handler.GetBlockHeadersMessageWrapper;
import org.ethereum.net.eth.message.EthMessage;
import org.ethereum.net.eth.message.TransactionsMessage;
import org.ethereum.net.message.ReasonCode;
import org.ethereum.net.server.Channel;
import org.ethereum.sync.SyncState;
import org.ethereum.sync.SyncStatistics;
import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

import static org.ethereum.net.eth.EthVersion.V62;
import static org.ethereum.net.message.ReasonCode.USELESS_PEER;

/**
 * Eth 62 New Protocol
 *
 * @author ajlopez
 * @since 05.16.2016
 */
@Component
@Scope("prototype")
public class RskWireProtocol extends EthHandler {

    protected static final int MAX_HASHES_TO_SEND = 65536;

    private static final Logger logger = LoggerFactory.getLogger("sync");
    private static final Logger loggerNet = LoggerFactory.getLogger("net");
    /**
     * Header list sent in GET_BLOCK_BODIES message,
     * used to create blocks from headers and bodies
     * also, is useful when returned BLOCK_BODIES msg doesn't cover all sent hashes
     * or in case when peer is disconnected
     */
    protected final List<BlockHeaderWrapper> sentHeaders = Collections.synchronizedList(new ArrayList<BlockHeaderWrapper>());
    protected final SyncStatistics syncStats = new SyncStatistics();
    @Autowired
    protected BlockStore blockstore;
    @Autowired
    protected PendingState pendingState;
    protected EthState ethState = EthState.INIT;
    protected SyncState syncState = SyncState.IDLE;
    protected boolean syncDone = false;
    /**
     * Last block hash to be asked from the peer,
     * is set on header retrieving start
     */
    protected byte[] lastHashToAsk;
    /**
     * Number and hash of best known remote block
     */
    protected Queue<GetBlockHeadersMessageWrapper> headerRequests = new LinkedBlockingQueue<>();

    @Autowired
    private Rsk rsk;

    private MessageSender messageSender;
    private MessageHandler messageHandler;

    private MessageRecorder messageRecorder;

    public RskWireProtocol() {
        super(V62);
    }

    @Override
    public void setChannel(Channel channel) {
        super.setChannel(channel);

        if (channel == null) {
            return;
        }

        this.messageSender.setNodeID(channel.getNodeId());
    }

    @PostConstruct
    private void init()
    {
        this.messageSender = new EthMessageSender(this);
        this.messageRecorder = RskSystemProperties.RSKCONFIG.getMessageRecorder();

        if (this.rsk != null)
            this.messageHandler = this.rsk.getMessageHandler();

        maxHashesAsk = config.maxHashesAsk();
    }

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, EthMessage msg) throws InterruptedException {
        super.channelRead0(ctx, msg);

        if (this.messageRecorder != null)
            this.messageRecorder.recordMessage(messageSender.getNodeID(), msg);

        Metrics.messageBytes(messageSender.getNodeID(), msg.getEncoded().length);
        switch (msg.getCommand()) {
            case STATUS:
                processStatus((org.ethereum.net.eth.message.StatusMessage) msg, ctx);
                break;
            case RSK_MESSAGE:
                RskMessage rskmessage = (RskMessage)msg;
                Message message = rskmessage.getMessage();

                switch (message.getMessageType()) {
                    case BLOCK_MESSAGE:
                        loggerNet.trace("RSK Block Message: Block {} {} from {}", ((BlockMessage)message).getBlock().getNumber(), ((BlockMessage)message).getBlock().getShortHash(), this.messageSender.getNodeID());
                        syncStats.addBlocks(1);
                        break;
                    case GET_BLOCK_MESSAGE:
                        loggerNet.trace("RSK Get Block Message: Block {} from {}", Hex.toHexString(((GetBlockMessage)message).getBlockHash()).substring(0, 10), this.messageSender.getNodeID());
                        syncStats.getBlock();
                        break;
                    case STATUS_MESSAGE:
                        loggerNet.trace("RSK Status Message: Block {} {} from {}", ((StatusMessage)message).getStatus().getBestBlockNumber(), Hex.toHexString(((StatusMessage)message).getStatus().getBestBlockHash()).substring(0, 10), this.messageSender.getNodeID());
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
            Genesis genesis = GenesisLoader.loadGenesis(config.genesisInfo(), config.getBlockchainConfig().getCommonConstants().getInitialNonce(), true);
            if (!Arrays.equals(msg.getGenesisHash(), genesis.getHash())
                    || msg.getProtocolVersion() != version.getCode()) {
                loggerNet.info("Removing EthHandler for {} due to protocol incompatibility", ctx.channel().remoteAddress());
                ethState = EthState.STATUS_FAILED;
                disconnect(ReasonCode.INCOMPATIBLE_PROTOCOL);
                ctx.pipeline().remove(this); // Peer is not compatible for the 'eth' sub-protocol
                return;
            }

            if (msg.getNetworkId() != config.networkId()) {
                ethState = EthState.STATUS_FAILED;
                disconnect(ReasonCode.NULL_IDENTITY);
                return;
            }

            // basic checks passed, update statistics
            channel.getNodeStatistics().ethHandshake(msg);
            ethereumListener.onEthStatusUpdated(channel, msg);

            if (peerDiscoveryMode) {
                loggerNet.debug("Peer discovery mode: STATUS received, disconnecting...");
                disconnect(ReasonCode.REQUESTED);
                ctx.close().sync();
                ctx.disconnect().sync();
                return;
            }
        } catch (NoSuchElementException e) {
            loggerNet.debug("EthHandler already removed");
        }
    }


    /*************************
     *    Message Sending    *
     *************************/

    @Override
    public void sendStatus() {
        byte protocolVersion = version.getCode();
        int networkId = config.networkId();

        BigInteger totalDifficulty = blockchain.getTotalDifficulty();
        byte[] bestHash = blockchain.getBestBlockHash();
        Genesis genesis = GenesisLoader.loadGenesis(config.genesisInfo(), config.getBlockchainConfig().getCommonConstants().getInitialNonce(), true);
        org.ethereum.net.eth.message.StatusMessage msg = new org.ethereum.net.eth.message.StatusMessage(protocolVersion, networkId,
                ByteUtil.bigIntegerToBytes(totalDifficulty), bestHash, genesis.getHash());
        sendMessage(msg);

        // RSK new protocol send status
        Block bestBlock = blockchain.getBestBlock();
        Status status = new Status(bestBlock.getNumber(), bestBlock.getHash());
        RskMessage rskmessage = new RskMessage(new StatusMessage(status));
        loggerNet.trace("Sending status best block {} to {}", status.getBestBlockNumber(), this.messageSender.getNodeID().toString());
        sendMessage(rskmessage);

        ethState = EthState.STATUS_SENT;
    }

    @Override
    public void recoverGap(BlockWrapper block) {

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

    @Override
    public void fetchBodies(List<BlockHeaderWrapper> headers) {

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
