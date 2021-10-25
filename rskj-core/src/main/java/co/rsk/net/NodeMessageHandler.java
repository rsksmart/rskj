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

import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.ethereum.crypto.HashUtil;
import org.ethereum.net.server.ChannelManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.rsk.config.InternalService;
import co.rsk.config.RskSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.BlockUtils;
import co.rsk.crypto.Keccak256;
import co.rsk.net.messages.BlockMessage;
import co.rsk.net.messages.Message;
import co.rsk.net.messages.MessageType;
import co.rsk.net.messages.MessageVisitor;
import co.rsk.scoring.EventType;
import co.rsk.scoring.PeerScoringManager;
import co.rsk.util.FormatUtils;

public class NodeMessageHandler implements MessageHandler, InternalService, Runnable {

    private static final Logger logger = LoggerFactory.getLogger("messagehandler");
    private static final Logger loggerMessageProcess = LoggerFactory.getLogger("messageProcess");

    private static final int MAX_NUMBER_OF_MESSAGES_CACHED = 5000;
    private static final long RECEIVED_MESSAGES_CACHE_DURATION = TimeUnit.MINUTES.toMillis(2);

    private final RskSystemProperties config;
    private final BlockProcessor blockProcessor;
    private final SyncProcessor syncProcessor;
    private final ChannelManager channelManager;
    private final TransactionGateway transactionGateway;
    private final PeerScoringManager peerScoringManager;

    private volatile long lastStatusSent = System.currentTimeMillis();
    private volatile long lastTickSent = System.currentTimeMillis();

    private final StatusResolver statusResolver;
    private Set<Keccak256> receivedMessages = Collections.synchronizedSet(new HashSet<>());
    private long cleanMsgTimestamp = 0;

    private final Set<RskAddress> bannedMiners;
    
    private PriorityBlockingQueue<MessageTask> queue;

    private volatile boolean stopped;

    private MessageCounter messageCounter = new MessageCounter();
    private final int messageQueueMaxSize;

    /**
     * Creates a new node message handler.
     */
    public NodeMessageHandler(RskSystemProperties config,
            BlockProcessor blockProcessor,
            SyncProcessor syncProcessor,
            @Nullable ChannelManager channelManager,
            @Nullable TransactionGateway transactionGateway,
            @Nullable PeerScoringManager peerScoringManager,
            StatusResolver statusResolver) {
        this.config = config;
        this.channelManager = channelManager;
        this.blockProcessor = blockProcessor;
        this.syncProcessor = syncProcessor;
        this.transactionGateway = transactionGateway;
        this.statusResolver = statusResolver;
        this.cleanMsgTimestamp = System.currentTimeMillis();
        this.peerScoringManager = peerScoringManager;
        this.queue = new PriorityBlockingQueue<>(11, new MessageTask.TaskComparator());
        this.bannedMiners = Collections.unmodifiableSet(
                config.bannedMinerList().stream().map(RskAddress::new).collect(Collectors.toSet())
        );
        this.messageQueueMaxSize = config.getMessageQueueMaxSize();
    }

    /**
     * processMessage processes a RSK Message, doing the appropriate action based on the message type.
     *
     * @param sender  the message sender.
     * @param message the message to be processed.
     */
    public synchronized void processMessage(final Peer sender, @Nonnull final Message message) {
        long start = System.nanoTime();
        MessageType messageType = message.getMessageType();
        logger.trace("Process message type: {}", messageType);

        MessageVisitor mv = new MessageVisitor(config, blockProcessor, syncProcessor, transactionGateway, peerScoringManager, channelManager, sender);
        message.accept(mv);

        long processTime = System.nanoTime() - start;
        String timeInSeconds = FormatUtils.formatNanosecondsToSeconds(processTime);

        if ((messageType == MessageType.BLOCK_MESSAGE || messageType == MessageType.BODY_RESPONSE_MESSAGE) && BlockUtils.tooMuchProcessTime(processTime)) {
            loggerMessageProcess.warn("Message[{}] processed after [{}] seconds.", message.getMessageType(), timeInSeconds);
        } else {
            loggerMessageProcess.debug("Message[{}] processed after [{}] seconds.", message.getMessageType(), timeInSeconds);
        }

        messageCounter.decrement(sender);
    }

    @Override
    public void postMessage(Peer sender, Message message) {
        logger.trace("Start post message (queue size {}) (message type {})", this.queue.size(), message.getMessageType());
        // There's an obvious race condition here, but fear not.
        // receivedMessages and logger are thread-safe
        // cleanMsgTimestamp is a long replaced by the next value, we don't care
        // enough about the precision of the value it takes
        cleanExpiredMessages();
        tryAddMessage(sender, message);
        logger.trace("End post message (queue size {})", this.queue.size());
    }

