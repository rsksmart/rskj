package co.rsk.net.sync;

import co.rsk.net.NodeID;
import org.ethereum.net.server.Channel;
import org.ethereum.net.server.ChannelManager;

import java.time.Instant;
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
    private final ChannelManager channelManager;
    private final SyncInformation syncInformation;
    private final SyncConfiguration syncConfiguration;
    private Map<NodeID, SyncPeerStatus> peerStatuses = new HashMap<>();

    public PeersInformation(SyncInformation syncInformation, ChannelManager channelManager, SyncConfiguration syncConfiguration){
        this.channelManager = channelManager;
        this.syncInformation = syncInformation;
        this.syncConfiguration = syncConfiguration;
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

    public SyncPeerStatus getOrRegisterPeer(NodeID nodeID) {
        SyncPeerStatus peerStatus = this.peerStatuses.get(nodeID);

        if (peerStatus != null && peerNotExpired(peerStatus)) {
            return peerStatus;
        }

        return this.registerPeer(nodeID);
    }

    public SyncPeerStatus getPeer(NodeID nodeID) {
        return this.peerStatuses.get(nodeID);
    }

    public Optional<NodeID> getBestPeer() {
        return getCandidatesStream()
                .max(this::peerComparator)
                .map(Map.Entry::getKey);
    }

    private int peerComparator(Map.Entry<NodeID, SyncPeerStatus> entry, Map.Entry<NodeID, SyncPeerStatus> other) {
        int score = syncInformation.getScore(entry.getKey());
        int otherScore = syncInformation.getScore(other.getKey());
        Instant failInstant = syncInformation.getFailInstant(entry.getKey());
        Instant otherFailInstant = syncInformation.getFailInstant(other.getKey());
        if (failInstant.isAfter(otherFailInstant) ){
            return -1;
        } else if (failInstant.isBefore(otherFailInstant)){
            return 1;
        } else if (score >= 0 && otherScore >= 0) {
            return entry.getValue().peerTotalDifficultyComparator(other.getValue());
        } else if (score < otherScore){
            return -1;
        } else if (score > otherScore){
            return 1;
        }
        return entry.getValue().peerTotalDifficultyComparator(other.getValue());
    }

    private Stream<Map.Entry<NodeID, SyncPeerStatus>> getCandidatesStream(){
        Set<NodeID> activeNodes = channelManager.getActivePeers().stream()
                .map(Channel::getNodeId).collect(Collectors.toSet());

        return peerStatuses.entrySet().stream()
                .filter(e -> peerNotExpired(e.getValue()))
                .filter(e -> activeNodes.contains(e.getKey()))
                .filter(e -> syncInformation.hasGoodReputation(e.getKey()))
                .filter(e -> syncInformation.hasLowerDifficulty(e.getKey()));
    }

    public List<NodeID> getPeerCandidates() {
        return getCandidatesStream().map(Map.Entry::getKey).collect(Collectors.toList());
    }

    public Set<NodeID> knownNodeIds() {
        return peerStatuses.keySet();
    }

    public SyncPeerStatus registerPeer(NodeID nodeID) {
        SyncPeerStatus peerStatus = new SyncPeerStatus();
        peerStatuses.put(nodeID, peerStatus);
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
