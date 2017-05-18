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
import co.rsk.net.MessageSender;
import co.rsk.net.messages.Message;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Created by ajlopez on 5/15/2016.
 */
public class SimpleAsyncNode extends SimpleNode implements Runnable {
    private BlockingQueue<MessageTask> messages = new ArrayBlockingQueue<MessageTask>(1000);
    private volatile boolean stopped = false;

    public SimpleAsyncNode(MessageHandler handler) {
        super(handler);
        (new Thread(this)).start();
    }

    @Override
    public void processMessage(MessageSender sender, Message message) {
        if (this.stopped)
            return;

        this.messages.add(new MessageTask(sender, message));
    }

    public void stop() {
        this.stopped = true;
    }

    public void run() {
        while (!this.stopped || !this.messages.isEmpty()) {
            MessageTask task = null;
            try {
                task = this.messages.take();
            } catch (InterruptedException e) {
                e.printStackTrace();
                return;
            }
            task.execute(this.getHandler());
        }
    }

    private class MessageTask {
        private MessageSender sender;
        private Message message;

        public MessageTask(MessageSender sender, Message message) {
            this.sender = sender;
            this.message = message;
        }

        public void execute(MessageHandler handler) {
            handler.processMessage(sender, message);
        }
    }
}
