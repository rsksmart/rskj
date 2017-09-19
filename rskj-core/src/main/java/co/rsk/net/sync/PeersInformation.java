package co.rsk.net.sync;

import co.rsk.net.MessageSender;
import co.rsk.net.NodeID;
import co.rsk.net.SyncPeerStatus;

import java.time.Duration;
import java.util.*;

/**
 * This is mostly a workaround because SyncProcessor needs to access MessageSender instances.
 * TODO(mc) remove this after the logical node abstraction is created, since it will wrap
 *     things such as the underlying communication channel.
 */
public class PeersInformation {
    private Map<NodeID, SyncPeerStatus> peerStatuses = new HashMap<>();
    private Map<NodeID, MessageSender> senders = new HashMap<>();
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

    public SyncPeerStatus getOrCreate(NodeID nodeID) {
        SyncPeerStatus peerStatus = this.peerStatuses.get(nodeID);

        if (peerStatus != null && !peerStatus.isExpired(peerTimeout))
            return peerStatus;

        return this.createPeerStatus(nodeID);
    }

    public SyncPeerStatus getOrCreateWithSender(MessageSender sender) {
        SyncPeerStatus peerStatus = getOrCreate(sender.getNodeID());
        senders.put(sender.getNodeID(), sender);
        return peerStatus;
    }

    public Optional<MessageSender> getBestPeerSender() {
        // TODO(mc) check expiration
        return senders.entrySet().stream()
                .max(this::bestPeerComparator)
                .map(Map.Entry::getValue);
    }

    public Set<NodeID> knownNodeIds() {
        return peerStatuses.keySet();
    }

    private int bestPeerComparator(Map.Entry<NodeID, MessageSender> left, Map.Entry<NodeID, MessageSender> right) {
        // TODO(mc) check expiration
         return Long.compare(
                 peerStatuses.get(left.getKey()).getStatus().getBestBlockNumber(),
                 peerStatuses.get(right.getKey()).getStatus().getBestBlockNumber());
    }

    private SyncPeerStatus createPeerStatus(NodeID nodeID) {
        SyncPeerStatus peerStatus = new SyncPeerStatus();
        peerStatuses.put(nodeID, peerStatus);
        return peerStatus;
    }

    public boolean isKnownSender(MessageSender sender) {
        return senders.containsKey(sender.getNodeID());
    }
}
