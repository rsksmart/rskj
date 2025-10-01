/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package co.rsk.net.sync;

import co.rsk.core.BlockDifficulty;
import co.rsk.core.bc.BlockChainStatus;
import co.rsk.net.NodeID;
import co.rsk.net.Peer;
import co.rsk.net.Status;
import co.rsk.scoring.EventType;
import co.rsk.scoring.PeerScoringManager;
import co.rsk.util.MaxSizeHashMap;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Blockchain;
import org.ethereum.net.server.ChannelManager;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This is mostly a workaround because SyncProcessor needs to access Peer instances.
 * TODO(mc) remove this after the logical node abstraction is created, since it will wrap
 *     things such as the underlying communication channel.
 */
public class PeersInformation implements SnapshotPeersInformation {

    private static final int TIME_LIMIT_FAILURE_RECORD = 600;
    private static final int MAX_SIZE_FAILURE_RECORDS = 10;
    private final ChannelManager channelManager;
    private final SyncConfiguration syncConfiguration;
    private final Blockchain blockchain;
    private final Map<NodeID, Instant> failedPeers;
    private final PeerScoringManager peerScoringManager;
    private final Comparator<Map.Entry<Peer, SyncPeerStatus>> peerComparator;
    private final double topBest;
    private final Random random;
    private Map<Peer, SyncPeerStatus> peerStatuses = new HashMap<>();

    public PeersInformation(ChannelManager channelManager,
                            SyncConfiguration syncConfiguration,
                            Blockchain blockchain,
                            PeerScoringManager peerScoringManager) {
        this(channelManager, syncConfiguration, blockchain, peerScoringManager, new SecureRandom());
    }

    @VisibleForTesting
    PeersInformation(ChannelManager channelManager,
                     SyncConfiguration syncConfiguration,
                     Blockchain blockchain,
                     PeerScoringManager peerScoringManager,
                     Random random) {
        this.channelManager = channelManager;
        this.syncConfiguration = syncConfiguration;
        this.blockchain = blockchain;
        this.failedPeers = new MaxSizeHashMap<>(MAX_SIZE_FAILURE_RECORDS, true);
        this.peerScoringManager = peerScoringManager;
        this.peerComparator = ((Comparator<Map.Entry<Peer, SyncPeerStatus>>) this::comparePeerFailInstant)
                // TODO reenable when unprocessable blocks stop being marked as invalid blocks
                // .thenComparing(this::comparePeerScoring)
                .thenComparing(this::comparePeerTotalDifficulty);
        this.topBest = syncConfiguration.getTopBest();
        this.random = random;
    }

    public void reportEventToPeerScoring(Peer peer, EventType eventType, String message, Object... arguments) {
        this.peerScoringManager.recordEvent(peer.getPeerNodeID(), peer.getAddress(), eventType, message, arguments);
    }

