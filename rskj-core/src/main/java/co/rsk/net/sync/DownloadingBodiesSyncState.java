package co.rsk.net.sync;

import co.rsk.net.messages.BodyResponseMessage;
import org.ethereum.core.BlockHeader;

import java.time.Duration;
import java.util.Queue;

public class DownloadingBodiesSyncState  extends BaseSyncState {
    private Queue<BlockHeader> pendingHeaders;

    private Duration timeElapsed;

    public DownloadingBodiesSyncState(SyncConfiguration syncConfiguration, SyncEventsHandler syncEventsHandler, SyncInformation syncInformation, Queue<BlockHeader> pendingHeaders) {
        super(syncInformation, syncEventsHandler, syncConfiguration);

        this.pendingHeaders = pendingHeaders;

        this.resetTimeElapsed();
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
            syncEventsHandler.sendBodyRequest(pendingHeaders.remove());
            return;
        }

        // Finished syncing
        syncEventsHandler.stopSyncing();
    }

    @Override
    public void onEnter() {
        syncEventsHandler.sendBodyRequest(pendingHeaders.remove());
    }

    @Override
    public boolean isSyncing() {
        return true;
    }
}
