package co.rsk.net.sync;

import javax.annotation.Nonnull;

public class DecidingSyncStatus implements SyncStatus {
    @Nonnull
    @Override
    public SyncStatuses getStatus() {
        return SyncStatuses.DECIDING;
    }
}
