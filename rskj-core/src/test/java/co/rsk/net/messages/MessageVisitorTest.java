package co.rsk.net.messages;

import co.rsk.config.RskSystemProperties;
import co.rsk.crypto.Keccak256;
import co.rsk.net.*;
import co.rsk.scoring.EventType;
import co.rsk.scoring.PeerScoringManager;
import org.ethereum.config.Constants;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.net.server.ChannelManager;
import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class MessageVisitorTest {

    private MessageVisitor target;
    private Peer sender;
    private ChannelManager channelManager;
    private PeerScoringManager peerScoringManager;
    private TransactionGateway transactionGateway;
    private SyncProcessor syncProcessor;
    private BlockProcessor blockProcessor;
    private RskSystemProperties config;

    @Before
    public void setUp() {
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
    public void blockMessage_invalidblock() {
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
                .recordEvent(eq(peer), eq(peerAddress), eq(EventType.INVALID_BLOCK));
        verify(blockProcessor, times(1)).processBlock(any(), any());
    }

    @Test
    public void blockMessage_genesisBlock() {
        BlockMessage message = mock(BlockMessage.class);
        Block block = mock(Block.class);

        when(message.getBlock()).thenReturn(block);
        when(block.isGenesis()).thenReturn(true);

        target.apply(message);

        verify(blockProcessor, never()).processBlock(any(), any());
    }

    @Test
    public void blockMessage_advancedBlockNumber() {
        BlockMessage message = mock(BlockMessage.class);
        Block block = mock(Block.class);

        when(message.getBlock()).thenReturn(block);
        when(block.getNumber()).thenReturn(24L);
        when(blockProcessor.isAdvancedBlock(anyLong())).thenReturn(true);

        target.apply(message);

        verify(blockProcessor, never()).processBlock(any(), any());
    }

    @Test
    public void blockMessage_ignoredForUnclesReward() {
        BlockMessage message = mock(BlockMessage.class);
        Block block = mock(Block.class);

        when(message.getBlock()).thenReturn(block);
        when(block.getNumber()).thenReturn(24L);
        when(blockProcessor.canBeIgnoredForUnclesRewards(anyLong())).thenReturn(true);

        target.apply(message);

        verify(blockProcessor, never()).processBlock(any(), any());
    }

    @Test
    public void blockMessage_hasBlockInSomeBlockchain() {
        BlockMessage message = mock(BlockMessage.class);
        Block block = mock(Block.class);
        Keccak256 blockHash = mock(Keccak256.class);
        byte[] hashBytes = new byte[]{0x0F};

        when(message.getBlock()).thenReturn(block);
        when(block.getNumber()).thenReturn(24L);

        when(block.getHash()).thenReturn(blockHash);
        when(blockHash.getBytes()).thenReturn(hashBytes);

        when(blockProcessor.hasBlockInSomeBlockchain(eq(hashBytes))).thenReturn(true);

        target.apply(message);

        verify(blockProcessor, never()).processBlock(any(), any());
    }

    @Test
    public void blockMessage_dontRelayBlock() {
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

        verify(blockProcessor, times(1)).processBlock(eq(sender), eq(block));
        verify(peerScoringManager, times(1))
                .recordEvent(eq(peer), eq(peerAddress), eq(EventType.VALID_BLOCK));
        verify(channelManager, never()).broadcastBlockHash(any(), any());
    }

    @Test
    public void blockMessage_relayBlock() {
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
        when(blockProcessResult.wasBlockAdded(eq(block))).thenReturn(true);
        when(blockProcessor.getNodeInformation()).thenReturn(blockNodeInformation);
        when(blockNodeInformation.getNodesByBlock(eq(blockHash))).thenReturn(new HashSet<>());
        when(syncProcessor.getKnownPeersNodeIDs()).thenReturn(new HashSet<>());

        target.apply(message);

        verify(blockProcessor, times(1)).processBlock(eq(sender), eq(block));
        verify(peerScoringManager, times(1))
                .recordEvent(eq(peer), eq(peerAddress), eq(EventType.VALID_BLOCK));
        verify(channelManager, times(1)).broadcastBlockHash(any(), any());
    }

    @Test
    public void statusMessage() {
        StatusMessage message = mock(StatusMessage.class);
        Status status = mock(Status.class);
        when(message.getStatus()).thenReturn(status);

        target.apply(message);

        verify(syncProcessor, times(1)).processStatus(eq(sender),eq(status));
    }

    @Test
    public void getBlockMessage() {
        GetBlockMessage message = mock(GetBlockMessage.class);
        byte[] blockHash = new byte[]{0x0F};

        when(message.getBlockHash()).thenReturn(blockHash);

        target.apply(message);

        verify(blockProcessor, times(1)).processGetBlock(eq(sender), eq(blockHash));
    }

    @Test
    public void blockRequestMessage() {
        BlockRequestMessage message = mock(BlockRequestMessage.class);
        byte[] blockHash = new byte[]{0x0F};

        when(message.getBlockHash()).thenReturn(blockHash);
        when(message.getId()).thenReturn(24L);

        target.apply(message);

        verify(blockProcessor, times(1))
                .processBlockRequest(eq(sender), eq(24L), eq(blockHash));
    }

    @Test
    public void blockResponseMessage() {
        BlockResponseMessage message = mock(BlockResponseMessage.class);

        target.apply(message);

        verify(syncProcessor, times(1))
                .processBlockResponse(eq(sender), eq(message));
    }

    @Test
    public void skeletonRequestMessage() {
        SkeletonRequestMessage message = mock(SkeletonRequestMessage.class);
        when(message.getStartNumber()).thenReturn(24L);
        when(message.getId()).thenReturn(1L);

        target.apply(message);

        verify(blockProcessor, times(1))
                .processSkeletonRequest(eq(sender), eq(1L), eq(24L));
    }

    @Test
    public void blockHeadersRequestMessage() {
        BlockHeadersRequestMessage message = mock(BlockHeadersRequestMessage.class);
        byte[] hash = new byte[]{0x0F};

        when(message.getHash()).thenReturn(hash);
        when(message.getId()).thenReturn(1L);
        when(message.getCount()).thenReturn(10);

        target.apply(message);

        verify(blockProcessor, times(1))
                .processBlockHeadersRequest(eq(sender), eq(1L), eq(hash), eq(10));
    }

    @Test
    public void blockHashRequestMessage() {
        BlockHashRequestMessage message = mock(BlockHashRequestMessage.class);

        when(message.getId()).thenReturn(1L);
        when(message.getHeight()).thenReturn(10L);

        target.apply(message);

        verify(blockProcessor, times(1))
                .processBlockHashRequest(eq(sender), eq(1L), eq(10L));
    }

    @Test
    public void blockHashResponseMessage() {
        BlockHashResponseMessage message = mock(BlockHashResponseMessage.class);

        target.apply(message);

        verify(syncProcessor, times(1))
                .processBlockHashResponse(eq(sender), eq(message));
    }

    @Test
    public void newBlockHashMessage() {
        NewBlockHashMessage message = mock(NewBlockHashMessage.class);

        target.apply(message);

        verify(syncProcessor, times(1)).processNewBlockHash(eq(sender), eq(message));
    }

    @Test
    public void skeletonResponseMessage() {
        SkeletonResponseMessage message = mock(SkeletonResponseMessage.class);

        target.apply(message);

        verify(syncProcessor, times(1))
                .processSkeletonResponse(eq(sender), eq(message));
    }

    @Test
    public void blockHeadersResponseMessage() {
        BlockHeadersResponseMessage message = mock(BlockHeadersResponseMessage.class);

        target.apply(message);

        verify(syncProcessor, times(1))
                .processBlockHeadersResponse(eq(sender), eq(message));
    }

    @Test
    public void bodyRequestMessage() {
        BodyRequestMessage message = mock(BodyRequestMessage.class);
        byte[] blockHash = new byte[]{0x0F};

        when(message.getId()).thenReturn(1L);
        when(message.getBlockHash()).thenReturn(blockHash);

        target.apply(message);

        verify(blockProcessor, times(1))
                .processBodyRequest(eq(sender), eq(1L), eq(blockHash));
    }

    @Test
    public void bodyResponseMessage() {
        BodyResponseMessage message = mock(BodyResponseMessage.class);

        target.apply(message);

        verify(syncProcessor, times(1))
                .processBodyResponse(eq(sender), eq(message));
    }

    @Test
    public void newBlockHashesMessage() {
        NewBlockHashesMessage message = mock(NewBlockHashesMessage.class);

        target.apply(message);

        verify(blockProcessor, times(1))
                .processNewBlockHashesMessage(eq(sender), eq(message));
    }

    @Test
    public void newBlockHashesMessage_betterBlockToSync() {
        NewBlockHashesMessage message = mock(NewBlockHashesMessage.class);

        when(blockProcessor.hasBetterBlockToSync()).thenReturn(true);

        target.apply(message);

        verify(blockProcessor, never())
                .processNewBlockHashesMessage(eq(sender), eq(message));
    }

    @Test
    public void transactionsMessage_betterBlockToSync() {
        TransactionsMessage message = mock(TransactionsMessage.class);

        when(blockProcessor.hasBetterBlockToSync()).thenReturn(true);

        target.apply(message);

        verify(transactionGateway, never()).receiveTransactionsFrom(any(), any());
    }

    @Test
    public void transactionsMessage_oneInvalidTransaction() {
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
            when(tx.acceptTransactionSignature(anyByte())).thenReturn(true);
            validTransactions.add(tx);
        });

        List<Transaction> invalidTransactions = new LinkedList<>();
        Transaction invalidTx = mock(Transaction.class);
        when(invalidTx.acceptTransactionSignature(anyByte())).thenReturn(false);
        invalidTransactions.add(invalidTx);

        when(message.getTransactions())
                .thenReturn(Stream.concat(validTransactions.stream(), invalidTransactions.stream())
                        .collect(Collectors.toList()));
        when(blockProcessor.hasBetterBlockToSync()).thenReturn(false);

        target.apply(message);

        verify(transactionGateway, times(1))
                .receiveTransactionsFrom(eq(validTransactions), any());
        verify(peerScoringManager, times(3))
                .recordEvent(eq(peer), eq(peerAddress), eq(EventType.VALID_TRANSACTION));
        verify(peerScoringManager, times(1))
                .recordEvent(eq(peer), eq(peerAddress), eq(EventType.INVALID_TRANSACTION));
    }
}
