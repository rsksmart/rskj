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

import com.google.common.annotations.VisibleForTesting;
import org.ethereum.net.rlpx.Node;

import javax.annotation.concurrent.Immutable;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Immutable
public final class SyncConfiguration {
    /** Legacy behaviour: one body request in flight per peer (one block per round trip). */
    private static final boolean DEFAULT_DERIVE_EMPTY_BODIES = true;
    private static final int DEFAULT_MAX_IN_FLIGHT_BODY_REQUESTS_PER_PEER = 1;
    /** Legacy behaviour: no outbound throttling. */
    private static final int DEFAULT_MAX_BODY_REQUESTS_PER_MINUTE_PER_PEER = 0;
    /** Legacy behaviour: one header chunk request at a time, to the selected peer only. */
    private static final int DEFAULT_MAX_CONCURRENT_HEADER_REQUESTS = 1;
    private static final int DEFAULT_MAX_HEADER_REQUESTS_PER_PEER = 1;
    /** Legacy behaviour: one skeleton per sync round. */
    private static final int DEFAULT_SKELETON_RANGE_MULTIPLIER = 1;

    @VisibleForTesting
    public static final SyncConfiguration DEFAULT = new SyncConfiguration(5, 60, 30, 5, 20, 192, 20, 10, 0, false, false, 0);

    @VisibleForTesting
    public static final SyncConfiguration IMMEDIATE_FOR_TESTING = new SyncConfiguration(1, 1, 3, 1, 5, 192, 20, 10, 0, false, false, 0);

    private final int expectedPeers;
    private final Duration timeoutWaitingPeers;
    private final Duration timeoutWaitingRequest;
    private final Duration expirationTimePeerStatus;
    private final int maxSkeletonChunks;
    private final int chunkSize;
    private final int longSyncLimit;
    private final int maxRequestedBodies;
    private final double topBest;
    private final boolean isServerSnapSyncEnabled;
    private final boolean isClientSnapSyncEnabled;

    private final int snapshotSyncLimit;
    private final Map<String, Node> nodeIdToSnapshotTrustedPeerMap;

    private final int maxInFlightBodyRequestsPerPeer;
    private final boolean deriveEmptyBodies;
    private final int maxBodyRequestsPerMinutePerPeer;
    private final int maxConcurrentHeaderRequests;
    private final int maxHeaderRequestsPerPeer;
    private final int skeletonRangeMultiplier;

    /**
     * @param expectedPeers            The expected number of peers we would want to start finding a connection point.
     * @param timeoutWaitingPeers      Timeout in minutes to start finding the connection point when we have at least one peer
     * @param timeoutWaitingRequest    Timeout in seconds to wait for syncing requests
     * @param expirationTimePeerStatus Expiration time in minutes for peer status
     * @param maxSkeletonChunks        Maximum amount of chunks included in a skeleton message
     * @param chunkSize                Amount of blocks contained in a chunk
     * @param maxRequestedBodies       Amount of bodies to request at the same time when synchronizing backwards.
     * @param longSyncLimit            Distance to the tip of the peer's blockchain to enable long synchronization.
     * @param topBest                  % of top best nodes that  will be considered for random selection.
     * @param isServerSnapSyncEnabled  Flag that indicates if server-side snap sync is enabled
     * @param isClientSnapSyncEnabled  Flag that indicates if client-side snap sync is enabled
     * @param snapshotSyncLimit        Distance to the tip of the peer's blockchain to enable snap synchronization.
     */
    public SyncConfiguration(
            int expectedPeers,
            int timeoutWaitingPeers,
            int timeoutWaitingRequest,
            int expirationTimePeerStatus,
            int maxSkeletonChunks,
            int chunkSize,
            int maxRequestedBodies,
            int longSyncLimit,
            double topBest,
            boolean isServerSnapSyncEnabled,
            boolean isClientSnapSyncEnabled,
            int snapshotSyncLimit) {
        this(expectedPeers,
                timeoutWaitingPeers,
                timeoutWaitingRequest,
                expirationTimePeerStatus,
                maxSkeletonChunks,
                chunkSize,
                maxRequestedBodies,
                longSyncLimit,
                topBest,
                isServerSnapSyncEnabled,
                isClientSnapSyncEnabled,
                snapshotSyncLimit,
                Collections.emptyList());
    }

    public SyncConfiguration(
            int expectedPeers,
            int timeoutWaitingPeers,
            int timeoutWaitingRequest,
            int expirationTimePeerStatus,
            int maxSkeletonChunks,
            int chunkSize,
            int maxRequestedBodies,
            int longSyncLimit,
            double topBest,
            boolean isServerSnapSyncEnabled,
            boolean isClientSnapSyncEnabled,
            int snapshotSyncLimit,
            List<Node> snapBootNodes) {
        this(expectedPeers, timeoutWaitingPeers, timeoutWaitingRequest, expirationTimePeerStatus,
                maxSkeletonChunks, chunkSize, maxRequestedBodies, longSyncLimit, topBest,
                isServerSnapSyncEnabled, isClientSnapSyncEnabled, snapshotSyncLimit, snapBootNodes,
                DEFAULT_MAX_IN_FLIGHT_BODY_REQUESTS_PER_PEER, DEFAULT_MAX_BODY_REQUESTS_PER_MINUTE_PER_PEER,
                DEFAULT_MAX_CONCURRENT_HEADER_REQUESTS, DEFAULT_MAX_HEADER_REQUESTS_PER_PEER,
                DEFAULT_SKELETON_RANGE_MULTIPLIER);
    }

