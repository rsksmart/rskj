package co.rsk.net.sync;

import co.rsk.net.NodeID;
import co.rsk.net.Status;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.Set;

public interface SyncStatus {
    @Nonnull
    SyncStatusIds getId();

    default void newPeerStatus(SyncStatusSetter statusSetter, NodeID peerID, Status status, Set<Runnable> finishedWaitingForPeersCallbacks) {
    }

    default void tick(SyncStatusSetter statusSetter, Duration duration) {
    }
}
