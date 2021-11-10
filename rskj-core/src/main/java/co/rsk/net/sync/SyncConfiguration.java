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

import javax.annotation.concurrent.Immutable;
import java.time.Duration;

@Immutable
public final class SyncConfiguration {
    public static final SyncConfiguration DEFAULT = new SyncConfiguration(5, 60, 30, 5, 20, 192, 20, 10);

    @VisibleForTesting
    public static final SyncConfiguration IMMEDIATE_FOR_TESTING = new SyncConfiguration(1, 1, 3, 1, 5, 192, 20, 10);

    private final int expectedPeers;
    private final Duration timeoutWaitingPeers;
    private final Duration timeoutWaitingRequest;
    private final Duration expirationTimePeerStatus;
    private final int maxSkeletonChunks;
    private final int chunkSize;
    private final int longSyncLimit;
    private final int maxRequestedBodies;

    /**
     * @param expectedPeers The expected number of peers we would want to start finding a connection point.
     * @param timeoutWaitingPeers Timeout in minutes to start finding the connection point when we have at least one peer
     * @param timeoutWaitingRequest Timeout in seconds to wait for syncing requests
     * @param expirationTimePeerStatus Expiration time in minutes for peer status
     * @param maxSkeletonChunks Maximum amount of chunks included in a skeleton message
     * @param chunkSize Amount of blocks contained in a chunk
     * @param maxRequestedBodies Amount of bodies to request at the same time when synchronizing backwards.
     * @param longSyncLimit Distance to the tip of the peer's blockchain to enable long synchronization.
     */
    public SyncConfiguration(
            int expectedPeers,
            int timeoutWaitingPeers,
            int timeoutWaitingRequest,
            int expirationTimePeerStatus,
            int maxSkeletonChunks,
            int chunkSize,
            int maxRequestedBodies,
            int longSyncLimit) {
        this.expectedPeers = expectedPeers;
        this.timeoutWaitingPeers = Duration.ofSeconds(timeoutWaitingPeers);
        this.timeoutWaitingRequest = Duration.ofSeconds(timeoutWaitingRequest);
        this.expirationTimePeerStatus = Duration.ofMinutes(expirationTimePeerStatus);
        this.maxSkeletonChunks = maxSkeletonChunks;
        this.chunkSize = chunkSize;
        this.maxRequestedBodies = maxRequestedBodies;
        this.longSyncLimit = longSyncLimit;
    }

    public final int getExpectedPeers() {
        return expectedPeers;
    }

    public final int getMaxSkeletonChunks() {
        return maxSkeletonChunks;
    }

    public final Duration getTimeoutWaitingPeers() {
        return timeoutWaitingPeers;
    }

    public final Duration getTimeoutWaitingRequest() {
        return  timeoutWaitingRequest;
    }

    public final Duration getExpirationTimePeerStatus() {
        return expirationTimePeerStatus;
    }

    public final int getChunkSize() {
        return chunkSize;
    }

    public int getMaxRequestedBodies() {
        return maxRequestedBodies;
    }

    public int getLongSyncLimit() {
        return longSyncLimit;
    }
}
