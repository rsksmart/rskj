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

import co.rsk.core.bc.ConsensusValidationMainchainView;
import co.rsk.crypto.Keccak256;
import co.rsk.net.Peer;
import co.rsk.scoring.EventType;
import co.rsk.validators.BlockHeaderValidationRule;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.ethereum.validator.DependentBlockHeaderRule;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DownloadingHeadersSyncState extends BaseSelectedPeerSyncState {

    private final Map<Peer, List<BlockIdentifier>> skeletons;
    private final List<Deque<BlockHeader>> pendingHeaders;
    private final ChunksDownloadHelper chunksDownloadHelper;
    private final DependentBlockHeaderRule blockParentValidationRule;
    private final BlockHeaderValidationRule blockHeaderValidationRule;
    private Map<Keccak256, BlockHeader> pendingHeadersByHash;

    public DownloadingHeadersSyncState(
            SyncConfiguration syncConfiguration,
            SyncEventsHandler syncEventsHandler,
            ConsensusValidationMainchainView mainchainView,
            DependentBlockHeaderRule blockParentValidationRule,
            BlockHeaderValidationRule blockHeaderValidationRule,
            Peer peer,
            Map<Peer, List<BlockIdentifier>> skeletons,
            long connectionPoint) {
        super(syncEventsHandler, syncConfiguration, peer);
        this.blockParentValidationRule = blockParentValidationRule;
        this.blockHeaderValidationRule = blockHeaderValidationRule;
        this.pendingHeaders = new ArrayList<>();
        this.skeletons = skeletons;
        this.chunksDownloadHelper = new ChunksDownloadHelper(
                syncConfiguration,
                skeletons.get(selectedPeer),
                connectionPoint);
        this.pendingHeadersByHash = new ConcurrentHashMap<>();
        mainchainView.setPendingHeaders(pendingHeadersByHash);
    }

    @Override
    public void newBlockHeaders(List<BlockHeader> chunk) {
        Optional<ChunkDescriptor> currentChunkOpt = chunksDownloadHelper.getCurrentChunk();
        if (!currentChunkOpt.isPresent()) {
            syncEventsHandler.onSyncIssue(selectedPeer, "Current chunk not present for node [{}] on {}", this.getClass());
            return;
        }
        ChunkDescriptor currentChunk = currentChunkOpt.get();

        boolean unexpectedChunkSize = chunk.size() != currentChunk.getCount();
        if (unexpectedChunkSize) {
            syncEventsHandler.onErrorSyncing(selectedPeer, EventType.INVALID_MESSAGE,
                    "Unexpected chunk size received from node [{}] on {}: hash: {}",
                    this.getClass(), HashUtil.toPrintableHash(currentChunk.getHash()));
            return;
        }

        boolean unexpectedHeader = !ByteUtil.fastEquals(chunk.get(0).getHash().getBytes(), currentChunk.getHash());
        if (unexpectedHeader) {
            syncEventsHandler.onErrorSyncing(selectedPeer, EventType.INVALID_MESSAGE,
                    "Unexpected chunk header hash received from node [{}] on {}: hash: {}",
                    this.getClass(), HashUtil.toPrintableHash(currentChunk.getHash()));
            return;
        }

        Deque<BlockHeader> headers = new ArrayDeque<>();
        // the headers come ordered by block number desc
        // we start adding the first parent header
        BlockHeader headerToAdd = chunk.get(chunk.size() - 1);
        headers.add(headerToAdd);
        pendingHeadersByHash.put(headerToAdd.getHash(), headerToAdd);

        for (int k = 1; k < chunk.size(); ++k) {
            BlockHeader parentHeader = chunk.get(chunk.size() - k);
            BlockHeader header = chunk.get(chunk.size() - k - 1);

            if (!blockHeaderIsValid(header, parentHeader)) {
                syncEventsHandler.onErrorSyncing(selectedPeer, EventType.INVALID_HEADER,
                        "Invalid header received from node [{}] on {}, no: {}, hash: {}",
                        this.getClass(), header.getNumber(), header.getPrintableHash());
                return;
            }

            headers.add(header);
            pendingHeadersByHash.put(header.getHash(), header);
        }

        pendingHeaders.add(headers);

        if (!chunksDownloadHelper.hasNextChunk()) {
            // Finished verifying headers
            syncEventsHandler.startDownloadingBodies(pendingHeaders, skeletons, selectedPeer);
            return;
        }

        resetTimeElapsed();
        trySendRequest();
    }

    @Override
    public void onEnter() {
        trySendRequest();
    }

    @VisibleForTesting
    public List<BlockIdentifier> getSkeleton() {
        return chunksDownloadHelper.getSkeleton();
    }

    private void trySendRequest() {
        syncEventsHandler.sendBlockHeadersRequest(selectedPeer, chunksDownloadHelper.getNextChunk());
    }

    private boolean blockHeaderIsValid(BlockHeader header, BlockHeader parentHeader) {
        if (!parentHeader.getHash().equals(header.getParentHash())) {
            return false;
        }

        if (header.getNumber() != parentHeader.getNumber() + 1) {
            return false;
        }

        if (!blockHeaderValidationRule.isValid(header)) {
            return false;
        }

        return blockParentValidationRule.validate(header, parentHeader);
    }
}