    /**
     * verify if the message is allowed, and if so, add it to the queue 
     */
    private void tryAddMessage(Peer sender, Message message) {

        double score = sender.score(System.currentTimeMillis(), message.getMessageType());

        boolean allowed = controlMessageIngress(sender, message, score);

        if (allowed) {
            this.addMessage(sender, message, score);
        }

    }

    /**
     * Responds if a message must be allowed 
     */
    private boolean controlMessageIngress(Peer sender, Message message, double score) {

        return 
                allowByScore(score) && 
                allowByMessageCount(sender) && 
                allowByMinerNotBanned(sender, message) &&
                allowByMessageUniqueness(sender, message); // prevent repeated is the most expensive and MUST be the last 

    }

    /**
     * assert score is acceptable 
     */
    private boolean allowByScore(double score) {
        return score >= 0;
    }
    
    /**
     * assert message count is under the threshold defined in config
     */
    private boolean allowByMessageCount(Peer sender) {
        boolean allow = messageCounter.getValue(sender) < messageQueueMaxSize;
        if (!allow && logger.isInfoEnabled()) {
            logger.info("Peer [{}] has its queue full(maxSize: {}). Its messages will not be allowed for a while.", sender.getPeerNodeID(), messageQueueMaxSize);
        }
        return allow;
    }

    private boolean allowByMinerNotBanned(Peer sender, Message message) {

        boolean allow = true;
        
        if (!this.bannedMiners.isEmpty() && message.getMessageType() == MessageType.BLOCK_MESSAGE) {
            RskAddress miner = ((BlockMessage) message).getBlock().getCoinbase();
            if (this.bannedMiners.contains(miner)) {
                logger.trace("Received block mined by banned miner {} from peer {}, not added to the queue", miner, sender);
                allow = false;
            }
        }

        return allow;
    }
    
    /**
     * assert message was not received twice
     * add it to a map and manages the state of the map
     * record event if message is repeated 
     */
    private boolean allowByMessageUniqueness(Peer sender, Message message) {

        Keccak256 encodedMessage = new Keccak256(HashUtil.keccak256(message.getEncoded()));

        boolean contains = receivedMessages.contains(encodedMessage);

        if (!contains) {
            if (message.getMessageType() == MessageType.BLOCK_MESSAGE || message.getMessageType() == MessageType.TRANSACTIONS) {
                if (this.receivedMessages.size() >= MAX_NUMBER_OF_MESSAGES_CACHED) {
                    this.receivedMessages.clear();
                }
                this.receivedMessages.add(encodedMessage);
            }

        } else {
            recordEvent(sender, EventType.REPEATED_MESSAGE);
            logger.trace("Received message already known, not added to the queue");
        }

        return !contains;
    }

    private void addMessage(Peer sender, Message message, double score) {

        boolean messageAdded = this.queue.offer(new MessageTask(sender, message, score));

        if (messageAdded) {
            messageCounter.increment(sender);
        } else {
            logger.warn("Unexpected path. Is message queue bounded now?");
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
        new Thread(this, "message handler").start();
    }

    @Override
    public void stop() {
        this.stopped = true;
    }

    @Override
    public long getMessageQueueSize() {
        return this.queue.size();
    }

    public int getMessageQueueSize(Peer peer) {
        return messageCounter.getValue(peer);
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
            } catch (InterruptedException iex) {
                logger.error("Interrupted exception processing: {}", task, iex);
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                logger.error("Unexpected error processing: {}", task, ex);
            }
        }
    }

    private void updateTimedEvents() {
        long now = System.currentTimeMillis();
        Duration timeTick = Duration.ofMillis(now - lastTickSent);
        // TODO(lsebrie): handle timeouts properly
        lastTickSent = now;
        if (queue.isEmpty()) {
            this.syncProcessor.onTimePassed(timeTick);
        }

        //Refresh status to peers every 10 seconds or so
        Duration timeStatus = Duration.ofMillis(now - lastStatusSent);
        if (timeStatus.getSeconds() > 10) {
            Status status = statusResolver.currentStatus();
            logger.trace("Sending status best block to all {} {}", status.getBestBlockNumber(), status.getBestBlockHash());
            channelManager.broadcastStatus(status);
            lastStatusSent = now;
        }
    }

    private void recordEvent(Peer sender, EventType event) {
        if (sender == null) {
            return;
        }

        this.peerScoringManager.recordEvent(sender.getPeerNodeID(), sender.getAddress(), event);
    }

    private static class MessageTask {
        private Peer sender;
        private Message message;
        private double score;

        public MessageTask(Peer sender, Message message, double score) {
            this.sender = sender;
            this.message = message;
            this.score = score;
        }

        public Peer getSender() {
            return this.sender;
        }

        public Message getMessage() {
            return this.message;
        }

        @Override
        public String toString() {
            return "MessageTask{" + "sender=" + sender + ", message=" + message + '}';
        }

        private static class TaskComparator implements Comparator<MessageTask> {
            @Override
            public int compare(MessageTask m1, MessageTask m2) {
                return Double.compare(m2.score, m1.score);
            }
        }

    }

}