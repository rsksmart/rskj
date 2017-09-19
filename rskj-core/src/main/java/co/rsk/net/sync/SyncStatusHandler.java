package co.rsk.net.sync;

import co.rsk.net.MessageSender;
import co.rsk.net.Status;
import co.rsk.net.SyncPeerStatus;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

public class SyncStatusHandler {
    private Set<Runnable> finishedWaitingForPeersCallbacks = new HashSet<>();
    private SyncStatus status;
    private PeersInformation peerStatuses;

    public SyncStatusHandler(PeersInformation peerStatuses) {
        this.peerStatuses = peerStatuses;
        status = new WaitingForPeersSyncStatus();
    }

    public SyncStatuses getStatus() {
        return status.getStatus();
    }

    public void newPeerStatus(MessageSender sender, Status status) {
        boolean knownSender = this.peerStatuses.isKnownSender(sender);
        SyncPeerStatus peerStatus = peerStatuses.getOrCreateWithSender(sender);
        peerStatus.setStatus(status);
        if (!knownSender) {
            newPeerFound(sender);
        }

//        // TODO(mc) this should be part of WaitingForPeersSyncStatus, but the problem is that
//        // we want to update the current status first.
//        SyncStatus oldStatus = this.status;
//        if (this.status.getStatus() != oldStatus.getStatus() && oldStatus.getStatus() == SyncStatuses.WAITING_FOR_PEERS) {
//            finishedWaitingForPeersCallbacks.forEach(Runnable::run);
//        }
    }

    public void newPeerFound(MessageSender sender) {
        // TODO(mc) this should be part of WaitingForPeersSyncStatus, but the problem is that
        // we want to update the current status first.
        SyncStatus oldStatus = status;
        status = status.newPeerFound();
        finishedWaitingForPeersCallbacks.forEach(Runnable::run);
//        if (oldStatus.getStatus() == SyncStatuses.WAITING_FOR_PEERS
//                && status.getStatus() == SyncStatuses.FINDING_CONNECTION_POINT) {
//            finishedWaitingForPeersCallbacks.forEach(Runnable::run);
//        }
    }

    public void tick(Duration duration) {
        status = status.tick(duration);
    }

    public void onFinishedWaitingForPeers(Runnable callback) {
        this.finishedWaitingForPeersCallbacks.add(callback);
    }
}
