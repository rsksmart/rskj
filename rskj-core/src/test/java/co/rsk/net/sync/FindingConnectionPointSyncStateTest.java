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
import org.ethereum.db.BlockStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


class FindingConnectionPointSyncStateTest {

    // TODO Test other logic

    private SyncConfiguration syncConfiguration;
    private SyncEventsHandler syncEventsHandler;
    private BlockStore blockStore;
    private Peer peer;

    @BeforeEach
    void setUp() throws UnknownHostException {
        syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        syncEventsHandler = mock(SyncEventsHandler.class);
        blockStore = mock(BlockStore.class);
        peer = mock(Peer.class);
        NodeID nodeID = mock(NodeID.class);
        when(peer.getPeerNodeID()).thenReturn(nodeID);
        when(peer.getAddress()).thenReturn(InetAddress.getByName("127.0.0.1"));
    }

    @Test
    void noConnectionPoint() {
        when(blockStore.getMinNumber()).thenReturn(0L);
        FindingConnectionPointSyncState target =
                new FindingConnectionPointSyncState(
                        SyncConfiguration.IMMEDIATE_FOR_TESTING,
                        syncEventsHandler,
                        blockStore,
                        peer, 10L);

        when(blockStore.isBlockExist(any())).thenReturn(false);

        target.onEnter();
        for(int i = 0; i < 4; i++) {
            target.newConnectionPointData(new byte[32]);
        }

        verify(syncEventsHandler, times(1))
                .onSyncIssue(peer, "Connection point not found on {}", FindingConnectionPointSyncState.class);
    }

    @Test
    void onMessageTimeOut() {
        FindingConnectionPointSyncState target = new FindingConnectionPointSyncState(
                syncConfiguration,
                syncEventsHandler,
                blockStore,
                peer,
                10L);

        target.onMessageTimeOut();
        verify(syncEventsHandler, times(1)).onErrorSyncing(peer, EventType.TIMEOUT_MESSAGE,
                        "Timeout waiting requests on {}", FindingConnectionPointSyncState.class);
    }

}
