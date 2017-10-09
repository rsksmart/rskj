package co.rsk.net.sync;

import co.rsk.net.messages.MessageType;
import co.rsk.net.messages.MessageWithId;
import com.google.common.annotations.VisibleForTesting;

import java.util.HashMap;
import java.util.Map;

public class PendingMessages {

    private Map<Long, MessageType> messages = new HashMap<>();
    private long lastRequestId;

    public void register(MessageWithId message) {
        this.messages.put(message.getId(), message.getResponseMessageType());
    }

    public long getNextRequestId(){
        return ++lastRequestId;
    }

    public boolean isPending(long responseId, MessageType type) {
        if (!this.messages.containsKey(responseId) || this.messages.get(responseId) != type)
            return false;

        this.messages.remove(responseId);

        return true;
    }

    public void clear() {
        this.messages.clear();
        // lastRequestId isn't cleaned on purpose
    }

    @VisibleForTesting
    public Map<Long, MessageType> getExpectedMessages() {
        return this.messages;
    }

    @VisibleForTesting
    public void registerExpectedMessage(MessageWithId message) {
        this.messages.put(message.getId(), message.getMessageType());
    }
}
