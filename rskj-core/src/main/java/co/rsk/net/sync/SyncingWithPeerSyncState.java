package co.rsk.net.sync;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.Optional;

public class SyncingWithPeerSyncState implements SyncState {
    private Duration timeElapsed;
    private SyncConfiguration syncConfiguration;
    private SyncEventsHandler syncEventsHandler;
    private SyncInformation syncInformation;

    public SyncingWithPeerSyncState(SyncConfiguration syncConfiguration, SyncEventsHandler syncEventsHandler, SyncInformation syncInformation) {
        this.syncConfiguration = syncConfiguration;
        this.syncEventsHandler = syncEventsHandler;
        this.syncInformation = syncInformation;
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
    public void newBlockHash(byte[] hash) {
        ConnectionPointFinder connectionPointFinder = syncInformation.getConnectionPointFinder();
        if (this.syncInformation.isKnownBlock(hash))
            connectionPointFinder.updateFound();
        else
            connectionPointFinder.updateNotFound();

        Optional<Long> cp = connectionPointFinder.getConnectionPoint();
        if (cp.isPresent()) {
            syncEventsHandler.sendSkeletonRequestTo(cp.get());
            return;
        }

        syncEventsHandler.sendBlockHashRequestTo(connectionPointFinder.getFindingHeight());
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
