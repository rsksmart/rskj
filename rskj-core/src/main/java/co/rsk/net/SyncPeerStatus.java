package co.rsk.net;

/**
 * Created by ajlopez on 29/08/2017.
 */
public class SyncPeerStatus {
    private boolean hasConnectionPoint;
    private long connectionPoint;

    private long findingHeight;
    private long findingInterval;

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
}

