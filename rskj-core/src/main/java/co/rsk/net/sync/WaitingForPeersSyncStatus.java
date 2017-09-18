package co.rsk.net.sync;

import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;

public class WaitingForPeersSyncStatus implements SyncStatus {
    private int peers;

    private long secondsPassed;

    @Nonnull
    @Override
    public SyncStatuses getStatus() {
        return SyncStatuses.WAITING_FOR_PEERS;
    }

    @Nonnull
    @Override
    public SyncStatus newPeerFound(Object peer) {
        peers++;
        if (peers == 5) {
            return new DecidingSyncStatus();
        }

        return this;
    }

    @Nonnull
    @Override
    public SyncStatus tick(int amount, TimeUnit unit) {
        secondsPassed += unit.toSeconds(amount);
        if (peers > 0 && secondsPassed >= TimeUnit.MINUTES.toSeconds(2)) {
            return new DecidingSyncStatus();
        }

        return this;
    }
}
