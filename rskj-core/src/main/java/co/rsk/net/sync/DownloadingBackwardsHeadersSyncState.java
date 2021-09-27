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

import co.rsk.crypto.Keccak256;
import co.rsk.net.Peer;
import co.rsk.scoring.EventType;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.db.BlockStore;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Retrieves the oldest block in the storage and requests the headers that come before.
 */
public class DownloadingBackwardsHeadersSyncState extends BaseSyncState {

    private final Block child;
    private final Peer selectedPeer;

    public DownloadingBackwardsHeadersSyncState(
            SyncConfiguration syncConfiguration,
            SyncEventsHandler syncEventsHandler,
            BlockStore blockStore,
            Peer peer) {
        super(syncEventsHandler, syncConfiguration);
        this.selectedPeer = peer;
        this.child = blockStore.getChainBlockByNumber(blockStore.getMinNumber());
    }

    @Override
    public void newBlockHeaders(List<BlockHeader> toRequest) {
        syncEventsHandler.backwardDownloadBodies(
                child, toRequest.stream().skip(1).collect(Collectors.toList()), selectedPeer
        );
    }

    @Override
    public void onEnter() {
        Keccak256 hashToRequest = child.getHash();
        ChunkDescriptor chunkDescriptor = new ChunkDescriptor(
                hashToRequest.getBytes(),
                syncConfiguration.getChunkSize());

        syncEventsHandler.sendBlockHeadersRequest(selectedPeer, chunkDescriptor);
    }

    @Override
    protected void onMessageTimeOut() {
        syncEventsHandler.onErrorSyncing(
                selectedPeer.getPeerNodeID(),
                "Timeout waiting requests {}",
                EventType.TIMEOUT_MESSAGE,
                this.getClass(),
                selectedPeer.getPeerNodeID());
    }
}