    public SyncConfiguration(
            int expectedPeers,
            int timeoutWaitingPeers,
            int timeoutWaitingRequest,
            int expirationTimePeerStatus,
            int maxSkeletonChunks,
            int chunkSize,
            int maxRequestedBodies,
            int longSyncLimit,
            double topBest,
            boolean isServerSnapSyncEnabled,
            boolean isClientSnapSyncEnabled,
            int snapshotSyncLimit,
            List<Node> snapBootNodes,
            int maxInFlightBodyRequestsPerPeer,
            int maxBodyRequestsPerMinutePerPeer,
            int maxConcurrentHeaderRequests,
            int maxHeaderRequestsPerPeer,
            int skeletonRangeMultiplier) {
        this(expectedPeers, timeoutWaitingPeers, timeoutWaitingRequest, expirationTimePeerStatus,
                maxSkeletonChunks, chunkSize, maxRequestedBodies, longSyncLimit, topBest,
                isServerSnapSyncEnabled, isClientSnapSyncEnabled, snapshotSyncLimit, snapBootNodes,
                maxInFlightBodyRequestsPerPeer, maxBodyRequestsPerMinutePerPeer,
                maxConcurrentHeaderRequests, maxHeaderRequestsPerPeer, skeletonRangeMultiplier,
                DEFAULT_DERIVE_EMPTY_BODIES);
    }

    public SyncConfiguration(
            int expectedPeers,
            int timeoutWaitingPeers,
            int timeoutWaitingRequest,
            int expirationTimePeerStatus,
            int maxSkeletonChunks,
            int chunkSize,
            int maxRequestedBodies,
            int longSyncLimit,
            double topBest,
            boolean isServerSnapSyncEnabled,
            boolean isClientSnapSyncEnabled,
            int snapshotSyncLimit,
            List<Node> snapBootNodes,
            int maxInFlightBodyRequestsPerPeer,
            int maxBodyRequestsPerMinutePerPeer,
            int maxConcurrentHeaderRequests,
            int maxHeaderRequestsPerPeer,
            int skeletonRangeMultiplier,
            boolean deriveEmptyBodies) {
        this.deriveEmptyBodies = deriveEmptyBodies;
        this.skeletonRangeMultiplier = skeletonRangeMultiplier;
        this.maxInFlightBodyRequestsPerPeer = maxInFlightBodyRequestsPerPeer;
        this.maxBodyRequestsPerMinutePerPeer = maxBodyRequestsPerMinutePerPeer;
        this.maxConcurrentHeaderRequests = maxConcurrentHeaderRequests;
        this.maxHeaderRequestsPerPeer = maxHeaderRequestsPerPeer;
        this.expectedPeers = expectedPeers;
        this.timeoutWaitingPeers = Duration.ofSeconds(timeoutWaitingPeers);
        this.timeoutWaitingRequest = Duration.ofSeconds(timeoutWaitingRequest);
        this.expirationTimePeerStatus = Duration.ofMinutes(expirationTimePeerStatus);
        this.maxSkeletonChunks = maxSkeletonChunks;
        this.chunkSize = chunkSize;
        this.maxRequestedBodies = maxRequestedBodies;
        this.longSyncLimit = longSyncLimit;
        this.topBest = topBest;
        this.isServerSnapSyncEnabled = isServerSnapSyncEnabled;
        this.isClientSnapSyncEnabled = isClientSnapSyncEnabled;
        this.snapshotSyncLimit = snapshotSyncLimit;

        List<Node> snapBootNodesList = snapBootNodes != null ? snapBootNodes : Collections.emptyList();

        nodeIdToSnapshotTrustedPeerMap = Collections.unmodifiableMap(snapBootNodesList.stream()
                .collect(Collectors.toMap(peer -> peer.getId().toString(), peer -> peer)));
    }

    public int getExpectedPeers() {
        return expectedPeers;
    }

    public int getMaxSkeletonChunks() {
        return maxSkeletonChunks;
    }

    public Duration getTimeoutWaitingPeers() {
        return timeoutWaitingPeers;
    }

    public Duration getTimeoutWaitingRequest() {
        return  timeoutWaitingRequest;
    }

    public Duration getExpirationTimePeerStatus() {
        return expirationTimePeerStatus;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public int getMaxRequestedBodies() {
        return maxRequestedBodies;
    }

    public int getLongSyncLimit() {
        return longSyncLimit;
    }

    public double getTopBest() {
        return topBest;
    }

    public boolean isServerSnapSyncEnabled() {
        return isServerSnapSyncEnabled;
    }

    public boolean isClientSnapSyncEnabled() {
        return isClientSnapSyncEnabled;
    }

    public int getSnapshotSyncLimit() {
        return snapshotSyncLimit;
    }

    /**
     * When true, a block whose body follows from its header - no uncles, REMASC only - is built
     * locally instead of being requested from a peer. Set to false to restore the stock behaviour of
     * requesting every body.
     */
    public boolean isDeriveEmptyBodiesEnabled() {
        return deriveEmptyBodies;
    }

    public int getMaxInFlightBodyRequestsPerPeer() {
        return maxInFlightBodyRequestsPerPeer;
    }

    public int getMaxBodyRequestsPerMinutePerPeer() {
        return maxBodyRequestsPerMinutePerPeer;
    }

    public int getSkeletonRangeMultiplier() {
        return skeletonRangeMultiplier;
    }

    public int getMaxConcurrentHeaderRequests() {
        return maxConcurrentHeaderRequests;
    }

    public int getMaxHeaderRequestsPerPeer() {
        return maxHeaderRequestsPerPeer;
    }

    public Map<String, Node> getNodeIdToSnapshotTrustedPeerMap() {
        return nodeIdToSnapshotTrustedPeerMap;
    }
}
