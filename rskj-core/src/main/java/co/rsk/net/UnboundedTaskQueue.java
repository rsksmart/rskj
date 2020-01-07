package co.rsk.net;

import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;

public class UnboundedTaskQueue implements TaskQueue {

    private final AcceptancePolicy acceptance;
    private Queue<MessageTask> queue;

    public UnboundedTaskQueue(AcceptancePolicy acceptance) {

        this.acceptance = acceptance;
        queue = new ArrayDeque<>();
    }

    @Override
    public void push(MessageTask messageTask) {
        queue.add(messageTask);
    }

    @Override
    public Optional<MessageTask> pop() {
        if (queue.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(queue.poll());
    }

    @Override
    public int size() {
        return queue.size();
    }

    @Override
    public boolean accepts(MessageTask messageTask) {
        return acceptance.accepts(messageTask);
    }
}
