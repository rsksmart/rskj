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
import co.rsk.util.ExecState;
import co.rsk.util.TraceUtils;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Block;
import org.ethereum.crypto.HashUtil;
import org.ethereum.net.server.ChannelManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class NodeMessageHandler implements MessageHandler, InternalService, Runnable {

    private static final Logger logger = LoggerFactory.getLogger("messagehandler");
    private static final Logger loggerMessageProcess = LoggerFactory.getLogger("messageProcess");

    private static final Logger loggerSnapExperiment = LoggerFactory.getLogger("snapExperiment");

    private static final int MAX_NUMBER_OF_MESSAGES_CACHED = 5000;
    private static final long RECEIVED_MESSAGES_CACHE_DURATION = TimeUnit.MINUTES.toMillis(2);
    private static final int QUEUED_TIME_TO_WARN_LIMIT = 2; // seconds
    private static final int QUEUED_TIME_TO_WARN_PERIOD = 10; // seconds
    private static final int PROCESSING_TIME_TO_WARN_LIMIT = 2; // seconds

    private final RskSystemProperties config;
    private final BlockProcessor blockProcessor;
    private final SyncProcessor syncProcessor;
    private final SnapshotProcessor snapshotProcessor;
    private final ChannelManager channelManager;
    private final TransactionGateway transactionGateway;
    private final PeerScoringManager peerScoringManager;

    private final StatusResolver statusResolver;
    private final Set<Keccak256> receivedMessages = Collections.synchronizedSet(new HashSet<>());

    private final Set<RskAddress> bannedMiners;

    private final Thread thread;

    private final PriorityBlockingQueue<MessageTask> queue;

    private final MessageCounter messageCounter = new MessageCounter();
    private final int messageQueueMaxSize;

    private volatile boolean recentIdleTime = false;
    private volatile long lastIdleWarn = System.currentTimeMillis();

    private volatile long lastStatusSent = System.currentTimeMillis();
    private volatile long lastTickSent = System.currentTimeMillis();

    private volatile ExecState state = ExecState.CREATED;

    private long cleanMsgTimestamp;

    /**
     * Creates a new node message handler.
     */
    public NodeMessageHandler(RskSystemProperties config,
            BlockProcessor blockProcessor,
            SyncProcessor syncProcessor,
            SnapshotProcessor snapshotProcessor,
            @Nullable ChannelManager channelManager,
            @Nullable TransactionGateway transactionGateway,
            @Nullable PeerScoringManager peerScoringManager,
            StatusResolver statusResolver) {
        this.config = config;
        this.channelManager = channelManager;
        this.blockProcessor = blockProcessor;
        this.syncProcessor = syncProcessor;
        this.snapshotProcessor = snapshotProcessor;
        this.transactionGateway = transactionGateway;
        this.statusResolver = statusResolver;
        this.cleanMsgTimestamp = System.currentTimeMillis();
        this.peerScoringManager = peerScoringManager;
        this.queue = new PriorityBlockingQueue<>(11, new MessageTask.TaskComparator());
        this.bannedMiners = Collections.unmodifiableSet(
                config.bannedMinerList().stream().map(RskAddress::new).collect(Collectors.toSet())
        );
        this.messageQueueMaxSize = config.getMessageQueueMaxSize();
        this.thread = new Thread(this, "message handler");
    }

    /**
     * processMessage processes a RSK Message, doing the appropriate action based on the message type.
     *
     * @param sender  the message sender.
     * @param message the message to be processed.
     */
    public synchronized void processMessage(final Peer sender, @Nonnull final Message message) {
        messageCounter.decrement(sender);

        MessageType messageType = message.getMessageType();
        logger.trace("Process message type: {}", messageType);

        MessageVisitor mv = new MessageVisitor(config, blockProcessor, syncProcessor,
                                               snapshotProcessor, transactionGateway, peerScoringManager, channelManager, sender);
        message.accept(mv);
    }

    @Override
    public void postMessage(Peer sender, Message message, NodeMsgTraceInfo nodeMsgTraceInfo) {
        logger.trace("Start post message (queue size {}) (message type {})", this.queue.size(), message.getMessageType());
        // There's an obvious race condition here, but fear not.
        // receivedMessages and logger are thread-safe
        // cleanMsgTimestamp is a long replaced by the next value, we don't care
        // enough about the precision of the value it takes
        cleanExpiredMessages();
        tryAddMessage(sender, message, nodeMsgTraceInfo);
        logger.trace("End post message (queue size {})", this.queue.size());
    }

    /**
     * verify if the message is allowed, and if so, add it to the queue
     */
    private void tryAddMessage(Peer sender, Message message, NodeMsgTraceInfo nodeMsgTraceInfo) {
        double score = sender.score(System.currentTimeMillis(), message.getMessageType());

        boolean allowed = controlMessageIngress(sender, message, score);

        if (allowed) {
            this.addMessage(sender, message, score, nodeMsgTraceInfo);
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
            reportEventToPeerScoring(sender, EventType.REPEATED_MESSAGE, "Received repeated message on {}, not added to the queue");
        }

        return !contains;
    }

    @VisibleForTesting
    void addMessage(Peer sender, Message message, double score, NodeMsgTraceInfo nodeMsgTraceInfo) {
        // optimistic increment() to ensure it is called before decrement() on processMessage()
        // there was a race condition on which queue got the new item and decrement() was called before increment() for the same sender
        // also, while queue implementation stays unbounded, offer() will never return false
        messageCounter.increment(sender);
        MessageTask messageTask = new MessageTask(sender, message, score, nodeMsgTraceInfo);
        boolean messageAdded = this.queue.offer(messageTask);
        if (!messageAdded) {
            messageCounter.decrement(sender);
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
    public synchronized void start() {
        if (!state.isCreated()) {
            logger.warn("Cannot start message handler as current state is {}", state);
            return;
        }

        state = ExecState.RUNNING;

        thread.start();
    }

    @Override
    public synchronized void stop() {
        if (!state.isRunning()) {
            logger.warn("Message handler is not running. Ignoring");
            return;
        }

        state = ExecState.FINISHED;

        thread.interrupt();
    }

    @Override
    public long getMessageQueueSize() {
        return this.queue.size();
    }

    @VisibleForTesting
    int getMessageQueueSize(Peer peer) {
        return messageCounter.getValue(peer);
    }

    @Override
    public void run() {
        while (this.state.isRunning()) {
            MessageTask task = null;
            try {
                logger.trace("Get task");

                task = this.queue.poll(1, TimeUnit.SECONDS);

                loggerMessageProcess.debug("Queued Messages: {}", this.queue.size());

                if (task != null) {
                    addTracingKeys(task.getNodeMsgTraceInfo());
                    long startNanos = System.nanoTime();
                    logStart(task);
                    this.processMessage(task.getSender(), task.getMessage());
                    if (task.getMessage().getMessageType() == MessageType.BLOCK_MESSAGE) {
                        BlockMessage message = (BlockMessage) task.getMessage();
                        loggerSnapExperiment.debug("BlockMessage block: [{}] took: [{}]milliseconds", message.getBlock().getNumber(), task.getNodeMsgTraceInfo().getLifeTimeInSeconds()*1000);
                        loggerSnapExperiment.debug("BlockMessage block: [{}] was processed at: [{}]", message.getBlock().getNumber(), System.currentTimeMillis());
                    }
                    logEnd(task, startNanos);
                } else {
                    logger.trace("No task");
                }

                updateTimedEvents();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Got unexpected error while processing task:", e);
            } catch (IllegalAccessError e) { // Usually this is been thrown by DB instances when closed
                logger.warn("Message handler got `{}`. Exiting", e.getClass().getSimpleName(), e);
                return;
            } finally {
                removeTracingKeys();
            }
        }

        logger.trace("Message handler was finished. Exiting");
    }

    private void addTracingKeys(NodeMsgTraceInfo nodeMsgTraceInfo) {
        if (nodeMsgTraceInfo != null) {
            MDC.put(TraceUtils.MSG_ID, nodeMsgTraceInfo.getMessageId());
            MDC.put(TraceUtils.SESSION_ID, nodeMsgTraceInfo.getSessionId());
        }
    }

    private void logStart(MessageTask task) {
        MessageType messageType = task.getMessage().getMessageType();

        if (task.getNodeMsgTraceInfo() == null) {
            logger.trace("Start {} message task", messageType);
            return;
        }

        long taskWaitTimeInSeconds = task.getNodeMsgTraceInfo().getLifeTimeInSeconds();
        logger.trace("Start {} message task after [{}]s", messageType, taskWaitTimeInSeconds);

        if (taskWaitTimeInSeconds >= QUEUED_TIME_TO_WARN_LIMIT) {
            logger.debug("Task {} was waiting too much in the queue: [{}]s", messageType, taskWaitTimeInSeconds);
            recentIdleTime = true;
        }
    }

    @VisibleForTesting
    void logEnd(MessageTask task, long startNanos) {
        Duration processTime = Duration.ofNanos(System.nanoTime() - startNanos);

        Message message = task.getMessage();
        boolean isBlockRelated = message.getMessageType() == MessageType.BLOCK_MESSAGE || message.getMessageType() == MessageType.BODY_RESPONSE_MESSAGE;
        boolean isBlockSlow = isBlockRelated && BlockUtils.tooMuchProcessTime(processTime.toNanos());
        boolean isOtherSlow = !isBlockRelated && processTime.getSeconds() > PROCESSING_TIME_TO_WARN_LIMIT;

        if (isBlockSlow || isOtherSlow) {
            loggerMessageProcess.warn("Message[{}] processing took too much: [{}]s.", message.getMessageType(), processTime.getSeconds());
        }

        logger.trace("End {} message task after [{}]s", task.getMessage().getMessageType(), processTime.getSeconds());
    }

    private void removeTracingKeys() {
        MDC.remove(TraceUtils.MSG_ID);
        MDC.remove(TraceUtils.SESSION_ID);
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

        Duration timeIdleWarn = Duration.ofMillis(now - lastIdleWarn);
        boolean isTimeToWarnIdle = recentIdleTime && timeIdleWarn.getSeconds() > QUEUED_TIME_TO_WARN_PERIOD;
        if (isTimeToWarnIdle) {
            logger.warn("Tasks are waiting too much in the queue: > [{}]s", QUEUED_TIME_TO_WARN_LIMIT);
            recentIdleTime = false;
            lastIdleWarn = now;
        }
    }

    @SuppressWarnings("SameParameterValue")
    private void reportEventToPeerScoring(Peer sender, EventType event, String message) {
        if (sender == null) {
            return;
        }

        this.peerScoringManager.recordEvent(sender.getPeerNodeID(), sender.getAddress(), event, message, this.getClass());
    }

    @VisibleForTesting
    static class MessageTask {
        private final Peer sender;
        private final Message message;
        private final double score;
        private final NodeMsgTraceInfo nodeMsgTraceInfo;

        public MessageTask(Peer sender, Message message, double score, NodeMsgTraceInfo nodeMsgTraceInfo) {
            this.sender = sender;
            this.message = message;
            this.score = score;
            this.nodeMsgTraceInfo = nodeMsgTraceInfo;
        }

        public Peer getSender() {
            return this.sender;
        }

        public Message getMessage() {
            return this.message;
        }

        public NodeMsgTraceInfo getNodeMsgTraceInfo() {
            return nodeMsgTraceInfo;
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
