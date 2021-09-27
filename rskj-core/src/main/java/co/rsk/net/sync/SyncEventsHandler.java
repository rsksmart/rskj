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
import co.rsk.net.Peer;
import co.rsk.scoring.EventType;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;

import javax.annotation.Nullable;
import java.util.Deque;
import java.util.List;
import java.util.Map;

public interface SyncEventsHandler {
    void sendSkeletonRequest(Peer peer, long height);

    void sendBlockHashRequest(Peer peer, long height);

    void sendBlockHeadersRequest(Peer peer, ChunkDescriptor chunk);

    long sendBodyRequest(Peer peer, BlockHeader header);

    void startDownloadingBodies(List<Deque<BlockHeader>> pendingHeaders, Map<Peer, List<BlockIdentifier>> skeletons, Peer peer);

    void startDownloadingHeaders(Map<Peer, List<BlockIdentifier>> skeletons, long connectionPoint, Peer peer);

    void startDownloadingSkeleton(long connectionPoint, Peer peer);

    void startSyncing(Peer peer);

    void backwardDownloadBodies(Block parent, List<BlockHeader> toRequest, Peer peer);

    void stopSyncing();

    void onLongSyncUpdate(boolean isSyncing, @Nullable Long peerBestBlockNumber);

    void onErrorSyncing(NodeID peerId, String message, EventType eventType, Object... arguments);

    void onSyncIssue(String message, Object... arguments);

    void startFindingConnectionPoint(Peer peer);

    void backwardSyncing(Peer peer);
}
