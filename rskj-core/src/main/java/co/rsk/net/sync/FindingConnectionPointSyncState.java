package co.rsk.net.sync;

import com.google.common.annotations.VisibleForTesting;

import java.util.Optional;

public class FindingConnectionPointSyncState extends BaseSyncState {
    private ConnectionPointFinder connectionPointFinder;

    public FindingConnectionPointSyncState(SyncConfiguration syncConfiguration, SyncEventsHandler syncEventsHandler, SyncInformation syncInformation, long bestBlockNumber) {
        super(syncInformation, syncEventsHandler, syncConfiguration);
        this.connectionPointFinder = new ConnectionPointFinder(bestBlockNumber);
    }

    @Override
    public void newConnectionPointData(byte[] hash) {
        if (this.syncInformation.isKnownBlock(hash)) {
            connectionPointFinder.updateFound();
        } else {
            connectionPointFinder.updateNotFound();
        }

        Optional<Long> cp = connectionPointFinder.getConnectionPoint();
        if (!cp.isPresent()) {
            this.resetTimeElapsed();
            trySendRequest();
            return;
        }

        // connection point found
        syncEventsHandler.startDownloadingSkeleton(cp.get());
    }

    private void trySendRequest() {
        boolean sent = syncEventsHandler.sendBlockHashRequest(connectionPointFinder.getFindingHeight());
        if (!sent) {
            syncEventsHandler.onSyncIssue("Channel failed to sent on {} to {}",
                    this.getClass(), syncInformation.getSelectedPeerId());
        }
    }

    @Override
    public void onEnter() {
        trySendRequest();
    }

    @VisibleForTesting
    public void setConnectionPoint(long height) {
        connectionPointFinder.setConnectionPoint(height);
    }
}
