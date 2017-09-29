package co.rsk.net.sync;

import javax.annotation.Nonnull;
import java.time.Duration;

public class SyncingWithPeerSyncState implements SyncState {
    private Duration timeElapsed;
    private SyncConfiguration syncConfiguration;
    private SyncEventsHandler syncEventsHandler;

    public SyncingWithPeerSyncState(SyncConfiguration syncConfiguration, SyncEventsHandler syncEventsHandler) {
        this.syncConfiguration = syncConfiguration;
        this.syncEventsHandler = syncEventsHandler;
        this.resetTimeElapsed();
    }

    private void resetTimeElapsed() {
        timeElapsed = Duration.ZERO;
    }

    @Nonnull
    @Override
    public SyncStatesIds getId() {
        return SyncStatesIds.SYNC_WITH_PEER;
    }

    @Override
    public void tick(Duration duration) {
        timeElapsed = timeElapsed.plus(duration);
        if (timeElapsed.compareTo(syncConfiguration.getTimeoutWaitingRequest()) >= 0) {
            syncEventsHandler.stopSyncing();
        }
    }

    @Override
    public void messageSent(){
        this.resetTimeElapsed();
    }

    @Override
    public boolean isSyncing(){
        return true;
    }
}
