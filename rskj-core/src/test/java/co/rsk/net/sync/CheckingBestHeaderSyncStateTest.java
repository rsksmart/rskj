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
import co.rsk.validators.BlockHeaderValidationRule;
import org.ethereum.core.BlockHeader;
import org.ethereum.crypto.HashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;

import static org.mockito.Mockito.*;

class CheckingBestHeaderSyncStateTest {

    private static final byte[] HASH_1 = HashUtil.sha256(new byte[]{1});

    private SyncEventsHandler syncEventsHandler;
    private Peer peer;
    private BlockHeaderValidationRule blockHeaderValidationRule;
    private CheckingBestHeaderSyncState state;

    @BeforeEach
    void setUp() throws UnknownHostException {
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        syncEventsHandler = mock(SyncEventsHandler.class);
        blockHeaderValidationRule = mock(BlockHeaderValidationRule.class);
        peer = mock(Peer.class);
        state = new CheckingBestHeaderSyncState(syncConfiguration, syncEventsHandler, blockHeaderValidationRule, peer, HASH_1);

        when(peer.getPeerNodeID()).thenReturn(new NodeID(new byte[]{2}));
        when(peer.getAddress()).thenReturn(InetAddress.getByName("127.0.0.1"));
    }

    @Test
    void onEnterContinue() {
        state.onEnter();
        ChunkDescriptor chunk = new ChunkDescriptor(HASH_1, 1);
        verify(syncEventsHandler, times(1)).sendBlockHeadersRequest(peer, chunk);
    }

    @Test
    void newBlockHeadersWhenValidHeaderContinue() {
        BlockHeader header = mock(BlockHeader.class, Mockito.RETURNS_DEEP_STUBS);
        when(header.getHash().getBytes()).thenReturn(HASH_1);
        when(blockHeaderValidationRule.isValid(header)).thenReturn(true);

        state.newBlockHeaders(Collections.singletonList(header));

        verify(syncEventsHandler, times(1)).startFindingConnectionPoint(peer);
    }

    @Test
    void newBlockHeadersWhenInValidHeaderOnErrorSyncing() {
        BlockHeader header = mock(BlockHeader.class, Mockito.RETURNS_DEEP_STUBS);
        when(header.getHash().getBytes()).thenReturn(HASH_1);
        when(blockHeaderValidationRule.isValid(header)).thenReturn(false);

        state.newBlockHeaders(Collections.singletonList(header));

        verify(syncEventsHandler, times(1))
                .onErrorSyncing(peer, EventType.INVALID_HEADER,
                        "Invalid header received on {}", CheckingBestHeaderSyncState.class);
    }

    @Test
    void newBlockHeadersWhenDifferentHeaderOnErrorSyncing() {
        BlockHeader header = mock(BlockHeader.class, Mockito.RETURNS_DEEP_STUBS);
        when(header.getHash().getBytes()).thenReturn(HashUtil.sha256(new byte[]{5}));
        when(blockHeaderValidationRule.isValid(header)).thenReturn(true);

        state.newBlockHeaders(Collections.singletonList(header));

        verify(syncEventsHandler, times(1))
                .onErrorSyncing(peer, EventType.INVALID_HEADER,
                        "Unexpected header received on {}", CheckingBestHeaderSyncState.class);
    }

    @Test
    void onMessageTimeOut() {
        state.onMessageTimeOut();
        verify(syncEventsHandler, times(1))
                .onErrorSyncing(peer, EventType.TIMEOUT_MESSAGE,
                        "Timeout waiting requests on {}", CheckingBestHeaderSyncState.class);
    }
}
