/*
 * This file is part of RskJ
 * Copyright (C) 2022 RSK Labs Ltd.
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
import co.rsk.net.Peer;
import co.rsk.scoring.EventType;
import co.rsk.validators.BlockHeaderValidationRule;
import org.ethereum.TestUtils;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;
import org.ethereum.crypto.HashUtil;
import org.ethereum.validator.DependentBlockHeaderRule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.*;

import static org.mockito.Mockito.*;

class DownloadingHeadersSyncStateTest {
    @Test
    void itIgnoresNewPeerInformation() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SimpleSyncEventsHandler syncEventsHandler = new SimpleSyncEventsHandler();
        Map<Peer, List<BlockIdentifier>> skeletons = Collections.singletonMap(null, null);
        SyncState syncState = new DownloadingHeadersSyncState(
                syncConfiguration,
                syncEventsHandler,
                mock(ConsensusValidationMainchainView.class),
                mock(DependentBlockHeaderRule.class),
                mock(BlockHeaderValidationRule.class),
                mock(Peer.class), skeletons,
                0);

        for (int i = 0; i < 10; i++) {
            syncState.newPeerStatus();
            Assertions.assertFalse(syncEventsHandler.stopSyncingWasCalled());
        }
    }

    @Test
    void itTimeoutsWhenWaitingForRequest() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SimpleSyncEventsHandler syncEventsHandler = new SimpleSyncEventsHandler();
        SyncState syncState = new DownloadingHeadersSyncState(
                syncConfiguration,
                syncEventsHandler,
                mock(ConsensusValidationMainchainView.class),
                mock(DependentBlockHeaderRule.class),
                mock(BlockHeaderValidationRule.class),
                mock(Peer.class), Collections.emptyMap(),
                0);

        syncState.newPeerStatus();
        Assertions.assertFalse(syncEventsHandler.stopSyncingWasCalled());

        syncState.tick(syncConfiguration.getTimeoutWaitingRequest().dividedBy(2));
        Assertions.assertFalse(syncEventsHandler.stopSyncingWasCalled());

        syncState.tick(syncConfiguration.getTimeoutWaitingRequest());
        Assertions.assertTrue(syncEventsHandler.stopSyncingWasCalled());
    }

    @Test
    void itDoesntTimeoutWhenSendingMessages() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SimpleSyncEventsHandler syncEventsHandler = new SimpleSyncEventsHandler();
        DownloadingHeadersSyncState syncState = new DownloadingHeadersSyncState(
                syncConfiguration,
                syncEventsHandler,
                mock(ConsensusValidationMainchainView.class),
                mock(DependentBlockHeaderRule.class),
                mock(BlockHeaderValidationRule.class),
                mock(Peer.class), Collections.emptyMap(),
                0);

        syncState.newPeerStatus();
        Assertions.assertFalse(syncEventsHandler.stopSyncingWasCalled());

        for (int i = 0; i < 10; i++) {
            syncState.messageSent();
            Assertions.assertFalse(syncEventsHandler.stopSyncingWasCalled());

            syncState.tick(syncConfiguration.getTimeoutWaitingRequest().dividedBy(2));
            Assertions.assertFalse(syncEventsHandler.stopSyncingWasCalled());
        }

        syncState.tick(syncConfiguration.getTimeoutWaitingRequest());
        Assertions.assertTrue(syncEventsHandler.stopSyncingWasCalled());
    }

    @Test
    void newBlockHeadersWhenNoCurrentChunkThenSyncIssue() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SyncEventsHandler syncEventsHandler = mock(SyncEventsHandler.class);
        Peer selectedPeer = mock(Peer.class);
        DownloadingHeadersSyncState syncState = new DownloadingHeadersSyncState(
                syncConfiguration,
                syncEventsHandler,
                mock(ConsensusValidationMainchainView.class),
                mock(DependentBlockHeaderRule.class),
                mock(BlockHeaderValidationRule.class),
                selectedPeer, Collections.emptyMap(),
                0);

        ChunksDownloadHelper chunksDownloadHelper = mock(ChunksDownloadHelper.class);
        TestUtils.setInternalState(syncState, "chunksDownloadHelper", chunksDownloadHelper);

        when(chunksDownloadHelper.getCurrentChunk()).thenReturn(Optional.empty());

        syncState.newBlockHeaders(selectedPeer, new ArrayList<>());

        verify(syncEventsHandler, times(1)).onSyncIssue(selectedPeer,
                "Current chunk not present on {}", DownloadingHeadersSyncState.class);
    }

    @Test
    void newBlockHeadersWhenUnexpectedChunkSizeThenInvalidMessage() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SyncEventsHandler syncEventsHandler = mock(SyncEventsHandler.class);
        Peer selectedPeer = mock(Peer.class);
        DownloadingHeadersSyncState syncState = new DownloadingHeadersSyncState(
                syncConfiguration,
                syncEventsHandler,
                mock(ConsensusValidationMainchainView.class),
                mock(DependentBlockHeaderRule.class),
                mock(BlockHeaderValidationRule.class),
                selectedPeer, Collections.emptyMap(),
                0);

        ChunksDownloadHelper chunksDownloadHelper = mock(ChunksDownloadHelper.class);
        TestUtils.setInternalState(syncState, "chunksDownloadHelper", chunksDownloadHelper);

        ChunkDescriptor currentChunk = mock(ChunkDescriptor.class);
        when(currentChunk.getCount()).thenReturn(2); // different from chunk size
        byte[] chunkHash = TestUtils.generateBytes(DownloadingHeadersSyncStateTest.class,"chunkHash",32);
        when(currentChunk.getHash()).thenReturn(chunkHash);
        when(chunksDownloadHelper.getCurrentChunk()).thenReturn(Optional.of(currentChunk));

        List<BlockHeader> chunk = new ArrayList<>();
        chunk.add(mock(BlockHeader.class));
        syncState.newBlockHeaders(selectedPeer, chunk);

        verify(syncEventsHandler, times(1)).onErrorSyncing(selectedPeer, EventType.INVALID_MESSAGE,
                "Unexpected chunk size received on {}: hash: {}", DownloadingHeadersSyncState.class, HashUtil.toPrintableHash(currentChunk.getHash()));
    }

    @Test
    void newBlockHeadersWhenUnexpectedHeaderThenInvalidMessage() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SyncEventsHandler syncEventsHandler = mock(SyncEventsHandler.class);
        Peer selectedPeer = mock(Peer.class);
        DownloadingHeadersSyncState syncState = new DownloadingHeadersSyncState(
                syncConfiguration,
                syncEventsHandler,
                mock(ConsensusValidationMainchainView.class),
                mock(DependentBlockHeaderRule.class),
                mock(BlockHeaderValidationRule.class),
                selectedPeer, Collections.emptyMap(),
                0);

        ChunksDownloadHelper chunksDownloadHelper = mock(ChunksDownloadHelper.class);
        TestUtils.setInternalState(syncState, "chunksDownloadHelper", chunksDownloadHelper);

        ChunkDescriptor currentChunk = mock(ChunkDescriptor.class);
        when(currentChunk.getCount()).thenReturn(1);
        byte[] chunkHash = TestUtils.generateBytes(DownloadingHeadersSyncStateTest.class,"chunkHash",32);
        when(currentChunk.getHash()).thenReturn(chunkHash);
        when(chunksDownloadHelper.getCurrentChunk()).thenReturn(Optional.of(currentChunk));

        List<BlockHeader> chunk = new ArrayList<>();
        BlockHeader header = mock(BlockHeader.class, Mockito.RETURNS_DEEP_STUBS);
        byte[] headerHash = TestUtils.generateBytes(DownloadingHeadersSyncStateTest.class,"headerHash",32);
        when(header.getHash().getBytes()).thenReturn(headerHash);; // different from chunkHash
        chunk.add(header);
        syncState.newBlockHeaders(selectedPeer, chunk);

        verify(syncEventsHandler, times(1)).onErrorSyncing(selectedPeer, EventType.INVALID_MESSAGE,
                "Unexpected chunk header hash received on {}: hash: {}", DownloadingHeadersSyncState.class, HashUtil.toPrintableHash(currentChunk.getHash()));
    }
}
