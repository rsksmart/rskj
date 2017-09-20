package co.rsk.net.sync;

import co.rsk.net.NodeID;
import co.rsk.net.SyncPeerStatus;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * This is mostly a workaround because SyncProcessor needs to access MessageChannel instances.
 * TODO(mc) remove this after the logical node abstraction is created, since it will wrap
 *     things such as the underlying communication channel.
 */
public class PeersInformation {
    private Map<NodeID, SyncPeerStatus> peerStatuses = new HashMap<>();
    private static final Duration peerTimeout = Duration.ofMinutes(10);

    public int count() {
        return peerStatuses.size();
    }

    public int countSyncing() {
        long count = peerStatuses.values().stream()
                .filter(SyncPeerStatus::isSyncing)
                .count();
        return Math.toIntExact(count);
    }

    public SyncPeerStatus getOrRegisterPeer(NodeID nodeID) {
        SyncPeerStatus peerStatus = this.peerStatuses.get(nodeID);

        if (peerStatus != null && !peerStatus.isExpired(peerTimeout))
            return peerStatus;

        return this.registerPeer(nodeID);
    }

    public SyncPeerStatus getPeer(NodeID nodeID) {
        // TODO(mc) check expiration
        return this.peerStatuses.get(nodeID);
    }

    public Optional<NodeID> getBestPeerID() {
        return peerStatuses.entrySet().stream()
                .filter(e -> !e.getValue().isExpired(peerTimeout))
                .max(this::bestPeerComparator)
                .map(Map.Entry::getKey);
    }

    public Set<NodeID> knownNodeIds() {
        return peerStatuses.keySet();
    }

    private int bestPeerComparator(Map.Entry<NodeID, SyncPeerStatus> left, Map.Entry<NodeID, SyncPeerStatus> right) {
        // TODO(mc) check expiration
         return Long.compare(
                 left.getValue().getStatus().getBestBlockNumber(),
                 right.getValue().getStatus().getBestBlockNumber());
    }

    public SyncPeerStatus registerPeer(NodeID nodeID) {
        SyncPeerStatus peerStatus = new SyncPeerStatus();
        peerStatuses.put(nodeID, peerStatus);
        return peerStatus;
    }

    public boolean isKnownPeer(NodeID peerID) {
        return peerStatuses.containsKey(peerID);
    }
}
