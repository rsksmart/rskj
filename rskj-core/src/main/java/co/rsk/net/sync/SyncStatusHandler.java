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
    private final PeersInformation peerStatuses;
    private final SyncConfiguration syncConfiguration;
    private Set<Runnable> finishedWaitingForPeersCallbacks = new HashSet<>();
    private SyncStatus status;

    public SyncStatusHandler(PeersInformation peerStatuses, SyncConfiguration syncConfiguration) {
        this.peerStatuses = peerStatuses;
        this.syncConfiguration = syncConfiguration;
        status = new DecidingSyncStatus(this.syncConfiguration);
    }

    public SyncStatuses getStatus() {
        return status.getStatus();
    }

    public void newPeerStatus(NodeID peerID, Status status) {
        this.peerStatuses.getOrRegisterPeer(peerID).setStatus(status);
        this.status = this.status.newPeerStatus(peerID, status, finishedWaitingForPeersCallbacks);
    }

    public void tick(Duration duration) {
        status = status.tick(duration);
    }

    /**
     * This event is raised when we transition from Waiting For Peers to Finding Connection Point.
     * @param callback a callback to be executed.
     */
    public void onFinishedWaitingForPeers(Runnable callback) {
        this.finishedWaitingForPeersCallbacks.add(callback);
    }

    public void finishedDownloadingBlocks() {
        status = new DecidingSyncStatus(this.syncConfiguration);
    }
}
