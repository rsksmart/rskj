package co.rsk.net.sync;

import co.rsk.net.messages.MessageType;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.BlockIdentifier;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SyncPeerProcessor {

    // Status used to find connection point
    private long findingHeight;
    private long findingInterval;

    // Connection point found or not
    private Optional<Long> connectionPoint = Optional.empty();

    // Block identifiers retrieved in skeleton
    private Optional<List<BlockIdentifier>> skeleton = Optional.empty();
    private Optional<Integer> lastRequestedLinkIndex;

    // Expected response
    private Map<Long, MessageType> expectedResponses = new HashMap<>();

    public void startFindConnectionPoint(long height) {
        this.findingInterval = height / 2;
        this.findingHeight = height - this.findingInterval;
        this.connectionPoint = Optional.empty();
    }

    public void setConnectionPoint(long height) {
        this.connectionPoint = Optional.of(height);
    }

    public Optional<Long> getConnectionPoint() {
        return this.connectionPoint;
    }

    public long getFindingHeight() { return this.findingHeight; }

    public void updateFound() {
        if (this.findingInterval == -1) {
            this.setConnectionPoint(this.findingHeight);
            return;
        }

        this.findingInterval = Math.abs(this.findingInterval / 2);

        if (this.findingInterval <= 1)
            this.findingInterval = 2;

        this.findingHeight += this.findingInterval;
    }

    public void updateNotFound() {
        if (this.findingInterval == 1) {
            this.setConnectionPoint(this.findingHeight - 1);
            return;
        }

        this.findingInterval = -Math.abs(this.findingInterval / 2);

        if (this.findingInterval == 0)
            this.findingInterval = -1;

        this.findingHeight += this.findingInterval;
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
        this.lastRequestedLinkIndex = Optional.empty();
    }

    public Optional<Integer> getLastRequestedLinkIndex() { return this.lastRequestedLinkIndex; }

    public void setLastRequestedLinkIndex(int index) { this.lastRequestedLinkIndex = Optional.of(index); }

    public void registerExpectedResponse(long responseId, MessageType type) {
        this.expectedResponses.put(responseId, type);
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
}
