package co.rsk.net.sync;

import co.rsk.core.BlockDifficulty;
import co.rsk.core.bc.BlockChainStatus;
import co.rsk.net.NodeID;
import co.rsk.net.Status;
import co.rsk.net.SyncProcessor;
import co.rsk.scoring.EventType;
import co.rsk.scoring.PeerScoringManager;
import org.ethereum.core.Blockchain;
import org.ethereum.net.server.Channel;
import org.ethereum.net.server.ChannelManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger logger = LoggerFactory.getLogger(PeersInformation.class);

    private final ChannelManager channelManager;
    private final SyncConfiguration syncConfiguration;
    private final Blockchain blockchain;
    private final Map<NodeID, Instant> failedPeers;
    private final PeerScoringManager peerScoringManager;
    private final Comparator<Map.Entry<NodeID, SyncPeerStatus>> peerComparator;
    private Map<NodeID, SyncPeerStatus> peerStatuses = new HashMap<>();

    public PeersInformation(ChannelManager channelManager,
                            SyncConfiguration syncConfiguration,
                            Blockchain blockchain,
                            Map<NodeID, Instant> failedPeers,
                            PeerScoringManager peerScoringManager){
        this.channelManager = channelManager;
        this.syncConfiguration = syncConfiguration;
        this.blockchain = blockchain;
        this.failedPeers = failedPeers;
        this.peerScoringManager = peerScoringManager;
        this.peerComparator = ((Comparator<Map.Entry<NodeID, SyncPeerStatus>>) this::comparePeerFailInstant)
                // TODO reenable when unprocessable blocks stop being marked as invalid blocks
//                .thenComparing(this::comparePeerScoring)
                .thenComparing(this::comparePeerTotalDifficulty);
    }

    public void reportEvent(String message, EventType eventType, NodeID peerId, Object... arguments) {
        logger.trace(message, arguments);
        peerScoringManager.recordEvent(peerId, null, eventType);
    }

    public int getScore(NodeID peerId) {
        return peerScoringManager.getPeerScoring(peerId).getScore();
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
                .max(this.peerComparator)
                .map(Map.Entry::getKey);
    }

    private Stream<Map.Entry<NodeID, SyncPeerStatus>> getCandidatesStream(){
        Set<NodeID> activeNodes = channelManager.getActivePeers().stream()
                .map(Channel::getNodeId).collect(Collectors.toSet());

        return peerStatuses.entrySet().stream()
                .filter(e -> peerNotExpired(e.getValue()))
                .filter(e -> activeNodes.contains(e.getKey()))
                .filter(e -> peerScoringManager.hasGoodReputation(e.getKey()))
                .filter(e -> hasLowerDifficulty(e.getKey()));
    }

    private boolean hasLowerDifficulty(NodeID key) {
        Status status = getPeer(key).getStatus();
        if (status == null) {
            return false;
        }

        boolean hasTotalDifficulty = status.getTotalDifficulty() != null;
        BlockChainStatus nodeStatus = blockchain.getStatus();
        // this works only for testing purposes, real status without difficulty don't reach this far
        return  (hasTotalDifficulty && nodeStatus.hasLowerTotalDifficultyThan(status)) ||
                (!hasTotalDifficulty && nodeStatus.getBestBlockNumber() < status.getBestBlockNumber());
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

    private int comparePeerFailInstant(
            Map.Entry<NodeID, SyncPeerStatus> entry,
            Map.Entry<NodeID, SyncPeerStatus> other) {
        Instant failInstant = getFailInstant(entry.getKey());
        Instant otherFailInstant = getFailInstant(other.getKey());
        // note that this is in inverse order
        return otherFailInstant.compareTo(failInstant);
    }

    private int comparePeerScoring(
            Map.Entry<NodeID, SyncPeerStatus> entry,
            Map.Entry<NodeID, SyncPeerStatus> other) {
        int score = getScore(entry.getKey());
        int scoreOther = getScore(other.getKey());
        // Treats all non-negative scores the same for calculating the best peer
        if (score >= 0 && scoreOther >= 0) {
            return 0;
        }

        return Integer.compare(score, scoreOther);
    }

    private int comparePeerTotalDifficulty(
            Map.Entry<NodeID, SyncPeerStatus> entry,
            Map.Entry<NodeID, SyncPeerStatus> other) {
        BlockDifficulty ttd = entry.getValue().getStatus().getTotalDifficulty();
        BlockDifficulty otd = other.getValue().getStatus().getTotalDifficulty();

        // status messages from outdated nodes might have null difficulties
        if (ttd == null && otd == null) {
            return 0;
        }

        if (ttd == null) {
            return -1;
        }

        if (otd == null) {
            return 1;
        }

        return ttd.compareTo(otd);
    }

    private Instant getFailInstant(NodeID peerId) {
        Instant instant = failedPeers.get(peerId);
        if (instant != null){
            return instant;
        }
        return Instant.EPOCH;
    }


}
