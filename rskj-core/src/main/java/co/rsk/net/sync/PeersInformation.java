package co.rsk.net.sync;

import co.rsk.net.MessageChannel;
import co.rsk.net.NodeID;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * This is mostly a workaround because SyncProcessor needs to access MessageChannel instances.
 * TODO(mc) remove this after the logical node abstraction is created, since it will wrap
 *     things such as the underlying communication channel.
 */
public class PeersInformation {
    private final SyncConfiguration syncCofiguration;
    private Map<NodeID, SyncPeerStatus> peerStatuses = new HashMap<>();

    public PeersInformation(SyncConfiguration syncConfiguration){
        this.syncCofiguration = syncConfiguration;
    }

    public int count() {
        return peerStatuses.size();
    }

    public int countIf(Predicate<SyncPeerStatus> predicate) {
        long count = peerStatuses.values().stream()
                .filter(predicate)
                .count();
        return Math.toIntExact(count);
    }

    public SyncPeerStatus getOrRegisterPeer(MessageChannel messageChannel) {
        SyncPeerStatus peerStatus = this.peerStatuses.get(messageChannel.getPeerNodeID());

        if (peerStatus != null && !peerStatus.isExpired(syncCofiguration.getExpirationTimePeerStatus()))
            return peerStatus;

        return this.registerPeer(messageChannel);
    }

    public SyncPeerStatus getPeer(NodeID nodeID) {
        // TODO(mc) check expiration
        return this.peerStatuses.get(nodeID);
    }

    public Optional<MessageChannel> getBestPeer() {
        return peerStatuses.entrySet().stream()
                .filter(e -> !e.getValue().isExpired(syncCofiguration.getExpirationTimePeerStatus()))
                .max(this::bestPeerComparator)
                .map(Map.Entry::getValue)
                .map(SyncPeerStatus::getMessageChannel);
    }

    public Set<NodeID> knownNodeIds() {
        return peerStatuses.keySet();
    }

    private int bestPeerComparator(Map.Entry<NodeID, SyncPeerStatus> left, Map.Entry<NodeID, SyncPeerStatus> right) {
         return Long.compare(
                 left.getValue().getStatus().getBestBlockNumber(),
                 right.getValue().getStatus().getBestBlockNumber());
    }

    public SyncPeerStatus registerPeer(MessageChannel messageChannel) {
        SyncPeerStatus peerStatus = new SyncPeerStatus(messageChannel);
        peerStatuses.put(messageChannel.getPeerNodeID(), peerStatus);
        return peerStatus;
    }
}
