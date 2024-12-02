/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
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

package co.rsk.net.sync;

import co.rsk.net.Peer;
import co.rsk.net.messages.Message;
import co.rsk.net.messages.MessageType;
import co.rsk.util.FormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;

public abstract class SyncMessageHandler implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger("syncmessagehandler");

    public static final String QUEUE_NAME = "queue";

    private final String name;

    private final BlockingQueue<Job> jobQueue;

    private final Listener listener;

    protected SyncMessageHandler(String name, BlockingQueue<Job> jobQueue) {
        this(name, jobQueue, null);
    }

    protected SyncMessageHandler(String name, BlockingQueue<Job> jobQueue, Listener listener) {
        this.name = name;
        this.jobQueue = jobQueue;
        this.listener = listener;
    }

    public abstract boolean isRunning();

    @Override
    public void run() {
        logger.debug("Starting processing queue of messages for: [{}]", name);

        if (listener != null) {
            listener.onStart();
        }

        Job job = null;
        Instant jobStart = Instant.MIN;
        while (isRunning()) {
            try {
                job = jobQueue.take();

                MDC.put(QUEUE_NAME, name);

                if (logger.isDebugEnabled()) {
                    logger.debug("Processing msg: [{}] from: [{}] for: [{}]. Pending count: [{}]", job.getMsgType(), job.getSender(), name, jobQueue.size());
                    jobStart = Instant.now();
                }

                job.run();

                if (logger.isDebugEnabled()) {
                    logger.debug("Finished processing of msg: [{}] from: [{}] for: [{}] after [{}] seconds.",
                            job.getMsgType(), job.getSender(), name,
                            FormatUtils.formatNanosecondsToSeconds(Duration.between(jobStart, Instant.now()).toNanos()));
                }

                if (listener != null) {
                    listener.onJobRun(job);
                    if (jobQueue.isEmpty()) {
                        listener.onQueueEmpty();
                    }
                }
            } catch (InterruptedException e) {
                logger.warn("Queue processing was interrupted for: [{}]", name, e);
                if (listener != null) {
                    listener.onInterrupted();
                }
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Unexpected error processing msg: '[{}]' for: [{}]", job, name, e);
                if (listener != null) {
                    listener.onException(e);
                }
            } finally {
                MDC.remove(QUEUE_NAME);
            }
        }

        if (listener != null) {
            listener.onComplete();
        }

        logger.debug("Finished processing queue of messages for: [{}]", name);
    }

    public interface Listener {
        void onStart();
        void onJobRun(Job job);
        void onQueueEmpty();
        void onInterrupted();
        void onException(Exception e);
        void onComplete();
    }

    public static abstract class Job implements Runnable {
        private final Peer sender;
        private final MessageType msgType;

        public Job(Peer sender, Message msg) {
            this.sender = sender;
            this.msgType = msg.getMessageType();
        }

        public Job(Peer sender, MessageType msgType) {
            this.sender = sender;
            this.msgType = msgType;
        }

        public Peer getSender() {
            return sender;
        }

        public MessageType getMsgType() {
            return msgType;
        }

        @Override
        public String toString() {
            return "SyncMessageHandler{" +
                    "sender=" + sender +
                    ", msgType=" + msgType +
                    '}';
        }
    }
}
