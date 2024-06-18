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
import co.rsk.net.messages.*;
import co.rsk.net.sync.PeersInformation;
import co.rsk.net.sync.SnapSyncState;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.trie.TrieStore;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.TransactionPool;
import org.ethereum.db.BlockStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

import static org.mockito.Mockito.*;

public class SnapshotProcessorTest {
    public static final int TEST_CHUNK_SIZE = 200;
    private Blockchain blockchain;
    private TransactionPool transactionPool;
    private BlockStore blockStore;
    private TrieStore trieStore;
    private Peer peer;
    private final PeersInformation peersInformation = mock(PeersInformation.class);
    private final SnapSyncState snapSyncState = mock(SnapSyncState.class);
    private SnapshotProcessor underTest;

    @BeforeEach
    void setUp() throws UnknownHostException {
        peer = mockedPeer();
        when(peersInformation.getBestPeerCandidates()).thenReturn(Collections.singletonList(peer));
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
                TEST_CHUNK_SIZE,
                false);
        //when
        underTest.startSyncing();
        //then
        verify(peer).sendMessage(any(SnapStatusRequestMessage.class));
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
                TEST_CHUNK_SIZE,
                false);

        for (long blockNumber = 0; blockNumber < blockchain.getSize(); blockNumber ++){
            Block currentBlock = blockchain.getBlockByNumber(blockNumber);
            blocks.add(currentBlock);
            difficulties.add(blockStore.getTotalDifficultyForHash(currentBlock.getHash().getBytes()));
        }

        SnapStatusResponseMessage snapStatusResponseMessage = new SnapStatusResponseMessage(blocks, difficulties, 100000L);

        doReturn(blocks.get(blocks.size() - 1)).when(snapSyncState).getLastBlock();
        doReturn(snapStatusResponseMessage.getTrieSize()).when(snapSyncState).getRemoteTrieSize();
        doReturn(new LinkedList<>()).when(snapSyncState).getChunkTaskQueue();

        underTest.startSyncing();

        //when
        underTest.processSnapStatusResponse(snapSyncState, peer, snapStatusResponseMessage);

        //then
        verify(peer, atLeast(3)).sendMessage(any()); // 1 for SnapStatusRequestMessage, 1 for SnapBlocksRequestMessage and 1 for SnapStateChunkRequestMessage
        verify(peersInformation, times(2)).getBestPeerCandidates();
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
                TEST_CHUNK_SIZE,
                false);
        //when
        underTest.processSnapStatusRequestInternal(peer, mock(SnapStatusRequestMessage.class));

        //then
        verify(peer, atLeast(1)).sendMessage(any(SnapStatusResponseMessage.class));
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
                TEST_CHUNK_SIZE,
                false);

        SnapBlocksRequestMessage snapBlocksRequestMessage = new SnapBlocksRequestMessage(460);
        //when
        underTest.processSnapBlocksRequestInternal(peer, snapBlocksRequestMessage);

        //then
        verify(peer, atLeast(1)).sendMessage(any(SnapBlocksResponseMessage.class));
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
                200,
                false);

        for (long blockNumber = 0; blockNumber < blockchain.getSize(); blockNumber ++){
            Block currentBlock = blockchain.getBlockByNumber(blockNumber);
            blocks.add(currentBlock);
            difficulties.add(blockStore.getTotalDifficultyForHash(currentBlock.getHash().getBytes()));
        }

        SnapStatusResponseMessage snapStatusResponseMessage = new SnapStatusResponseMessage(blocks, difficulties, 100000L);
        doReturn(new LinkedList<>()).when(snapSyncState).getChunkTaskQueue();

        underTest.startSyncing();
        underTest.processSnapStatusResponse(snapSyncState, peer, snapStatusResponseMessage);

        SnapBlocksResponseMessage snapBlocksResponseMessage = new SnapBlocksResponseMessage(blocks, difficulties);

        when(snapSyncState.getLastBlock()).thenReturn(blocks.get(blocks.size() - 1));
        //when
        underTest.processSnapBlocksResponse(snapSyncState, peer, snapBlocksResponseMessage);

        //then
        verify(peer, atLeast(2)).sendMessage(any(SnapBlocksRequestMessage.class));
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
                TEST_CHUNK_SIZE,
                false);

        SnapStateChunkRequestMessage snapStateChunkRequestMessage = new SnapStateChunkRequestMessage(1L, 1L,1, TEST_CHUNK_SIZE);

        //when
        underTest.processStateChunkRequestInternal(peer, snapStateChunkRequestMessage);

        //then
        verify(peer, timeout(5000).atLeast(1)).sendMessage(any(SnapStateChunkResponseMessage.class)); // We have to wait because this method does the job insides thread
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
        when(peersInformation.getBestPeerCandidates()).thenReturn(Arrays.asList(peer));
        return mockedPeer;
    }

}