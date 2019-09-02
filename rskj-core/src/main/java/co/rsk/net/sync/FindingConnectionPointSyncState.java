package co.rsk.net.sync;

import co.rsk.net.NodeID;
import co.rsk.scoring.EventType;
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
        syncEventsHandler.startDownloadingSkeleton(cp.get(), selectedPeerId);
    }

    private boolean isKnownBlock(byte[] hash) {
        return blockchain.getBlockByHash(hash) != null;
    }

    private void trySendRequest() {
        boolean sent = syncEventsHandler.sendBlockHashRequest(connectionPointFinder.getFindingHeight(), selectedPeerId);
        if (!sent) {
            syncEventsHandler.onSyncIssue("Channel failed to sent on {} to {}",
                    this.getClass(), selectedPeerId);
        }
    }

    @Override
    public void onEnter() {
        trySendRequest();
    }

    @Override
    protected void onMessageTimeOut() {
        syncEventsHandler.onErrorSyncing(selectedPeerId,
                "Timeout waiting requests {}", EventType.TIMEOUT_MESSAGE, this.getClass(), selectedPeerId);
    }
}
