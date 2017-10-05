package co.rsk.net.sync;

import co.rsk.net.messages.MessageType;
import com.google.common.annotations.VisibleForTesting;

import java.util.HashMap;
import java.util.Map;

public class ExpectedMessages {

    private Map<Long, MessageType> messages = new HashMap<>();
    private long lastRequestId;

    public long registerExpectedMessage(MessageType type) {
        lastRequestId++;
        this.messages.put(lastRequestId, type);
        return lastRequestId;
    }

    public boolean isExpectedMessage(long responseId, MessageType type) {
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
}
