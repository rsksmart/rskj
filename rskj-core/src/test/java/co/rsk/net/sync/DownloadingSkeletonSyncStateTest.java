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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

public class DownloadingSkeletonSyncStateTest {

    // TODO Test other logic

    private SyncConfiguration syncConfiguration;
    private SyncEventsHandler syncEventsHandler;
    private PeersInformation peersInformation;
    private Peer selectedPeer;

    @BeforeEach
    public void setUp () throws UnknownHostException {
        syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        syncEventsHandler = mock(SyncEventsHandler.class);
        peersInformation = mock(PeersInformation.class);
        selectedPeer = mock(Peer.class);
        NodeID nodeID = mock(NodeID.class);
        when(selectedPeer.getPeerNodeID()).thenReturn(nodeID);
        when(selectedPeer.getAddress()).thenReturn(InetAddress.getByName("127.0.0.1"));
    }

    @Test
    public void onMessageTimeOut() {
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

}

