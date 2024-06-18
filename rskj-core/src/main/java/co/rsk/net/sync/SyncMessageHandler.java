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
import co.rsk.util.FormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;

public abstract class SyncMessageHandler implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger("syncprocessor");

    private final String name;

    private final BlockingQueue<Job> jobQueue;

    protected SyncMessageHandler(String name, BlockingQueue<Job> jobQueue) {
        this.name = name;
        this.jobQueue = jobQueue;
    }

    public abstract boolean isRunning();

    @Override
    public void run() {
        logger.debug("Starting processing queue of messages for: [{}]", name);

        Job job = null;
        Instant jobStart = Instant.MIN;
        while (isRunning()) {
            try {
                job = jobQueue.take();

                if (logger.isDebugEnabled()) {
                    logger.debug("Processing msg: [{}] from: [{}] for: [{}]", job.getMsg().getMessageType(), job.getSender(), name);
                    jobStart = Instant.now();
                }

                job.run();

                if (logger.isDebugEnabled()) {
                    logger.debug("Finished processing of msg: [{}] from: [{}] for: [{}] after [{}] seconds.",
                            job.getMsg().getMessageType(), job.getSender(), name,
                            FormatUtils.formatNanosecondsToSeconds(Duration.between(jobStart, Instant.now()).toNanos()));
                }
            } catch (InterruptedException e) {
                logger.warn("Queue processing was interrupted for: [{}]", name, e);
            } catch (Exception e) {
                logger.error("Unexpected error processing msg: '[{}]' for: [{}]", job, name, e);
            }
        }

        logger.debug("Finished processing queue of messages for: [{}]", name);
    }

    public static abstract class Job implements Runnable {
        private final Peer sender;

        private final Message msg;

        public Job(Peer sender, Message msg) {
            this.sender = sender;
            this.msg = msg;
        }

        public Peer getSender() {
            return sender;
        }

        public Message getMsg() {
            return msg;
        }

        @Override
        public String toString() {
            return "SyncMessageHandler{" +
                    "sender=" + sender +
                    ", msgType=" + msg.getMessageType() +
                    '}';
        }
    }
}
