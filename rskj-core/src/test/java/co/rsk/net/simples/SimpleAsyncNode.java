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

package co.rsk.net.simples;

import co.rsk.net.MessageHandler;
import co.rsk.net.SyncProcessor;
import co.rsk.net.messages.Message;
import co.rsk.net.messages.MessageType;
import com.google.common.annotations.VisibleForTesting;

import java.util.Map;
import java.util.concurrent.*;

/**
 * Created by ajlopez on 5/15/2016.
 */
public class SimpleAsyncNode extends SimpleNode {
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private LinkedBlockingQueue<Future> futures = new LinkedBlockingQueue<>(1000);
    private SyncProcessor syncProcessor;

    public SimpleAsyncNode(MessageHandler handler) {
        super(handler);
    }

    public SimpleAsyncNode(MessageHandler handler, SyncProcessor syncProcessor) {
        super(handler);
        this.syncProcessor = syncProcessor;
    }

    @Override
    public void receiveMessageFrom(SimpleNode peer, Message message) {
        SimpleNodeChannel senderToPeer = new SimpleNodeChannel(this, peer);
        futures.add(
                executor.submit(() -> this.getHandler().processMessage(senderToPeer, message)));
    }

    public void joinWithTimeout() {
        try {
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            throw new RuntimeException("Join operation timed out. Remaining tasks: " + this.futures.size() + " .");
        }
    }

    public void waitUntilNTasksWithTimeout(int number) {
        try {
            for (int i = 0; i < number; i++) {
                Future task = this.futures.poll(10, TimeUnit.SECONDS);
                if (task == null) {
                    throw new RuntimeException("Exceeded waiting time. Expected " + (number - i) + " more tasks.");
                }
                task.get();
            }
        } catch (InterruptedException | ExecutionException ignored) {
        }
    }

    public void waitExactlyNTasksWithTimeout(int number) {
        waitUntilNTasksWithTimeout(number);
        int remaining = this.futures.size();
        if (remaining > 0)
            throw new RuntimeException("Too many tasks. Expected " + number + " but got " + (number + remaining));
    }

    @VisibleForTesting
    public Map<Long, MessageType> getExpectedResponses() {
        return this.syncProcessor.getPeerStatus(this.getNodeID()).getExpectedResponses();
    }

    @VisibleForTesting
    public SyncProcessor getSyncProcessor() {
        return this.syncProcessor;
    }
}
