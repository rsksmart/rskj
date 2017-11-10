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

    public boolean isPending(MessageWithId message) {
        long messageId = message.getId();
        if (!this.messages.containsKey(messageId) || this.messages.get(messageId) != message.getMessageType())
            return false;

        this.messages.remove(messageId);

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
