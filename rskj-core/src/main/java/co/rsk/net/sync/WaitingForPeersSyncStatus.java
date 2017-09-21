package co.rsk.net.sync;

import co.rsk.net.NodeID;
import co.rsk.net.Status;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.Set;

public class WaitingForPeersSyncStatus implements SyncStatus {
    private int peers;
    private Duration timeElapsed = Duration.ZERO;
    private PeersInformation knownPeers;

    public WaitingForPeersSyncStatus(PeersInformation knownPeers) {
        this.knownPeers = knownPeers;
    }

    @Nonnull
    @Override
    public SyncStatuses getStatus() {
        return SyncStatuses.WAITING_FOR_PEERS;
    }

    @Nonnull
    @Override
    public SyncStatus newPeerStatus(NodeID peerID, Status status, Set<Runnable> finishedWaitingForPeersCallbacks) {
        // TODO(mc) enforce policy
//        peers++;
//        if (peers == 5) {
//            return new DecidingSyncStatus();
//        }
//
//        return this;


        if (knownPeers.isKnownPeer(peerID)) {
            return this;
        }

        knownPeers.registerPeer(peerID).setStatus(status);
        finishedWaitingForPeersCallbacks.forEach(Runnable::run);
        return this;
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
