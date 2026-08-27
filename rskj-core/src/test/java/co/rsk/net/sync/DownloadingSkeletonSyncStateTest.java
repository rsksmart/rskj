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

import co.rsk.net.NodeID;
import co.rsk.net.Peer;
import co.rsk.scoring.EventType;
import org.ethereum.TestUtils;
import org.ethereum.core.BlockIdentifier;
import org.junit.jupiter.api.Assertions;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

class DownloadingSkeletonSyncStateTest {

    // TODO Test other logic

    private SyncConfiguration syncConfiguration;
    private SyncEventsHandler syncEventsHandler;
    private PeersInformation peersInformation;
    private Peer selectedPeer;

    @BeforeEach
    void setUp () throws UnknownHostException {
        syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        syncEventsHandler = mock(SyncEventsHandler.class);
        peersInformation = mock(PeersInformation.class);
        selectedPeer = mock(Peer.class);
        NodeID nodeID = mock(NodeID.class);
        when(selectedPeer.getPeerNodeID()).thenReturn(nodeID);
        when(selectedPeer.getAddress()).thenReturn(InetAddress.getByName("127.0.0.1"));
    }

    @Test
    void onMessageTimeOut() {
        DownloadingSkeletonSyncState target = new DownloadingSkeletonSyncState(
                syncConfiguration,
                syncEventsHandler,
                peersInformation,
                selectedPeer,
                0);

        target.onMessageTimeOut();
        verify(syncEventsHandler, times(1))
                .onErrorSyncing(selectedPeer, EventType.TIMEOUT_MESSAGE,
                        "Timeout waiting requests on {}", DownloadingSkeletonSyncState.class);
    }


    private static SyncConfiguration configWithRangeMultiplier(int multiplier) {
        return new SyncConfiguration(1, 1, 3, 1, 5, 192, 20, 10, 0, false, false, 0,
                Collections.emptyList(), 24, 0, 12, 3, multiplier);
    }

    /** Builds a skeleton of `chunks` links of 192 blocks each, starting at `start`. */
    private static List<BlockIdentifier> skeletonAt(long start, int chunks, String salt) {
        List<BlockIdentifier> ids = new ArrayList<>();
        for (int i = 0; i <= chunks; i++) {
            long n = start + (long) i * 192;
            ids.add(new BlockIdentifier(
                    TestUtils.generateBytes(DownloadingSkeletonSyncStateTest.class, salt + n, 32), n));
        }
        return ids;
    }

    @Test
    void severalSkeletonsAreRequestedAtOnceAndJoinedIntoOneRange() {
        when(peersInformation.getBestPeerCandidates()).thenReturn(Collections.singletonList(selectedPeer));

        int multiplier = 3;
        int chunksPerSkeleton = 20;
        long stride = 192L * chunksPerSkeleton;

        DownloadingSkeletonSyncState target = new DownloadingSkeletonSyncState(
                configWithRangeMultiplier(multiplier), syncEventsHandler, peersInformation, selectedPeer, 0);

        target.onEnter();

        // one request per candidate plus the extra slices, all issued together
        verify(syncEventsHandler, times(multiplier)).sendSkeletonRequest(eq(selectedPeer), anyLong());
        verify(syncEventsHandler).sendSkeletonRequest(selectedPeer, 0L);
        verify(syncEventsHandler).sendSkeletonRequest(selectedPeer, stride);
        verify(syncEventsHandler).sendSkeletonRequest(selectedPeer, 2 * stride);

        // slices that line up end-to-end: each starts on the block the previous one ended
        List<BlockIdentifier> first = skeletonAt(0, chunksPerSkeleton, "a");
        List<BlockIdentifier> second = new ArrayList<>();
        second.add(first.get(first.size() - 1));
        second.addAll(skeletonAt(stride + 192, chunksPerSkeleton - 1, "b"));
        List<BlockIdentifier> third = new ArrayList<>();
        third.add(second.get(second.size() - 1));
        third.addAll(skeletonAt(second.get(second.size() - 1).getNumber() + 192, chunksPerSkeleton - 1, "c"));

        target.newSkeleton(first, selectedPeer);
        target.newSkeleton(second, selectedPeer);
        target.newSkeleton(third, selectedPeer);

        ArgumentCaptor<Map<Peer, List<BlockIdentifier>>> captor = ArgumentCaptor.forClass(Map.class);
        verify(syncEventsHandler).startDownloadingHeaders(captor.capture(), eq(0L), eq(selectedPeer));

        List<BlockIdentifier> joined = captor.getValue().get(selectedPeer);
        // shared boundaries are dropped, so the range is the sum of the slices
        Assertions.assertEquals(first.size() + second.size() + third.size() - 2, joined.size());
        Assertions.assertEquals(0L, joined.get(0).getNumber());
        Assertions.assertEquals(third.get(third.size() - 1).getNumber(), joined.get(joined.size() - 1).getNumber());
        // strictly increasing, no gaps
        for (int i = 1; i < joined.size(); i++) {
            Assertions.assertEquals(joined.get(i - 1).getNumber() + 192, joined.get(i).getNumber());
        }
    }

    @Test
    void aSliceThatDoesNotJoinIsDroppedAndTheShorterRangeIsUsed() {
        when(peersInformation.getBestPeerCandidates()).thenReturn(Collections.singletonList(selectedPeer));

        DownloadingSkeletonSyncState target = new DownloadingSkeletonSyncState(
                configWithRangeMultiplier(2), syncEventsHandler, peersInformation, selectedPeer, 0);
        target.onEnter();

        List<BlockIdentifier> first = skeletonAt(0, 20, "a");
        // a second slice from a different chain: its first link does not match the first slice's last
        List<BlockIdentifier> disjoint = skeletonAt(20L * 192, 20, "other");

        target.newSkeleton(first, selectedPeer);
        target.newSkeleton(disjoint, selectedPeer);

        ArgumentCaptor<Map<Peer, List<BlockIdentifier>>> captor = ArgumentCaptor.forClass(Map.class);
        verify(syncEventsHandler).startDownloadingHeaders(captor.capture(), eq(0L), eq(selectedPeer));

        // only the slice we could verify is used; syncing still proceeds
        Assertions.assertEquals(first.size(), captor.getValue().get(selectedPeer).size());
    }
}
