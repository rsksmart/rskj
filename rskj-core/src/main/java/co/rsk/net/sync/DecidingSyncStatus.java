package co.rsk.net.sync;

import co.rsk.net.NodeID;
import co.rsk.net.Status;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

public class DecidingSyncStatus implements SyncStatus {
    private int peers;
    private Duration timeElapsed = Duration.ZERO;
    private Set<NodeID> knownPeers = new HashSet<>();
    private SyncConfiguration syncConfiguration;

    public DecidingSyncStatus(SyncConfiguration syncConfiguration) {
        this.syncConfiguration = syncConfiguration;
    }

    @Nonnull
    @Override
    public SyncStatusIds getId() {
        return SyncStatusIds.DECIDING;
    }

    @Override
    public void newPeerStatus(SyncStatusSetter statusSetter, NodeID peerID, Status status, Set<Runnable> finishedWaitingForPeersCallbacks) {
        if (knownPeers.contains(peerID)) {
            return;
        }

        knownPeers.add(peerID);

        peers++;
        if (peers == syncConfiguration.getMinimumPeers()) {
            statusSetter.setStatus(new FindingConnectionPointSyncStatus());
            finishedWaitingForPeersCallbacks.forEach(Runnable::run);
        }
    }

    @Override
    public void tick(SyncStatusSetter statusSetter, Duration duration) {
        timeElapsed = timeElapsed.plus(duration);
        if (peers <= 0 ||
                timeElapsed.toMinutes() < syncConfiguration.getTimeoutWaitingPeers()) {
            return;
        }

        statusSetter.setStatus(new FindingConnectionPointSyncStatus());
    }
}
