package co.rsk.net.sync;

import co.rsk.net.messages.BlockHeadersResponseMessage;
import co.rsk.net.messages.BodyResponseMessage;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.List;
import java.util.Queue;

public class DownloadingBodiesSyncState implements SyncState {
    private SyncConfiguration syncConfiguration;
    private SyncEventsHandler syncEventsHandler;
    private SyncInformation syncInformation;
    private Queue<BlockHeader> pendingHeaders;

    private Duration timeElapsed;

    public DownloadingBodiesSyncState(SyncConfiguration syncConfiguration, SyncEventsHandler syncEventsHandler, SyncInformation syncInformation, Queue<BlockHeader> pendingHeaders) {
        this.syncConfiguration = syncConfiguration;
        this.syncEventsHandler = syncEventsHandler;
        this.syncInformation = syncInformation;
        this.pendingHeaders = pendingHeaders;

        this.resetTimeElapsed();
    }

    private void resetTimeElapsed() {
        timeElapsed = Duration.ZERO;
    }

    @Nonnull
    @Override
    public SyncStatesIds getId() {
        return SyncStatesIds.DOWNLOADING_BODIES;
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

        if (!pendingHeaders.isEmpty()) {
            resetTimeElapsed();
            syncEventsHandler.sendNextBodyRequest(pendingHeaders.remove());
            return;
        }

        // Finished syncing
        syncEventsHandler.stopSyncing();
    }

    @Override
    public void newBlockHeaders(BlockHeadersResponseMessage message) {
        // TODO(mc) do peer scoring, banning and logging
        syncEventsHandler.stopSyncing();
    }

    @Override
    public void newConnectionPointData(byte[] hash) {
        // TODO(mc) do peer scoring, banning and logging
        syncEventsHandler.stopSyncing();
    }

    @Override
    public void newSkeleton(List<BlockIdentifier> skeleton) {
        // TODO(mc) do peer scoring, banning and logging
        syncEventsHandler.stopSyncing();
    }

    @Override
    public void onEnter() {
        syncEventsHandler.sendNextBodyRequest(pendingHeaders.remove());
    }

    @Override
    public void messageSent() {
        resetTimeElapsed();
    }

    @Override
    public boolean isSyncing() {
        return true;
    }
}
