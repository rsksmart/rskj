package co.rsk.net.sync;

import co.rsk.net.Status;
import com.google.common.annotations.VisibleForTesting;

import java.util.Optional;

public class FindingConnectionPointSyncState extends BaseSyncState {
    private ConnectionPointFinder connectionPointFinder;

    public FindingConnectionPointSyncState(SyncConfiguration syncConfiguration, SyncEventsHandler syncEventsHandler, SyncInformation syncInformation) {
        super(syncInformation, syncEventsHandler, syncConfiguration);

        this.connectionPointFinder = new ConnectionPointFinder();
    }

    @Override
    public boolean isSyncing() {
        return true;
    }

    @Override
    public void newConnectionPointData(byte[] hash) {
        this.resetTimeElapsed();

        if (this.syncInformation.isKnownBlock(hash)) {
            connectionPointFinder.updateFound();
        } else {
            connectionPointFinder.updateNotFound();
        }

        Optional<Long> cp = connectionPointFinder.getConnectionPoint();
        if (!cp.isPresent()) {
            syncEventsHandler.sendBlockHashRequest(connectionPointFinder.getFindingHeight());
            return;
        }

        // connection point found, request skeleton
        syncEventsHandler.startDownloadingSkeleton(cp.get());
    }

    @Override
    public void onEnter() {
        Status status = syncInformation.getSelectedPeerStatus();
        connectionPointFinder.startFindConnectionPoint(status.getBestBlockNumber());
        syncEventsHandler.sendBlockHashRequest(connectionPointFinder.getFindingHeight());
    }

    @VisibleForTesting
    public void setConnectionPoint(long height) {
        connectionPointFinder.setConnectionPoint(height);
    }
}
