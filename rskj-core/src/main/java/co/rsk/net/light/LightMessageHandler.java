package co.rsk.net.light;

import co.rsk.config.InternalService;
import co.rsk.net.eth.LightClientHandler;
import co.rsk.net.light.message.LightClientMessage;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

public class LightMessageHandler implements InternalService, Runnable {
    private static final Logger logger = LoggerFactory.getLogger("lightmessagehandler");
    private static final Logger loggerMessageProcess = LoggerFactory.getLogger("lightmessageProcess");

    private final LightProcessor lightProcessor;
    private final LightSyncProcessor lightSyncProcessor;

    private final PriorityBlockingQueue<LightMessageHandler.MessageTask> queue;

    private boolean stopped = true;

    public LightMessageHandler(LightProcessor lightProcessor, LightSyncProcessor lightSyncProcessor) {
        this.lightProcessor = lightProcessor;
        this.lightSyncProcessor = lightSyncProcessor;
        this.queue = new PriorityBlockingQueue<>(11);
    }

    public void processMessage(LightPeer lightPeer, LightClientMessage message,
                               ChannelHandlerContext ctx, LightClientHandler lightClientHandler) {
        LightClientMessageVisitor visitor = new LightClientMessageVisitor(lightPeer, lightProcessor, lightSyncProcessor, ctx, lightClientHandler);
        message.accept(visitor);
    }

    public void postMessage(LightPeer sender, LightClientMessage message, ChannelHandlerContext ctx,
                            LightClientHandler lightClientHandler) {
        logger.trace("Start post message (queue size {}) (message type {})", this.queue.size(), message);

        if (!this.queue.offer(new LightMessageHandler.MessageTask(sender, message, ctx, lightClientHandler))) {
            logger.warn("Unexpected path. Is message queue bounded now?");
        }
        logger.trace("End post message (queue size {})", this.queue.size());
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
