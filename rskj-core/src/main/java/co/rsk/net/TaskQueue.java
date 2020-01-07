package co.rsk.net;

import java.util.Optional;

public interface TaskQueue {
    void push(MessageTask messageTask);
    Optional<MessageTask> pop();
    int size();
}
