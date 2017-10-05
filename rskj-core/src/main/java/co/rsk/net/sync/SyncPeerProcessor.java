package co.rsk.net.sync;

import co.rsk.net.NodeID;
import co.rsk.net.messages.MessageType;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.BlockIdentifier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SyncPeerProcessor {

    // Block identifiers retrieved in skeleton
    private Optional<List<BlockIdentifier>> skeleton = Optional.empty();
    private int lastRequestedLinkIndex;
    private long lastRequestId;
    private NodeID selectedPeerId;

    // Expected response
    private Map<Long, MessageType> expectedResponses = new HashMap<>();

    public SyncPeerProcessor(@Nullable NodeID selectedPeerId) {
        this.selectedPeerId = selectedPeerId;
    }

    public boolean hasSkeleton() {
        return this.skeleton.isPresent();
    }

    @Nonnull
    public List<BlockIdentifier> getSkeleton() {
        return this.skeleton.orElseThrow(IllegalStateException::new);
    }

    public void setSkeleton(@Nonnull List<BlockIdentifier> skeleton) {
        this.skeleton = Optional.of(skeleton);
        this.lastRequestedLinkIndex = 0;
    }

    public int getLastRequestedLinkIndex() { return this.lastRequestedLinkIndex; }

    public void setLastRequestedLinkIndex(int index) { this.lastRequestedLinkIndex = index; }

    public long registerExpectedResponse(MessageType type) {
        lastRequestId++;
        this.expectedResponses.put(lastRequestId, type);
        return lastRequestId;
    }

    public boolean isExpectedResponse(long responseId, MessageType type) {
        if (!this.expectedResponses.containsKey(responseId) || this.expectedResponses.get(responseId) != type)
            return false;

        this.expectedResponses.remove(responseId);

        return true;
    }

    @VisibleForTesting
    public Map<Long, MessageType> getExpectedResponses() {
        return this.expectedResponses;
    }

    public NodeID getSelectedPeerId() {
        return selectedPeerId;
    }
}
