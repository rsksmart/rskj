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

import co.rsk.net.NodeID;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.net.rlpx.Node;

import javax.annotation.concurrent.Immutable;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Immutable
public final class SyncConfiguration {
    @VisibleForTesting
    public static final SyncConfiguration DEFAULT = new SyncConfiguration(5, 1, 30, 5, 20, 192, 20, 10, 0, false, false, 0);

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
    private final Set<NodeID> snapBootNodeIds;

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
        this.expectedPeers = expectedPeers;
        this.timeoutWaitingPeers = Duration.ofMinutes(timeoutWaitingPeers);
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

        snapBootNodeIds = snapBootNodesList.stream().map(Node::getId).collect(Collectors.toSet());
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
        return timeoutWaitingRequest;
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

    public Set<NodeID> getSnapBootNodeIds() {
        return snapBootNodeIds;
    }
}
