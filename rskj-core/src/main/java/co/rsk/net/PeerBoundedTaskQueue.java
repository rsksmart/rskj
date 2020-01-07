package co.rsk.net;


import java.util.*;

public class PeerBoundedTaskQueue implements TaskQueue {

    private final AcceptancePolicy acceptancePolicy;
    private final int maxMessagesPerPeer;
    private Map<Peer, Queue<MessageTask>> queue;
    private int size;

    public PeerBoundedTaskQueue(AcceptancePolicy acceptancePolicy, int maxMessagesPerPeer) {

        this.acceptancePolicy = acceptancePolicy;
        this.maxMessagesPerPeer = maxMessagesPerPeer;
        queue = new HashMap<>();
        size = 0;
    }

    @Override
    public void push(MessageTask messageTask) {
        Peer peer = messageTask.getSender();
        Queue<MessageTask> messages = queue.getOrDefault(peer, new ArrayDeque<>());

        size += 1;
        messages.add(messageTask);
        if (messages.size() > maxMessagesPerPeer) {
            messages.poll();
            size -=1;
        }

        queue.put(messageTask.getSender(), messages);
    }

    @Override
    public Optional<MessageTask> pop() {
        if (queue.size() == 0) {
            return Optional.empty();
        }

        Queue<MessageTask> messages = queue.entrySet().stream().findAny().map(Map.Entry::getValue).get();
        MessageTask res = messages.poll();
        if (messages.isEmpty()) {
            queue.remove(res.getSender());
        }
        size -= 1;
        return Optional.of(res);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean accepts(MessageTask messageTask) {
        return acceptancePolicy.accepts(messageTask);
    }
}
