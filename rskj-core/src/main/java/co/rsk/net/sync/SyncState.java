package co.rsk.net.sync;

import javax.annotation.Nonnull;
import java.time.Duration;

public interface SyncState {
    @Nonnull
    SyncStatesIds getId();

    /**
     * should only be called when a new peer arrives
     */
    default void newPeerStatus() {}

    default void tick(Duration duration) {}

    default void messageSent() {}

    default boolean isSyncing(){
        return false;
    }
}
