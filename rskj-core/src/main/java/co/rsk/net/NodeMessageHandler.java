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

import co.rsk.core.bc.BlockChainStatus;
import co.rsk.net.handler.TxHandler;
import co.rsk.net.messages.*;
import co.rsk.scoring.EventType;
import co.rsk.scoring.PeerScoringManager;
import co.rsk.validators.BlockValidationRule;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Block;
import org.ethereum.core.PendingState;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.net.server.ChannelManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigInteger;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Created by ajlopez on 5/10/2016.
 */
public class NodeMessageHandler implements MessageHandler, Runnable {
    private static final Logger logger = LoggerFactory.getLogger("messagehandler");
    private static final Logger loggerMessageProcess = LoggerFactory.getLogger("messageProcess");
    public static final int MAX_NUMBER_OF_MESSAGES_CACHED = 5000;
    public static final long RECEIVED_MESSAGES_CACHE_DURATION = TimeUnit.MINUTES.toMillis(2);
    private final BlockProcessor blockProcessor;
    private final SyncProcessor syncProcessor;
    private final ChannelManager channelManager;
    private final PendingState pendingState;
    private final PeerScoringManager peerScoringManager;
    private volatile long lastStatusSent = System.currentTimeMillis();
    private volatile long lastTickSent = System.currentTimeMillis();

    private BlockValidationRule blockValidationRule;

    private TransactionNodeInformation transactionNodeInformation;

    private LinkedBlockingQueue<MessageTask> queue = new LinkedBlockingQueue<>();
    private Set<ByteArrayWrapper> receivedMessages = Collections.synchronizedSet(new HashSet<ByteArrayWrapper>());
    private long cleanMsgTimestamp = 0;

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
        this.transactionNodeInformation = new TransactionNodeInformation();
        this.txHandler = txHandler;
        this.cleanMsgTimestamp = System.currentTimeMillis();
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

        if (mType == MessageType.GET_BLOCK_MESSAGE) {
            this.processGetBlockMessage(sender, (GetBlockMessage) message);
        } else if (mType == MessageType.BLOCK_MESSAGE) {
            this.processBlockMessage(sender, (BlockMessage) message);
        } else if (mType == MessageType.STATUS_MESSAGE) {
            this.processStatusMessage(sender, (StatusMessage) message);
        } else if (mType == MessageType.BLOCK_REQUEST_MESSAGE) {
            this.processBlockRequestMessage(sender, (BlockRequestMessage) message);
        } else if (mType == MessageType.BLOCK_RESPONSE_MESSAGE) {
            this.processBlockResponseMessage(sender, (BlockResponseMessage) message);
        } else if (mType == MessageType.BODY_REQUEST_MESSAGE) {
            this.processBodyRequestMessage(sender, (BodyRequestMessage) message);
        } else if (mType == MessageType.BODY_RESPONSE_MESSAGE) {
            this.processBodyResponseMessage(sender, (BodyResponseMessage) message);
        } else if (mType == MessageType.BLOCK_HEADERS_REQUEST_MESSAGE) {
            this.processBlockHeadersRequestMessage(sender, (BlockHeadersRequestMessage) message);
        } else if (mType == MessageType.BLOCK_HEADERS_RESPONSE_MESSAGE) {
            this.processBlockHeadersResponseMessage(sender, (BlockHeadersResponseMessage) message);
        } else if (mType == MessageType.BLOCK_HASH_REQUEST_MESSAGE) {
            this.processBlockHashRequestMessage(sender, (BlockHashRequestMessage) message);
        } else if (mType == MessageType.BLOCK_HASH_RESPONSE_MESSAGE) {
            this.processBlockHashResponseMessage(sender, (BlockHashResponseMessage) message);
        } else if (mType == MessageType.SKELETON_REQUEST_MESSAGE) {
            this.processSkeletonRequestMessage(sender, (SkeletonRequestMessage) message);
        } else if (mType == MessageType.SKELETON_RESPONSE_MESSAGE) {
            this.processSkeletonResponseMessage(sender, (SkeletonResponseMessage) message);
        } else if (mType == MessageType.NEW_BLOCK_HASH_MESSAGE) {
            this.processNewBlockHashMessage(sender, (NewBlockHashMessage) message);
        } else if(!blockProcessor.hasBetterBlockToSync()) {
            if (mType == MessageType.NEW_BLOCK_HASHES) {
                this.processNewBlockHashesMessage(sender, (NewBlockHashesMessage) message);
            } else if (mType == MessageType.TRANSACTIONS) {
                this.processTransactionsMessage(sender, (TransactionsMessage) message);
            }
        } else {
            loggerMessageProcess.debug("Message[{}] not processed.", message.getMessageType());
        }

