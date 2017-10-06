package co.rsk.net.sync;

import co.rsk.net.Status;
import co.rsk.net.messages.BodyResponseMessage;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.BlockIdentifier;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

public class SyncingWithPeerSyncState implements SyncState {
    private Duration timeElapsed;
    private SyncConfiguration syncConfiguration;
    private SyncEventsHandler syncEventsHandler;
    private SyncInformation syncInformation;
    private ConnectionPointFinder connectionPointFinder;

    public SyncingWithPeerSyncState(SyncConfiguration syncConfiguration, SyncEventsHandler syncEventsHandler, SyncInformation syncInformation) {
        this.syncConfiguration = syncConfiguration;
        this.syncEventsHandler = syncEventsHandler;
        this.syncInformation = syncInformation;
        this.connectionPointFinder = new ConnectionPointFinder();
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
    public void newBody(BodyResponseMessage message) {
        if (!syncInformation.isExpectedBody(message.getId())) {
            // Invalid body response
            // TODO(mc) do peer scoring, banning and logging
            syncEventsHandler.stopSyncing();
            return;
        }

        // TODO(mc) validate transactions and uncles are part of this block (with header)
        syncInformation.saveBlock(message);

        if (syncInformation.isExpectingMoreBodies()) {
            this.resetTimeElapsed();
            syncEventsHandler.sendNextBodyRequest();
            return;
        }

        // Finished syncing
        syncEventsHandler.stopSyncing();
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
        syncEventsHandler.sendSkeletonRequest(cp.get());
    }

    @Override
    public void newSkeleton(List<BlockIdentifier> skeleton) {
        // defensive programming: this should never happen
        if (!connectionPointFinder.getConnectionPoint().isPresent()
                || skeleton.size() < 2) {
            syncEventsHandler.stopSyncing();
            return;
        }

        syncEventsHandler.startRequestingHeaders(skeleton, connectionPointFinder.getConnectionPoint().get());
    }

    @Override
    public void onEnter() {
        Status status = syncInformation.getSelectedPeerStatus();
        connectionPointFinder.startFindConnectionPoint(status.getBestBlockNumber());
        syncEventsHandler.sendBlockHashRequest(connectionPointFinder.getFindingHeight());
    }

    @Override
    public void messageSent() {
        this.resetTimeElapsed();
    }

    @Override
    public boolean isSyncing() {
        return true;
    }

    @VisibleForTesting
    public void setConnectionPoint(long height) {
        connectionPointFinder.setConnectionPoint(height);
    }
}
