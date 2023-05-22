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

package co.rsk.net.messages;

import co.rsk.config.RskSystemProperties;
import co.rsk.crypto.Keccak256;
import co.rsk.net.*;
import co.rsk.scoring.EventType;
import co.rsk.scoring.PeerScoringManager;
import org.ethereum.TestUtils;
import org.ethereum.config.Constants;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.net.server.ChannelManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MessageVisitorTest {

    private MessageVisitor target;
    private Peer sender;
    private ChannelManager channelManager;
    private PeerScoringManager peerScoringManager;
    private TransactionGateway transactionGateway;
    private SyncProcessor syncProcessor;
    private BlockProcessor blockProcessor;
    private RskSystemProperties config;

    @BeforeEach
    void setUp() {
        config = mock(RskSystemProperties.class);
        blockProcessor = mock(BlockProcessor.class);
        syncProcessor = mock(SyncProcessor.class);
        transactionGateway = mock(TransactionGateway.class);
        peerScoringManager = mock(PeerScoringManager.class);
        channelManager = mock(ChannelManager.class);
        sender = mock(Peer.class);

        target = new MessageVisitor(
                config,
                blockProcessor,
                syncProcessor,
                transactionGateway,
                peerScoringManager,
                channelManager,
                sender
        );
    }

    @Test
    void blockMessage_invalidblock() {
        BlockMessage message = mock(BlockMessage.class);
        Block block = mock(Block.class);
        NodeID peer = mock(NodeID.class);
        InetAddress peerAddress = mock(InetAddress.class);

        when(sender.getAddress()).thenReturn(peerAddress);
        when(sender.getPeerNodeID()).thenReturn(peer);
        when(message.getBlock()).thenReturn(block);
        Keccak256 blockHash = mock(Keccak256.class);
        when(block.getHash()).thenReturn(blockHash);

        BlockProcessResult result = mock(BlockProcessResult.class);
        when(result.isInvalidBlock()).thenReturn(true);
        when(blockProcessor.processBlock(any(), any())).thenReturn(result);

        target.apply(message);

        verify(peerScoringManager, times(1))
                .recordEvent(peer, peerAddress, EventType.INVALID_BLOCK,
                        "Invalid block {} {} at {}", block.getNumber(), null, MessageVisitor.class);
        verify(blockProcessor, times(1)).processBlock(any(), any());
    }

    @Test
    void blockMessage_genesisBlock() {
        BlockMessage message = mock(BlockMessage.class);
        Block block = mock(Block.class);

        when(message.getBlock()).thenReturn(block);
        when(block.isGenesis()).thenReturn(true);

        target.apply(message);

        verify(blockProcessor, never()).processBlock(any(), any());
    }

    @Test
    void blockMessage_advancedBlockNumber() {
        BlockMessage message = mock(BlockMessage.class);
        Block block = mock(Block.class);

        when(message.getBlock()).thenReturn(block);
        when(block.getNumber()).thenReturn(24L);
        when(blockProcessor.isAdvancedBlock(anyLong())).thenReturn(true);

        target.apply(message);

        verify(blockProcessor, never()).processBlock(any(), any());
    }

    @Test
    void blockMessage_ignoredForUnclesReward() {
        BlockMessage message = mock(BlockMessage.class);
        Block block = mock(Block.class);

        when(message.getBlock()).thenReturn(block);
        when(block.getNumber()).thenReturn(24L);
        when(blockProcessor.canBeIgnoredForUnclesRewards(anyLong())).thenReturn(true);

        target.apply(message);

        verify(blockProcessor, never()).processBlock(any(), any());
    }

    @Test
    void blockMessage_hasBlockInSomeBlockchain() {
        BlockMessage message = mock(BlockMessage.class);
        Block block = mock(Block.class);
        Keccak256 blockHash = mock(Keccak256.class);
        byte[] hashBytes = new byte[]{0x0F};

        when(message.getBlock()).thenReturn(block);
        when(block.getNumber()).thenReturn(24L);

        when(block.getHash()).thenReturn(blockHash);
        when(blockHash.getBytes()).thenReturn(hashBytes);

        when(blockProcessor.hasBlockInSomeBlockchain(hashBytes)).thenReturn(true);

        target.apply(message);

        verify(blockProcessor, never()).processBlock(any(), any());
    }

    @Test
    void blockMessage_dontRelayBlock() {
        BlockMessage message = mock(BlockMessage.class);
        Block block = mock(Block.class);
        Keccak256 blockHash = mock(Keccak256.class);
        BlockProcessResult blockProcessResult = mock(BlockProcessResult.class);
        NodeID peer = mock(NodeID.class);
        InetAddress peerAddress = mock(InetAddress.class);

        when(sender.getAddress()).thenReturn(peerAddress);
        when(sender.getPeerNodeID()).thenReturn(peer);

        when(message.getBlock()).thenReturn(block);
        when(block.getNumber()).thenReturn(24L);

        when(block.getHash()).thenReturn(blockHash);

        when(blockProcessor.processBlock(sender, block)).thenReturn(blockProcessResult);

        target.apply(message);

        verify(blockProcessor, times(1)).processBlock(sender, block);
        verify(peerScoringManager, times(1))
                .recordEvent(peer, peerAddress, EventType.VALID_BLOCK,
                        "Valid block {} {} at {}", block.getNumber(), null, MessageVisitor.class);
        verify(channelManager, never()).broadcastBlockHash(any(), any());
    }

    @Test
    void blockMessage_relayBlock() {
        BlockMessage message = mock(BlockMessage.class);
        Block block = mock(Block.class);
        Keccak256 blockHash = mock(Keccak256.class);
        BlockProcessResult blockProcessResult = mock(BlockProcessResult.class);
        NodeID peer = mock(NodeID.class);
        InetAddress peerAddress = mock(InetAddress.class);
        BlockNodeInformation blockNodeInformation = mock(BlockNodeInformation.class);
        byte[] hashBytes = new byte[]{0x0F};

        when(sender.getAddress()).thenReturn(peerAddress);
        when(sender.getPeerNodeID()).thenReturn(peer);

        when(message.getBlock()).thenReturn(block);
        when(block.getNumber()).thenReturn(24L);

        when(block.getHash()).thenReturn(blockHash);
        when(blockHash.getBytes()).thenReturn(hashBytes);

        when(blockProcessor.processBlock(sender, block)).thenReturn(blockProcessResult);
        when(blockProcessor.hasBetterBlockToSync()).thenReturn(false);
        when(blockProcessResult.wasBlockAdded(block)).thenReturn(true);
        when(blockProcessor.getNodeInformation()).thenReturn(blockNodeInformation);
        when(blockNodeInformation.getNodesByBlock(blockHash)).thenReturn(new HashSet<>());
        when(syncProcessor.getKnownPeersNodeIDs()).thenReturn(new HashSet<>());

        target.apply(message);

        verify(blockProcessor, times(1)).processBlock(sender, block);
        verify(peerScoringManager, times(1))
                .recordEvent(peer, peerAddress, EventType.VALID_BLOCK,
                        "Valid block {} {} at {}", block.getNumber(), null, MessageVisitor.class);
        verify(channelManager, times(1)).broadcastBlockHash(any(), any());
    }

    @Test
    void statusMessage() {
        StatusMessage message = mock(StatusMessage.class);
        Status status = mock(Status.class);
        when(message.getStatus()).thenReturn(status);

        target.apply(message);

        verify(syncProcessor, times(1)).processStatus(sender, status);
    }

    @Test
    void getBlockMessage() {
        GetBlockMessage message = mock(GetBlockMessage.class);
        byte[] blockHash = new byte[]{0x0F};

        when(message.getBlockHash()).thenReturn(blockHash);

        target.apply(message);

        verify(blockProcessor, times(1)).processGetBlock(sender, blockHash);
    }

    @Test
    void blockRequestMessage() {
        BlockRequestMessage message = mock(BlockRequestMessage.class);
        byte[] blockHash = new byte[]{0x0F};

        when(message.getBlockHash()).thenReturn(blockHash);
        when(message.getId()).thenReturn(24L);

        target.apply(message);

        verify(blockProcessor, times(1))
                .processBlockRequest(sender, 24L, blockHash);
    }

    @Test
    void blockResponseMessage() {
        BlockResponseMessage message = mock(BlockResponseMessage.class);

        target.apply(message);

        verify(syncProcessor, times(1))
                .processBlockResponse(sender, message);
    }

    @Test
    void skeletonRequestMessage() {
        SkeletonRequestMessage message = mock(SkeletonRequestMessage.class);
        when(message.getStartNumber()).thenReturn(24L);
        when(message.getId()).thenReturn(1L);

        target.apply(message);

        verify(blockProcessor, times(1))
                .processSkeletonRequest(sender, 1L, 24L);
    }

    @Test
    void blockHeadersRequestMessage() {
        BlockHeadersRequestMessage message = mock(BlockHeadersRequestMessage.class);
        byte[] hash = new byte[]{0x0F};

        when(message.getHash()).thenReturn(hash);
        when(message.getId()).thenReturn(1L);
        when(message.getCount()).thenReturn(10);

        target.apply(message);

        verify(blockProcessor, times(1))
                .processBlockHeadersRequest(sender, 1L, hash, 10);
    }

    @Test
    void stateChunkRequestMessage() {
        StateChunkRequestMessage message = mock(StateChunkRequestMessage.class);
        byte[] hash = new byte[]{0x0F};

        when(message.getHash()).thenReturn(hash);
        when(message.getId()).thenReturn(1L);

        target.apply(message);

        verify(syncProcessor, times(1))
                .processStateChunkRequest(sender, 1L, hash);
    }

    @Test
    void blockHashRequestMessage() {
        BlockHashRequestMessage message = mock(BlockHashRequestMessage.class);

        when(message.getId()).thenReturn(1L);
        when(message.getHeight()).thenReturn(10L);

        target.apply(message);

        verify(blockProcessor, times(1))
                .processBlockHashRequest(sender, 1L, 10L);
    }

    @Test
    void blockHashResponseMessage() {
        BlockHashResponseMessage message = mock(BlockHashResponseMessage.class);

        target.apply(message);

        verify(syncProcessor, times(1))
                .processBlockHashResponse(sender, message);
    }

    @Test
    void newBlockHashMessage() {
        NewBlockHashMessage message = mock(NewBlockHashMessage.class);

        target.apply(message);

        verify(syncProcessor, times(1)).processNewBlockHash(sender, message);
    }

    @Test
    void skeletonResponseMessage() {
        SkeletonResponseMessage message = mock(SkeletonResponseMessage.class);

        target.apply(message);

        verify(syncProcessor, times(1))
                .processSkeletonResponse(sender, message);
    }

    @Test
    void blockHeadersResponseMessage() {
        BlockHeadersResponseMessage message = mock(BlockHeadersResponseMessage.class);

        target.apply(message);

        verify(syncProcessor, times(1))
                .processBlockHeadersResponse(sender, message);
    }

    @Test
    void bodyRequestMessage() {
        BodyRequestMessage message = mock(BodyRequestMessage.class);
        byte[] blockHash = new byte[]{0x0F};

        when(message.getId()).thenReturn(1L);
        when(message.getBlockHash()).thenReturn(blockHash);

        target.apply(message);

        verify(blockProcessor, times(1))
                .processBodyRequest(sender, 1L, blockHash);
    }

    @Test
    void bodyResponseMessage() {
        BodyResponseMessage message = mock(BodyResponseMessage.class);

        target.apply(message);

        verify(syncProcessor, times(1))
                .processBodyResponse(sender, message);
    }

    @Test
    void newBlockHashesMessage() {
        NewBlockHashesMessage message = mock(NewBlockHashesMessage.class);

        target.apply(message);

        verify(blockProcessor, times(1))
                .processNewBlockHashesMessage(sender, message);
    }

    @Test
    void newBlockHashesMessage_betterBlockToSync() {
        NewBlockHashesMessage message = mock(NewBlockHashesMessage.class);

        when(blockProcessor.hasBetterBlockToSync()).thenReturn(true);

        target.apply(message);

        verify(blockProcessor, never())
                .processNewBlockHashesMessage(sender, message);
    }

    @Test
    void transactionsMessage_betterBlockToSync() {
        TransactionsMessage message = mock(TransactionsMessage.class);

        when(blockProcessor.hasBetterBlockToSync()).thenReturn(true);

        target.apply(message);

        verify(transactionGateway, never()).receiveTransactionsFrom(any(), any());
    }

    @Test
    void transactionsMessage_oneInvalidTransaction() {
        TransactionsMessage message = mock(TransactionsMessage.class);
        Constants networkConstants = mock(Constants.class);
        NodeID peer = mock(NodeID.class);
        InetAddress peerAddress = mock(InetAddress.class);


        when(config.getNetworkConstants()).thenReturn(networkConstants);
        when(sender.getPeerNodeID()).thenReturn(peer);
        when(sender.getAddress()).thenReturn(peerAddress);

        List<Transaction> validTransactions = new LinkedList<>();

        IntStream.range(0, 3).forEach(i -> {
            Transaction tx = mock(Transaction.class);
            when(tx.getHash()).thenReturn(TestUtils.generateHash("tx"+i));
            when(tx.acceptTransactionSignature(anyByte())).thenReturn(true);
            validTransactions.add(tx);
        });

        List<Transaction> invalidTransactions = new LinkedList<>();
        Transaction invalidTx = mock(Transaction.class);
        when(invalidTx.getHash()).thenReturn(TestUtils.generateHash("invalidTx"));
        when(invalidTx.acceptTransactionSignature(anyByte())).thenReturn(false);
        invalidTransactions.add(invalidTx);

        when(message.getTransactions())
                .thenReturn(Stream.concat(validTransactions.stream(), invalidTransactions.stream())
                        .collect(Collectors.toList()));
        when(blockProcessor.hasBetterBlockToSync()).thenReturn(false);

        target.apply(message);

        verify(transactionGateway, times(1))
                .receiveTransactionsFrom(eq(validTransactions), any());
        verify(peerScoringManager, times(1))
                .recordEvent(peer, peerAddress, EventType.VALID_TRANSACTION, "Valid transaction {} at {}",
                        validTransactions.get(0).getHash().toString(), MessageVisitor.class);
        verify(peerScoringManager, times(1))
                .recordEvent(peer, peerAddress, EventType.VALID_TRANSACTION, "Valid transaction {} at {}",
                        validTransactions.get(1).getHash().toString(), MessageVisitor.class);
        verify(peerScoringManager, times(1))
                .recordEvent(peer, peerAddress, EventType.VALID_TRANSACTION, "Valid transaction {} at {}",
                        validTransactions.get(2).getHash().toString(), MessageVisitor.class);
        verify(peerScoringManager, times(1))
                .recordEvent(peer, peerAddress, EventType.INVALID_TRANSACTION, "Invalid transaction {} at {}",
                        invalidTx.getHash().toString(), MessageVisitor.class);
    }
}
