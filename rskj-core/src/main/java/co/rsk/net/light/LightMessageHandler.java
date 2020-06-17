/*
 * This file is part of RskJ
 * Copyright (C) 2020 RSK Labs Ltd.
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

package co.rsk.net.light;

import co.rsk.config.InternalService;
import co.rsk.net.eth.LightClientHandler;
import co.rsk.net.light.message.LightClientMessage;
import com.google.common.annotations.VisibleForTesting;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public class LightMessageHandler implements InternalService, Runnable {
    private static final Logger logger = LoggerFactory.getLogger("lightmessagehandler");
    private static final Logger loggerMessageProcess = LoggerFactory.getLogger("lightmessageProcess");

    private final LightProcessor lightProcessor;
    private final LightSyncProcessor lightSyncProcessor;

    private final ArrayBlockingQueue<MessageTask> queue;

    private volatile boolean stopped;

    public LightMessageHandler(LightProcessor lightProcessor, LightSyncProcessor lightSyncProcessor) {
        this.lightProcessor = lightProcessor;
        this.lightSyncProcessor = lightSyncProcessor;
        this.queue = new ArrayBlockingQueue<>(11);
    }

    public void processMessage(LightPeer lightPeer, LightClientMessage message,
                               ChannelHandlerContext ctx, LightClientHandler lightClientHandler) {
        LightClientMessageVisitor visitor = new LightClientMessageVisitor(lightPeer, lightProcessor, lightSyncProcessor, ctx, lightClientHandler);
        message.accept(visitor);
    }

    public void postMessage(LightPeer lightPeer, LightClientMessage message, ChannelHandlerContext ctx,
                            LightClientHandler lightClientHandler) {
        logger.trace("Start post message (queue size {}) (message type {})", queue.size(), message);

        if (!queue.offer(new LightMessageHandler.MessageTask(lightPeer, message, ctx, lightClientHandler))) {
            logger.warn("Unexpected path. Is message queue bounded now?");
        }
        logger.trace("End post message (queue size {})", queue.size());
    }

    public long getMessageQueueSize() {
        return queue.size();
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
    public void run() {
        while (!stopped) {
            handleMessage();
        }
    }

    @VisibleForTesting
    public void handleMessage() {
        MessageTask task = null;
        try {
            logger.trace("Get task");

            task = queue.poll(1, TimeUnit.SECONDS);

            loggerMessageProcess.debug("Queued Messages: {}", queue.size());

            if (task != null) {
                logger.trace("Start task");
                this.processMessage(task.getSender(), task.getMessage(),
                        task.getCtx(), task.getLightClientHandler());
                logger.trace("End task");
            } else {
                logger.trace("No task");
            }

            // THIS SHOULD BE IMPLEMENTED? RELATED TO SYNC
            //updateTimedEvents();
        }
        catch (Exception ex) {
            logger.error("Unexpected error processing: {}", task, ex);
        }
    }

    private static class MessageTask  {
        private final LightPeer sender;
        private final LightClientMessage message;
        private final ChannelHandlerContext ctx;
        private final LightClientHandler lightClientHandler;

        public MessageTask(LightPeer sender, LightClientMessage message,
                           ChannelHandlerContext ctx, LightClientHandler lightClientHandler) {
            this.sender = sender;
            this.message = message;
            this.ctx = ctx;
            this.lightClientHandler = lightClientHandler;
        }

        public LightPeer getSender() {
            return this.sender;
        }

        public LightClientMessage getMessage() {
            return this.message;
        }

        public ChannelHandlerContext getCtx() {
            return this.ctx;
        }

        public LightClientHandler getLightClientHandler() {
            return this.lightClientHandler;
        }

        @Override
        public String toString() {
            return "MessageTask{" +
                    "sender=" + sender +
                    ", message=" + message +
                    ", ctx=" + ctx +
                    ", lightClientHandler=" + lightClientHandler +
                    '}';
        }
    }
}
