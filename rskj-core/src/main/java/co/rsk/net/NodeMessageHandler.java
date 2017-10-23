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

package co.rsk.net;

import co.rsk.net.handler.TxHandler;
import co.rsk.net.messages.*;
import co.rsk.scoring.EventType;
import co.rsk.scoring.PeerScoringManager;
import co.rsk.validators.BlockValidationRule;
import org.ethereum.core.Block;
import org.ethereum.core.PendingState;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.net.server.ChannelManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigInteger;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Created by ajlopez on 5/10/2016.
 */
public class NodeMessageHandler implements MessageHandler, Runnable {
    private static final Logger logger = LoggerFactory.getLogger("messagehandler");
    private static final Logger loggerMessageProcess = LoggerFactory.getLogger("messageProcess");
    public static final int MAX_NUMBER_OF_MESSAGES_CACHED = 5000;
    public static final long RECEIVED_MESSAGES_CACHE_DURATION = TimeUnit.MINUTES.toMillis(2);
    public static final long WAIT_TIME_ACCEPT_ADVANCED_BLOCKS = TimeUnit.MINUTES.toMillis(10);
    private final BlockProcessor blockProcessor;
    private final SyncProcessor syncProcessor;
    private final ChannelManager channelManager;
    private final PendingState pendingState;
    private final PeerScoringManager peerScoringManager;
    private long lastStatusSent = System.currentTimeMillis();

    @Nonnull
    private BlockValidationRule blockValidationRule;

    private TransactionNodeInformation transactionNodeInformation;

    private LinkedBlockingQueue<MessageTask> queue = new LinkedBlockingQueue<>();
    private Set<ByteArrayWrapper> receivedMessages = Collections.synchronizedSet(new HashSet<ByteArrayWrapper>());
    private long cleanMsgTimestamp = 0;
    private long lastImportedBestBlock;

    private volatile boolean stopped;

    private TxHandler txHandler;

    public NodeMessageHandler(@Nonnull final BlockProcessor blockProcessor,
                              final SyncProcessor syncProcessor,
                              @Nullable final ChannelManager channelManager,
                              @Nullable final PendingState pendingState,
                              final TxHandler txHandler,
                              @Nullable final PeerScoringManager peerScoringManager,
                              @Nonnull BlockValidationRule blockValidationRule) {
        this.channelManager = channelManager;
        this.blockProcessor = blockProcessor;
        this.syncProcessor = syncProcessor;
        this.pendingState = pendingState;
        this.blockValidationRule = blockValidationRule;
        transactionNodeInformation = new TransactionNodeInformation();
        this.txHandler = txHandler;
        this.lastImportedBestBlock = System.currentTimeMillis();
        this.cleanMsgTimestamp = this.lastImportedBestBlock;
        this.peerScoringManager = peerScoringManager;
    }

