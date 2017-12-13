package co.rsk.net.sync;

import co.rsk.net.MessageChannel;
import co.rsk.net.NodeID;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This is mostly a workaround because SyncProcessor needs to access MessageChannel instances.
 * TODO(mc) remove this after the logical node abstraction is created, since it will wrap
 *     things such as the underlying communication channel.
 */
public class PeersInformation {
    private final SyncConfiguration syncConfiguration;
    private final SyncInformation syncInformation;
    private Map<NodeID, SyncPeerStatus> peerStatuses = new HashMap<>();

    public PeersInformation(SyncConfiguration syncConfiguration, SyncInformation syncInformation){
        this.syncConfiguration = syncConfiguration;
        this.syncInformation = syncInformation;
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

        if (peerStatus != null && peerNotExpired(peerStatus)) {
            return peerStatus;
        }

        return this.registerPeer(messageChannel);
    }

    public SyncPeerStatus getPeer(NodeID nodeID) {
        return this.peerStatuses.get(nodeID);
    }

    public Optional<MessageChannel> getBestPeer() {
        return getCandidates()
                .max(this::peerComparator)
                .map(Map.Entry::getValue)
                .map(SyncPeerStatus::getMessageChannel);
    }

    private int peerComparator(Map.Entry<NodeID, SyncPeerStatus> entry, Map.Entry<NodeID, SyncPeerStatus> other) {
        int score = syncInformation.getScore(entry.getKey());
        int otherScore = syncInformation.getScore(other.getKey());
        if (score >= 0 && otherScore >= 0) {
            return entry.getValue().peerTotalDifficultyComparator(other.getValue());
        } else if (score < otherScore){
            return -1;
        } else if (score > otherScore){
            return 1;
        }
        return entry.getValue().peerTotalDifficultyComparator(other.getValue());
    }

    private Stream<Map.Entry<NodeID,SyncPeerStatus>> getCandidates(){
        return peerStatuses.entrySet().stream()
                .filter(e -> peerNotExpired(e.getValue()))
                .filter(e -> syncInformation.hasGoodReputation(e.getKey()))
                .filter(e -> syncInformation.hasLowerDifficulty(e.getKey()));
    }

    public List<MessageChannel> getPeerCandidates() {
        return getCandidates()
                .map(e -> e.getValue().getMessageChannel())
                .collect(Collectors.toList());
    }

    public Set<NodeID> knownNodeIds() {
        return peerStatuses.keySet();
    }

    public SyncPeerStatus registerPeer(MessageChannel messageChannel) {
        SyncPeerStatus peerStatus = new SyncPeerStatus(messageChannel);
        peerStatuses.put(messageChannel.getPeerNodeID(), peerStatus);
        return peerStatus;
    }

    public void cleanExpired() {
        peerStatuses = peerStatuses.entrySet().stream()
                .filter(e -> peerNotExpired(e.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private boolean peerNotExpired(SyncPeerStatus peer) {
        return !peer.isExpired(syncConfiguration.getExpirationTimePeerStatus());
    }
}
