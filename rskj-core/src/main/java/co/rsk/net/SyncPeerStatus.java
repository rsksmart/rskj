package co.rsk.net;

/**
 * Created by ajlopez on 29/08/2017.
 */
public class SyncPeerStatus {
    private boolean hasConnectionPoint;
    private long connectionPoint;

    private long findingHeight;
    private long findingInterval;

    public Status getStatus() { return this.status; }

    public void startFindConnectionPoint(long height) {
        this.findingInterval = height / 2;
        this.findingHeight = height - this.findingInterval;
    }

    public boolean hasConnectionPoint() { return this.hasConnectionPoint; }

    public long getFindingHeight() { return this.findingHeight; }

    public long getFindingInterval() { return this.findingInterval; }
}