    /**
     * processMessage processes a RSK Message, doing the appropriate action based on the message type.
     *
     * @param sender  the message sender.
     * @param message the message to be processed.
     */
    public synchronized void processMessage(final MessageChannel sender, @Nonnull final Message message) {
        long start = System.nanoTime();
        logger.trace("Process message type: {}", message.getMessageType());

        MessageType mType = message.getMessageType();

        if (mType == MessageType.GET_BLOCK_MESSAGE)
            this.processGetBlockMessage(sender, (GetBlockMessage) message);
        else if (mType == MessageType.GET_BLOCK_HEADERS_MESSAGE)
            this.processGetBlockHeadersMessage(sender, (GetBlockHeadersMessage) message);
        else if (mType == MessageType.BLOCK_MESSAGE)
            this.processBlockMessage(sender, (BlockMessage) message);
        else if (mType == MessageType.STATUS_MESSAGE)
            this.processStatusMessage(sender, (StatusMessage) message);
        else if (mType == MessageType.BLOCK_HEADERS_MESSAGE)
            this.processBlockHeadersMessage(sender, (BlockHeadersMessage) message);
        else if (mType == MessageType.BLOCK_REQUEST_MESSAGE)
            this.processBlockRequestMessage(sender, (BlockRequestMessage) message);
        else if (mType == MessageType.BLOCK_RESPONSE_MESSAGE)
            this.processBlockResponseMessage(sender, (BlockResponseMessage) message);
        else if (mType == MessageType.BODY_REQUEST_MESSAGE)
            this.processBodyRequestMessage(sender, (BodyRequestMessage) message);
        else if (mType == MessageType.BODY_RESPONSE_MESSAGE)
            this.processBodyResponseMessage(sender, (BodyResponseMessage) message);
        else if (mType == MessageType.BLOCK_HEADERS_REQUEST_MESSAGE)
            this.processBlockHeadersRequestMessage(sender, (BlockHeadersRequestMessage) message);
        else if (mType == MessageType.BLOCK_HEADERS_RESPONSE_MESSAGE)
            this.processBlockHeadersResponseMessage(sender, (BlockHeadersResponseMessage) message);
        else if (mType == MessageType.BLOCK_HASH_REQUEST_MESSAGE)
            this.processBlockHashRequestMessage(sender, (BlockHashRequestMessage) message);
        else if (mType == MessageType.BLOCK_HASH_RESPONSE_MESSAGE)
            this.processBlockHashResponseMessage(sender, (BlockHashResponseMessage) message);
        else if (mType == MessageType.SKELETON_REQUEST_MESSAGE)
            this.processSkeletonRequestMessage(sender, (SkeletonRequestMessage) message);
        else if (mType == MessageType.SKELETON_RESPONSE_MESSAGE)
            this.processSkeletonResponseMessage(sender, (SkeletonResponseMessage) message);
        else if (mType == MessageType.NEW_BLOCK_HASH_MESSAGE)
            this.processNewBlockHashMessage(sender, (NewBlockHashMessage) message);
        else if(!blockProcessor.hasBetterBlockToSync()) {
            if (mType == MessageType.NEW_BLOCK_HASHES)
                this.processNewBlockHashesMessage(sender, (NewBlockHashesMessage) message);
            else if (mType == MessageType.TRANSACTIONS)
                this.processTransactionsMessage(sender, (TransactionsMessage) message);
        }
        else
            loggerMessageProcess.debug("Message[{}] not processed.", message.getMessageType());

        loggerMessageProcess.debug("Message[{}] processed after [{}] nano.", message.getMessageType(), System.nanoTime() - start);
    }

    @Override
    public void postMessage(MessageChannel sender, Message message) throws InterruptedException {
        ByteArrayWrapper encodedMessage = new ByteArrayWrapper(HashUtil.sha3(message.getEncoded()));
        logger.trace("Start post message (queue size {}) (message type {})", this.queue.size(), message.getMessageType());
        if (!receivedMessages.contains(encodedMessage)) {
            if (message.getMessageType() == MessageType.BLOCK_MESSAGE || message.getMessageType() == MessageType.TRANSACTIONS) {
                addReceivedMessage(encodedMessage);
            }
            if (!this.queue.offer(new MessageTask(sender, message))){
                logger.trace("Queue full, message not added to the queue");
            }
        } else {
            recordEvent(sender, EventType.REPEATED_MESSAGE);
            logger.trace("Received message already known, not added to the queue");
        }

        logger.trace("End post message (queue size {})", this.queue.size());

        // There's an obvious race condition here, but fear not.
        // receivedMessages and logger are thread-safe
        // cleanMsgTimestamp is a long replaced by the next value, we don't care
        // enough about the precision of the value it takes
        long currentTime = System.currentTimeMillis();
        if (currentTime - cleanMsgTimestamp > RECEIVED_MESSAGES_CACHE_DURATION) {
            logger.trace("Cleaning {} messages from rlp queue", receivedMessages.size());
            receivedMessages.clear();
            cleanMsgTimestamp = currentTime;
        }
    }

    private void addReceivedMessage(ByteArrayWrapper message) {
        if (message != null) {
            if (this.receivedMessages.size() >= MAX_NUMBER_OF_MESSAGES_CACHED) {
                this.receivedMessages.clear();
            }
            this.receivedMessages.add(message);
        }
    }

    public void start() {
        new Thread(this).start();
    }

    public void stop() {
        this.stopped = true;
    }

    @Override
    public void run() {
        while (!stopped) {
            try {
                logger.trace("Get task");

                final MessageTask task = this.queue.poll(10, TimeUnit.SECONDS);

                loggerMessageProcess.debug("Queued Messages: {}", this.queue.size());

                if (task != null) {
                    logger.trace("Start task");
                    this.processMessage(task.getSender(), task.getMessage());
                    logger.trace("End task");
                } else {
                    logger.trace("No task");
                }

                Long now = System.currentTimeMillis();
                Duration timePassed = Duration.ofMillis(now - lastStatusSent);
                if (timePassed.getSeconds() > 10) {
                    lastStatusSent = now;

                    //Refresh status to peers every 10 seconds or so
                    this.blockProcessor.sendStatusToAll();

                    // Notify SyncProcessor that some time has passed.
                    // This will allow to perform cleanup and other time-dependant tasks.
                    this.syncProcessor.onTimePassed(timePassed);
                }
            }
            catch (Exception ex) {
                logger.error("Error {}", ex.getMessage());
            }
        }
    }

