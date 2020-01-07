package co.rsk.net;

import co.rsk.net.messages.MessageType;

import java.util.HashSet;
import java.util.Set;

public abstract class AcceptancePolicy {
    abstract boolean accepts(MessageTask messageTask);

    public static AcceptancePolicy AcceptAll() {
        return new AcceptAll();
    }

    public static AcceptancePolicy AcceptTypes(Set<MessageType> acceptedTypes) {
        return new AcceptTypes(acceptedTypes);
    }

    public static class AcceptAll extends AcceptancePolicy {
        @Override
        boolean accepts(MessageTask messageTask) {
            return true;
        }
    }

    public static class AcceptTypes extends AcceptancePolicy {
        private final Set<MessageType> acceptedTypes;

        public AcceptTypes(Set<MessageType> acceptedTypes) {
            this.acceptedTypes = new HashSet<>(acceptedTypes);
        }

        @Override
        boolean accepts(MessageTask messageTask) {
            return acceptedTypes.contains(messageTask.getMessage().getMessageType());
        }
    }
}