        loggerMessageProcess.debug("Message[{}] processed after [{}] nano.", message.getMessageType(), System.nanoTime() - start);
    }

    @Override
    public void postMessage(MessageChannel sender, Message message) throws InterruptedException {
        logger.trace("Start post message (queue size {}) (message type {})", this.queue.size(), message.getMessageType());
        // There's an obvious race condition here, but fear not.
        // receivedMessages and logger are thread-safe
        // cleanMsgTimestamp is a long replaced by the next value, we don't care
        // enough about the precision of the value it takes
        cleanExpiredMessages();
        tryAddMessage(sender, message);
        logger.trace("End post message (queue size {})", this.queue.size());
    }

    private void tryAddMessage(MessageChannel sender, Message message) {
        ByteArrayWrapper encodedMessage = new ByteArrayWrapper(HashUtil.sha3(message.getEncoded()));
        if (!receivedMessages.contains(encodedMessage)) {
            if (message.getMessageType() == MessageType.BLOCK_MESSAGE || message.getMessageType() == MessageType.TRANSACTIONS) {
                if (this.receivedMessages.size() >= MAX_NUMBER_OF_MESSAGES_CACHED) {
                    this.receivedMessages.clear();
                }
                this.receivedMessages.add(encodedMessage);
            }
            if (!this.queue.offer(new MessageTask(sender, message))){
                logger.trace("Queue full, message not added to the queue");
            }
        } else {
            recordEvent(sender, EventType.REPEATED_MESSAGE);
            logger.trace("Received message already known, not added to the queue");
        }
    }

    private void cleanExpiredMessages() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - cleanMsgTimestamp > RECEIVED_MESSAGES_CACHE_DURATION) {
            logger.trace("Cleaning {} messages from rlp queue", receivedMessages.size());
            receivedMessages.clear();
            cleanMsgTimestamp = currentTime;
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

                final MessageTask task = this.queue.poll(1, TimeUnit.SECONDS);

                loggerMessageProcess.debug("Queued Messages: {}", this.queue.size());

                if (task != null) {
                    logger.trace("Start task");
                    this.processMessage(task.getSender(), task.getMessage());
                    logger.trace("End task");
                } else {
                    logger.trace("No task");
                }

                updateTimedEvents();
            }
            catch (Exception ex) {
                logger.error("Error {}", ex);
            }
        }
    }

    private void updateTimedEvents() {
        Long now = System.currentTimeMillis();
        Duration timeTick = Duration.ofMillis(now - lastTickSent);
        this.syncProcessor.onTimePassed(timeTick);
        lastTickSent = now;

        //Refresh status to peers every 10 seconds or so
        Duration timeStatus = Duration.ofMillis(now - lastStatusSent);
        if (timeStatus.getSeconds() > 10) {
            sendStatusToAll();
            lastStatusSent = now;
        }
    }

    private synchronized void sendStatusToAll() {
        BlockChainStatus blockChainStatus = this.blockProcessor.getBlockchain().getStatus();
        Block block = blockChainStatus.getBestBlock();
        BigInteger totalDifficulty = blockChainStatus.getTotalDifficulty();

        Status status = new Status(block.getNumber(), block.getHash(), block.getParentHash(), totalDifficulty);
        logger.trace("Sending status best block to all {} {}", status.getBestBlockNumber(), Hex.toHexString(status.getBestBlockHash()).substring(0, 8));
        this.channelManager.broadcastStatus(status);
    }

    public synchronized Block getBestBlock() {
        return this.blockProcessor.getBlockchain().getBestBlock();
    }

    public synchronized BigInteger getTotalDifficulty() {
        return this.blockProcessor.getBlockchain().getTotalDifficulty();
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

        if (!isValidBlock(block)) {
            logger.trace("Invalid block {} {}", block.getNumber(), block.getShortHash());
            recordEvent(sender, EventType.INVALID_BLOCK);
            return;
        }

        boolean wasOrphan = !this.blockProcessor.hasBlockInSomeBlockchain(block.getHash());
        BlockProcessResult result = this.blockProcessor.processBlock(sender, block);

        Metrics.processBlockMessage("blockProcessed", block, sender.getPeerNodeID());

        recordEvent(sender, EventType.VALID_BLOCK);
        tryRelayBlock(sender, block, wasOrphan, result);

        Metrics.processBlockMessage("finish", block, sender.getPeerNodeID());
    }

    private void tryRelayBlock(@Nonnull MessageChannel sender, Block block, boolean wasOrphan, BlockProcessResult result) {
        // is new block and it is not orphan, it is in some blockchain
        if (!wasOrphan && result.wasBlockAdded(block) && !this.blockProcessor.hasBetterBlockToSync()) {
            relayBlock(sender, block);
        }
    }

    private void relayBlock(@Nonnull MessageChannel sender, Block block) {
        final BlockNodeInformation nodeInformation = this.blockProcessor.getNodeInformation();
        final Set<NodeID> nodesWithBlock = nodeInformation.getNodesByBlock(block.getHash());
        final Set<NodeID> newNodes = this.syncProcessor.getKnownPeersNodeIDs().stream()
                .filter(p -> !nodesWithBlock.contains(p))
                .collect(Collectors.toSet());

        channelManager.broadcastBlockHash(block.getHash(), newNodes);

        Metrics.processBlockMessage("blockRelayed", block, sender.getPeerNodeID());
    }

    private void processStatusMessage(@Nonnull final MessageChannel sender, @Nonnull final StatusMessage message) {
        final Status status = message.getStatus();
        logger.trace("Process status {}", status.getBestBlockNumber());
        this.syncProcessor.processStatus(sender, status);
    }

    private void processGetBlockMessage(@Nonnull final MessageChannel sender, @Nonnull final GetBlockMessage message) {
        final byte[] hash = message.getBlockHash();
        this.blockProcessor.processGetBlock(sender, hash);
    }

    private void processBlockRequestMessage(@Nonnull final MessageChannel sender, @Nonnull final BlockRequestMessage message) {
        final long requestId = message.getId();
        final byte[] hash = message.getBlockHash();
        this.blockProcessor.processBlockRequest(sender, requestId, hash);
    }

    private void processBlockResponseMessage(@Nonnull final MessageChannel sender, @Nonnull final BlockResponseMessage message) {
        this.syncProcessor.processBlockResponse(sender, message);
    }

    private void processSkeletonRequestMessage(@Nonnull final MessageChannel sender, @Nonnull final SkeletonRequestMessage message) {
        final long requestId = message.getId();
        final long startNumber = message.getStartNumber();
        this.blockProcessor.processSkeletonRequest(sender, requestId, startNumber);
    }

    private void processBlockHeadersRequestMessage(@Nonnull final MessageChannel sender, @Nonnull final BlockHeadersRequestMessage message) {
        final long requestId = message.getId();
        final byte[] hash = message.getHash();
        final int count = message.getCount();
        this.blockProcessor.processBlockHeadersRequest(sender, requestId, hash, count);
    }

    private void processBlockHashRequestMessage(@Nonnull final MessageChannel sender, @Nonnull final BlockHashRequestMessage message) {
        final long requestId = message.getId();
        final long height = message.getHeight();
        this.blockProcessor.processBlockHashRequest(sender, requestId, height);
    }

    private void processBlockHashResponseMessage(@Nonnull final MessageChannel sender, @Nonnull final BlockHashResponseMessage message) {
        this.syncProcessor.processBlockHashResponse(sender, message);
    }

    private void processNewBlockHashMessage(@Nonnull final MessageChannel sender, @Nonnull final NewBlockHashMessage message) {
        this.syncProcessor.processNewBlockHash(sender, message);
    }

    private void processSkeletonResponseMessage(@Nonnull final MessageChannel sender, @Nonnull final SkeletonResponseMessage message) {
        this.syncProcessor.processSkeletonResponse(sender, message);
    }

    private void processBlockHeadersResponseMessage(@Nonnull final MessageChannel sender, @Nonnull final BlockHeadersResponseMessage message) {
        this.syncProcessor.processBlockHeadersResponse(sender, message);
    }

    private void processBodyRequestMessage(@Nonnull final MessageChannel sender, @Nonnull final BodyRequestMessage message) {
        final long requestId = message.getId();
        final byte[] hash = message.getBlockHash();
        this.blockProcessor.processBodyRequest(sender, requestId, hash);
    }

    private void processBodyResponseMessage(@Nonnull final MessageChannel sender, @Nonnull final BodyResponseMessage message) {
        this.syncProcessor.processBodyResponse(sender, message);
    }

    private void processNewBlockHashesMessage(@Nonnull final MessageChannel sender, @Nonnull final NewBlockHashesMessage message) {
        message.getBlockIdentifiers().forEach(bi -> Metrics.newBlockHash(bi, sender.getPeerNodeID()));
        blockProcessor.processNewBlockHashesMessage(sender, message);
    }

    private void processTransactionsMessage(@Nonnull final MessageChannel sender, @Nonnull final TransactionsMessage message) {
        long start = System.nanoTime();
        loggerMessageProcess.debug("Tx message about to be process: {}", message.getMessageContentInfo());

        List<Transaction> messageTxs = message.getTransactions();
        Metrics.processTxsMessage("start", messageTxs, sender.getPeerNodeID());

        List<Transaction> txs = new LinkedList();
        for (Transaction tx : messageTxs) {
            if (!tx.acceptTransactionSignature()) {
                recordEvent(sender, EventType.INVALID_TRANSACTION);
            } else {
                txs.add(tx);
                recordEvent(sender, EventType.VALID_TRANSACTION);
            }
        }

        List<Transaction> acceptedTxs = txHandler.retrieveValidTxs(txs);

        Metrics.processTxsMessage("txsValidated", acceptedTxs, sender.getPeerNodeID());

        // TODO(mmarquez): Add all this logic to the TxHandler
        acceptedTxs = pendingState.addWireTransactions(acceptedTxs);

        Metrics.processTxsMessage("validTxsAddedToPendingState", acceptedTxs, sender.getPeerNodeID());
        /* Relay all transactions to peers that don't have them */
        relayTransactions(sender, acceptedTxs);
        Metrics.processTxsMessage("validTxsRelayed", acceptedTxs, sender.getPeerNodeID());

        Metrics.processTxsMessage("finish", acceptedTxs, sender.getPeerNodeID());

        loggerMessageProcess.debug("Tx message process finished after [{}] nano.", System.nanoTime() - start);
    }

    private void relayTransactions(@Nonnull MessageChannel sender, List<Transaction> acceptedTxs) {
        for (Transaction tx : acceptedTxs) {
            final ByteArrayWrapper txHash = new ByteArrayWrapper(tx.getHash());
            transactionNodeInformation.addTransactionToNode(txHash, sender.getPeerNodeID());
            final Set<NodeID> nodesToSkip = new HashSet<>(transactionNodeInformation.getNodesByTransaction(tx.getHash()));
            final Set<NodeID> newNodes = channelManager.broadcastTransaction(tx, nodesToSkip);

            newNodes.forEach(nodeID -> transactionNodeInformation.addTransactionToNode(txHash, nodeID));
        }
    }

    private void recordEvent(MessageChannel sender, EventType event) {
        if (sender == null) {
            return;
        }

        this.peerScoringManager.recordEvent(sender.getPeerNodeID(), sender.getAddress(), event);
    }

    @VisibleForTesting
    public BlockProcessor getBlockProcessor() {
        return blockProcessor;
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