    @CheckForNull
    public synchronized Block getBestBlock() {
        if (this.blockProcessor != null)
            return this.blockProcessor.getBlockchain().getBestBlock();

        return null;
    }

    @CheckForNull
    public synchronized BigInteger getTotalDifficulty() {
        if (this.blockProcessor != null)
            return this.blockProcessor.getBlockchain().getTotalDifficulty();

        return null;
    }

    /**
     * isValidBlock validates if the given block meets the minimum criteria to be processed:
     * The PoW should be valid and the block can't be too far in the future.
     *
     * @param block the block to check
     * @return true if the block is valid, false otherwise.
     */
    private boolean isValidBlock(@Nonnull final Block block) {
        try {
            return blockValidationRule.isValid(block);
        } catch (Exception e) {
            logger.error("Failed to validate PoW from block {}: {}", block.getShortHash(), e);
            return false;
        }
    }

    /**
     * processBlockMessage processes a BlockMessage message, adding the block to the blockchain if appropriate, or
     * forwarding it to peers that are missing the Block.
     *
     * @param sender  the message sender.
     * @param message the BlockMessage.
     */
    private void processBlockMessage(@Nonnull final MessageChannel sender, @Nonnull final BlockMessage message) {
        final Block block = message.getBlock();
        logger.trace("Process block {} {}", block.getNumber(), block.getShortHash());

        if (block.isGenesis()) {
            logger.trace("Skip block processing {} {}", block.getNumber(), block.getShortHash());
            return;
        }

        Metrics.processBlockMessage("start", block, sender.getPeerNodeID());

        boolean wasOrphan = !this.blockProcessor.hasBlockInSomeBlockchain(block.getHash());

        if (!isValidBlock(block)) {
            logger.trace("Invalid block {} {}", block.getNumber(), block.getShortHash());
            recordEvent(sender, EventType.INVALID_BLOCK);
            return;
        }

        long start = System.nanoTime();

        BlockProcessResult result = this.blockProcessor.processBlock(sender, block);

        long time = System.nanoTime() - start;

        if (time >= 1000000000)
            result.logResult(block.getShortHash(), time);

        Metrics.processBlockMessage("blockProcessed", block, sender.getPeerNodeID());

        recordEvent(sender, EventType.VALID_BLOCK);

        long currentTimeMillis = System.currentTimeMillis();

        if (result.anyImportedBestResult())
            lastImportedBestBlock = currentTimeMillis;

        if (currentTimeMillis - lastImportedBestBlock > WAIT_TIME_ACCEPT_ADVANCED_BLOCKS)
        {
            logger.trace("Removed blocks advanced filter in NodeBlockProcessor");
            this.blockProcessor.acceptAnyBlock();
            this.receivedMessages.clear();
        }

        // is new block and it is not orphan, it is in some blockchain
        if (wasOrphan && result.wasBlockAdded(block) && !this.blockProcessor.isSyncingBlocks())
            relayBlock(sender, block);

        Metrics.processBlockMessage("finish", block, sender.getPeerNodeID());
    }

    private void relayBlock(@Nonnull MessageChannel sender, Block block) {
        // TODO(mvanotti): Remove when channel manager is required.
        if (channelManager == null)
            return;

        final BlockNodeInformation nodeInformation = this.blockProcessor.getNodeInformation();
        final Set<NodeID> nodesWithBlock = nodeInformation.getNodesByBlock(block.getHash());

        final Set<NodeID> nodesToSkip = new HashSet<>();
        nodesToSkip.addAll(nodesWithBlock);

        if (this.syncProcessor != null) {
            final Set<NodeID> newNodes = this.syncProcessor.getKnownPeersNodeIDs();
            nodesToSkip.addAll(newNodes);

            channelManager.broadcastBlockHash(block.getHash(), newNodes);
        }

        final Set<NodeID> nodesSent = channelManager.broadcastBlock(block, nodesToSkip);

        // These set of nodes now know about this block.
        for (final NodeID nodeID : nodesSent)
            nodeInformation.addBlockToNode(new ByteArrayWrapper(block.getHash()), nodeID);

        Metrics.processBlockMessage("blockRelayed", block, sender.getPeerNodeID());
    }

