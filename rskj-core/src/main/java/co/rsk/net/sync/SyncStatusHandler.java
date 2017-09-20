package co.rsk.net.sync;

import co.rsk.net.NodeID;
import co.rsk.net.Status;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/**
 * This only runs inside a SyncProcessor instance.
 * Messages are synchronized by NodeMessageHandler, therefore this code effectively runs in one thread and we don't need syncing.
 */
public class SyncStatusHandler {
    private Set<Runnable> finishedWaitingForPeersCallbacks = new HashSet<>();
    private SyncStatus status;

    public SyncStatusHandler(PeersInformation peerStatuses) {
        status = new WaitingForPeersSyncStatus(peerStatuses);
    }

    public SyncStatuses getStatus() {
        return status.getStatus();
    }

    public void newPeerStatus(NodeID peerID, Status status) {
        this.status = this.status.newPeerStatus(peerID, status, finishedWaitingForPeersCallbacks);
    }

    public void tick(Duration duration) {
        status = status.tick(duration);
    }

    public void onFinishedWaitingForPeers(Runnable callback) {
        this.finishedWaitingForPeersCallbacks.add(callback);
    }
}
