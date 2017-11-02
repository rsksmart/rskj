package co.rsk.net.sync;

import co.rsk.net.MessageChannel;
import co.rsk.net.messages.BodyResponseMessage;
import co.rsk.scoring.EventType;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;

import java.time.Duration;
import java.util.List;

public abstract class BaseSyncState implements SyncState {
    protected SyncConfiguration syncConfiguration;
    protected SyncEventsHandler syncEventsHandler;
    protected SyncInformation syncInformation;

    protected Duration timeElapsed;

    public BaseSyncState(SyncInformation syncInformation, SyncEventsHandler syncEventsHandler, SyncConfiguration syncConfiguration) {
        this.syncInformation = syncInformation;
        this.syncEventsHandler = syncEventsHandler;
        this.syncConfiguration = syncConfiguration;

        this.resetTimeElapsed();
    }

    protected void resetTimeElapsed() {
        timeElapsed = Duration.ZERO;
    }

    @Override
    public void tick(Duration duration) {
        timeElapsed = timeElapsed.plus(duration);
        if (timeElapsed.compareTo(syncConfiguration.getTimeoutWaitingRequest()) >= 0) {
            syncEventsHandler.onErrorSyncing(
                    "Timeout waiting requests from node {}",
                    EventType.TIMEOUT_MESSAGE,
                    syncInformation.getSelectedPeerId());
        }
    }

    @Override
    public void newBlockHeaders(List<BlockHeader> chunk) {
        // TODO(mc) do peer scoring, banning and logging
        syncEventsHandler.stopSyncing();
    }

    @Override
    public void newBody(BodyResponseMessage message, MessageChannel peer) {
        // TODO(mc) do peer scoring, banning and logging
        syncEventsHandler.stopSyncing();
    }

    @Override
    public void newConnectionPointData(byte[] hash) {
        // TODO(mc) do peer scoring, banning and logging
        syncEventsHandler.stopSyncing();
    }

    @Override
    public void newPeerStatus() { }

    @Override
    public void newSkeleton(List<BlockIdentifier> skeleton, MessageChannel peer) {
        // TODO(mc) do peer scoring, banning and logging
        syncEventsHandler.stopSyncing();
    }

    @Override
    public void onEnter() { }

    @VisibleForTesting
    public void messageSent() {
        resetTimeElapsed();
    }
}
