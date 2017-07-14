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
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Block;
import org.ethereum.core.PendingState;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.validator.ProofOfWorkRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private final ChannelManager channelManager;
    private final PendingState pendingState;
    private long lastStatusSent = 0;

    private ProofOfWorkRule powRule;

    private TransactionNodeInformation transactionNodeInformation;

    private LinkedBlockingQueue<MessageTask> queue = new LinkedBlockingQueue<>();
    private Set<ByteArrayWrapper> receivedMessages = Collections.synchronizedSet(new HashSet<ByteArrayWrapper>());
    private long cleanMsgTimestamp = 0;
    private long lastImportedBestBlock;

    private volatile boolean stopped;

    private TxHandler txHandler;

    public NodeMessageHandler(@Nonnull final BlockProcessor blockProcessor,
                              @Nullable final ChannelManager channelManager,
                              @Nullable final PendingState pendingState,
                              final TxHandler txHandler) {
        this.channelManager = channelManager;
        this.blockProcessor = blockProcessor;
        this.pendingState = pendingState;
        powRule = new ProofOfWorkRule();
        transactionNodeInformation = new TransactionNodeInformation();
        this.txHandler = txHandler;
        this.lastImportedBestBlock = System.currentTimeMillis();
    }

    @VisibleForTesting
    public NodeMessageHandler disablePoWValidation() {
        this.powRule = null;
        return this;
    }

    /**
     * processMessage processes a RSK Message, doing the appropriate action based on the message type.
     *
     * @param sender  the message sender.
     * @param message the message to be processed.
     */
    public synchronized void processMessage(final MessageSender sender, @Nonnull final Message message) {
        long start = System.nanoTime();
        logger.trace("Process message type: {}", message.getMessageType());

        MessageType mType = message.getMessageType();

        if (mType == MessageType.GET_BLOCK_MESSAGE)
            this.processGetBlockMessage(sender, (GetBlockMessage) message);

        if (mType == MessageType.GET_BLOCK_HEADERS_MESSAGE)
            this.processGetBlockHeadersMessage(sender, (GetBlockHeadersMessage) message);

        if (mType == MessageType.BLOCK_MESSAGE)
            this.processBlockMessage(sender, (BlockMessage) message);

        if (mType == MessageType.STATUS_MESSAGE)
            this.processStatusMessage(sender, (StatusMessage) message);

        if (mType == MessageType.BLOCK_HEADERS_MESSAGE)
            this.processBlockHeadersMessage(sender, (BlockHeadersMessage) message);

        if(!blockProcessor.hasBetterBlockToSync()) {
            if (mType == MessageType.NEW_BLOCK_HASHES)
                this.processNewBlockHashesMessage(sender, (NewBlockHashesMessage) message);

            if (mType == MessageType.TRANSACTIONS)
                this.processTransactionsMessage(sender, (TransactionsMessage) message);
        }
        loggerMessageProcess.debug("Message[{}] processed after [{}] nano.", message.getMessageType(), System.nanoTime() - start);
    }

    @Override
    public void postMessage(MessageSender sender, Message message) throws InterruptedException {
        ByteArrayWrapper encodedMessage = new ByteArrayWrapper(HashUtil.sha3(message.getEncoded()));
        logger.trace("Start post message (queue size {}) (message type {})", this.queue.size(), message.getMessageType());
        if (!receivedMessages.contains(encodedMessage)) {
            if (message.getMessageType() == MessageType.BLOCK_MESSAGE || message.getMessageType() == MessageType.TRANSACTIONS) {
                addReceivedMessage(encodedMessage);
            }
            this.queue.offer(new MessageTask(sender, message));
        } else {
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

                //Refresh status to peers every 10 seconds or so
                Long now = System.currentTimeMillis();
                if (now - lastStatusSent > TimeUnit.SECONDS.toMillis(10)) {
                    this.blockProcessor.sendStatusToAll();
                    lastStatusSent = now;
                }
            }
            catch (Throwable ex) {
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

    /**
     * isValidBlock validates if the given block meets the minimum criteria to be processed:
     * The PoW should be valid and the block can't be too far in the future.
     *
     * @param block the block to check
     * @return true if the block is valid, false otherwise.
     */
    private boolean isValidBlock(@Nonnull final Block block) {
        if (powRule == null)
            return true;

        try {

            if (!powRule.isValid(block))
                return false;

        } catch (Exception e) {
            logger.error("Failed to validate PoW from block {}: {}", block.getShortHash(), e);
            return false;
        }
        return true;
    }

    /**
     * processBlockMessage processes a BlockMessage message, adding the block to the blockchain if appropriate, or
     * forwarding it to peers that are missing the Block.
     *
     * @param sender  the message sender.
     * @param message the BlockMessage.
     */
    private void processBlockMessage(@Nonnull final MessageSender sender, @Nonnull final BlockMessage message) {
        final Block block = message.getBlock();
        logger.trace("Process block {} {}", block.getNumber(), block.getShortHash());
        Metrics.processBlockMessage("start", block, sender.getNodeID());

        boolean wasOrphan = !this.blockProcessor.hasBlockInSomeBlockchain(block.getHash());

        if (!isValidBlock(block)) {
            logger.trace("Invalid block {} {}", block.getNumber(), block.getShortHash());
            return;
        }

        long start = System.nanoTime();

        BlockProcessResult result = this.blockProcessor.processBlock(sender, block);

        long time = System.nanoTime() - start;

        if (time >= 1000000000)
            result.logResult(block.getShortHash(), time);

        Metrics.processBlockMessage("blockProcessed", block, sender.getNodeID());

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
        if (wasOrphan && result.wasBlockAdded(block) && !this.blockProcessor.isSyncingBlocks()) {
            final BlockNodeInformation nodeInformation = this.blockProcessor.getNodeInformation();
            final Set<NodeID> nodesToSkip = nodeInformation.getNodesByBlock(block.getHash());

            // TODO(mvanotti): Remove when channel manager is required.
            if (channelManager == null)
                return;

            final Set<NodeID> nodesSent = channelManager.broadcastBlock(block, nodesToSkip);

            // These set of nodes now know about this block.
            for (final NodeID nodeID : nodesSent) {
                nodeInformation.addBlockToNode(new ByteArrayWrapper(block.getHash()), nodeID);
            }

            Metrics.processBlockMessage("blockRelayed", block, sender.getNodeID());
        }

        Metrics.processBlockMessage("finish", block, sender.getNodeID());
    }

    private void processStatusMessage(@Nonnull final MessageSender sender, @Nonnull final StatusMessage message) {
        final Status status = message.getStatus();
        logger.trace("Process status {}", status.getBestBlockNumber());

        if (this.blockProcessor != null)
            this.blockProcessor.processStatus(sender, status);
    }

    private void processGetBlockMessage(@Nonnull final MessageSender sender, @Nonnull final GetBlockMessage message) {
        final byte[] hash = message.getBlockHash();

        if (this.blockProcessor != null)
            this.blockProcessor.processGetBlock(sender, hash);
    }

    private void processGetBlockHeadersMessage(@Nonnull final MessageSender sender, @Nonnull final GetBlockHeadersMessage message) {
        // TODO(mvanotti): Add upper bound to maxHeaders.
        final byte[] hash = message.getBlockHash();
        final int maxHeaders = message.getMaxHeaders();
        final int skipBlocks = message.getSkipBlocks();
        final boolean reverse = message.isReverse();
        final long blockNumber = message.getBlockNumber();

        if (this.blockProcessor != null)
            this.blockProcessor.processGetBlockHeaders(sender, blockNumber, hash, maxHeaders, skipBlocks, reverse);
    }

    private void processBlockHeadersMessage(@Nonnull final MessageSender sender, @Nonnull final BlockHeadersMessage message) {
        message.getBlockHeaders().forEach(h -> Metrics.newBlockHeader(h, sender.getNodeID()));

        if (blockProcessor != null) {
            blockProcessor.processBlockHeaders(sender, message.getBlockHeaders());
        }
    }

    private void processNewBlockHashesMessage(@Nonnull final MessageSender sender, @Nonnull final NewBlockHashesMessage message) {
        message.getBlockIdentifiers().forEach(bi -> Metrics.newBlockHash(bi, sender.getNodeID()));

        if (blockProcessor != null) {
            blockProcessor.processNewBlockHashesMessage(sender, message);
        }
    }

    private void processTransactionsMessage(@Nonnull final MessageSender sender, @Nonnull final TransactionsMessage message) {
        long start = System.nanoTime();
        loggerMessageProcess.debug("Tx message about to be process: {}", message.getMessageContentInfo());

        List<Transaction> txs = message.getTransactions();
        Metrics.processTxsMessage("start", txs, sender.getNodeID());

        List<Transaction> acceptedTxs = txHandler.retrieveValidTxs(txs);

        Metrics.processTxsMessage("txsValidated", acceptedTxs, sender.getNodeID());

        // TODO(mmarquez): Add all this logic to the TxHandler
        if (pendingState != null) {
            acceptedTxs = pendingState.addWireTransactions(acceptedTxs);
        }

        Metrics.processTxsMessage("validTxsAddedToPendingState", acceptedTxs, sender.getNodeID());

        if (channelManager != null) {
            /* Relay all transactions to peers that don't have them */
            for (Transaction tx : acceptedTxs) {
                final Set<NodeID> nodesToSkip = new HashSet<>(transactionNodeInformation.getNodesByTransaction(tx.getHash()));
                nodesToSkip.add(sender.getNodeID());
                final Set<NodeID> newNodes = channelManager.broadcastTransaction(tx, nodesToSkip);

                final ByteArrayWrapper txhash = new ByteArrayWrapper(tx.getHash());
                for (NodeID nodeID : newNodes)
                    transactionNodeInformation.addTransactionToNode(txhash, nodeID);
            }

            Metrics.processTxsMessage("validTxsRelayed", acceptedTxs, sender.getNodeID());
        }

        for (Transaction tx : acceptedTxs) {
            final ByteArrayWrapper txhash = new ByteArrayWrapper(tx.getHash());
            transactionNodeInformation.addTransactionToNode(txhash, sender.getNodeID());
        }

        Metrics.processTxsMessage("txToNodeInfoUpdated", acceptedTxs, sender.getNodeID());
        Metrics.processTxsMessage("finish", acceptedTxs, sender.getNodeID());
        loggerMessageProcess.debug("Tx message process finished after [{}] nano.", System.nanoTime() - start);
    }

    private static class MessageTask {
        private MessageSender sender;
        private Message message;

        public MessageTask(MessageSender sender, Message message) {
            this.sender = sender;
            this.message = message;
        }

        public MessageSender getSender() {
            return this.sender;
        }

        public Message getMessage() {
            return this.message;
        }
    }
}

