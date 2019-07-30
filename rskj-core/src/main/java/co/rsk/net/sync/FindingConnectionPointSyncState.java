package co.rsk.net.sync;

import co.rsk.net.NodeID;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Blockchain;

import java.util.Optional;

public class FindingConnectionPointSyncState extends BaseSyncState {
    private final Blockchain blockchain;
    private final NodeID selectedPeerId;
    private ConnectionPointFinder connectionPointFinder;

    public FindingConnectionPointSyncState(SyncConfiguration syncConfiguration,
                                           SyncEventsHandler syncEventsHandler,
                                           Blockchain blockchain,
                                           NodeID selectedPeerId,
                                           long bestBlockNumber) {
        super(syncEventsHandler, syncConfiguration);
        this.blockchain = blockchain;
        this.selectedPeerId = selectedPeerId;
        this.connectionPointFinder = new ConnectionPointFinder(bestBlockNumber);
    }

    @Override
    public void newConnectionPointData(byte[] hash) {
        if (isKnownBlock(hash)) {
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

    private boolean isKnownBlock(byte[] hash) {
        return blockchain.getBlockByHash(hash) != null;
    }

    private void trySendRequest() {
        boolean sent = syncEventsHandler.sendBlockHashRequest(connectionPointFinder.getFindingHeight());
        if (!sent) {
            syncEventsHandler.onSyncIssue("Channel failed to sent on {} to {}",
                    this.getClass(), selectedPeerId);
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
