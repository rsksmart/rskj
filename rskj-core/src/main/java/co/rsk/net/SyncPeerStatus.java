package co.rsk.net;

import co.rsk.net.messages.MessageType;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.BlockIdentifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by ajlopez on 29/08/2017.
 */
public class SyncPeerStatus {
    // Peer status
    private Status status;

    // Status used to find connection point
    private long findingHeight;
    private long findingInterval;

    // Connection point found or not
    private boolean hasConnectionPoint;
    private long connectionPoint;

    // Block identifiers retrieved in skeleton
    private List<BlockIdentifier> blockIdentifiers;
    private int lastBlockIdentifierRequested;

    // Expected response
    private Map<Long, MessageType> expectedResponses = new HashMap<>();

    public void setStatus(Status status) {
        this.status = status;
    }

    public Status getStatus() {
        return this.status;
    }

    public void startFindConnectionPoint(long height) {
        this.findingInterval = height / 2;
        this.findingHeight = height - this.findingInterval;
    }

    public boolean hasConnectionPoint() { return this.hasConnectionPoint; }

    public void setConnectionPoint(long height) {
        this.connectionPoint = height;
        this.hasConnectionPoint = true;
    }

    public long getConnectionPoint() {
        return this.connectionPoint;
    }

    public long getFindingHeight() { return this.findingHeight; }

    public void updateFound() {
        if (this.findingInterval == -1) {
            this.setConnectionPoint(this.findingHeight);
            return;
        }

        this.findingInterval = Math.abs(this.findingInterval / 2);

        if (this.findingInterval == 0)
            this.findingInterval = 1;

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

    public boolean hasBlockIdentifiers() {
        return this.blockIdentifiers != null;
    }

    public List<BlockIdentifier> getBlockIdentifiers() {
        return this.blockIdentifiers;
    }

    public void setBlockIdentifiers(List<BlockIdentifier> blockIdentifiers) {
        this.blockIdentifiers = blockIdentifiers;
        this.lastBlockIdentifierRequested = -1;
    }

    public int getLastBlockIdentifierRequested() { return this.lastBlockIdentifierRequested; }

    public void setLastBlockIdentifierRequested(int index) { this.lastBlockIdentifierRequested = index; }

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
    public Map<Long, MessageType> getExpectedReponses() {
        return this.expectedResponses;
    }
}

