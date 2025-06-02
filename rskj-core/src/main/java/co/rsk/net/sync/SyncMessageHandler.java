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

import co.rsk.metrics.profilers.Metric;
import co.rsk.metrics.profilers.MetricKind;
import co.rsk.metrics.profilers.Profiler;
import co.rsk.metrics.profilers.ProfilerFactory;
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

    private static final Profiler profiler = ProfilerFactory.getInstance();

    public static final String QUEUE_NAME = "queue";

    private final String name;

    private final BlockingQueue<Job> jobQueue;

    private final Listener listener;

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
        while (isRunning()) {
            Metric metric = null;
            try {
                job = jobQueue.take();

                metric = profiler.start(job.getMetricKind());

                MDC.put(QUEUE_NAME, name);
                processJob(job);
            } catch (InterruptedException e) {
                handleInterruptedException(e);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                handleException(job, e);
            } finally {
                MDC.remove(QUEUE_NAME);

                if (metric != null) {
                    profiler.stop(metric);
                }
            }
        }

        if (listener != null) {
            listener.onComplete();
        }

        logger.debug("Finished processing queue of messages for: [{}]", name);
    }

    public void processJob(Job job) {
        Instant jobStart = Instant.MIN;

        if (logger.isDebugEnabled()) {
            logger.debug("Processing msg: [{}] from: [{}] for: [{}]. Pending count: [{}]", job.getMsgType(), job.getSender(), name, jobQueue.size());
            jobStart = Instant.now();
        }

        job.run();

        if (logger.isDebugEnabled()) {
            logger.debug("Finished processing of msg: [{}] from: [{}] for: [{}] after [{}] seconds",
                    job.getMsgType(), job.getSender(), name,
                    FormatUtils.formatNanosecondsToSeconds(Duration.between(jobStart, Instant.now()).toNanos()));
        }

        handleListenerOnProcessJob(job);
    }

    private void handleException(Job job, Exception e) {
        logger.error("Unexpected error processing msg: '[{}]' for: [{}]", job, name, e);
        if (listener != null) {
            listener.onException(e);
        }
    }

    private void handleInterruptedException(InterruptedException e) {
        logger.warn("Queue processing was interrupted for: [{}]", name, e);
        if (listener != null) {
            listener.onInterrupted();
        }
    }

    private void handleListenerOnProcessJob(Job job) {
        if (listener != null) {
            listener.onJobRun(job);
            if (jobQueue.isEmpty()) {
                listener.onQueueEmpty();
            }
        }
    }

    public interface Listener {
        void onStart();

        void onJobRun(Job job);

        void onQueueEmpty();

        void onInterrupted();

        void onException(Exception e);

        void onComplete();
    }

    public abstract static class Job implements Runnable {
        private final Peer sender;
        private final MessageType msgType;
        private final MetricKind metricKind;

        public Job(Peer sender, Message msg, MetricKind metricKind) {
            this.sender = sender;
            this.msgType = msg.getMessageType();
            this.metricKind = metricKind;
        }

        public Peer getSender() {
            return sender;
        }

        public MessageType getMsgType() {
            return msgType;
        }

        public MetricKind getMetricKind() {
            return metricKind;
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
