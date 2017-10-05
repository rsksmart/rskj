package co.rsk.net.sync;

import com.google.common.annotations.VisibleForTesting;

import java.util.Optional;

public class ConnectionPointFinder {

    // Status used to find connection point
    private long start;
    private long end;

    // Connection point found or not
    private Optional<Long> connectionPoint = Optional.empty();


    public void startFindConnectionPoint(long height) {
        this.start = 0;
        this.end = height;
        this.connectionPoint = Optional.empty();
    }

    @VisibleForTesting
    public void setConnectionPoint(long height) {
        this.connectionPoint = Optional.of(height);
    }

    public Optional<Long> getConnectionPoint() {
        return this.connectionPoint;
    }

    public long getFindingHeight() {
        return this.start + (this.end - this.start) / 2;
    }

    public void updateFound() {
        this.start = getFindingHeight();

        if (this.end - this.start <= 1) {
            this.setConnectionPoint(this.start);
        }
    }

    public void updateNotFound() {
        this.end = getFindingHeight();

        if (this.end - this.start <= 1) {
            this.setConnectionPoint(this.start);
        }
    }

}
