package co.rsk.net.sync;

import co.rsk.net.NodeID;
import co.rsk.net.Status;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.Set;

public interface SyncStatus {
    @Nonnull
    SyncStatuses getStatus();

    @Nonnull
    default SyncStatus newPeerStatus(NodeID peerID, Status status, Set<Runnable> finishedWaitingForPeersCallbacks) {
        return this;
    }

    @Nonnull
    default SyncStatus tick(Duration duration) {
        return this;
    }
}
