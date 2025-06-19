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
import co.rsk.crypto.Keccak256;
import co.rsk.net.messages.*;
import co.rsk.net.sync.SnapSyncState;
import co.rsk.net.sync.SnapshotPeersInformation;
import co.rsk.net.sync.SyncMessageHandler;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.trie.TrieDTO;
import co.rsk.trie.TrieStore;
import co.rsk.util.HexUtils;
import co.rsk.validators.BlockHeaderParentDependantValidationRule;
import co.rsk.validators.BlockHeaderValidationRule;
import co.rsk.validators.BlockParentDependantValidationRule;
import co.rsk.validators.BlockValidationRule;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Blockchain;
import org.ethereum.core.TransactionPool;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.datasource.DbKind;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.KeyValueDataSourceUtils;
import org.ethereum.db.BlockStore;
import org.ethereum.util.RLP;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static co.rsk.net.sync.SnapSyncRequestManager.PeerSelector;
import static co.rsk.net.sync.SnapSyncRequestManager.RequestFactory;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class SnapshotProcessorTest {
    @TempDir
    private Path tempDir;

    private record ChunksResponse (SnapSyncState state, Peer peer, SnapStateChunkResponseMessage responseMessage) {}

    private static final int TEST_CHUNK_SIZE = 200;
    private static final int TEST_CHECKPOINT_DISTANCE = 10;
    private static final int TEST_MAX_SENDER_REQUESTS = 3;
    private static final long THREAD_JOIN_TIMEOUT = 10_000; // 10 secs

    private Blockchain blockchain;
    private TransactionPool transactionPool;
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
    private SnapshotProcessor underTest;
    private KeyValueDataSource tmpSnapSyncKeyValueDataSource;

    @BeforeEach
    void setUp() throws UnknownHostException {
        peer = mockedPeer();
        when(peersInformation.getBestSnapPeerCandidates()).thenReturn(Collections.singletonList(peer));
    }

    @AfterEach
    void tearDown() {
        if (underTest != null) {
            underTest.stop();
        }
    }

    @Test
    void givenStartSyncingIsCalled_thenSnapStatusStartToBeRequestedFromPeer() throws IOException {
        //given
        initializeBlockchainWithAmountOfBlocks(10);
        underTest = new SnapshotProcessor(
                blockchain,
                trieStore,
                peersInformation,
                blockStore,
                transactionPool,
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                TEST_CHUNK_SIZE,
                TEST_CHECKPOINT_DISTANCE,
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
    void givenSnapStatusResponseCalled_thenSnapChunkRequestsAreMade() throws IOException {
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
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                TEST_CHUNK_SIZE,
                TEST_CHECKPOINT_DISTANCE,
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
    void givenSnapStatusRequestReceived_thenSnapStatusResponseIsSent() throws IOException {
        //given
        initializeBlockchainWithAmountOfBlocks(5010);
        underTest = new SnapshotProcessor(
                blockchain,
                trieStore,
                peersInformation,
                blockStore,
                transactionPool,
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                TEST_CHUNK_SIZE,
                TEST_CHECKPOINT_DISTANCE,
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
    void givenSnapBlockRequestReceived_thenSnapBlocksResponseMessageIsSent() throws IOException {
        //given
        initializeBlockchainWithAmountOfBlocks(5010);
        underTest = new SnapshotProcessor(
                blockchain,
                trieStore,
                peersInformation,
                blockStore,
                transactionPool,
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                TEST_CHUNK_SIZE,
                TEST_CHECKPOINT_DISTANCE,
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
    void givenSnapBlocksResponseReceived_thenSnapBlocksRequestMessageIsSent() throws IOException {
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
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                200,
                TEST_CHECKPOINT_DISTANCE,
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
    void givenSnapStateChunkRequest_thenSnapStateChunkResponseMessageIsSent() throws IOException {
        //given
        initializeBlockchainWithAmountOfBlocks(1000);
        underTest = new SnapshotProcessor(
                blockchain,
                trieStore,
                peersInformation,
                blockStore,
                transactionPool,
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                TEST_CHUNK_SIZE,
                TEST_CHECKPOINT_DISTANCE,
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
    void givenProcessSnapStatusRequestIsCalled_thenInternalOneIsCalledLater() throws InterruptedException, IOException {
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
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                TEST_CHUNK_SIZE,
                TEST_CHECKPOINT_DISTANCE,
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
    void givenProcessSnapStatusRequestIsCalledFourTimes_thenItGetsRateLimited() throws InterruptedException, IOException {
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
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                TEST_CHUNK_SIZE,
                TEST_CHECKPOINT_DISTANCE,
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
    void givenProcessSnapBlocksRequestIsCalled_thenInternalOneIsCalledLater() throws InterruptedException, IOException {
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
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                TEST_CHUNK_SIZE,
                TEST_CHECKPOINT_DISTANCE,
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
    void givenProcessSnapBlocksRequestIsCalledFourTimes_thenItGetsRateLimited() throws InterruptedException, IOException {
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
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                TEST_CHUNK_SIZE,
                TEST_CHECKPOINT_DISTANCE,
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
    void givenProcessStateChunkRequestIsCalled_thenInternalOneIsCalledLater() throws InterruptedException, IOException {
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
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                TEST_CHUNK_SIZE,
                TEST_CHECKPOINT_DISTANCE,
                TEST_MAX_SENDER_REQUESTS,
                true,
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
    void givenProcessStateChunkRequestIsCalledFourTimes_thenItGetsRateLimited() throws InterruptedException, IOException {
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
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                TEST_CHUNK_SIZE,
                TEST_CHECKPOINT_DISTANCE,
                TEST_MAX_SENDER_REQUESTS,
                true,
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
    void givenErrorRLPData_thenOnStateChunkErrorIsCalled() throws IOException {
        underTest = new SnapshotProcessor(
                blockchain,
                trieStore,
                peersInformation,
                blockStore,
                transactionPool,
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                TEST_CHUNK_SIZE,
                TEST_CHECKPOINT_DISTANCE,
                TEST_MAX_SENDER_REQUESTS,
                true,
                false
        );

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
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                TEST_CHUNK_SIZE,
                TEST_CHECKPOINT_DISTANCE,
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

    @Test
    void givenProcessStateChunkResponseIsCalled_thenTemporaryDatasourceShouldFetchedAndCleanedUpProperly() {
        final var blockChainBuilder = new BlockChainBuilder();
        blockChainBuilder.build();

        blockStore = mock(BlockStore.class);
        blockchain = mock(Blockchain.class);
        transactionPool = mock(TransactionPool.class);
        doReturn(true).when(blockStore).isBlockExist(Mockito.any(byte[].class));
        trieStore = mock(TrieStore.class);

        //given
        underTest = new SnapshotProcessor(
                blockchain,
                trieStore,
                peersInformation,
                blockStore,
                transactionPool,
                blockParentValidator,
                blockValidator,
                blockHeaderParentValidator,
                blockHeaderValidator,
                TEST_CHUNK_SIZE,
                TEST_CHECKPOINT_DISTANCE,
                TEST_MAX_SENDER_REQUESTS,
                true,
                false,
                listener);

        //when
        readChunks(chunk -> underTest.processStateChunkResponse(chunk.state(), chunk.peer(), chunk.responseMessage()));

        //then
        Assertions.assertTrue(tmpSnapSyncKeyValueDataSource.keys().isEmpty());
        Mockito.verify(tmpSnapSyncKeyValueDataSource, Mockito.times(658)).put(Mockito.any(byte[].class), Mockito.any(byte[].class));
        Mockito.verify(trieStore, Mockito.times(655)).saveDTO(Mockito.any(TrieDTO.class));
    }

    private void initializeBlockchainWithAmountOfBlocks(int numberOfBlocks) {
        BlockChainBuilder blockChainBuilder = new BlockChainBuilder();
        blockchain = blockChainBuilder.ofSize(numberOfBlocks);
        transactionPool = blockChainBuilder.getTransactionPool();
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

    private void readChunks (Consumer<ChunksResponse> readFn) {
        final var tmpDatabasePath = tempDir.resolve(SnapSyncState.TMP_NODES_DIR_NAME);
        tmpSnapSyncKeyValueDataSource = Mockito.spy(KeyValueDataSourceUtils.makeDataSource(tmpDatabasePath, DbKind.ROCKS_DB));

        try {
            final var chunksFile = Objects.requireNonNull(this.getClass().getResource("/trie/chunksData.txt")).getFile();
            final var reader = new BufferedReader(new FileReader(chunksFile));

            reader.lines().forEach(line -> {
                try {
                    final var chunksDataMap = new ObjectMapper().readValue(line, new TypeReference<HashMap<String,String>>() {});
                    final var chunkResponse = buildChunksToProcess(chunksDataMap, tmpSnapSyncKeyValueDataSource);
                    readFn.accept(chunkResponse);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            });

            reader.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private ChunksResponse buildChunksToProcess (Map<String, String> chunksDataMap, KeyValueDataSource tmpSnapSyncKeyValueDataSource) {
        final var remoteRootHash = HexUtils.stringHexToByteArray(chunksDataMap.get("state.getRemoteRootHash()"));
        // This data was extracted from a large data set of chunks,
        // that means the last verification for the whole trie will fail
        // because there will be missing parts.
        // In order to make it work for this test, we added a
        // getFinalRemoteRootHash to the chunksData.txt, so we can force
        // the last verification to pass
        final var finalRemoteRootHash = chunksDataMap.containsKey("state.getFinalRemoteRootHash()") ? HexUtils.stringHexToByteArray(chunksDataMap.get("state.getFinalRemoteRootHash()")) : null;
        final var id = Long.parseLong(chunksDataMap.get("responseMessage.getId()"));
        final var from = Long.parseLong(chunksDataMap.get("responseMessage.getFrom()"));
        final var to = Long.parseLong(chunksDataMap.get("responseMessage.getTo()"));
        final var isComplete = Boolean.parseBoolean(chunksDataMap.get("responseMessage.isComplete()"));
        final var chunk = HexUtils.stringHexToByteArray(chunksDataMap.get("responseMessage.getChunkOfTrieKeyValue()"));
        final var blockNumber = Long.parseLong(chunksDataMap.get("responseMessage.getBlockNumber()"));

        final var snapSyncState = mockSnapSyncStateToProcessChunk(from, remoteRootHash, finalRemoteRootHash, tmpSnapSyncKeyValueDataSource);

        return new ChunksResponse(snapSyncState, peer, new SnapStateChunkResponseMessage(id, chunk, blockNumber, from, to, isComplete));
    }

    private SnapSyncState mockSnapSyncStateToProcessChunk(long from, byte[] remoteRootHash, byte[] finalRemoteRootHash, KeyValueDataSource tmpSnapSyncKeyValueDataSource) {
        final var blockHeader = mock(BlockHeader.class);
        final var snapSyncState = mock(SnapSyncState.class);
        final var blockHeaderParentHash = new Keccak256(Keccak256Helper.keccak256("a6b071a6a8138226b29e46a375d3f3fbeeab3efa98be2d565375d07012bb1aaa"));

        doReturn(from).when(snapSyncState).getNextExpectedFrom();
        doReturn(new PriorityQueue<>()).when(snapSyncState).getChunkTaskQueue();
        doReturn(remoteRootHash).doReturn(finalRemoteRootHash).when(snapSyncState).getRemoteRootHash();
        doReturn(BigInteger.ZERO).when(snapSyncState).getStateSize();
        doReturn(BigInteger.ZERO).when(snapSyncState).getStateChunkSize();
        doReturn(true).when(snapSyncState).isRunning();
        doReturn(new PriorityQueue<>(
                Comparator.comparingLong(SnapStateChunkResponseMessage::getFrom)
        )).when(snapSyncState).getSnapStateChunkQueue();
        doReturn(blockHeader).when(snapSyncState).getLastVerifiedBlockHeader();
        doReturn(blockHeaderParentHash).when(blockHeader).getParentHash();
        doReturn(tmpSnapSyncKeyValueDataSource).when(snapSyncState).getTmpSnapSyncKeyValueDataSource();

        return snapSyncState;
    }
}
