/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package org.ethereum.net;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import co.rsk.panic.PanicProcessor;
import org.ethereum.net.eth.message.EthMessage;
import org.ethereum.net.message.Message;
import org.ethereum.net.message.ReasonCode;
import org.ethereum.net.p2p.DisconnectMessage;
import org.ethereum.net.p2p.PingMessage;
import org.ethereum.net.server.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.ethereum.net.message.StaticMessages.DISCONNECT_MESSAGE;

/**
 * This class contains the logic for sending messages in a queue
 *
 * Messages open by send and answered by receive of appropriate message
 *      PING by PONG
 *      GET_PEERS by PEERS
 *      GET_TRANSACTIONS by TRANSACTIONS
 *      GET_BLOCK_HASHES by BLOCK_HASHES
 *      GET_BLOCKS by BLOCKS
 *
 * The following messages will not be answered:
 *      PONG, PEERS, HELLO, STATUS, TRANSACTIONS, BLOCKS
 *
 * @author Roman Mandeleil
 */
public class MessageQueue {

    private static final Logger logger = LoggerFactory.getLogger("net");
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    private static final ScheduledExecutorService timer = Executors.newScheduledThreadPool(4, new ThreadFactory() {
        private AtomicInteger cnt = new AtomicInteger(0);

        public Thread newThread(Runnable r) {
            return new Thread(r, "MessageQueueTimer-" + cnt.getAndIncrement());
        }
    });

    private Queue<MessageRoundtrip> requestQueue = new LinkedBlockingQueue<>();
    private Queue<MessageRoundtrip> respondQueue = new LinkedBlockingQueue<>();
    private ChannelHandlerContext ctx = null;

    boolean hasPing = false;
    private ScheduledFuture<?> timerTask;
    private Channel channel;

    public MessageQueue() {
    }

    public void activate(ChannelHandlerContext ctx) {
        this.ctx = ctx;
        timerTask = timer.scheduleAtFixedRate(new Runnable() {
            public void run() {
                try {
                    nudgeQueue();
                } catch (Exception e) {
                    logger.error("Unhandled exception", e);
                    panicProcessor.panic("messagequeue", String.format("Unhandled exception %s", e.toString()));
                }
            }
        }, 10, 10, TimeUnit.MILLISECONDS);
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public void sendMessage(Message msg) {
        if (msg instanceof PingMessage) {
            if (hasPing) {
                return;
            }
            logger.trace("Sending Ping Message to {}", channel);
            hasPing = true;
        }

        Queue<MessageRoundtrip> queue = msg.getAnswerMessage() != null ? requestQueue : respondQueue;
        queue.add(new MessageRoundtrip(msg));
    }

    public void disconnect() {
        disconnect(DISCONNECT_MESSAGE);
    }

    public void disconnect(ReasonCode reason) {
        disconnect(new DisconnectMessage(reason));
    }

    private void disconnect(DisconnectMessage msg) {
        ctx.writeAndFlush(msg);
        ctx.close();
    }

    public void receivedMessage(Message msg) throws InterruptedException {

        MessageRoundtrip messageRoundtrip = requestQueue.peek();
        if (messageRoundtrip != null) {
            Message waitingMessage = messageRoundtrip.getMsg();

            if (waitingMessage instanceof PingMessage) {
                hasPing = false;
            }

            if (waitingMessage.getAnswerMessage() != null
                    && msg.getClass() == waitingMessage.getAnswerMessage()) {
                messageRoundtrip.answer();
                if (waitingMessage instanceof EthMessage)
                    channel.getPeerStats().pong(messageRoundtrip.lastTimestamp);
                logger.trace("Message round trip covered: [{}] ",
                        messageRoundtrip.getMsg().getClass());
            }
        }
    }

    private void removeAnsweredMessage(MessageRoundtrip messageRoundtrip) {
        if (messageRoundtrip != null && messageRoundtrip.isAnswered())
            requestQueue.remove();
    }

    private void nudgeQueue() {
        // remove last answered message on the queue
        removeAnsweredMessage(requestQueue.peek());
        // Now send the next message
        sendToWire(respondQueue.poll());
        sendToWire(requestQueue.peek());
    }

    private void sendToWire(MessageRoundtrip messageRoundtrip) {

        if (messageRoundtrip != null && messageRoundtrip.getRetryTimes() == 0) {
            // TODO: retry logic. See messageRoundtrip.hasToRetry

            Message msg = messageRoundtrip.getMsg();

            ctx.writeAndFlush(msg).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);

            if (msg.getAnswerMessage() != null) {
                messageRoundtrip.incRetryTimes();
                messageRoundtrip.saveTime();
            }
        }
    }

    public void close() {
        if (timerTask != null) {
            timerTask.cancel(false);
        }
    }
}
