package co.rsk.net;

import co.rsk.net.messages.MessageType;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class MessageQueue {

    private static final int NEW_BLOCK_HASHES_MAX_CAPACITY = 10;

    private List<TaskQueue> pushingOrder;
    private List<TaskQueue> poppingOrder;

    private int size;
    private final ReentrantLock lock;
    private final Condition empty;

    public MessageQueue() {
        size = 0;
        pushingOrder = new ArrayList<>();
        poppingOrder = new ArrayList<>();

        Set<MessageType> priorityMessages = new HashSet<>();
        priorityMessages.add(MessageType.BLOCK_HEADERS_RESPONSE_MESSAGE);
        priorityMessages.add(MessageType.BLOCK_RESPONSE_MESSAGE);
        priorityMessages.add(MessageType.SKELETON_RESPONSE_MESSAGE);
        priorityMessages.add(MessageType.BLOCK_HASH_RESPONSE_MESSAGE);
        priorityMessages.add(MessageType.STATUS_MESSAGE);
        priorityMessages.add(MessageType.BODY_RESPONSE_MESSAGE);
        Set<MessageType> blockHashesMessages = Collections.singleton(MessageType.NEW_BLOCK_HASHES);

        UnboundedTaskQueue priority = new UnboundedTaskQueue(AcceptancePolicy.AcceptTypes(priorityMessages));
        PeerBoundedTaskQueue newHashes = new PeerBoundedTaskQueue(AcceptancePolicy.AcceptTypes(blockHashesMessages), NEW_BLOCK_HASHES_MAX_CAPACITY);
        UnboundedTaskQueue nonPriority = new UnboundedTaskQueue(AcceptancePolicy.AcceptAll());

        pushingOrder.add(priority);
        pushingOrder.add(newHashes);
        pushingOrder.add(nonPriority);

        poppingOrder.add(priority);
        poppingOrder.add(nonPriority);
        poppingOrder.add(newHashes);

        lock = new ReentrantLock();
        empty = lock.newCondition();
    }

    public int size() {
        return size;
    }

    public void push(MessageTask messageTask) {
        try {
            lock.lock();

            pushingOrder.stream().filter(q -> q.accepts(messageTask)).findFirst().get().push(messageTask);

            empty.signal();
        } finally {
            size = calculateSize();
            lock.unlock();
        }
    }

    public Optional<MessageTask> pop(int seconds) {
        try {
            lock.lock();

            while (true) {
                //Order of priority for message consumption

                Optional<MessageTask> res = poppingOrder.stream().filter(q -> q.size() > 0)
                        .findFirst()
                        .flatMap(TaskQueue::pop);
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
            size = calculateSize();
            lock.unlock();
        }
    }

    private Integer calculateSize() {
        return pushingOrder.stream().map(TaskQueue::size).reduce(0, Integer::sum);
    }
}
