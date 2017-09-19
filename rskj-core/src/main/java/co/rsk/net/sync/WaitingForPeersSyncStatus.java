package co.rsk.net.sync;

import javax.annotation.Nonnull;
import java.time.Duration;

public class WaitingForPeersSyncStatus implements SyncStatus {
    private int peers;
    private Duration timeElapsed = Duration.ZERO;

    @Nonnull
    @Override
    public SyncStatuses getStatus() {
        return SyncStatuses.WAITING_FOR_PEERS;
    }

    @Nonnull
    @Override
    public SyncStatus newPeerFound() {
        // TODO(mc) enforce policy
//        peers++;
//        if (peers == 5) {
//            return new DecidingSyncStatus();
//        }
//
//        return this;
        return new FindingConnectionPointSyncStatus();
    }

    @Nonnull
    @Override
    public SyncStatus tick(Duration duration) {
        timeElapsed = timeElapsed.plus(duration);
        if (peers > 0 && timeElapsed.toMinutes() >= 2) {
            return new DecidingSyncStatus();
        }

        return this;
    }
}
