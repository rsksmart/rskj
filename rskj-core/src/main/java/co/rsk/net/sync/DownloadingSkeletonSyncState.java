package co.rsk.net.sync;

import org.ethereum.core.BlockIdentifier;

import java.util.List;

public class DownloadingSkeletonSyncState extends BaseSyncState {

    private long connectionPoint;

    public DownloadingSkeletonSyncState(SyncConfiguration syncConfiguration, SyncEventsHandler syncEventsHandler, SyncInformation syncInformation, long connectionPoint) {
        super(syncInformation, syncEventsHandler, syncConfiguration);
        this.connectionPoint = connectionPoint;
    }

    @Override
    public boolean isSyncing() {
        return true;
    }

    @Override
    public void newSkeleton(List<BlockIdentifier> skeleton) {
        // defensive programming: this should never happen
        if (skeleton.size() < 2) {
            syncEventsHandler.stopSyncing();
            return;
        }

        syncEventsHandler.startDownloadingHeaders(skeleton, connectionPoint);
    }

    @Override
    public void onEnter() {
        syncEventsHandler.sendSkeletonRequest(connectionPoint);
    }
}
