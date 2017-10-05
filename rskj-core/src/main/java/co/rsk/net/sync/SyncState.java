package co.rsk.net.sync;

import javax.annotation.Nonnull;
import java.time.Duration;

public interface SyncState {
    @Nonnull
    SyncStatesIds getId();

    default void messageSent() {}

    /**
     * should only be called when a new peer arrives
     */
    default void newPeerStatus() {}

    default void newBlockHash(byte[] hash) {}

    default void tick(Duration duration) {}

    default boolean isSyncing(){
        return false;
    }
}
