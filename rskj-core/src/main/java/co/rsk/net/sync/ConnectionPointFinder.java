package co.rsk.net.sync;

import com.google.common.annotations.VisibleForTesting;

import java.util.Optional;

/**
 * Uses Binary Search to help find a connection point with another peer.
 */
public class ConnectionPointFinder {

    // Status used to find connection point
    private long start;
    private long end;

    // Connection point found or not
    private Long connectionPoint = null;

    public ConnectionPointFinder(long height) {
        this.start = 0;
        this.end = height;
    }

    public Optional<Long> getConnectionPoint() {
        return Optional.ofNullable(this.connectionPoint);
    }

    public long getFindingHeight() {
        // this is implemented like this to avoid overflow problems
        return this.start + (this.end - this.start) / 2;
    }

    public void updateFound() {
        this.start = getFindingHeight();
        trySettingConnectionPoint();
    }

    public void updateNotFound() {
        this.end = getFindingHeight();
        trySettingConnectionPoint();
    }

    private void trySettingConnectionPoint() {
        if (this.end - this.start <= 1) {
            this.setConnectionPoint(this.start);
        }
    }

    @VisibleForTesting
    public void setConnectionPoint(long height) {
        this.connectionPoint = height;
    }
}
