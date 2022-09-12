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

import co.rsk.crypto.Keccak256;
import co.rsk.net.NodeID;
import co.rsk.net.Peer;
import co.rsk.scoring.EventType;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.db.BlockStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DownloadingBackwardsHeadersSyncStateTest {

    private SyncConfiguration syncConfiguration;
    private SyncEventsHandler syncEventsHandler;
    private BlockStore blockStore;
    private Peer selectedPeer;

    @BeforeEach
    void setUp() throws UnknownHostException {
        syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        syncEventsHandler = mock(SyncEventsHandler.class);
        blockStore = mock(BlockStore.class);
        selectedPeer = mock(Peer.class);
        NodeID nodeID = mock(NodeID.class);
        when(selectedPeer.getPeerNodeID()).thenReturn(nodeID);
        when(selectedPeer.getAddress()).thenReturn(InetAddress.getByName("127.0.0.1"));
    }

    @Test
    void onEnter() {
        when(blockStore.getMinNumber()).thenReturn(50L);
        Block child = mock(Block.class);
        Keccak256 hash = new Keccak256(new byte[32]);
        when(child.getHash()).thenReturn(hash);
        when(blockStore.getChainBlockByNumber(50L)).thenReturn(child);

        DownloadingBackwardsHeadersSyncState target = new DownloadingBackwardsHeadersSyncState(
                syncConfiguration,
                syncEventsHandler,
                blockStore,
                selectedPeer);

        ArgumentCaptor<ChunkDescriptor> descriptorCaptor = ArgumentCaptor.forClass(ChunkDescriptor.class);

        target.onEnter();

        verify(syncEventsHandler).sendBlockHeadersRequest(eq(selectedPeer), descriptorCaptor.capture());
        verify(syncEventsHandler, never()).onSyncIssue(any(), any());

        assertEquals(descriptorCaptor.getValue().getHash(), hash.getBytes());
        assertEquals(descriptorCaptor.getValue().getCount(), syncConfiguration.getChunkSize());
    }

    @Test
    void newHeaders() {
        when(blockStore.getMinNumber()).thenReturn(50L);
        Block child = mock(Block.class);
        Keccak256 hash = new Keccak256(new byte[32]);
        when(child.getHash()).thenReturn(hash);
        when(blockStore.getChainBlockByNumber(50L)).thenReturn(child);

        DownloadingBackwardsHeadersSyncState target = new DownloadingBackwardsHeadersSyncState(
                syncConfiguration,
                syncEventsHandler,
                blockStore,
                selectedPeer);


        List<BlockHeader> receivedHeaders = new LinkedList<>();
        target.newBlockHeaders(receivedHeaders);


        verify(syncEventsHandler).backwardDownloadBodies(child, receivedHeaders, selectedPeer);
    }

    @Test
    void onMessageTimeOut() {
        DownloadingBackwardsHeadersSyncState target = new DownloadingBackwardsHeadersSyncState(
                syncConfiguration,
                syncEventsHandler,
                blockStore,
                selectedPeer);

        target.onMessageTimeOut();
        verify(syncEventsHandler, times(1))
                .onErrorSyncing(selectedPeer, EventType.TIMEOUT_MESSAGE,
                        "Timeout waiting requests on {}", DownloadingBackwardsHeadersSyncState.class);
    }
}