    private void processStatusMessage(@Nonnull final MessageChannel sender, @Nonnull final StatusMessage message) {
        final Status status = message.getStatus();
        logger.trace("Process status {}", status.getBestBlockNumber());

        if (status.getBestBlockParentHash() != null)
            this.syncProcessor.processStatus(sender, status);
        else
            this.blockProcessor.processStatus(sender, status);
    }

    private void processGetBlockMessage(@Nonnull final MessageChannel sender, @Nonnull final GetBlockMessage message) {
        if (this.blockProcessor != null) {
            final byte[] hash = message.getBlockHash();
            this.blockProcessor.processGetBlock(sender, hash);
        }
    }

    private void processBlockRequestMessage(@Nonnull final MessageChannel sender, @Nonnull final BlockRequestMessage message) {
        if (this.blockProcessor != null) {
            final long requestId = message.getId();
            final byte[] hash = message.getBlockHash();
            this.blockProcessor.processBlockRequest(sender, requestId, hash);
        }
    }

    private void processBlockResponseMessage(@Nonnull final MessageChannel sender, @Nonnull final BlockResponseMessage message) {
        if (this.syncProcessor != null)
            this.syncProcessor.processBlockResponse(sender, message);

        // TODO: in the new protocol, review the relay of blocks
        relayBlock(sender, message.getBlock());
    }

    private void processSkeletonRequestMessage(@Nonnull final MessageChannel sender, @Nonnull final SkeletonRequestMessage message) {
        if (this.blockProcessor != null) {
            final long requestId = message.getId();
            final long startNumber = message.getStartNumber();
            this.blockProcessor.processSkeletonRequest(sender, requestId, startNumber);
        }
    }

    private void processBlockHeadersRequestMessage(@Nonnull final MessageChannel sender, @Nonnull final BlockHeadersRequestMessage message) {
        if (this.blockProcessor != null) {
            final long requestId = message.getId();
            final byte[] hash = message.getHash();
            final int count = message.getCount();
            this.blockProcessor.processBlockHeadersRequest(sender, requestId, hash, count);
        }
    }

    private void processBlockHashRequestMessage(@Nonnull final MessageChannel sender, @Nonnull final BlockHashRequestMessage message) {
        if (this.blockProcessor != null) {
            final long requestId = message.getId();
            final long height = message.getHeight();
            this.blockProcessor.processBlockHashRequest(sender, requestId, height);
        }
    }

    private void processBlockHashResponseMessage(@Nonnull final MessageChannel sender, @Nonnull final BlockHashResponseMessage message) {
        if (this.syncProcessor != null)
            this.syncProcessor.processBlockHashResponse(sender, message);
    }

    private void processNewBlockHashMessage(@Nonnull final MessageChannel sender, @Nonnull final NewBlockHashMessage message) {
        if (this.syncProcessor != null)
            this.syncProcessor.processNewBlockHash(sender, message);
    }

    private void processSkeletonResponseMessage(@Nonnull final MessageChannel sender, @Nonnull final SkeletonResponseMessage message) {
        if (this.syncProcessor != null)
            this.syncProcessor.processSkeletonResponse(sender, message);
    }

    private void processBlockHeadersResponseMessage(@Nonnull final MessageChannel sender, @Nonnull final BlockHeadersResponseMessage message) {
        if (this.syncProcessor != null)
            this.syncProcessor.processBlockHeadersResponse(sender, message);
    }

    private void processBodyRequestMessage(@Nonnull final MessageChannel sender, @Nonnull final BodyRequestMessage message) {
        if (this.blockProcessor != null) {
            final long requestId = message.getId();
            final byte[] hash = message.getBlockHash();
            this.blockProcessor.processBodyRequest(sender, requestId, hash);
        }
    }

    private void processBodyResponseMessage(@Nonnull final MessageChannel sender, @Nonnull final BodyResponseMessage message) {
        if (this.syncProcessor != null)
            this.syncProcessor.processBodyResponse(sender, message);
    }

