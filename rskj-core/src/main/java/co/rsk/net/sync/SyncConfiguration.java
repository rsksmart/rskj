package co.rsk.net.sync;

import com.google.common.annotations.VisibleForTesting;

import javax.annotation.concurrent.Immutable;

@Immutable
public final class SyncConfiguration {
    public static final SyncConfiguration DEFAULT = new SyncConfiguration(5, 2);
    @VisibleForTesting
    public static final SyncConfiguration IMMEDIATE_FOR_TESTING = new SyncConfiguration(1, 2);

    private final int minimumPeers;
    private final int timeoutWaitingPeers;

    public SyncConfiguration(int minimumPeers, int timeoutWaitingPeers) {
        this.minimumPeers = minimumPeers;
        this.timeoutWaitingPeers = timeoutWaitingPeers;
    }

    /**
     * @return Ideally, the minimum number of peers we would want to start finding a connection point.
     */
    public final int getMinimumPeers() {
        return minimumPeers;
    }

    /**
     * @return Timeout in minutes to start finding the connection point when we have at least one peer.
     */
    public final int getTimeoutWaitingPeers() {
        return timeoutWaitingPeers;
    }
}
