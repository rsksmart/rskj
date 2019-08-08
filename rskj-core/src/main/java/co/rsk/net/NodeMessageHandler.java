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

import co.rsk.config.RskSystemProperties;
import co.rsk.core.BlockDifficulty;
import co.rsk.core.bc.BlockChainStatus;
import co.rsk.crypto.Keccak256;
import co.rsk.net.messages.*;
import co.rsk.scoring.EventType;
import co.rsk.scoring.PeerScoringManager;
import co.rsk.validators.BlockValidationRule;
import com.google.common.annotations.VisibleForTesting;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Block;
import org.ethereum.crypto.HashUtil;
import org.ethereum.net.server.ChannelManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class NodeMessageHandler implements MessageHandler, Runnable {
    private static final Logger logger = LoggerFactory.getLogger("messagehandler");
    private static final Logger loggerMessageProcess = LoggerFactory.getLogger("messageProcess");
    public static final int MAX_NUMBER_OF_MESSAGES_CACHED = 5000;
    public static final long RECEIVED_MESSAGES_CACHE_DURATION = TimeUnit.MINUTES.toMillis(2);

    private final RskSystemProperties config;
    private final BlockProcessor blockProcessor;
    private final SyncProcessor syncProcessor;
    private final ChannelManager channelManager;
    private final TransactionGateway transactionGateway;
    private final PeerScoringManager peerScoringManager;
    private volatile long lastStatusSent = System.currentTimeMillis();
    private volatile long lastTickSent = System.currentTimeMillis();

    private BlockValidationRule blockValidationRule;

    private LinkedBlockingQueue<MessageTask> queue = new LinkedBlockingQueue<>();
    private Set<Keccak256> receivedMessages = Collections.synchronizedSet(new HashSet<>());
    private long cleanMsgTimestamp = 0;

    private volatile boolean stopped;

    public NodeMessageHandler(RskSystemProperties config,
                              @Nonnull final BlockProcessor blockProcessor,
                              final SyncProcessor syncProcessor,
                              @Nullable final ChannelManager channelManager,
                              @Nullable final TransactionGateway transactionGateway,
                              @Nullable final PeerScoringManager peerScoringManager,
                              @Nonnull BlockValidationRule blockValidationRule) {
        this.config = config;
        this.channelManager = channelManager;
        this.blockProcessor = blockProcessor;
        this.syncProcessor = syncProcessor;
        this.transactionGateway = transactionGateway;
        this.blockValidationRule = blockValidationRule;
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

        MessageVisitor mv = new MessageVisitor(config,
                blockProcessor,
                syncProcessor,
                transactionGateway,
                peerScoringManager,
                channelManager,
                blockValidationRule,
                sender);
        message.accept(mv);

        loggerMessageProcess.debug("Message[{}] processed after [{}] nano.", message.getMessageType(), System.nanoTime() - start);
    }

    @Override
    public void postMessage(MessageChannel sender, Message message) {
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
        Keccak256 encodedMessage = new Keccak256(HashUtil.keccak256(message.getEncoded()));
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

    @Override
    public void start() {
        new Thread(this).start();
    }

    @Override
    public void stop() {
        this.stopped = true;
    }

    @Override
    public long getMessageQueueSize() {
        return this.queue.size();
    }

    @Override
    public void run() {
        while (!stopped) {
            MessageTask task = null;
            try {
                logger.trace("Get task");

                task = this.queue.poll(1, TimeUnit.SECONDS);

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
                logger.error("Unexpected error processing: {}", task, ex);
            }
        }
    }

    private void updateTimedEvents() {
        Long now = System.currentTimeMillis();
        Duration timeTick = Duration.ofMillis(now - lastTickSent);
        // TODO(lsebrie): handle timeouts properly
        lastTickSent = now;
        if (queue.isEmpty()){
            this.syncProcessor.onTimePassed(timeTick);
        }

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
        BlockDifficulty totalDifficulty = blockChainStatus.getTotalDifficulty();

        Status status = new Status(block.getNumber(), block.getHash().getBytes(), block.getParentHash().getBytes(), totalDifficulty);
        logger.trace("Sending status best block to all {} {}", status.getBestBlockNumber(), Hex.toHexString(status.getBestBlockHash()).substring(0, 8));
        this.channelManager.broadcastStatus(status);
    }

    public synchronized Block getBestBlock() {
        return this.blockProcessor.getBlockchain().getBestBlock();
    }

    public synchronized BlockDifficulty getTotalDifficulty() {
        return this.blockProcessor.getBlockchain().getTotalDifficulty();
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

        @Override
        public String toString() {
            return "MessageTask{" +
                    "sender=" + sender +
                    ", message=" + message +
                    '}';
        }
    }
}