    private void processGetBlockHeadersMessage(@Nonnull final MessageChannel sender, @Nonnull final GetBlockHeadersMessage message) {
        // TODO(mvanotti): Add upper bound to maxHeaders.
        final byte[] hash = message.getBlockHash();
        final int maxHeaders = message.getMaxHeaders();
        final int skipBlocks = message.getSkipBlocks();
        final boolean reverse = message.isReverse();
        final long blockNumber = message.getBlockNumber();

        if (this.blockProcessor != null)
            this.blockProcessor.processGetBlockHeaders(sender, blockNumber, hash, maxHeaders, skipBlocks, reverse);
    }

    private void processBlockHeadersMessage(@Nonnull final MessageChannel sender, @Nonnull final BlockHeadersMessage message) {
        message.getBlockHeaders().forEach(h -> Metrics.newBlockHeader(h, sender.getPeerNodeID()));

        if (blockProcessor != null) {
            blockProcessor.processBlockHeaders(sender, message.getBlockHeaders());
        }
    }

    private void processNewBlockHashesMessage(@Nonnull final MessageChannel sender, @Nonnull final NewBlockHashesMessage message) {
        message.getBlockIdentifiers().forEach(bi -> Metrics.newBlockHash(bi, sender.getPeerNodeID()));

        if (blockProcessor != null) {
            blockProcessor.processNewBlockHashesMessage(sender, message);
        }
    }

    private void processTransactionsMessage(@Nonnull final MessageChannel sender, @Nonnull final TransactionsMessage message) {
        long start = System.nanoTime();
        loggerMessageProcess.debug("Tx message about to be process: {}", message.getMessageContentInfo());

        List<Transaction> ptxs = message.getTransactions();
        Metrics.processTxsMessage("start", ptxs, sender.getPeerNodeID());

        List<Transaction> txs = new LinkedList();
        for (Transaction tx : ptxs) {
            if (tx.getSignature() == null || !tx.acceptTransactionSignature()) {
                recordEvent(sender, EventType.INVALID_TRANSACTION);
            } else {
                txs.add(tx);
                recordEvent(sender, EventType.VALID_TRANSACTION);
            }
        }

        List<Transaction> acceptedTxs = txHandler.retrieveValidTxs(txs);

        Metrics.processTxsMessage("txsValidated", acceptedTxs, sender.getPeerNodeID());

        // TODO(mmarquez): Add all this logic to the TxHandler
        if (pendingState != null) {
            acceptedTxs = pendingState.addWireTransactions(acceptedTxs);
        }

        Metrics.processTxsMessage("validTxsAddedToPendingState", acceptedTxs, sender.getPeerNodeID());

        if (channelManager != null) {
            /* Relay all transactions to peers that don't have them */
            for (Transaction tx : acceptedTxs) {
                final Set<NodeID> nodesToSkip = new HashSet<>(transactionNodeInformation.getNodesByTransaction(tx.getHash()));
                nodesToSkip.add(sender.getPeerNodeID());
                final Set<NodeID> newNodes = channelManager.broadcastTransaction(tx, nodesToSkip);

                final ByteArrayWrapper txhash = new ByteArrayWrapper(tx.getHash());
                for (NodeID nodeID : newNodes)
                    transactionNodeInformation.addTransactionToNode(txhash, nodeID);
            }

            Metrics.processTxsMessage("validTxsRelayed", acceptedTxs, sender.getPeerNodeID());
        }

        for (Transaction tx : acceptedTxs) {
            final ByteArrayWrapper txhash = new ByteArrayWrapper(tx.getHash());
            transactionNodeInformation.addTransactionToNode(txhash, sender.getPeerNodeID());
        }

        Metrics.processTxsMessage("txToNodeInfoUpdated", acceptedTxs, sender.getPeerNodeID());
        Metrics.processTxsMessage("finish", acceptedTxs, sender.getPeerNodeID());

        loggerMessageProcess.debug("Tx message process finished after [{}] nano.", System.nanoTime() - start);
    }

    private void recordEvent(MessageChannel sender, EventType event) {
        if (this.peerScoringManager == null)
            return;

        if (sender == null)
            return;

        this.peerScoringManager.recordEvent(sender.getPeerNodeID(), sender.getAddress(), event);
    }

    private static class MessageTask {
        private MessageChannel sender;
        private Message message;

        public MessageTask(MessageChannel sender, Message message) {
            this.sender = sender;
            this.message = message;
        }

        public MessageChannel getSender() {
            return this.sender;
        }

        public Message getMessage() {
            return this.message;
        }
    }
}

