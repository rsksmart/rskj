package co.rsk.net.sync;

import javax.annotation.Nonnull;

public class FindingConnectionPointSyncStatus implements SyncStatus {
    @Nonnull
    @Override
    public SyncStatusIds getId() {
        return SyncStatusIds.FINDING_CONNECTION_POINT;
    }
}
