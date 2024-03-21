/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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
import co.rsk.net.SnapshotProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.mockito.Mockito.*;

class SnapSyncStateTest {

    private final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
    private final SyncEventsHandler syncEventsHandler = mock(SyncEventsHandler.class);
    private final PeersInformation peersInformation = mock(PeersInformation.class);
    private final SnapshotProcessor snapshotProcessor = mock(SnapshotProcessor.class);

    private final SnapSyncState underTest = new SnapSyncState(syncEventsHandler,
            snapshotProcessor,
            syncConfiguration,
            peersInformation);

    @BeforeEach
    void setUp(){
        reset(syncEventsHandler,peersInformation, snapshotProcessor);
    }

    @Test
    void givenOnEnterWasCalled_thenSyncingStartWithTestObjectAsParameter(){
        //given-when
        underTest.onEnter();
        //then
        verify(snapshotProcessor).startSyncing(peersInformation, underTest);
    }

    @Test
    void givenNewChunk_thenTimerIsReset(){
        //given
        underTest.timeElapsed = Duration.ofMinutes(1);
        assertThat(underTest.timeElapsed, greaterThan(Duration.ZERO));

        // when
        underTest.newChunk();
        //then
        assertThat(underTest.timeElapsed, equalTo(Duration.ZERO));
    }

    @Test
    void givenTickIsCalledBeforeTimeout_thenTimerIsUpdated_andNoTimeoutHappens(){
        //given
        Duration elapsedTime = Duration.ofMillis(10);
        underTest.timeElapsed = Duration.ZERO;
        // when
        underTest.tick(elapsedTime);
        //then
        assertThat(underTest.timeElapsed, equalTo(elapsedTime));
        verify(syncEventsHandler, never()).stopSyncing();
        verify(syncEventsHandler, never()).onErrorSyncing(any(),any(),any(),any());
    }

    @Test
    void givenTickIsCalledAfterTimeout_thenTimerIsUpdated_andTimeoutHappens() throws UnknownHostException {
        //given
        Duration elapsedTime = Duration.ofMinutes(1);
        underTest.timeElapsed = Duration.ZERO;
        Peer mockedPeer = mock(Peer.class);
        NodeID nodeID = mock(NodeID.class);
        when(mockedPeer.getPeerNodeID()).thenReturn(nodeID);
        when(mockedPeer.getAddress()).thenReturn(InetAddress.getByName("127.0.0.1"));
        when(peersInformation.getBestPeer()).thenReturn(Optional.of(mockedPeer));
        // when
        underTest.tick(elapsedTime);
        //then
        assertThat(underTest.timeElapsed, equalTo(elapsedTime));
        verify(syncEventsHandler, times(1)).stopSyncing();
        verify(syncEventsHandler, times(1)).onErrorSyncing(any(),any(),any(),any());
    }

    @Test
    void givenFinishedIsCalled_thenSyncEventHandlerStopsSync(){
        //given-when
        underTest.finished();
        //then
        verify(syncEventsHandler, times(1)).snapSyncFinished();
        verify(syncEventsHandler, times(1)).stopSyncing();
    }
}