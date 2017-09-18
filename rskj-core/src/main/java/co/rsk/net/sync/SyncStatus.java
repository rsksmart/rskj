package co.rsk.net.sync;

import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;

public interface SyncStatus {
    @Nonnull
    SyncStatuses getStatus();

    @Nonnull
    default SyncStatus newPeerFound(Object peer) {
        return this;
    }

    @Nonnull
    default SyncStatus tick(int amount, TimeUnit unit) {
        return this;
    }
}
