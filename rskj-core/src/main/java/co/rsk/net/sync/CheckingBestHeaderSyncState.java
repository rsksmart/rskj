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

import co.rsk.net.Peer;
import co.rsk.scoring.EventType;
import co.rsk.validators.BlockHeaderValidationRule;
import org.ethereum.core.BlockHeader;
import org.ethereum.util.ByteUtil;

import java.util.List;

public class CheckingBestHeaderSyncState extends BaseSyncState implements SyncState {
    private final BlockHeaderValidationRule blockHeaderValidationRule;
    private final Peer selectedPeer;
    private final ChunkDescriptor miniChunk;

    public CheckingBestHeaderSyncState(
            SyncConfiguration syncConfiguration,
            SyncEventsHandler syncEventsHandler,
            BlockHeaderValidationRule blockHeaderValidationRule,
            Peer peer,
            byte[] bestBlockHash) {
        super(syncEventsHandler, syncConfiguration);
        this.blockHeaderValidationRule = blockHeaderValidationRule;
        this.selectedPeer = peer;
        this.miniChunk = new ChunkDescriptor(bestBlockHash, 1);
    }

    @Override
    public void onEnter(){
        trySendRequest();
    }

    @Override
    public void newBlockHeaders(List<BlockHeader> chunk){
        BlockHeader header = chunk.get(0);
        if (!ByteUtil.fastEquals(header.getHash().getBytes(), miniChunk.getHash()) ||
                !blockHeaderValidationRule.isValid(header)) {
            syncEventsHandler.onErrorSyncing(
                    selectedPeer.getPeerNodeID(),
                    "Invalid chunk received from node {}", EventType.INVALID_HEADER,
                    this.getClass());
            return;
        }

        syncEventsHandler.startFindingConnectionPoint(selectedPeer);
    }

    private void trySendRequest() {
        syncEventsHandler.sendBlockHeadersRequest(selectedPeer, miniChunk);
    }

    @Override
    protected void onMessageTimeOut() {
        syncEventsHandler.onErrorSyncing(selectedPeer.getPeerNodeID(),
                "Timeout waiting requests {}",
                EventType.TIMEOUT_MESSAGE,
                this.getClass(),
                selectedPeer.getPeerNodeID());
    }
}
