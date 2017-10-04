package co.rsk.net.sync;

import com.google.common.annotations.VisibleForTesting;

import javax.annotation.concurrent.Immutable;
import java.time.Duration;

@Immutable
public final class SyncConfiguration {
    public static final SyncConfiguration DEFAULT = new SyncConfiguration(5, 2, 30, 10);

    @VisibleForTesting
    public static final SyncConfiguration IMMEDIATE_FOR_TESTING = new SyncConfiguration(1, 2, 1, 10);

    private final int expectedPeers;
    private final Duration timeoutWaitingPeers;
    private final Duration timeoutWaitingRequest;
    private final Duration expirationTimePeerStatus;

    /**
     *
     * @param expectedPeers The expected number of peers we would want to start finding a connection point.
     * @param timeoutWaitingPeers Timeout in minutes to start finding the connection point when we have at least one peer
     * @param timeoutWaitingRequest Timeout in seconds to wait for syncing requests
     * @param expirationTimePeerStatus Expiration time in minutes for peer status
     */
    public SyncConfiguration(int expectedPeers, int timeoutWaitingPeers, int timeoutWaitingRequest, int expirationTimePeerStatus) {
        this.expectedPeers = expectedPeers;
        this.timeoutWaitingPeers = Duration.ofMinutes(timeoutWaitingPeers);
        this.timeoutWaitingRequest = Duration.ofSeconds(timeoutWaitingRequest);
        this.expirationTimePeerStatus = Duration.ofMinutes(expirationTimePeerStatus);
    }

    public final int getExpectedPeers() {
        return expectedPeers;
    }

    public final Duration getTimeoutWaitingPeers() {
        return timeoutWaitingPeers;
    }

    public final Duration getTimeoutWaitingRequest() {
        return  timeoutWaitingRequest;
    }

    public final Duration getExpirationTimePeerStatus() {
        return expirationTimePeerStatus;
    }

    public final int getMaxSkeletonChunks() {
        return 5;
    }
}
