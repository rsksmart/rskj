package co.rsk.net;

import org.ethereum.core.BlockIdentifier;

import java.util.List;

/**
 * Created by ajlopez on 29/08/2017.
 */
public class SyncPeerStatus {
    // Status used to find connection point
    private long findingHeight;
    private long findingInterval;

    // Connection point found or not
    private boolean hasConnectionPoint;
    private long connectionPoint;

    // Block identifiers retrieved in skeleton
    private List<BlockIdentifier> blockIdentifiers;

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
    }
}

