package co.rsk.net.sync;

import javax.annotation.Nonnull;

public class FindingConnectionPointSyncStatus implements SyncStatus {
    @Nonnull
    @Override
    public SyncStatuses getStatus() {
        return SyncStatuses.FINDING_CONNECTION_POINT;
    }
}
