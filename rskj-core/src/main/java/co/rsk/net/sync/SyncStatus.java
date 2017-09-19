package co.rsk.net.sync;

import javax.annotation.Nonnull;
import java.time.Duration;

public interface SyncStatus {
    @Nonnull
    SyncStatuses getStatus();

    @Nonnull
    default SyncStatus newPeerFound() {
        return this;
    }

    @Nonnull
    default SyncStatus tick(Duration duration) {
        return this;
    }
}