    public void processSyncingError(Peer peer, EventType eventType, String message, Object... arguments) {
        failedPeers.put(peer.getPeerNodeID(), Instant.now());
        reportEventToPeerScoring(peer, eventType, message, arguments);
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

    public SyncPeerStatus getOrRegisterPeer(Peer peer) {
        SyncPeerStatus peerStatus = this.peerStatuses.get(peer);

        if (peerStatus != null && peerNotExpired(peerStatus)) {
            return peerStatus;
        }

        return this.registerPeer(peer);
    }

    public SyncPeerStatus getPeer(Peer peer) {
        return this.peerStatuses.get(peer);
    }

    private Optional<Peer> getBestPeer(Stream<Map.Entry<Peer, SyncPeerStatus>> bestCandidatesStream) {
        if (topBest > 0.0D) {
            List<Map.Entry<Peer, SyncPeerStatus>> entries = bestCandidatesStream
                    .sorted(this.peerComparator.reversed())
                    .collect(Collectors.toList());

            if (entries.isEmpty()) {
                return Optional.empty();
            }

            int numberOfPeersToConsider = (int) Math.round(((double) entries.size()) * (topBest / 100.0D));
            numberOfPeersToConsider = Math.max(1, numberOfPeersToConsider);
            numberOfPeersToConsider = Math.min(entries.size(), numberOfPeersToConsider);

            List<Map.Entry<Peer, SyncPeerStatus>> entriesToConsider = entries.subList(0, numberOfPeersToConsider);

            int randomIndex = random.nextInt(entriesToConsider.size());

            return Optional.of(entriesToConsider.get(randomIndex).getKey());
        }

        return bestCandidatesStream
                .max(this.peerComparator)
                .map(Map.Entry::getKey);
    }

    public Optional<Peer> getBestPeer() {
        return getBestPeer(getBestCandidatesStream());
    }

    @Override
    public Optional<Peer> getBestSnapPeer() {
        return getBestPeer(
                getBestCandidatesStream()
                        .filter(this::isSnapPeerCandidateOrCapable)
        );
    }

    public Optional<Peer> getBestOrEqualPeer() {
        return getTrustedPeers()
                .filter(e -> isMyDifficultyLowerThan(e.getKey(), false))
                .max(this.peerComparator)
                .map(Map.Entry::getKey);
    }

    private Stream<Map.Entry<Peer, SyncPeerStatus>> getBestCandidatesStream() {
        return getTrustedPeers().filter(e -> isMyDifficultyLowerThan(e.getKey(), true));
    }

    private Stream<Map.Entry<Peer, SyncPeerStatus>> getTrustedPeers() {
        Collection<Peer> activeNodes = channelManager.getActivePeers();

        return peerStatuses.entrySet().stream()
                .filter(e -> peerNotExpired(e.getValue()))
                .filter(e -> activeNodes.contains(e.getKey()))
                .filter(e -> peerScoringManager.hasGoodReputation(e.getKey().getPeerNodeID()));
    }

    private boolean isMyDifficultyLowerThan(Peer peer, boolean strictLower) {
        Status peerStatus = getPeer(peer).getStatus();
        if (peerStatus == null) {
            return false;
        }

        BlockChainStatus myStatus = blockchain.getStatus();
        return strictLower ? myStatus.hasLowerTotalDifficultyThan(peerStatus) : myStatus.hasLowerOrSameTotalDifficultyThan(peerStatus);
    }

    public List<Peer> getBestPeerCandidates() {
        return getBestCandidatesStream()
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    @Override
    public List<Peer> getBestSnapPeerCandidates() {
        return getBestCandidatesStream()
                .filter(entry -> entry.getKey().isSnapCapable())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Peer> getBestPeer(Set<NodeID> exclude) {
        return getBestPeer(getBestCandidatesStream().filter(p -> !exclude.contains(p.getKey().getPeerNodeID())));
    }

    @Override
    public Optional<Peer> getBestSnapPeer(Set<NodeID> exclude) {
        return getBestPeer(
                getBestCandidatesStream()
                        .filter(this::isSnapPeerCandidateOrCapable)
                        .filter(p -> !exclude.contains(p.getKey().getPeerNodeID()))
        );
    }

    public Set<NodeID> knownNodeIds() {
        return peerStatuses.keySet().stream()
                .map(Peer::getPeerNodeID)
                .collect(Collectors.toSet());
    }

    public SyncPeerStatus registerPeer(Peer peer) {
        SyncPeerStatus peerStatus = new SyncPeerStatus();
        peerStatuses.put(peer, peerStatus);
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
            Map.Entry<Peer, SyncPeerStatus> entry,
            Map.Entry<Peer, SyncPeerStatus> other) {
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

    private int getScore(NodeID peerId) {
        return peerScoringManager.getPeerScoring(peerId).getScore();
    }

    private int comparePeerTotalDifficulty(
            Map.Entry<Peer, SyncPeerStatus> entry,
            Map.Entry<Peer, SyncPeerStatus> other) {
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

    private Instant getFailInstant(Peer peer) {
        Instant instant = failedPeers.get(peer.getPeerNodeID());
        if (instant != null) {
            return instant;
        }
        return Instant.EPOCH;
    }

    public void clearOldFailedPeers() {
        failedPeers.values().removeIf(Instant.now().minusSeconds(TIME_LIMIT_FAILURE_RECORD)::isAfter);
    }

    private boolean isSnapPeerCandidate(Map.Entry<Peer, SyncPeerStatus> entry) {
        return syncConfiguration.getNodeIdToSnapshotTrustedPeerMap().containsKey(entry.getKey().getPeerNodeID().toString());
    }

    private boolean isSnapPeerCandidateOrCapable(Map.Entry<Peer, SyncPeerStatus> entry) {
        return isSnapPeerCandidate(entry) || entry.getKey().isSnapCapable();
    }
}
