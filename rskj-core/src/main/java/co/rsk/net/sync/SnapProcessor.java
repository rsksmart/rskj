/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
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

import co.rsk.net.Peer;
import co.rsk.net.messages.*;
import org.ethereum.core.BlockHeader;

import java.util.List;

/**
 * Interface for SnapshotProcessor to break the circular dependency between
 * co.rsk.net.SnapshotProcessor and co.rsk.net.sync.SnapSyncState
 */
@SuppressWarnings("java:S7091")
public interface SnapProcessor {
    /**
     * Start a synchronization process
     * @param state The SnapSyncState to use
     */
    void startSyncing(SnapSyncState state);

    /**
     * Process snapshot status response
     * @param state Current state
     * @param sender Message sender
     * @param responseMessage Response message
     */
    void processSnapStatusResponse(SnapSyncState state, Peer sender, SnapStatusResponseMessage responseMessage);

    /**
     * Process block headers chunk
     * @param state Current state
     * @param sender Message sender
     * @param chunk List of block headers
     */
    void processBlockHeaderChunk(SnapSyncState state, Peer sender, List<BlockHeader> chunk);

    /**
     * Process snap blocks response
     * @param state Current state
     * @param sender Message sender
     * @param responseMessage Response message
     */
    void processSnapBlocksResponse(SnapSyncState state, Peer sender, SnapBlocksResponseMessage responseMessage);

    /**
     * Process state chunk response
     * @param state Current state
     * @param sender Message sender
     * @param responseMessage Response message
     */
    void processStateChunkResponse(SnapSyncState state, Peer sender, SnapStateChunkResponseMessage responseMessage);

    /**
     * Process state chunk response
     * @param state Current state
     * @param sender Message sender
     * @param responseMessage Response message
     */
    void processStateChunkResponse(SnapSyncState state, Peer sender, SnapStateChunkV2ResponseMessage responseMessage);

    /**
     * Process snap status request
     * @param sender Message sender
     * @param requestMessage Request message
     */
    void processSnapStatusRequest(Peer sender, SnapStatusRequestMessage requestMessage);

    /**
     * Process snap blocks request
     * @param sender Message sender
     * @param requestMessage Request message
     */
    void processSnapBlocksRequest(Peer sender, SnapBlocksRequestMessage requestMessage);

    /**
     * Process state chunk request
     * @param sender Message sender
     * @param requestMessage Request message
     */
    void processStateChunkRequest(Peer sender, SnapStateChunkRequestMessage requestMessage);

    /**
     * Process state chunk request
     * @param sender Message sender
     * @param requestMessage Request message
     */
    void processStateChunkRequest(Peer sender, SnapStateChunkV2RequestMessage requestMessage);
}
