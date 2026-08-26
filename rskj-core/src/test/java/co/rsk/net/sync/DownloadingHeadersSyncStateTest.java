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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    private static List<BlockIdentifier> skeleton(byte[] tipHash) {
        List<BlockIdentifier> skeleton = new ArrayList<>();
        skeleton.add(new BlockIdentifier(TestUtils.generateBytes(DownloadingHeadersSyncStateTest.class, "base", 32), 0L));
        skeleton.add(new BlockIdentifier(tipHash, 1L));
        return skeleton;
    }

    private static DownloadingHeadersSyncState stateWith(SyncEventsHandler syncEventsHandler,
                                                         Peer selectedPeer,
                                                         Map<Peer, List<BlockIdentifier>> skeletons) {
        return new DownloadingHeadersSyncState(
                SyncConfiguration.DEFAULT,
                syncEventsHandler,
                mock(ConsensusValidationMainchainView.class),
                mock(DependentBlockHeaderRule.class),
                mock(BlockHeaderValidationRule.class),
                selectedPeer, skeletons,
                0);
    }

    private static BlockHeader headerWithHash(byte[] hash) {
        BlockHeader header = mock(BlockHeader.class, Mockito.RETURNS_DEEP_STUBS);
        when(header.getHash().getBytes()).thenReturn(hash);
        return header;
    }

    @Test
    void onEnterWithEmptySkeletonThenSyncIssue() {
        SyncEventsHandler syncEventsHandler = mock(SyncEventsHandler.class);
        Peer selectedPeer = mock(Peer.class);
        DownloadingHeadersSyncState syncState = stateWith(syncEventsHandler, selectedPeer, Collections.emptyMap());

        syncState.onEnter();

        verify(syncEventsHandler, times(1)).onSyncIssue(selectedPeer,
                "Empty skeleton on {}", DownloadingHeadersSyncState.class);
    }

    @Test
    void newBlockHeadersWhenUnexpectedChunkSizeThenInvalidMessage() {
        SyncEventsHandler syncEventsHandler = mock(SyncEventsHandler.class);
        Peer selectedPeer = mock(Peer.class);
        byte[] chunkHash = TestUtils.generateBytes(DownloadingHeadersSyncStateTest.class, "chunkHash", 32);
        DownloadingHeadersSyncState syncState = stateWith(syncEventsHandler, selectedPeer,
                Collections.singletonMap(selectedPeer, skeleton(chunkHash)));

        syncState.onEnter();

        // top header matches the skeleton boundary, but the chunk carries more headers than the
        // descriptor says it should
        List<BlockHeader> chunk = new ArrayList<>();
        chunk.add(headerWithHash(chunkHash));
        chunk.add(headerWithHash(TestUtils.generateBytes(DownloadingHeadersSyncStateTest.class, "extra", 32)));

        syncState.newBlockHeaders(selectedPeer, chunk);

        verify(syncEventsHandler, times(1)).onErrorSyncing(selectedPeer, EventType.INVALID_MESSAGE,
                "Unexpected chunk size received on {}: hash: {}", DownloadingHeadersSyncState.class,
                HashUtil.toPrintableHash(chunkHash));
    }

    @Test
    void newBlockHeadersWhenUnknownChunkThenInvalidMessage() {
        SyncEventsHandler syncEventsHandler = mock(SyncEventsHandler.class);
        Peer selectedPeer = mock(Peer.class);
        byte[] chunkHash = TestUtils.generateBytes(DownloadingHeadersSyncStateTest.class, "chunkHash", 32);
        DownloadingHeadersSyncState syncState = stateWith(syncEventsHandler, selectedPeer,
                Collections.singletonMap(selectedPeer, skeleton(chunkHash)));

        syncState.onEnter();

        // a chunk whose top header hashes to nothing in the trusted skeleton
        List<BlockHeader> chunk = new ArrayList<>();
        chunk.add(headerWithHash(TestUtils.generateBytes(DownloadingHeadersSyncStateTest.class, "headerHash", 32)));

        syncState.newBlockHeaders(selectedPeer, chunk);

        verify(syncEventsHandler, times(1)).onErrorSyncing(selectedPeer, EventType.INVALID_MESSAGE,
                "Unexpected headers chunk received on {}", DownloadingHeadersSyncState.class);
    }

    @Test
    void chunkRequestsAreSpreadOverEveryPeerThatSharesTheSkeleton() {
        SyncEventsHandler syncEventsHandler = mock(SyncEventsHandler.class);
        Peer selectedPeer = mock(Peer.class);
        Peer helperPeer = mock(Peer.class);

        // three chunks, so more than one request can be in flight at a time
        List<BlockIdentifier> sk = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            sk.add(new BlockIdentifier(
                    TestUtils.generateBytes(DownloadingHeadersSyncStateTest.class, "link" + i, 32), i));
        }
        Map<Peer, List<BlockIdentifier>> skeletons = new HashMap<>();
        skeletons.put(selectedPeer, sk);
        skeletons.put(helperPeer, sk);

        // up to 3 chunk requests in flight, at most 2 to any single peer
        SyncConfiguration parallelConfig = new SyncConfiguration(
                5, 60, 30, 5, 20, 192, 20, 10, 0, false, false, 0,
                Collections.emptyList(), 24, 0, 3, 2);

        DownloadingHeadersSyncState syncState = new DownloadingHeadersSyncState(
                parallelConfig,
                syncEventsHandler,
                mock(ConsensusValidationMainchainView.class),
                mock(DependentBlockHeaderRule.class),
                mock(BlockHeaderValidationRule.class),
                selectedPeer, skeletons,
                0);

        syncState.onEnter();

        // all three chunks dispatched at once, and the load shared with the helper peer
        verify(syncEventsHandler, times(3)).sendBlockHeadersRequest(any(Peer.class), any(ChunkDescriptor.class));
        verify(syncEventsHandler, atLeastOnce()).sendBlockHeadersRequest(eq(helperPeer), any(ChunkDescriptor.class));
    }
}
