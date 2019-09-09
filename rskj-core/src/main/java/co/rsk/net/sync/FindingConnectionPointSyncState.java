package co.rsk.net.sync;

import co.rsk.net.NodeID;
import co.rsk.scoring.EventType;
import org.ethereum.core.Blockchain;

import java.util.Optional;

public class FindingConnectionPointSyncState extends BaseSyncState {

    private final NodeID selectedPeerId;
    private final ConnectionPointFinder connectionPointFinder;
    private final Blockchain blockchain;
    private final boolean forwardSync;

    public FindingConnectionPointSyncState(SyncConfiguration syncConfiguration,
                                           SyncEventsHandler syncEventsHandler,
                                           Blockchain blockchain,
                                           boolean forwardSync,
                                           NodeID selectedPeerId,
                                           long peerBestBlockNumber) {
        super(syncEventsHandler, syncConfiguration);
        this.blockchain = blockchain;
        this.forwardSync = forwardSync;
        long minNumber = blockchain.getFirstBlockNumber();

        this.selectedPeerId = selectedPeerId;
        this.connectionPointFinder = new ConnectionPointFinder(
                minNumber,
                peerBestBlockNumber);
    }

    @Override
    public void newConnectionPointData(byte[] hash) {
        boolean knownBlock = isKnownBlock(hash);
        Optional<Long> cp = connectionPointFinder.getConnectionPoint();
        if (cp.isPresent()) {
            if (knownBlock) {
                syncEventsHandler.startDownloadingSkeleton(cp.get(), selectedPeerId, forwardSync);
            } else {
                syncEventsHandler.onSyncIssue("Connection point not found with node {}", selectedPeerId);
            }
             return;
        }

        if (knownBlock) {
            connectionPointFinder.updateFound();
        } else {
            connectionPointFinder.updateNotFound();
        }

        cp = connectionPointFinder.getConnectionPoint();
        // No need to ask for genesis hash
        if (cp.isPresent() && cp.get() == 0L) {
            syncEventsHandler.startDownloadingSkeleton(cp.get(), selectedPeerId, forwardSync);
            return;
        }

        this.resetTimeElapsed();
        trySendRequest();
    }

    private boolean isKnownBlock(byte[] hash) {
        return blockchain.hasBlockInSomeBlockchain(hash);
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
