package co.rsk.net.light;

import co.rsk.config.InternalService;
import co.rsk.crypto.Keccak256;
import co.rsk.net.eth.LightClientHandler;
import co.rsk.net.light.message.LightClientMessage;
import io.netty.channel.ChannelHandlerContext;
import org.ethereum.crypto.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

public class LightMessageHandler implements InternalService, Runnable {
    private static final Logger logger = LoggerFactory.getLogger("lightmessagehandler");
    private static final Logger loggerMessageProcess = LoggerFactory.getLogger("lightmessageProcess");

    private final LightProcessor lightProcessor;
    private final LightSyncProcessor lightSyncProcessor;

    private PriorityBlockingQueue<LightMessageHandler.MessageTask> queue;
    private Set<Keccak256> receivedMessages = Collections.synchronizedSet(new HashSet<>());

    private boolean stopped = true;

    public static final int MAX_NUMBER_OF_MESSAGES_CACHED = 5000;

    public LightMessageHandler(LightProcessor lightProcessor, LightSyncProcessor lightSyncProcessor) {
        this.lightProcessor = lightProcessor;
        this.lightSyncProcessor = lightSyncProcessor;
    }

    public void processMessage(LightPeer lightPeer, LightClientMessage message,
                               ChannelHandlerContext ctx, LightClientHandler lightClientHandler) {
        LightClientMessageVisitor visitor = new LightClientMessageVisitor(lightPeer, lightProcessor, lightSyncProcessor, ctx, lightClientHandler);
        message.accept(visitor);
    }

    public void postMessage(LightPeer sender, LightClientMessage message, ChannelHandlerContext ctx,
                            LightClientHandler lightClientHandler) throws InterruptedException {
        logger.trace("Start post message (queue size {}) (message type {})", this.queue.size(), message);

        //cleanExpiredMessages();
        tryAddMessage(sender, message, ctx, lightClientHandler);
        logger.trace("End post message (queue size {})", this.queue.size());
    }

    private void tryAddMessage(LightPeer sender, LightClientMessage message,
                               ChannelHandlerContext ctx, LightClientHandler lightClientHandler) {
        Keccak256 encodedMessage = new Keccak256(HashUtil.keccak256(message.getEncoded()));
        if (!receivedMessages.contains(encodedMessage)) {
            //if (message.getMessageType() == MessageType.BLOCK_MESSAGE || message.getMessageType() == MessageType.TRANSACTIONS) {
                if (this.receivedMessages.size() >= MAX_NUMBER_OF_MESSAGES_CACHED) {
                    this.receivedMessages.clear();
                }
                this.receivedMessages.add(encodedMessage);
            //}

            if (!this.queue.offer(new LightMessageHandler.MessageTask(sender, message, ctx, lightClientHandler))) {
                logger.warn("Unexpected path. Is message queue bounded now?");
            }
        } else {
            logger.trace("Received message already known, not added to the queue");
        }
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
            LightMessageHandler.MessageTask task = null;
            try {
                logger.trace("Get task");

                task = this.queue.poll(1, TimeUnit.SECONDS);

                loggerMessageProcess.debug("Queued Messages: {}", this.queue.size());

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
            return ctx;
        }

        public LightClientHandler getLightClientHandler() {
            return lightClientHandler;
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
