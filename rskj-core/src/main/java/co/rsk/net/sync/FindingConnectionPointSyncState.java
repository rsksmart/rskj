package co.rsk.net.sync;

import co.rsk.crypto.Sha3Hash;
import com.google.common.annotations.VisibleForTesting;

import java.util.Optional;

public class FindingConnectionPointSyncState extends BaseSyncState {
    private ConnectionPointFinder connectionPointFinder;

    public FindingConnectionPointSyncState(SyncConfiguration syncConfiguration, SyncEventsHandler syncEventsHandler, SyncInformation syncInformation, long bestBlockNumber) {
        super(syncInformation, syncEventsHandler, syncConfiguration);

        this.connectionPointFinder = new ConnectionPointFinder(bestBlockNumber);
    }

    @Override
    public void newConnectionPointData(Sha3Hash hash) {

        if (this.syncInformation.isKnownBlock(hash)) {
            connectionPointFinder.updateFound();
        } else {
            connectionPointFinder.updateNotFound();
        }

        Optional<Long> cp = connectionPointFinder.getConnectionPoint();
        if (!cp.isPresent()) {
            this.resetTimeElapsed();
            syncEventsHandler.sendBlockHashRequest(connectionPointFinder.getFindingHeight());
            return;
        }

        // connection point found
        syncEventsHandler.startDownloadingSkeleton(cp.get());
    }

    @Override
    public void onEnter() {
        syncEventsHandler.sendBlockHashRequest(connectionPointFinder.getFindingHeight());
    }

    @VisibleForTesting
    public void setConnectionPoint(long height) {
        connectionPointFinder.setConnectionPoint(height);
    }
}
