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

import co.rsk.core.BlockDifficulty;
import co.rsk.net.Peer;
import co.rsk.net.SnapshotProcessor;
import co.rsk.net.messages.*;
import org.apache.commons.lang3.tuple.Pair;
import org.ethereum.core.Block;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SnapSyncStateTest {

    private static final long THREAD_JOIN_TIMEOUT = 10_000; // 10 secs

    private final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
    private final SyncEventsHandler syncEventsHandler = mock(SyncEventsHandler.class);
    private final SnapshotPeersInformation peersInformation = mock(SnapshotPeersInformation.class);
    private final SnapshotProcessor snapshotProcessor = mock(SnapshotProcessor.class);
    private final SnapSyncRequestManager snapRequestManager = mock(SnapSyncRequestManager.class);
    private final SyncMessageHandler.Listener listener = mock(SyncMessageHandler.Listener.class);

    private final SnapSyncState underTest = new SnapSyncState(syncEventsHandler, snapshotProcessor, snapRequestManager, syncConfiguration, listener, null, Files.createTempDirectory("").toString());

    SnapSyncStateTest() throws IOException {
    }

    @BeforeEach
    void setUp() {
        reset(syncEventsHandler, peersInformation, snapshotProcessor);
    }

    @AfterEach
    void tearDown() {
        underTest.finish();
    }

    @Test
    void givenOnEnterWasCalledAndNotRunningYet_thenSyncingStartsWithTestObjectAsParameter() {
        //given-when
        underTest.onEnter();
        //then
        verify(snapshotProcessor, times(1)).startSyncing(underTest);
    }

    @Test
    void givenFinishWasCalledTwice_thenStopSyncingOnlyOnce() {
        //given-when
        underTest.setRunning();
        underTest.finish();
        underTest.finish();
        //then
        verify(syncEventsHandler, times(1)).stopSyncing();
    }

    @Test
    void givenOnEnterWasCalledTwice_thenSyncingStartsOnlyOnce() {
        //given-when
        underTest.onEnter();
        underTest.onEnter();
        //then
        verify(snapshotProcessor, times(1)).startSyncing(underTest);
    }

    @Test
    void givenFinishIsCalled_thenSyncEventHandlerStopsSync() {
        //given-when
        underTest.setRunning();
        underTest.finish();
        //then
        verify(syncEventsHandler, times(1)).stopSyncing();
    }

    @Test
    void givenOnSnapStatusIsCalled_thenJobIsAddedAndRun() throws InterruptedException {
        //given
        Peer peer = mock(Peer.class);
        SnapStatusResponseMessage msg = new SnapStatusResponseMessage(1, Collections.emptyList(), Collections.emptyList(), 1);
        CountDownLatch latch = new CountDownLatch(1);
        doCountDownOnQueueEmpty(listener, latch);
        doReturn(true).when(snapRequestManager).processResponse(any());
        underTest.onEnter();

        //when
        underTest.onSnapStatus(peer, msg);

        //then
        assertTrue(latch.await(THREAD_JOIN_TIMEOUT, TimeUnit.MILLISECONDS));

        ArgumentCaptor<SyncMessageHandler.Job> jobArg = ArgumentCaptor.forClass(SyncMessageHandler.Job.class);
        verify(listener, times(1)).onJobRun(jobArg.capture());

        assertEquals(peer, jobArg.getValue().getSender());
        assertEquals(msg.getMessageType(), jobArg.getValue().getMsgType());
    }

    @Test
    void givenOnSnapBlocksIsCalled_thenJobIsAddedAndRun() throws InterruptedException {
        //given
        Peer peer = mock(Peer.class);
        SnapBlocksResponseMessage msg = new SnapBlocksResponseMessage(1, Collections.emptyList(), Collections.emptyList());
        CountDownLatch latch = new CountDownLatch(1);
        doCountDownOnQueueEmpty(listener, latch);
        doReturn(true).when(snapRequestManager).processResponse(any());
        underTest.onEnter();

        //when
        underTest.onSnapBlocks(peer, msg);

        //then
        assertTrue(latch.await(THREAD_JOIN_TIMEOUT, TimeUnit.MILLISECONDS));

        ArgumentCaptor<SyncMessageHandler.Job> jobArg = ArgumentCaptor.forClass(SyncMessageHandler.Job.class);
        verify(listener, times(1)).onJobRun(jobArg.capture());

        assertEquals(peer, jobArg.getValue().getSender());
        assertEquals(msg.getMessageType(), jobArg.getValue().getMsgType());
    }

    @Test
    void givenNewBlockHeadersIsCalled_thenJobIsAddedAndRun() throws InterruptedException {
        //given
        Peer peer = mock(Peer.class);
        BlockHeadersResponseMessage msg = new BlockHeadersResponseMessage(1, Collections.emptyList());
        CountDownLatch latch = new CountDownLatch(1);
        doCountDownOnQueueEmpty(listener, latch);
        doReturn(true).when(snapRequestManager).processResponse(any());
        underTest.onEnter();

        //when
        underTest.newBlockHeaders(peer, msg);

        //then
        assertTrue(latch.await(THREAD_JOIN_TIMEOUT, TimeUnit.MILLISECONDS));

        ArgumentCaptor<SyncMessageHandler.Job> jobArg = ArgumentCaptor.forClass(SyncMessageHandler.Job.class);
        verify(listener, times(1)).onJobRun(jobArg.capture());

        assertEquals(peer, jobArg.getValue().getSender());
        assertEquals(MessageType.BLOCK_HEADERS_RESPONSE_MESSAGE, jobArg.getValue().getMsgType());
    }

    @Test
    void givenOnSnapStateChunkIsCalled_thenJobIsAddedAndRun() throws InterruptedException {
        //given
        Peer peer = mock(Peer.class);
        SnapStateChunkResponseMessage msg = new SnapStateChunkResponseMessage(1, new byte[0], 1, 1, 1, true);
        CountDownLatch latch = new CountDownLatch(1);
        doCountDownOnQueueEmpty(listener, latch);
        doReturn(true).when(snapRequestManager).processResponse(any());
        underTest.onEnter();

        //when
        underTest.onSnapStateChunk(peer, msg);

        //then
        assertTrue(latch.await(THREAD_JOIN_TIMEOUT, TimeUnit.MILLISECONDS));

        ArgumentCaptor<SyncMessageHandler.Job> jobArg = ArgumentCaptor.forClass(SyncMessageHandler.Job.class);
        verify(listener, times(1)).onJobRun(jobArg.capture());

        assertEquals(peer, jobArg.getValue().getSender());
        assertEquals(msg.getMessageType(), jobArg.getValue().getMsgType());
    }

    @Test
    void testSetAndGetLastBlock() {
        Block mockBlock = mock(Block.class);
        BlockDifficulty mockBlockDifficulty = mock(BlockDifficulty.class);
        Peer mockPeer = mock(Peer.class);

        underTest.setLastBlock(mockBlock, mockBlockDifficulty, mockPeer);

        assertEquals(mockBlock, underTest.getLastBlock());
        assertEquals(mockBlockDifficulty, underTest.getLastBlockDifficulty());
        assertEquals(mockPeer, underTest.getLastBlockSender());
    }

    @Test
    void testSetAndGetStateChunkSize() {
        BigInteger expectedSize = BigInteger.valueOf(100L);
        underTest.setStateChunkSize(expectedSize);
        assertEquals(expectedSize, underTest.getStateChunkSize());
    }

    @Test
    void testSetAndGetStateSize() {
        BigInteger expectedSize = BigInteger.valueOf(1000L);
        underTest.setStateSize(expectedSize);
        assertEquals(expectedSize, underTest.getStateSize());
    }

    @Test
    void testGetChunkTaskQueue() {
        Queue<ChunkTask> queue = underTest.getChunkTaskQueue();
        assertNotNull(queue);
    }

    @Test
    void testSetAndGetNextExpectedFrom() {
        long expectedValue = 100L;
        underTest.setNextExpectedFrom(expectedValue);
        assertEquals(expectedValue, underTest.getNextExpectedFrom());
    }

    private static void doCountDownOnQueueEmpty(SyncMessageHandler.Listener listener, CountDownLatch latch) {
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(listener).onQueueEmpty();
    }

    @Test
    void testGetSnapStateChunkQueue() {
        PriorityQueue<SnapStateChunkResponseMessage> queue = underTest.getSnapStateChunkQueue();
        assertNotNull(queue);
    }

    @Test
    void testSetAndGetRemoteRootHash() {
        byte[] mockRootHash = new byte[]{1, 2, 3};
        underTest.setRemoteRootHash(mockRootHash);
        assertArrayEquals(mockRootHash, underTest.getRemoteRootHash());
    }

    @Test
    void testSetAndGetRemoteTrieSize() {
        long expectedSize = 12345L;
        underTest.setRemoteTrieSize(expectedSize);
        assertEquals(expectedSize, underTest.getRemoteTrieSize());
    }

    @Test
    void testConnectBlocks() {
        BlockConnectorHelper blockConnectorHelper = mock(BlockConnectorHelper.class);
        Pair<Block, BlockDifficulty> mockBlockPair = mock(Pair.class);
        underTest.addBlock(mockBlockPair);
        ArgumentCaptor<List<Pair<Block, BlockDifficulty>>> captor = ArgumentCaptor.forClass(List.class);

        underTest.connectBlocks(blockConnectorHelper);

        verify(blockConnectorHelper, times(1)).startConnecting(captor.capture());
        assertTrue(captor.getValue().contains(mockBlockPair));
    }


}
