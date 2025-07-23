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
package co.rsk.net;

import co.rsk.core.BlockDifficulty;
import co.rsk.db.StateRootHandler;
import co.rsk.net.messages.*;
import co.rsk.net.sync.SnapSyncState;
import co.rsk.net.sync.SnapshotPeersInformation;
import co.rsk.net.sync.SyncConfiguration;
import co.rsk.net.sync.SyncMessageHandler;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.trie.TrieStore;
import co.rsk.validators.BlockHeaderParentDependantValidationRule;
import co.rsk.validators.BlockHeaderValidationRule;
import co.rsk.validators.BlockParentDependantValidationRule;
import co.rsk.validators.BlockValidationRule;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.TransactionPool;
import org.ethereum.db.BlockStore;
import org.ethereum.util.RLP;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static co.rsk.net.sync.SnapSyncRequestManager.PeerSelector;
import static co.rsk.net.sync.SnapSyncRequestManager.RequestFactory;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SnapshotProcessorTest {
    private static final int TEST_CHUNK_SIZE = 200;
    private static final int TEST_MAX_SENDER_REQUESTS = 3;
    private static final long THREAD_JOIN_TIMEOUT = 10_000; // 10 secs

    private Blockchain blockchain;
    private TransactionPool transactionPool;
    private StateRootHandler stateRootHandler;
    private BlockStore blockStore;
    private TrieStore trieStore;
    private Peer peer;
    private final SnapshotPeersInformation peersInformation = mock(SnapshotPeersInformation.class);
    private final SnapSyncState snapSyncState = mock(SnapSyncState.class);
    private final SyncMessageHandler.Listener listener = mock(SyncMessageHandler.Listener.class);
    private final BlockParentDependantValidationRule blockParentValidator = mock(BlockParentDependantValidationRule.class);
    private final BlockValidationRule blockValidator = mock(BlockValidationRule.class);
    private final BlockHeaderParentDependantValidationRule blockHeaderParentValidator = mock(BlockHeaderParentDependantValidationRule.class);
    private final BlockHeaderValidationRule blockHeaderValidator = mock(BlockHeaderValidationRule.class);
    private final SyncConfiguration syncConfiguration = mock(SyncConfiguration.class);
    private SnapshotProcessor underTest;

    @BeforeEach
    void setUp() throws UnknownHostException {
        peer = mockedPeer();
        when(peersInformation.getBestSnapPeerCandidates()).thenReturn(Collections.singletonList(peer));

        when(syncConfiguration.getChunkSize()).thenReturn(5);
        when(syncConfiguration.getMaxSkeletonChunks()).thenReturn(2);
    }

    @AfterEach
    void tearDown() {
        if (underTest != null) {
            underTest.stop();
        }
    }

    @Test
    void givenStartSyncingIsCalled_thenSnapStatusStartToBeRequestedFromPeer() {
        //given
        initializeBlockchainWithAmountOfBlocks(10);
        underTest = new SnapshotProcessor(
                blockchain,
                trieStore,
                peersInformation,
                blockStore,
                transactionPool,
                stateRootHandler,
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                TEST_CHUNK_SIZE,
                syncConfiguration,
                TEST_MAX_SENDER_REQUESTS,
                true,
                false);
        doReturn(Optional.of(peer)).when(peersInformation).getBestSnapPeer();
        //when
        underTest.startSyncing(snapSyncState);
        //then
        verify(snapSyncState).submitRequest(any(PeerSelector.class), any(RequestFactory.class));
    }

    @Test
    void givenSnapStatusResponseCalled_thenSnapChunkRequestsAreMade() {
        //given
        List<Block> blocks = new ArrayList<>();
        List<BlockDifficulty> difficulties = new ArrayList<>();
        initializeBlockchainWithAmountOfBlocks(10);
        underTest = new SnapshotProcessor(
                blockchain,
                trieStore,
                peersInformation,
                blockStore,
                transactionPool,
                stateRootHandler,
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                TEST_CHUNK_SIZE,
                syncConfiguration,
                TEST_MAX_SENDER_REQUESTS,
                true,
                false);

        for (long blockNumber = 0; blockNumber < blockchain.getSize(); blockNumber++) {
            Block currentBlock = blockchain.getBlockByNumber(blockNumber);
            blocks.add(currentBlock);
            difficulties.add(blockStore.getTotalDifficultyForHash(currentBlock.getHash().getBytes()));
        }

        SnapStatusResponseMessage snapStatusResponseMessage = new SnapStatusResponseMessage(1, blocks, difficulties, 100000L);

        doReturn(blocks.get(blocks.size() - 1)).when(snapSyncState).getLastBlock();
        doReturn(snapStatusResponseMessage.getTrieSize()).when(snapSyncState).getRemoteTrieSize();
        doReturn(new LinkedList<>()).when(snapSyncState).getChunkTaskQueue();
        doReturn(Optional.of(peer)).when(peersInformation).getBestSnapPeer();
        doReturn(true).when(snapSyncState).isRunning();
        doReturn(true).when(blockValidator).isValid(any());
        doReturn(true).when(blockParentValidator).isValid(any(), any());

        underTest.startSyncing(snapSyncState);

        //when
        underTest.processSnapStatusResponse(snapSyncState, peer, snapStatusResponseMessage);

        //then
        verify(snapSyncState, times(2)).submitRequest(any(PeerSelector.class), any(RequestFactory.class)); // 1 for SnapStatusRequestMessage, 1 for SnapBlocksRequestMessage and 1 for SnapStateChunkRequestMessage
        verify(peersInformation, times(1)).getBestSnapPeer();
    }

    @Test
    void givenSnapStatusRequestReceived_thenSnapStatusResponseIsSent() {
        //given
        initializeBlockchainWithAmountOfBlocks(5010);
        underTest = new SnapshotProcessor(
                blockchain,
                trieStore,
                peersInformation,
                blockStore,
                transactionPool,
                stateRootHandler,
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                TEST_CHUNK_SIZE,
                syncConfiguration,
                TEST_MAX_SENDER_REQUESTS,
                true,
                false);

        ArgumentCaptor<SnapStatusResponseMessage> captor = ArgumentCaptor.forClass(SnapStatusResponseMessage.class);


        //when
        underTest.processSnapStatusRequestInternal(peer, mock(SnapStatusRequestMessage.class));

        //then
        verify(peer, atLeast(1)).sendMessage(captor.capture());

        SnapStatusResponseMessage capturedMessage = captor.getValue();
        assertNotNull(capturedMessage);
        int blockSize = capturedMessage.getBlocks().size();
        assertEquals(401, blockSize);
        assertEquals(4590L,capturedMessage.getBlocks().get(0).getNumber());
        assertEquals(4990L,capturedMessage.getBlocks().get(blockSize-1).getNumber());
    }

    @Test
    void givenSnapBlockRequestReceived_thenSnapBlocksResponseMessageIsSent() {
        //given
        initializeBlockchainWithAmountOfBlocks(5010);
        underTest = new SnapshotProcessor(
                blockchain,
                trieStore,
                peersInformation,
                blockStore,
                transactionPool,
                stateRootHandler,
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                TEST_CHUNK_SIZE,
                syncConfiguration,
                TEST_MAX_SENDER_REQUESTS,
                true,
                false);

        ArgumentCaptor<SnapBlocksResponseMessage> captor = ArgumentCaptor.forClass(SnapBlocksResponseMessage.class);

        SnapBlocksRequestMessage snapBlocksRequestMessage = new SnapBlocksRequestMessage(1, 460);
        //when
        underTest.processSnapBlocksRequestInternal(peer, snapBlocksRequestMessage);

        //then
        verify(peer, atLeast(1)).sendMessage(captor.capture());
        SnapBlocksResponseMessage capturedMessage = captor.getValue();
        assertNotNull(capturedMessage);
        int blockSize = capturedMessage.getBlocks().size();
        assertEquals(400, blockSize);
        assertEquals(60L,capturedMessage.getBlocks().get(0).getNumber());
        assertEquals(459,capturedMessage.getBlocks().get(blockSize-1).getNumber());
    }

    @Test
    void givenSnapBlocksResponseReceived_thenSnapBlocksRequestMessageIsSent() {
        //given
        List<Block> blocks = new ArrayList<>();
        List<BlockDifficulty> difficulties = new ArrayList<>();
        initializeBlockchainWithAmountOfBlocks(10);
        underTest = new SnapshotProcessor(
                blockchain,
                trieStore,
                peersInformation,
                blockStore,
                transactionPool,
                stateRootHandler,
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                200,
                syncConfiguration,
                TEST_MAX_SENDER_REQUESTS,
                true,
                false);

        for (long blockNumber = 0; blockNumber < blockchain.getSize(); blockNumber++) {
            Block currentBlock = blockchain.getBlockByNumber(blockNumber);
            blocks.add(currentBlock);
            difficulties.add(blockStore.getTotalDifficultyForHash(currentBlock.getHash().getBytes()));
        }

        SnapStatusResponseMessage snapStatusResponseMessage = new SnapStatusResponseMessage(1, blocks, difficulties, 100000L);
        doReturn(true).when(snapSyncState).isRunning();
        doReturn(true).when(blockValidator).isValid(any());
        doReturn(true).when(blockParentValidator).isValid(any(), any());
        doReturn(new LinkedList<>()).when(snapSyncState).getChunkTaskQueue();

        underTest.startSyncing(snapSyncState);
        underTest.processSnapStatusResponse(snapSyncState, peer, snapStatusResponseMessage);

        SnapBlocksResponseMessage snapBlocksResponseMessage = new SnapBlocksResponseMessage(1, blocks, difficulties);

        when(snapSyncState.getLastBlock()).thenReturn(blocks.get(blocks.size() - 1));
        //when
        underTest.processSnapBlocksResponse(snapSyncState, peer, snapBlocksResponseMessage);

        //then
        verify(snapSyncState, atLeast(2)).submitRequest(any(PeerSelector.class), any(RequestFactory.class));
    }

    @Test
    void givenSnapStateChunkRequest_thenSnapStateChunkResponseMessageIsSent() {
        //given
        initializeBlockchainWithAmountOfBlocks(1000);
        underTest = new SnapshotProcessor(
                blockchain,
                trieStore,
                peersInformation,
                blockStore,
                transactionPool,
                stateRootHandler,
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                TEST_CHUNK_SIZE,
                syncConfiguration,
                TEST_MAX_SENDER_REQUESTS,
                true,
                false);

        SnapStateChunkRequestMessage snapStateChunkRequestMessage = new SnapStateChunkRequestMessage(1L, 1L, 1);

        //when
        underTest.processStateChunkRequestInternal(peer, snapStateChunkRequestMessage);

        //then
        verify(peer, timeout(5000).atLeast(1)).sendMessage(any(SnapStateChunkResponseMessage.class)); // We have to wait because this method does the job insides thread
    }

    @Test
    void givenProcessSnapStatusRequestIsCalled_thenInternalOneIsCalledLater() throws InterruptedException {
        //given
        Peer mPeer = mock(Peer.class);
        SnapStatusRequestMessage msg = mock(SnapStatusRequestMessage.class);
        CountDownLatch latch = new CountDownLatch(2);
        doCountDownOnQueueEmpty(listener, latch);
        underTest = new SnapshotProcessor(
                blockchain,
                trieStore,
                peersInformation,
                blockStore,
                transactionPool,
                stateRootHandler,
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                TEST_CHUNK_SIZE,
                syncConfiguration,
                TEST_MAX_SENDER_REQUESTS,
                true,
                false,
                listener) {
            @Override
            void processSnapStatusRequestInternal(Peer sender, SnapStatusRequestMessage requestMessage) {
                latch.countDown();
            }
        };
        underTest.start();

        //when
        underTest.processSnapStatusRequest(mPeer, msg);

        //then
        assertTrue(latch.await(THREAD_JOIN_TIMEOUT, TimeUnit.MILLISECONDS));

        ArgumentCaptor<SyncMessageHandler.Job> jobArg = ArgumentCaptor.forClass(SyncMessageHandler.Job.class);
        verify(listener, times(1)).onJobRun(jobArg.capture());

        assertEquals(mPeer, jobArg.getValue().getSender());
        assertEquals(msg.getMessageType(), jobArg.getValue().getMsgType());
    }

    @Test
    void givenProcessSnapStatusRequestIsCalledFourTimes_thenItGetsRateLimited() throws InterruptedException {
        //given
        NodeID nodeID = mock(NodeID.class);
        Peer mPeer = mock(Peer.class);
        when(mPeer.getPeerNodeID()).thenReturn(nodeID);
        SnapStatusRequestMessage msg = mock(SnapStatusRequestMessage.class);
        CountDownLatch execLatch = new CountDownLatch(4);
        CountDownLatch startLatch = new CountDownLatch(1);
        doCountDownOnQueueEmpty(listener, execLatch);
        doAnswer(invocation -> {
            assertTrue(startLatch.await(THREAD_JOIN_TIMEOUT, TimeUnit.MILLISECONDS));
            return null;
        }).when(listener).onStart();
        underTest = new SnapshotProcessor(
                blockchain,
                trieStore,
                peersInformation,
                blockStore,
                transactionPool,
                stateRootHandler,
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                TEST_CHUNK_SIZE,
                syncConfiguration,
                TEST_MAX_SENDER_REQUESTS,
                true,
                false,
                listener) {
            @Override
            void processSnapStatusRequestInternal(Peer sender, SnapStatusRequestMessage requestMessage) {
                execLatch.countDown();
            }
        };
        underTest.start();

        //when
        for (int i = 0; i < 4; i++) {
            underTest.processSnapStatusRequest(mPeer, msg);
        }
        startLatch.countDown();

        //then
        assertTrue(execLatch.await(THREAD_JOIN_TIMEOUT, TimeUnit.MILLISECONDS));

        ArgumentCaptor<SyncMessageHandler.Job> jobArg = ArgumentCaptor.forClass(SyncMessageHandler.Job.class);
        verify(listener, times(3)).onJobRun(jobArg.capture());
    }

    @Test
    void givenProcessSnapBlocksRequestIsCalled_thenInternalOneIsCalledLater() throws InterruptedException {
        //given
        Peer mPeer = mock(Peer.class);
        SnapBlocksRequestMessage msg = mock(SnapBlocksRequestMessage.class);
        CountDownLatch latch = new CountDownLatch(2);
        doCountDownOnQueueEmpty(listener, latch);
        underTest = new SnapshotProcessor(
                blockchain,
                trieStore,
                peersInformation,
                blockStore,
                transactionPool,
                stateRootHandler,
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                TEST_CHUNK_SIZE,
                syncConfiguration,
                TEST_MAX_SENDER_REQUESTS,
                true,
                false,
                listener) {
            @Override
            void processSnapBlocksRequestInternal(Peer sender, SnapBlocksRequestMessage requestMessage) {
                latch.countDown();
            }
        };
        underTest.start();

        //when
        underTest.processSnapBlocksRequest(mPeer, msg);

        //then
        assertTrue(latch.await(THREAD_JOIN_TIMEOUT, TimeUnit.MILLISECONDS));

        ArgumentCaptor<SyncMessageHandler.Job> jobArg = ArgumentCaptor.forClass(SyncMessageHandler.Job.class);
        verify(listener, times(1)).onJobRun(jobArg.capture());

        assertEquals(mPeer, jobArg.getValue().getSender());
        assertEquals(msg.getMessageType(), jobArg.getValue().getMsgType());
    }

    @Test
    void givenProcessSnapBlocksRequestIsCalledFourTimes_thenItGetsRateLimited() throws InterruptedException {
        //given
        NodeID nodeID = mock(NodeID.class);
        Peer mPeer = mock(Peer.class);
        when(mPeer.getPeerNodeID()).thenReturn(nodeID);
        SnapBlocksRequestMessage msg = mock(SnapBlocksRequestMessage.class);
        CountDownLatch execLatch = new CountDownLatch(4);
        CountDownLatch startLatch = new CountDownLatch(1);
        doCountDownOnQueueEmpty(listener, execLatch);
        doAnswer(invocation -> {
            assertTrue(startLatch.await(THREAD_JOIN_TIMEOUT, TimeUnit.MILLISECONDS));
            return null;
        }).when(listener).onStart();
        underTest = new SnapshotProcessor(
                blockchain,
                trieStore,
                peersInformation,
                blockStore,
                transactionPool,
                stateRootHandler,
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                TEST_CHUNK_SIZE,
                syncConfiguration,
                TEST_MAX_SENDER_REQUESTS,
                true,
                false,
                listener) {
            @Override
            void processSnapBlocksRequestInternal(Peer sender, SnapBlocksRequestMessage requestMessage) {
                execLatch.countDown();
            }
        };
        underTest.start();

        //when
        for (int i = 0; i < 4; i++) {
            underTest.processSnapBlocksRequest(mPeer, msg);
        }
        startLatch.countDown();

        //then
        assertTrue(execLatch.await(THREAD_JOIN_TIMEOUT, TimeUnit.MILLISECONDS));

        ArgumentCaptor<SyncMessageHandler.Job> jobArg = ArgumentCaptor.forClass(SyncMessageHandler.Job.class);
        verify(listener, times(3)).onJobRun(jobArg.capture());
    }

    @Test
    void givenProcessStateChunkRequestIsCalled_thenInternalOneIsCalledLater() throws InterruptedException {
        //given
        Peer mPeer = mock(Peer.class);
        SnapStateChunkRequestMessage msg = mock(SnapStateChunkRequestMessage.class);
        CountDownLatch latch = new CountDownLatch(2);
        doCountDownOnQueueEmpty(listener, latch);
        underTest = new SnapshotProcessor(
                blockchain,
                trieStore,
                peersInformation,
                blockStore,
                transactionPool,
                stateRootHandler,
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                TEST_CHUNK_SIZE,
                syncConfiguration,
                TEST_MAX_SENDER_REQUESTS,
                true,
                false,
                false,
                listener) {
            @Override
            void processStateChunkRequestInternal(Peer sender, SnapStateChunkRequestMessage requestMessage) {
                latch.countDown();
            }
        };
        underTest.start();

        //when
        underTest.processStateChunkRequest(mPeer, msg);

        //then
        assertTrue(latch.await(THREAD_JOIN_TIMEOUT, TimeUnit.MILLISECONDS));

        ArgumentCaptor<SyncMessageHandler.Job> jobArg = ArgumentCaptor.forClass(SyncMessageHandler.Job.class);
        verify(listener, times(1)).onJobRun(jobArg.capture());

        assertEquals(mPeer, jobArg.getValue().getSender());
        assertEquals(msg.getMessageType(), jobArg.getValue().getMsgType());
    }

    @Test
    void givenProcessStateChunkRequestIsCalledFourTimes_thenItGetsRateLimited() throws InterruptedException {
        //given
        NodeID nodeID = mock(NodeID.class);
        Peer mPeer = mock(Peer.class);
        when(mPeer.getPeerNodeID()).thenReturn(nodeID);
        SnapStateChunkRequestMessage msg = mock(SnapStateChunkRequestMessage.class);
        CountDownLatch execLatch = new CountDownLatch(4);
        CountDownLatch startLatch = new CountDownLatch(1);
        doCountDownOnQueueEmpty(listener, execLatch);
        doAnswer(invocation -> {
            assertTrue(startLatch.await(THREAD_JOIN_TIMEOUT, TimeUnit.MILLISECONDS));
            return null;
        }).when(listener).onStart();
        underTest = new SnapshotProcessor(
                blockchain,
                trieStore,
                peersInformation,
                blockStore,
                transactionPool,
                stateRootHandler,
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                TEST_CHUNK_SIZE,
                syncConfiguration,
                TEST_MAX_SENDER_REQUESTS,
                true,
                false,
                false,
                listener) {
            @Override
            void processStateChunkRequestInternal(Peer sender, SnapStateChunkRequestMessage request) {
                execLatch.countDown();
            }
        };
        underTest.start();

        //when
        for (int i = 0; i < 4; i++) {
            underTest.processStateChunkRequest(mPeer, msg);
        }
        startLatch.countDown();

        //then
        assertTrue(execLatch.await(THREAD_JOIN_TIMEOUT, TimeUnit.MILLISECONDS));

        ArgumentCaptor<SyncMessageHandler.Job> jobArg = ArgumentCaptor.forClass(SyncMessageHandler.Job.class);
        verify(listener, times(3)).onJobRun(jobArg.capture());
    }

    @Test
    void givenErrorRLPData_thenOnStateChunkErrorIsCalled() {
        underTest = new SnapshotProcessor(
                blockchain,
                trieStore,
                peersInformation,
                blockStore,
                transactionPool,
                stateRootHandler,
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                TEST_CHUNK_SIZE,
                syncConfiguration,
                TEST_MAX_SENDER_REQUESTS,
                true,
                false, false, null);

        PriorityQueue<SnapStateChunkResponseMessage> queue = new PriorityQueue<>(
                Comparator.comparingLong(SnapStateChunkResponseMessage::getFrom));
        when(snapSyncState.getSnapStateChunkQueue()).thenReturn(queue);
        when(snapSyncState.getChunkTaskQueue()).thenReturn(new LinkedList<>());
        SnapStateChunkResponseMessage responseMessage = mock(SnapStateChunkResponseMessage.class);
        when(snapSyncState.getNextExpectedFrom()).thenReturn(1L);
        when(responseMessage.getFrom()).thenReturn(1L);
        when(responseMessage.getChunkOfTrieKeyValue()).thenReturn(RLP.encodedEmptyList());
        doReturn(true).when(snapSyncState).isRunning();
        doReturn(true).when(blockValidator).isValid(any());
        doReturn(true).when(blockParentValidator).isValid(any(), any());
        underTest = spy(underTest);

        underTest.processStateChunkResponse(snapSyncState, peer, responseMessage);

        verify(underTest, times(1)).onStateChunkResponseError(snapSyncState, peer, responseMessage);
        verify(snapSyncState, times(1)).submitRequest(any(PeerSelector.class), any(RequestFactory.class));
    }

    @Test
    void givenProcessSnapStatusRequestCalledTwice_thenSecondCallUsesCachedValue() {
        // Given
        initializeBlockchainWithAmountOfBlocks(5010);
        underTest = spy(new SnapshotProcessor(
                blockchain,
                trieStore,
                peersInformation,
                blockStore,
                transactionPool,
                stateRootHandler,
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                TEST_CHUNK_SIZE,
                syncConfiguration,
                TEST_MAX_SENDER_REQUESTS,
                true,
                false));

        SnapStatusRequestMessage requestMessage = mock(SnapStatusRequestMessage.class);

        // When - First call should process normally and cache the result
        underTest.processSnapStatusRequestInternal(peer, requestMessage);

        // Verify first call generated a response and cached it
        ArgumentCaptor<SnapStatusResponseMessage> responseCaptor = ArgumentCaptor.forClass(SnapStatusResponseMessage.class);
        verify(peer).sendMessage(responseCaptor.capture());
        assertNotNull(underTest.getLastSnapStatusCache());

        // Store details of first response to compare with second call
        SnapStatusResponseMessage firstResponse = responseCaptor.getValue();

        // Reset mock to verify second call
        reset(peer);

        // When - Second call should use cached data
        underTest.processSnapStatusRequestInternal(peer, requestMessage);

        // Then - Verify second call used cached data
        verify(peer).sendMessage(responseCaptor.capture());
        SnapStatusResponseMessage secondResponse = responseCaptor.getValue();

        // Verify the blocks and difficulties are the same objects (from cache)
        assertSame(firstResponse.getBlocks(), secondResponse.getBlocks());
        assertSame(firstResponse.getDifficulties(), secondResponse.getDifficulties());
        assertEquals(firstResponse.getTrieSize(), secondResponse.getTrieSize());

        // Verify retrieveBlocksAndDifficultiesBackwards was only called once (for the first request)
        verify(underTest, times(1)).retrieveBlocksAndDifficultiesBackwards(
                anyLong(), anyLong(), any(LinkedList.class), any(LinkedList.class));
    }

    // Tests for processStateChunkRequest(Peer, SnapStateChunkV2RequestMessage)
    @Test
    void testProcessStateChunkRequest_ValidRequest() throws InterruptedException {
        // Given
        initializeBlockchainWithAmountOfBlocks(5);
        underTest = new SnapshotProcessor(
                blockchain,
                trieStore,
                peersInformation,
                blockStore,
                transactionPool,
                stateRootHandler,
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                TEST_CHUNK_SIZE,
                syncConfiguration,
                TEST_MAX_SENDER_REQUESTS,
                true,
                false,
                true, // useStateChunkV2 = true
                listener);
        
        SnapStateChunkV2RequestMessage requestMessage = new SnapStateChunkV2RequestMessage(
                1L, 
                blockchain.getBlockByNumber(1).getHash().getBytes(), 
                "testkey".getBytes()
        );
        
        CountDownLatch latch = new CountDownLatch(1);
        doCountDownOnQueueEmpty(listener, latch);
        underTest.start();

        // When
        underTest.processStateChunkRequest(peer, requestMessage);

        // Then
        assertTrue(latch.await(THREAD_JOIN_TIMEOUT, TimeUnit.MILLISECONDS));
        ArgumentCaptor<SyncMessageHandler.Job> jobArg = ArgumentCaptor.forClass(SyncMessageHandler.Job.class);
        verify(listener, times(1)).onJobRun(jobArg.capture());
        assertEquals(peer, jobArg.getValue().getSender());
        assertEquals(requestMessage.getMessageType(), jobArg.getValue().getMsgType());
    }

    @Test
    void testProcessStateChunkRequest_NotRunning() {
        // Given
        underTest = new SnapshotProcessor(
                blockchain,
                trieStore,
                peersInformation,
                blockStore,
                transactionPool,
                stateRootHandler,
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                TEST_CHUNK_SIZE,
                syncConfiguration,
                TEST_MAX_SENDER_REQUESTS,
                true,
                false,
                true, // useStateChunkV2 = true
                listener);
        // SnapshotProcessor not started, so isRunning should be false/null
        
        SnapStateChunkV2RequestMessage requestMessage = new SnapStateChunkV2RequestMessage(
                1L, 
                new byte[32], 
                "testkey".getBytes()
        );

        // When
        underTest.processStateChunkRequest(peer, requestMessage);

        // Then - No job should be scheduled when not running
        verify(listener, never()).onJobRun(any());
    }

    @Test
    void testProcessStateChunkRequest_UseStateChunkV1() throws InterruptedException {
        // Given
        initializeBlockchainWithAmountOfBlocks(5);
        underTest = new SnapshotProcessor(
                blockchain,
                trieStore,
                peersInformation,
                blockStore,
                transactionPool,
                stateRootHandler,
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                TEST_CHUNK_SIZE,
                syncConfiguration,
                TEST_MAX_SENDER_REQUESTS,
                true,
                false,
                false, // useStateChunkV2 = false
                listener);
        
        SnapStateChunkV2RequestMessage requestMessage = new SnapStateChunkV2RequestMessage(
                1L, 
                blockchain.getBlockByNumber(1).getHash().getBytes(), 
                "testkey".getBytes()
        );
        
        underTest.start();

        // When
        underTest.processStateChunkRequest(peer, requestMessage);

        // Then - Should log warning and not process
        // No job should be scheduled
        Thread.sleep(100); // Give some time for potential processing
        verify(listener, never()).onJobRun(any());
    }

    // Tests for processStateChunkV2RequestInternal()
    @Test
    void testProcessStateChunkV2RequestInternal_ValidRequest() {
        // Given
        initializeBlockchainWithAmountOfBlocks(5);
        underTest = new SnapshotProcessor(
                blockchain,
                trieStore,
                peersInformation,
                blockStore,
                transactionPool,
                stateRootHandler,
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                TEST_CHUNK_SIZE,
                syncConfiguration,
                TEST_MAX_SENDER_REQUESTS,
                true,
                false,
                true, // useStateChunkV2 = true
                listener);
        
                 Block testBlock = blockchain.getBlockByNumber(1);
         // StateRootHandler is a real object from BlockChainBuilder, no mocking needed
        
        SnapStateChunkV2RequestMessage requestMessage = new SnapStateChunkV2RequestMessage(
                123L, 
                testBlock.getHash().getBytes(), 
                null // fromKey = null for first chunk
        );

        // When
        underTest.processStateChunkV2RequestInternal(peer, requestMessage);

        // Then
        verify(peer, times(1)).sendMessage(any(SnapStateChunkV2ResponseMessage.class));
    }

    @Test
    void testProcessStateChunkV2RequestInternal_NullBlockHash() {
        // Given
        initializeBlockchainWithAmountOfBlocks(5);
        underTest = new SnapshotProcessor(
                blockchain,
                trieStore,
                peersInformation,
                blockStore,
                transactionPool,
                stateRootHandler,
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                TEST_CHUNK_SIZE,
                syncConfiguration,
                TEST_MAX_SENDER_REQUESTS,
                true,
                false,
                true,
                listener);
        
        SnapStateChunkV2RequestMessage requestMessage = new SnapStateChunkV2RequestMessage(
                123L, 
                null, // null blockHash
                "testkey".getBytes()
        );

        // When
        underTest.processStateChunkV2RequestInternal(peer, requestMessage);

        // Then - Should handle error and not send message
        verify(peer, never()).sendMessage(any());
    }

    @Test
    void testProcessStateChunkV2RequestInternal_BlockNotFound() {
        // Given
        initializeBlockchainWithAmountOfBlocks(5);
        underTest = new SnapshotProcessor(
                blockchain,
                trieStore,
                peersInformation,
                blockStore,
                transactionPool,
                stateRootHandler,
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                TEST_CHUNK_SIZE,
                syncConfiguration,
                TEST_MAX_SENDER_REQUESTS,
                true,
                false,
                true,
                listener);
        
        byte[] nonExistentBlockHash = new byte[32]; // Block that doesn't exist
        Arrays.fill(nonExistentBlockHash, (byte) 0xFF);
        
        SnapStateChunkV2RequestMessage requestMessage = new SnapStateChunkV2RequestMessage(
                123L, 
                nonExistentBlockHash, 
                "testkey".getBytes()
        );

        // When
        underTest.processStateChunkV2RequestInternal(peer, requestMessage);

        // Then - Should handle error and not send message
        verify(peer, never()).sendMessage(any());
    }

    @Test
    void testProcessStateChunkV2RequestInternal_LargeFromKey() {
        // Given
        initializeBlockchainWithAmountOfBlocks(5);
        underTest = new SnapshotProcessor(
                blockchain,
                trieStore,
                peersInformation,
                blockStore,
                transactionPool,
                stateRootHandler,
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                TEST_CHUNK_SIZE,
                syncConfiguration,
                TEST_MAX_SENDER_REQUESTS,
                true,
                false,
                true,
                listener);
        
        Block testBlock = blockchain.getBlockByNumber(1);
        byte[] oversizedFromKey = new byte[1025]; // Exceeds MAX_STATE_KEY_SIZE (1024)
        Arrays.fill(oversizedFromKey, (byte) 0x01);
        
        SnapStateChunkV2RequestMessage requestMessage = new SnapStateChunkV2RequestMessage(
                123L, 
                testBlock.getHash().getBytes(), 
                oversizedFromKey
        );

        // When
        underTest.processStateChunkV2RequestInternal(peer, requestMessage);

        // Then - Should handle error and not send message
        verify(peer, never()).sendMessage(any());
    }

    // Tests for processStateChunkResponse(SnapSyncState, Peer, SnapStateChunkV2ResponseMessage)
    @Test
    void testProcessStateChunkResponse_NotRunning() {
        // Given
        initializeBlockchainWithAmountOfBlocks(5);
        underTest = new SnapshotProcessor(
                blockchain,
                trieStore,
                peersInformation,
                blockStore,
                transactionPool,
                stateRootHandler,
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                TEST_CHUNK_SIZE,
                syncConfiguration,
                TEST_MAX_SENDER_REQUESTS,
                true,
                false,
                true,
                listener);
        
        when(snapSyncState.isRunning()).thenReturn(false);
        
        SnapStateChunkV2ResponseMessage responseMessage = mock(SnapStateChunkV2ResponseMessage.class);

        // When
        underTest.processStateChunkResponse(snapSyncState, peer, responseMessage);

        // Then - Should return early without processing
        verify(snapSyncState, never()).getLastBlock();
    }

    @Test
    void testProcessStateChunkResponse_InvalidChunkVersion() {
        // Given
        initializeBlockchainWithAmountOfBlocks(5);
        underTest = new SnapshotProcessor(
                blockchain,
                trieStore,
                peersInformation,
                blockStore,
                transactionPool,
                stateRootHandler,
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                TEST_CHUNK_SIZE,
                syncConfiguration,
                TEST_MAX_SENDER_REQUESTS,
                true,
                false,
                false, // useStateChunkV2 = false
                listener);
        
        when(snapSyncState.isRunning()).thenReturn(true);
        
        SnapStateChunkV2ResponseMessage responseMessage = mock(SnapStateChunkV2ResponseMessage.class);

        // When
        underTest.processStateChunkResponse(snapSyncState, peer, responseMessage);

        // Then - Should process error and not continue
        verify(peersInformation, times(1)).processSyncingError(eq(peer), eq(co.rsk.scoring.EventType.INVALID_STATE_CHUNK), anyString(), any());
        verify(snapSyncState, never()).getLastBlock();
    }

    @Test
    void testProcessStateChunkResponse_EmptyChunk() {
        // Given
        initializeBlockchainWithAmountOfBlocks(5);
        underTest = new SnapshotProcessor(
                blockchain,
                trieStore,
                peersInformation,
                blockStore,
                transactionPool,
                stateRootHandler,
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                TEST_CHUNK_SIZE,
                syncConfiguration,
                TEST_MAX_SENDER_REQUESTS,
                true,
                false,
                true, // useStateChunkV2 = true
                listener);
        
        when(snapSyncState.isRunning()).thenReturn(true);
        Block lastBlock = blockchain.getBlockByNumber(1);
        when(snapSyncState.getLastBlock()).thenReturn(lastBlock);
        
        co.rsk.trie.TrieChunk emptyChunk = new co.rsk.trie.TrieChunk(new LinkedHashMap<>(), co.rsk.trie.TrieChunk.Proof.EMPTY);
        SnapStateChunkV2ResponseMessage responseMessage = new SnapStateChunkV2ResponseMessage(123L, emptyChunk);

        // When
        underTest.processStateChunkResponse(snapSyncState, peer, responseMessage);

        // Then - Should process error for empty chunk
        verify(peersInformation, times(1)).processSyncingError(eq(peer), eq(co.rsk.scoring.EventType.INVALID_STATE_CHUNK), anyString(), any(), any());
    }

    @Test
    void testProcessStateChunkResponse_ChunkSizeExceedsMaximum() {
        // Given
        initializeBlockchainWithAmountOfBlocks(5);
        underTest = new SnapshotProcessor(
                blockchain,
                trieStore,
                peersInformation,
                blockStore,
                transactionPool,
                stateRootHandler,
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                TEST_CHUNK_SIZE,
                syncConfiguration,
                TEST_MAX_SENDER_REQUESTS,
                true,
                false,
                true, // useStateChunkV2 = true
                listener);
        
        when(snapSyncState.isRunning()).thenReturn(true);
        Block lastBlock = blockchain.getBlockByNumber(1);
        when(snapSyncState.getLastBlock()).thenReturn(lastBlock);
        
        // Create a chunk with too many items
        LinkedHashMap<byte[], byte[]> oversizedKeyValues = new LinkedHashMap<>();
        for (int i = 0; i < co.rsk.trie.TrieChunk.MAX_CHUNK_SIZE + 1; i++) {
            oversizedKeyValues.put(("key" + i).getBytes(), ("value" + i).getBytes());
        }
        co.rsk.trie.TrieChunk oversizedChunk = new co.rsk.trie.TrieChunk(oversizedKeyValues, co.rsk.trie.TrieChunk.Proof.EMPTY);
        SnapStateChunkV2ResponseMessage responseMessage = new SnapStateChunkV2ResponseMessage(123L, oversizedChunk);

        // When
        underTest.processStateChunkResponse(snapSyncState, peer, responseMessage);

        // Then - Should process error for oversized chunk
        verify(peersInformation, times(1)).processSyncingError(eq(peer), eq(co.rsk.scoring.EventType.INVALID_STATE_CHUNK), anyString(), any(), any(), any());
    }

    private void initializeBlockchainWithAmountOfBlocks(int numberOfBlocks) {
        BlockChainBuilder blockChainBuilder = new BlockChainBuilder();
        blockchain = blockChainBuilder.ofSize(numberOfBlocks);
        transactionPool = blockChainBuilder.getTransactionPool();
        stateRootHandler = blockChainBuilder.getStateRootHandler();
        blockStore = blockChainBuilder.getBlockStore();
        trieStore = blockChainBuilder.getTrieStore();
    }

    private Peer mockedPeer() throws UnknownHostException {
        Peer mockedPeer = mock(Peer.class);
        NodeID nodeID = mock(NodeID.class);
        when(mockedPeer.getPeerNodeID()).thenReturn(nodeID);
        when(mockedPeer.getAddress()).thenReturn(InetAddress.getByName("127.0.0.1"));
        when(peersInformation.getBestSnapPeerCandidates()).thenReturn(Arrays.asList(peer));
        return mockedPeer;
    }

    private static void doCountDownOnQueueEmpty(SyncMessageHandler.Listener listener, CountDownLatch latch) {
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(listener).onQueueEmpty();
    }
}
