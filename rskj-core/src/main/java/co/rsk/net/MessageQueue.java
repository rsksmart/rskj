package co.rsk.net;

import co.rsk.net.messages.MessageType;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class MessageQueue {

    private static final int NEW_BLOCK_HASHES_MAX_CAPACITY = 10;
    private TaskQueue newBlockHashesPerPeer;
    private TaskQueue nonPriorityQueue;
    private TaskQueue priorityQueue;
    private Set<MessageType> priorityMessages;

    private int size;
    private final ReentrantLock lock;
    private final Condition empty;

    public MessageQueue() {
        size = 0;
        priorityMessages = new HashSet<>();
        priorityMessages.add(MessageType.BLOCK_HEADERS_RESPONSE_MESSAGE);
        priorityMessages.add(MessageType.BLOCK_RESPONSE_MESSAGE);
        priorityMessages.add(MessageType.SKELETON_RESPONSE_MESSAGE);
        priorityMessages.add(MessageType.BLOCK_HASH_RESPONSE_MESSAGE);
        priorityMessages.add(MessageType.STATUS_MESSAGE);
        priorityMessages.add(MessageType.BODY_RESPONSE_MESSAGE);

        newBlockHashesPerPeer = new PeerBoundedTaskQueue(NEW_BLOCK_HASHES_MAX_CAPACITY);
        priorityQueue = new UnboundedTaskQueue();
        nonPriorityQueue = new UnboundedTaskQueue();
        lock = new ReentrantLock();
        empty = lock.newCondition();
    }

    public int size() {
        return size;
    }

    public void push(MessageTask messageTask) {
        try {
            lock.lock();
            if (priorityMessages.contains(messageTask.getMessage().getMessageType())) {
                priorityQueue.push(messageTask);
            } else if (messageTask.getMessage().getMessageType().equals(MessageType.NEW_BLOCK_HASHES)) {
                newBlockHashesPerPeer.push(messageTask);
            } else {
                nonPriorityQueue.push(messageTask);
            }

            empty.signal();
        } finally {
            size = nonPriorityQueue.size() + priorityQueue.size() + newBlockHashesPerPeer.size();
            lock.unlock();
        }
    }

    public Optional<MessageTask> pop(int seconds) {
        try {
            lock.lock();

            while (true) {
                //Order of priority for message consumption
                Optional<MessageTask> res = priorityQueue.pop();
                if (res.isPresent()) {
                    return res;
                }
                res = nonPriorityQueue.pop();
                if (res.isPresent()) {
                    return res;
                }
                res = newBlockHashesPerPeer.pop();
                if (res.isPresent()) {
                    return res;
                }

                try {
                    boolean wasSignaled = empty.await(seconds, TimeUnit.SECONDS);
                    if (!wasSignaled) {
                        // Timeout
                        return Optional.empty();
                    }
                } catch (InterruptedException e) {
                    return Optional.empty();
                }
            }
        } finally {
            size = nonPriorityQueue.size() + priorityQueue.size() + newBlockHashesPerPeer.size();
            lock.unlock();
        }
    }
}
