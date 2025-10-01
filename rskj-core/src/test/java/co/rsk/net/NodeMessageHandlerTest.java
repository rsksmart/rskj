/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.RskSystemProperties;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.BlockDifficulty;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.ConsensusValidationMainchainView;
import co.rsk.crypto.Keccak256;
import co.rsk.net.messages.*;
import co.rsk.net.simples.SimpleBlockProcessor;
import co.rsk.net.simples.SimplePeer;
import co.rsk.net.sync.PeersInformation;
import co.rsk.net.sync.SyncConfiguration;
import co.rsk.net.utils.TransactionUtils;
import co.rsk.scoring.EventType;
import co.rsk.scoring.PeerScoring;
import co.rsk.scoring.PeerScoringManager;
import co.rsk.scoring.PunishmentParameters;
import co.rsk.test.World;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.validators.*;
import org.awaitility.Awaitility;
import org.ethereum.TestUtils;
import org.ethereum.core.*;
import org.ethereum.db.BlockStore;
import org.ethereum.listener.EthereumListener;
import org.ethereum.net.NodeManager;
import org.ethereum.net.server.Channel;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.rpc.Simples.SimpleChannelManager;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RskMockFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import static org.mockito.Mockito.*;

/**
 * Created by ajlopez on 5/10/2016.
 */
class NodeMessageHandlerTest {

    private final TestSystemProperties config = new TestSystemProperties();
    private final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());

    @Test
    void processBlockMessageUsingProcessor() {
        SimplePeer sender = new SimplePeer();
        PeerScoringManager scoring = createPeerScoringManager();
        SimpleBlockProcessor sbp = new SimpleBlockProcessor();
        NodeMessageHandler processor = new NodeMessageHandler(config, sbp, null, null, null, null, scoring, mock(StatusResolver.class));
        Block block = new BlockChainBuilder().ofSize(1, true).getBestBlock();
        Message message = new BlockMessage(block);

        processor.processMessage(sender, message);

        Assertions.assertNotNull(sbp.getBlocks());
        Assertions.assertEquals(1, sbp.getBlocks().size());
        Assertions.assertSame(block, sbp.getBlocks().get(0));

        Assertions.assertFalse(scoring.isEmpty());

        PeerScoring pscoring = scoring.getPeerScoring(sender.getPeerNodeID());

        Assertions.assertNotNull(pscoring);
        Assertions.assertFalse(pscoring.isEmpty());
        Assertions.assertEquals(1, pscoring.getTotalEventCounter());
        Assertions.assertEquals(1, pscoring.getEventCounter(EventType.VALID_BLOCK));

        pscoring = scoring.getPeerScoring(sender.getAddress());

        Assertions.assertNotNull(pscoring);
        Assertions.assertFalse(pscoring.isEmpty());
        Assertions.assertEquals(1, pscoring.getTotalEventCounter());
        Assertions.assertEquals(1, pscoring.getEventCounter(EventType.VALID_BLOCK));
    }

    @Test
    void skipProcessGenesisBlock() {
        SimplePeer sender = new SimplePeer();
        PeerScoringManager scoring = createPeerScoringManager();
        SimpleBlockProcessor sbp = new SimpleBlockProcessor();
        NodeMessageHandler processor = new NodeMessageHandler(config, sbp, null, null, null, null, scoring, mock(StatusResolver.class));
        Block block = new BlockGenerator().getGenesisBlock();
        Message message = new BlockMessage(block);

        processor.processMessage(sender, message);

        Assertions.assertNotNull(sbp.getBlocks());
        Assertions.assertEquals(0, sbp.getBlocks().size());
        Assertions.assertTrue(scoring.isEmpty());

        PeerScoring pscoring = scoring.getPeerScoring(sender.getPeerNodeID());

        Assertions.assertNotNull(pscoring);
        Assertions.assertTrue(pscoring.isEmpty());
    }

    @Test
    void skipAdvancedBlock() {
        SimplePeer sender = new SimplePeer();
        PeerScoringManager scoring = createPeerScoringManager();
        SimpleBlockProcessor sbp = new SimpleBlockProcessor();
        sbp.setBlockGap(100000);
        NodeMessageHandler processor = new NodeMessageHandler(config, sbp, null, null, null, null, scoring, mock(StatusResolver.class));
        Block block = new BlockGenerator().createBlock(200000, 0);
        Message message = new BlockMessage(block);

        processor.processMessage(sender, message);

        Assertions.assertNotNull(sbp.getBlocks());
        Assertions.assertEquals(0, sbp.getBlocks().size());
        Assertions.assertTrue(scoring.isEmpty());

        PeerScoring pscoring = scoring.getPeerScoring(sender.getPeerNodeID());

        Assertions.assertNotNull(pscoring);
        Assertions.assertTrue(pscoring.isEmpty());
    }

    @Test
    void postBlockMessageTwice() {
        Peer sender = new SimplePeer();
        PeerScoringManager scoring = createPeerScoringManager();
        SimpleBlockProcessor sbp = new SimpleBlockProcessor();
        NodeMessageHandler processor = new NodeMessageHandler(config, sbp, null, null, null, null, scoring,
                mock(StatusResolver.class));
        Block block = new BlockChainBuilder().ofSize(1, true).getBestBlock();
        Message message = new BlockMessage(block);

        processor.postMessage(sender, message, null);
        processor.postMessage(sender, message, null);

        PeerScoring pscoring = scoring.getPeerScoring(sender.getPeerNodeID());

        Assertions.assertNotNull(pscoring);
        Assertions.assertFalse(pscoring.isEmpty());
        Assertions.assertEquals(1, pscoring.getTotalEventCounter());
        Assertions.assertEquals(1, pscoring.getEventCounter(EventType.REPEATED_MESSAGE));
    }

    @Test
    @SuppressWarnings("squid:S2925") // Thread.sleep() used
    void postBlockMessageUsingProcessor() throws InterruptedException {
        SimpleBlockProcessor sbp = new SimpleBlockProcessor();
        NodeMessageHandler processor = new NodeMessageHandler(config, sbp, null, null, null, null, null,
                mock(StatusResolver.class));
        Block block = new BlockChainBuilder().ofSize(1, true).getBestBlock();
        Message message = new BlockMessage(block);

        processor.start();
        processor.postMessage(new SimplePeer(), message, null);

        Thread.sleep(1000);

        processor.stop();

        Assertions.assertNotNull(sbp.getBlocks());
        Assertions.assertEquals(1, sbp.getBlocks().size());
        Assertions.assertSame(block, sbp.getBlocks().get(0));
    }

    @Test
    void postBlockMessageFromBannedMiner() {
        RskSystemProperties config = spy(this.config);
        Peer sender = new SimplePeer();
        PeerScoringManager scoring = createPeerScoringManager();
        SimpleBlockProcessor sbp = new SimpleBlockProcessor();
        Block block = new BlockChainBuilder().ofSize(1, true).getBestBlock();
        Message message = new BlockMessage(block);

        RskAddress bannedMiner = block.getCoinbase();
        doReturn(Collections.singletonList(bannedMiner.toHexString())).when(config).bannedMinerList();

        NodeMessageHandler nodeMessageHandler = new NodeMessageHandler(config, sbp, null,null, null, null, scoring,
                mock(StatusResolver.class));

        nodeMessageHandler.postMessage(sender, message, null);

        Assertions.assertEquals(0, nodeMessageHandler.getMessageQueueSize());
    }

    @Test
    void checkSnapMessagesOrderAndPriority() {
        RskSystemProperties config = spy(this.config);
        PeerScoringManager scoring = createPeerScoringManager();
        SimpleBlockProcessor sbp = new SimpleBlockProcessor();
        SyncProcessor syncProcessor = mock(SyncProcessor.class);
        StatusResolver statusResolver = mock(StatusResolver.class);
        Status status = mock(Status.class);
        ChannelManager channelManager = mock(ChannelManager.class);

        // Mock snap messages
        Message snapStateChunkRequestMessage = Mockito.mock(SnapStateChunkRequestMessage.class);
        Message snapStateChunkResponseMessage = Mockito.mock(SnapStateChunkResponseMessage.class);
        Message snapStatusRequestMessage = Mockito.mock(SnapStatusRequestMessage.class);
        Message snapStatusResponseMessage = Mockito.mock(SnapStatusResponseMessage.class);
        Message snapBlocksRequestMessage = Mockito.mock(SnapBlocksRequestMessage.class);
        Message snapBlocksResponseMessage = Mockito.mock(SnapBlocksResponseMessage.class);

        Mockito.when(snapStateChunkRequestMessage.getMessageType()).thenReturn(MessageType.SNAP_STATE_CHUNK_REQUEST_MESSAGE);
        Mockito.when(snapStateChunkResponseMessage.getMessageType()).thenReturn(MessageType.SNAP_STATE_CHUNK_RESPONSE_MESSAGE);
        Mockito.when(snapStatusRequestMessage.getMessageType()).thenReturn(MessageType.SNAP_STATUS_REQUEST_MESSAGE);
        Mockito.when(snapStatusResponseMessage.getMessageType()).thenReturn(MessageType.SNAP_STATUS_RESPONSE_MESSAGE);
        Mockito.when(snapBlocksRequestMessage.getMessageType()).thenReturn(MessageType.SNAP_BLOCKS_REQUEST_MESSAGE);
        Mockito.when(snapBlocksResponseMessage.getMessageType()).thenReturn(MessageType.SNAP_BLOCKS_RESPONSE_MESSAGE);

        Mockito.when(status.getBestBlockNumber()).thenReturn(0L);
        Mockito.when(status.getBestBlockHash()).thenReturn(ByteUtil.intToBytes(0));
        Mockito.when(channelManager.broadcastStatus(any())).thenReturn(0);
        Mockito.when(statusResolver.currentStatus()).thenReturn(status);

        Channel sender = new Channel(null, null, mock(NodeManager.class), null, null, null, null);
        InetAddress inetAddress = InetAddress.getLoopbackAddress();
        InetSocketAddress inetSocketAddress = new InetSocketAddress(inetAddress, 500);
        sender.setInetSocketAddress(inetSocketAddress);
        sender.setNode(new NodeID(TestUtils.generatePeerId("peer")).getID());

        RskAddress bannedMiner = new RskAddress("0000000000000000000000000000000000000023");
        doReturn(Collections.singletonList(bannedMiner.toHexString())).when(config).bannedMinerList();

        MessageCounter messageCounter = mock(MessageCounter.class);
        Mockito.doThrow(new IllegalAccessError()).when(messageCounter).decrement(any());

        NodeMessageHandler nodeMessageHandler = Mockito.spy( new NodeMessageHandler(
                config,
                sbp,
                syncProcessor,
                null,
                channelManager,
                null,
                scoring,
                statusResolver,
                Mockito.mock(Thread.class),
                messageCounter
        ));

        nodeMessageHandler.postMessage(sender, snapStateChunkRequestMessage, null);
        nodeMessageHandler.postMessage(sender, snapStateChunkResponseMessage, null);
        nodeMessageHandler.postMessage(sender, snapStatusRequestMessage, null);
        nodeMessageHandler.postMessage(sender, snapStatusResponseMessage, null);
        nodeMessageHandler.postMessage(sender, snapBlocksRequestMessage, null);
        nodeMessageHandler.postMessage(sender, snapBlocksResponseMessage, null);

        Assertions.assertEquals(6, nodeMessageHandler.getMessageQueueSize());

        ArgumentCaptor<Message> snapMessagesCaptor = ArgumentCaptor.forClass(Message.class);

        nodeMessageHandler.start();
        // Snap responses scores = 300
        // Snap requests scores = 100

        nodeMessageHandler.run();
        Mockito.verify(nodeMessageHandler, atLeastOnce()).processMessage(any(Peer.class), snapMessagesCaptor.capture());
        Assertions.assertEquals(MessageType.SNAP_STATE_CHUNK_RESPONSE_MESSAGE, snapMessagesCaptor.getValue().getMessageType());

        nodeMessageHandler.run();
        Mockito.verify(nodeMessageHandler, atLeastOnce()).processMessage(any(Peer.class), snapMessagesCaptor.capture());
        Assertions.assertEquals(MessageType.SNAP_STATUS_RESPONSE_MESSAGE, snapMessagesCaptor.getValue().getMessageType());

        nodeMessageHandler.run();
        Mockito.verify(nodeMessageHandler, atLeastOnce()).processMessage(any(Peer.class), snapMessagesCaptor.capture());
        Assertions.assertEquals(MessageType.SNAP_BLOCKS_RESPONSE_MESSAGE, snapMessagesCaptor.getValue().getMessageType());

        nodeMessageHandler.run();
        Mockito.verify(nodeMessageHandler, atLeastOnce()).processMessage(any(Peer.class), snapMessagesCaptor.capture());
        Assertions.assertEquals(MessageType.SNAP_STATE_CHUNK_REQUEST_MESSAGE, snapMessagesCaptor.getValue().getMessageType());

        nodeMessageHandler.run();
        Mockito.verify(nodeMessageHandler, atLeastOnce()).processMessage(any(Peer.class), snapMessagesCaptor.capture());
        Assertions.assertEquals(MessageType.SNAP_BLOCKS_REQUEST_MESSAGE, snapMessagesCaptor.getValue().getMessageType());

        nodeMessageHandler.run();
        Mockito.verify(nodeMessageHandler, atLeastOnce()).processMessage(any(Peer.class), snapMessagesCaptor.capture());
        Assertions.assertEquals(MessageType.SNAP_STATUS_REQUEST_MESSAGE, snapMessagesCaptor.getValue().getMessageType());

        nodeMessageHandler.stop();

        Assertions.assertEquals(0, nodeMessageHandler.getMessageQueueSize());
    }

    @Test
    void postBlockMessageFromNonBannedMiner() {
        RskSystemProperties config = spy(this.config);
        Peer sender = new SimplePeer();
        PeerScoringManager scoring = createPeerScoringManager();
        SimpleBlockProcessor sbp = new SimpleBlockProcessor();
        Block block = new BlockChainBuilder().ofSize(1, true).getBestBlock();
        Message message = new BlockMessage(block);

        RskAddress coinbase = block.getCoinbase();
        RskAddress bannedMiner = new RskAddress("0000000000000000000000000000000000000023");
        Assertions.assertNotEquals(coinbase, bannedMiner);

        doReturn(Collections.singletonList(bannedMiner.toHexString())).when(config).bannedMinerList();

        NodeMessageHandler nodeMessageHandler = new NodeMessageHandler(config, sbp, null,null, null, null, scoring,
                mock(StatusResolver.class));

        nodeMessageHandler.postMessage(sender, message, null);

        Assertions.assertEquals(1, nodeMessageHandler.getMessageQueueSize());
    }

    // TODO: Difficulty in RegTest is so small that this test will sometimes pass and other times fail
    @Disabled("This should be executed in a special mode where difficulty is high")
    public void processInvalidPoWMessageUsingProcessor() {
        SimplePeer sender = new SimplePeer();
        PeerScoringManager scoring = createPeerScoringManager();
        SimpleBlockProcessor sbp = new SimpleBlockProcessor();
        NodeMessageHandler processor = new NodeMessageHandler(config, sbp, null,null, null, null, scoring,
                mock(StatusResolver.class));
        Block block = new BlockChainBuilder().ofSize(1, true).getBestBlock();
        byte[] mergedMiningHeader = block.getBitcoinMergedMiningHeader();
        mergedMiningHeader[76] += 3; //change merged mining nonce.
        Message message = new BlockMessage(block);

        processor.processMessage(sender, message);

        Assertions.assertNotNull(sbp.getBlocks());
        Assertions.assertEquals(0, sbp.getBlocks().size());

        Assertions.assertFalse(scoring.isEmpty());

        PeerScoring pscoring = scoring.getPeerScoring(sender.getPeerNodeID());

        Assertions.assertNotNull(pscoring);
        Assertions.assertFalse(pscoring.isEmpty());
        Assertions.assertEquals(1, pscoring.getTotalEventCounter());
        Assertions.assertEquals(1, pscoring.getEventCounter(EventType.INVALID_BLOCK));
    }

    @Test
    void processMissingPoWBlockMessageUsingProcessor() {
        SimplePeer sender = new SimplePeer();
        PeerScoringManager scoring = createPeerScoringManager();
        SimpleBlockProcessor sbp = new SimpleBlockProcessor();
        NodeMessageHandler processor = new NodeMessageHandler(config, sbp, null,null, null, null, scoring, mock(StatusResolver.class));
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block = blockGenerator.getGenesisBlock();

        for (int i = 0; i < 50; i++) {
            block = blockGenerator.createChildBlock(block);
        }

        Message message = new BlockMessage(block);
        processor.processMessage(sender, message);

        Assertions.assertNotNull(sbp.getBlocks());
        Assertions.assertEquals(1, sbp.getBlocks().size());

        Assertions.assertFalse(scoring.isEmpty());

        PeerScoring pscoring = scoring.getPeerScoring(sender.getPeerNodeID());

        Assertions.assertNotNull(pscoring);
        Assertions.assertFalse(pscoring.isEmpty());
        Assertions.assertEquals(1, pscoring.getTotalEventCounter());
        Assertions.assertEquals(1, pscoring.getEventCounter(EventType.VALID_BLOCK));
        Assertions.assertEquals(0, pscoring.getEventCounter(EventType.INVALID_BLOCK));
    }

    @Test
    void processFutureBlockMessageUsingProcessor() {
        SimpleBlockProcessor sbp = new SimpleBlockProcessor();
        NodeMessageHandler processor = new NodeMessageHandler(config, sbp, null,null, null, null, null,
                mock(StatusResolver.class));
        Block block = new BlockGenerator().getGenesisBlock();
        Message message = new BlockMessage(block);
        SimplePeer sender = new SimplePeer();
        processor.processMessage(sender, message);

        Assertions.assertNotNull(sbp.getBlocks());
        Assertions.assertEquals(0, sbp.getBlocks().size());
    }

    @Test @Disabled("This should be reviewed with sync processor or deleted")
    void processStatusMessageUsingNodeBlockProcessor() {
        final World world = new World();
        final Blockchain blockchain = world.getBlockChain();
        final NetBlockStore store = new NetBlockStore();
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor bp = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);
        final SimplePeer sender = new SimplePeer();
        final NodeMessageHandler handler = new NodeMessageHandler(config, bp, null,null, null, null, null,
                mock(StatusResolver.class));

        BlockGenerator blockGenerator = new BlockGenerator();
        final Block block = blockGenerator.createChildBlock(blockGenerator.getGenesisBlock());
        final Status status = new Status(block.getNumber(), block.getHash().getBytes());
        final Message message = new StatusMessage(status);

        handler.processMessage(sender, message);

        Assertions.assertNotNull(sender.getGetBlockMessages());
        Assertions.assertEquals(1, sender.getGetBlockMessages().size());

        final Message msg = sender.getGetBlockMessages().get(0);

        Assertions.assertEquals(MessageType.GET_BLOCK_MESSAGE, msg.getMessageType());

        final GetBlockMessage gbMessage = (GetBlockMessage) msg;

        Assertions.assertArrayEquals(block.getHash().getBytes(), gbMessage.getBlockHash());
    }

    @Test
    void processStatusMessageUsingSyncProcessor() {
        final SimplePeer sender = new SimplePeer();

        final ChannelManager channelManager = mock(ChannelManager.class);
        when(channelManager.getActivePeers()).thenReturn(Collections.singletonList(sender));

        final NodeMessageHandler handler = NodeMessageHandlerUtil.createHandlerWithSyncProcessor(SyncConfiguration.IMMEDIATE_FOR_TESTING, channelManager);

        BlockGenerator blockGenerator = new BlockGenerator();
        final Block block = blockGenerator.createChildBlock(blockGenerator.getGenesisBlock());
        final Status status = new Status(block.getNumber(), block.getHash().getBytes(), block.getParentHash().getBytes(), new BlockDifficulty(BigInteger.TEN));
        final Message message = new StatusMessage(status);
        handler.processMessage(sender, message);

        Assertions.assertFalse(sender.getMessages().isEmpty());
        Assertions.assertEquals(MessageType.BLOCK_HEADERS_REQUEST_MESSAGE, sender.getMessages().get(0).getMessageType());
    }

    @Test
    void processStatusMessageWithKnownBestBlock() {
        final World world = new World();
        final Blockchain blockchain = world.getBlockChain();
        final NetBlockStore store = new NetBlockStore();
        final BlockStore blockStore = mock(BlockStore.class);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor bp = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);
        final SimplePeer sender = new SimplePeer();
        final SyncProcessor syncProcessor = new SyncProcessor(
                blockchain,
                blockStore, mock(ConsensusValidationMainchainView.class),
                blockSyncService,
                syncConfiguration,
                blockFactory,
                new DummyBlockValidationRule(),
                new SyncBlockValidatorRule(new BlockUnclesHashValidationRule(), new BlockRootValidationRule(config.getActivationConfig())),
                null,
                new PeersInformation(RskMockFactory.getChannelManager(), syncConfiguration, blockchain, RskMockFactory.getPeerScoringManager()), mock(Genesis.class), mock(EthereumListener.class));
        final NodeMessageHandler handler = new NodeMessageHandler(config,
                bp, syncProcessor, null, null, null,
                null, mock(StatusResolver.class));

        BlockGenerator blockGenerator = new BlockGenerator();

        Genesis genesisBlock = blockGenerator.getGenesisBlock();
        when(blockStore.getMinNumber()).thenReturn(genesisBlock.getNumber());

        final Block block = blockGenerator.createChildBlock(genesisBlock);
        final Status status = new Status(block.getNumber(), block.getHash().getBytes(), block.getParentHash().getBytes(), blockchain.getTotalDifficulty());
        final Message message = new StatusMessage(status);

        store.saveBlock(block);

        handler.processMessage(sender, message);

        Assertions.assertNotNull(sender.getMessages());
    }

    @Test
    void processGetBlockMessageUsingBlockInStore() {
        final Block block = new BlockGenerator().getBlock(3);
        final World world = new World();
        final Blockchain blockchain = world.getBlockChain();
        final NetBlockStore store = new NetBlockStore();


        store.saveBlock(block);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor bp = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

        final NodeMessageHandler handler = new NodeMessageHandler(config, bp, null, null, null, null,
                null, mock(StatusResolver.class));

        final SimplePeer sender = new SimplePeer();

        handler.processMessage(sender, new GetBlockMessage(block.getHash().getBytes()));

        Assertions.assertFalse(sender.getMessages().isEmpty());
        Assertions.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assertions.assertEquals(MessageType.BLOCK_MESSAGE, message.getMessageType());

        final BlockMessage bMessage = (BlockMessage) message;

        Assertions.assertEquals(block.getHash(), bMessage.getBlock().getHash());
    }

    @Test
    void processGetBlockMessageUsingBlockInBlockchain() {
        final World world = new World();
        final Blockchain blockchain = world.getBlockChain();
        final NetBlockStore store = new NetBlockStore();

        List<Block> blocks = new BlockGenerator().getBlockChain(blockchain.getBestBlock(), 10);

        for (Block b: blocks)
            blockchain.tryToConnect(b);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        NodeBlockProcessor bp = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

        NodeMessageHandler handler = new NodeMessageHandler(config, bp, null,null, null, null, null,
                mock(StatusResolver.class));

        SimplePeer sender = new SimplePeer();

        handler.processMessage(sender, new GetBlockMessage(blocks.get(4).getHash().getBytes()));

        Assertions.assertFalse(sender.getMessages().isEmpty());
        Assertions.assertEquals(1, sender.getMessages().size());

        Message message = sender.getMessages().get(0);

        Assertions.assertEquals(MessageType.BLOCK_MESSAGE, message.getMessageType());

        BlockMessage bmessage = (BlockMessage) message;

        Assertions.assertEquals(blocks.get(4).getHash(), bmessage.getBlock().getHash());
    }

    @Test
    void processGetBlockMessageUsingEmptyStore() {
        final Block block = new BlockGenerator().getBlock(3);

        final World world = new World();
        final Blockchain blockchain = world.getBlockChain();
        final NetBlockStore store = new NetBlockStore();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor bp = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

        final NodeMessageHandler handler = new NodeMessageHandler(config, bp, null, null, null, null, null, mock(StatusResolver.class));

        final SimplePeer sender = new SimplePeer();

        handler.processMessage(sender, new GetBlockMessage(block.getHash().getBytes()));

        Assertions.assertTrue(sender.getMessages().isEmpty());
    }

    @Test
    void processBlockHeaderRequestMessageUsingBlockInStore() {
        final Block block = new BlockGenerator().getBlock(3);

        final World world = new World();
        final Blockchain blockchain = world.getBlockChain();
        final NetBlockStore store = new NetBlockStore();

        store.saveBlock(block);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor bp = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

        final NodeMessageHandler handler = new NodeMessageHandler(config, bp, null, null, null, null, null,
                mock(StatusResolver.class));

        final SimplePeer sender = new SimplePeer();

        handler.processMessage(sender, new BlockHeadersRequestMessage(1, block.getHash().getBytes(), 1));

        Assertions.assertFalse(sender.getMessages().isEmpty());
        Assertions.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assertions.assertEquals(MessageType.BLOCK_HEADERS_RESPONSE_MESSAGE, message.getMessageType());

        final BlockHeadersResponseMessage bMessage = (BlockHeadersResponseMessage) message;

        Assertions.assertEquals(block.getHash(), bMessage.getBlockHeaders().get(0).getHash());
    }

    @Test
    void processBlockHeaderRequestMessageUsingBlockInBlockchain() {
        final World world = new World();
        final Blockchain blockchain = world.getBlockChain();
        final NetBlockStore store = new NetBlockStore();

        List<Block> blocks = new BlockGenerator().getBlockChain(blockchain.getBestBlock(), 10);

        for (Block b: blocks)
            blockchain.tryToConnect(b);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        NodeBlockProcessor bp = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

        NodeMessageHandler handler = new NodeMessageHandler(config, bp, null, null, null, null, null,
                mock(StatusResolver.class));

        SimplePeer sender = new SimplePeer();

        handler.processMessage(sender, new BlockHeadersRequestMessage(1, blocks.get(4).getHash().getBytes(), 1));

        Assertions.assertFalse(sender.getMessages().isEmpty());
        Assertions.assertEquals(1, sender.getMessages().size());

        Message message = sender.getMessages().get(0);

        Assertions.assertEquals(MessageType.BLOCK_HEADERS_RESPONSE_MESSAGE, message.getMessageType());

        BlockHeadersResponseMessage bMessage = (BlockHeadersResponseMessage) message;

        Assertions.assertEquals(blocks.get(4).getHash(), bMessage.getBlockHeaders().get(0).getHash());
    }

    @Test
    void processNewBlockHashesMessage() {
        final World world = new World();
        final Blockchain blockchain = world.getBlockChain();
        final NetBlockStore store = new NetBlockStore();

        final List<Block> blocks = new BlockGenerator().getBlockChain(blockchain.getBestBlock(), 15);
        final List<Block> bcBlocks = blocks.subList(0, 10);

        for (Block b: bcBlocks)
            blockchain.tryToConnect(b);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor bp = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);
        final NodeMessageHandler handler = new NodeMessageHandler(config, bp, null, null, null, null, null,
                mock(StatusResolver.class));

        class TestCase {
            protected final NewBlockHashesMessage message;
            protected final List<Block> expected;

            public TestCase(@Nonnull final NewBlockHashesMessage message, final List<Block> expected) {
                this.message = message;
                this.expected = expected;
            }
        }

        TestCase[] testCases = {
                new TestCase(
                        new NewBlockHashesMessage(new LinkedList<>()),
                        null
                ),
                new TestCase(
                        new NewBlockHashesMessage(
                                Arrays.asList(
                                        new BlockIdentifier(blocks.get(5).getHash().getBytes(), blocks.get(5).getNumber())
                                )
                        ),
                        null
                ),
                new TestCase(
                        new NewBlockHashesMessage(
                                Arrays.asList(
                                        new BlockIdentifier(blocks.get(11).getHash().getBytes(), blocks.get(5).getNumber())
                                )
                        ),
                        Arrays.asList(blocks.get(11))
                ),
                new TestCase(
                        new NewBlockHashesMessage(
                                Arrays.asList(
                                        new BlockIdentifier(blocks.get(11).getHash().getBytes(), blocks.get(5).getNumber()),
                                        new BlockIdentifier(blocks.get(5).getHash().getBytes(), blocks.get(5).getNumber())
                                )
                        ),
                        Arrays.asList(blocks.get(11))
                ),
                new TestCase(
                        new NewBlockHashesMessage(
                                Arrays.asList(
                                        new BlockIdentifier(blocks.get(11).getHash().getBytes(), blocks.get(5).getNumber()),
                                        new BlockIdentifier(blocks.get(12).getHash().getBytes(), blocks.get(5).getNumber())
                                )
                        ),
                        Arrays.asList(blocks.get(11), blocks.get(12))
                ),
                new TestCase(
                        new NewBlockHashesMessage(
                                Arrays.asList(
                                        new BlockIdentifier(blocks.get(11).getHash().getBytes(), blocks.get(5).getNumber()),
                                        new BlockIdentifier(blocks.get(11).getHash().getBytes(), blocks.get(5).getNumber())
                                )
                        ),
                        Arrays.asList(blocks.get(11))
                )
        };

        for (int i = 0; i < testCases.length; i += 1) {
            final TestCase testCase = testCases[i];
            final SimplePeer sender = new SimplePeer();

            handler.processMessage(sender, testCase.message);

            if (testCase.expected == null) {
                Assertions.assertTrue(sender.getMessages().isEmpty());
                continue;
            }

            Assertions.assertEquals(testCase.expected.size(), sender.getMessages().size());

            Assertions.assertTrue(sender.getMessages().stream().allMatch(m -> m.getMessageType() == MessageType.GET_BLOCK_MESSAGE));

            List<Keccak256> msgs = sender.getMessages().stream()
                    .map(m -> (GetBlockMessage) m)
                    .map(m -> m.getBlockHash())
                    .map(h -> new Keccak256(h))
                    .collect(Collectors.toList());

            Set<Keccak256> expected = testCase.expected.stream()
                    .map(b -> b.getHash().getBytes())
                    .map(h -> new Keccak256(h))
                    .collect(Collectors.toSet());

            for (Keccak256 h : msgs) {
                Assertions.assertTrue(expected.contains(h));
            }

            for (Keccak256 h : expected) {
                Assertions.assertEquals(1, msgs.stream()
                        .filter(h1 -> h.equals(h1))
                        .count());
            }

        }
    }

    @Test
    void processNewBlockHashesMessageDoesNothingBecauseNodeIsSyncing() {
        BlockProcessor blockProcessor = mock(BlockProcessor.class);
        Mockito.when(blockProcessor.hasBetterBlockToSync()).thenReturn(true);

        final NodeMessageHandler handler = new NodeMessageHandler(config, blockProcessor, null, null, null, null, null,
                mock(StatusResolver.class));

        Message message = mock(Message.class);
        Mockito.when(message.getMessageType()).thenReturn(MessageType.NEW_BLOCK_HASHES);

        final SimplePeer sender = new SimplePeer(new NodeID(new byte[] {1}));

        handler.processMessage(sender, message);

        verify(blockProcessor, never()).processNewBlockHashesMessage(any(), any());
    }

    @Test
    void processTransactionsMessage() {
        PeerScoringManager scoring = createPeerScoringManager();
        TransactionGateway transactionGateway = mock(TransactionGateway.class);
        BlockProcessor blockProcessor = mock(BlockProcessor.class);
        Mockito.when(blockProcessor.hasBetterBlockToSync()).thenReturn(false);

        final NodeMessageHandler handler = new NodeMessageHandler(config, blockProcessor, null, null, null, transactionGateway, scoring,
                mock(StatusResolver.class));

        final SimplePeer sender = new SimplePeer(new NodeID(new byte[] {1}));
        final SimplePeer sender2 = new SimplePeer(new NodeID(new byte[] {2}));

        final List<Transaction> txs = TransactionUtils.getTransactions(10);
        final TransactionsMessage message = new TransactionsMessage(txs);

        handler.processMessage(sender, message);

        Mockito.verify(transactionGateway, times(1)).receiveTransactionsFrom(txs, Collections.singleton(sender.getPeerNodeID()));

        handler.processMessage(sender2, message);

        Mockito.verify(transactionGateway, times(1)).receiveTransactionsFrom(txs, Collections.singleton(sender2.getPeerNodeID()));

        Assertions.assertFalse(scoring.isEmpty());

        PeerScoring pscoring = scoring.getPeerScoring(sender.getPeerNodeID());

        Assertions.assertNotNull(pscoring);
        Assertions.assertFalse(pscoring.isEmpty());
        Assertions.assertEquals(10, pscoring.getTotalEventCounter());
        Assertions.assertEquals(10, pscoring.getEventCounter(EventType.VALID_TRANSACTION));

        pscoring = scoring.getPeerScoring(sender2.getPeerNodeID());

        Assertions.assertNotNull(pscoring);
        Assertions.assertFalse(pscoring.isEmpty());
        Assertions.assertEquals(10, pscoring.getTotalEventCounter());
        Assertions.assertEquals(10, pscoring.getEventCounter(EventType.VALID_TRANSACTION));
    }

    @Test
    void processRejectedTransactionsMessage() {
        PeerScoringManager scoring = createPeerScoringManager();
        final SimpleChannelManager channelManager = new SimpleChannelManager();
        TransactionGateway transactionGateway = mock(TransactionGateway.class);
        BlockProcessor blockProcessor = mock(BlockProcessor.class);
        Mockito.when(blockProcessor.hasBetterBlockToSync()).thenReturn(false);

        final NodeMessageHandler handler = new NodeMessageHandler(config, blockProcessor, null, null, channelManager, transactionGateway, scoring,
                mock(StatusResolver.class));

        final SimplePeer sender = new SimplePeer();

        final List<Transaction> txs = TransactionUtils.getTransactions(0);
        final TransactionsMessage message = new TransactionsMessage(txs);

        handler.processMessage(sender, message);

        Assertions.assertNotNull(channelManager.getTransactions());
        Assertions.assertEquals(0, channelManager.getTransactions().size());

        Assertions.assertTrue(scoring.isEmpty());

        PeerScoring pscoring = scoring.getPeerScoring(sender.getPeerNodeID());

        Assertions.assertNotNull(pscoring);
        Assertions.assertTrue(pscoring.isEmpty());
        Assertions.assertEquals(0, pscoring.getTotalEventCounter());
        Assertions.assertEquals(0, pscoring.getEventCounter(EventType.INVALID_TRANSACTION));
    }

    @Test
    void processTooMuchGasTransactionMessage() {
        PeerScoringManager scoring = createPeerScoringManager();
        final SimpleChannelManager channelManager = new SimpleChannelManager();
        TransactionGateway transactionGateway = mock(TransactionGateway.class);
        BlockProcessor blockProcessor = mock(BlockProcessor.class);
        Mockito.when(blockProcessor.hasBetterBlockToSync()).thenReturn(false);

        final NodeMessageHandler handler = new NodeMessageHandler(config, blockProcessor, null, null, channelManager, transactionGateway, scoring,
                mock(StatusResolver.class));

        final SimplePeer sender = new SimplePeer();

        final List<Transaction> txs = new ArrayList<>();
        BigInteger value = BigInteger.ONE;
        BigInteger nonce = BigInteger.ZERO;
        BigInteger gasPrice = BigInteger.ONE;
        BigInteger gasLimit = BigDecimal.valueOf(Math.pow(2, 60)).add(BigDecimal.ONE).toBigInteger();
        txs.add(TransactionUtils.createTransaction(TransactionUtils.getPrivateKeyBytes(),
                TransactionUtils.getAddress(), value, nonce, gasPrice, gasLimit));
        final TransactionsMessage message = new TransactionsMessage(txs);

        handler.processMessage(sender, message);

        Assertions.assertNotNull(channelManager.getTransactions());
        Assertions.assertEquals(0, channelManager.getTransactions().size());

        Assertions.assertFalse(scoring.isEmpty());

        PeerScoring pscoring = scoring.getPeerScoring(sender.getPeerNodeID());

        Assertions.assertNotNull(pscoring);
        Assertions.assertFalse(pscoring.isEmpty());
        // besides this
        Assertions.assertEquals(1, pscoring.getTotalEventCounter());
        Assertions.assertEquals(1, pscoring.getEventCounter(EventType.VALID_TRANSACTION));
    }

    @Test
    void processTransactionsMessageUsingTransactionPool() {
        TransactionGateway transactionGateway = mock(TransactionGateway.class);
        BlockProcessor blockProcessor = mock(BlockProcessor.class);
        Mockito.when(blockProcessor.hasBetterBlockToSync()).thenReturn(false);

        final NodeMessageHandler handler = new NodeMessageHandler(config, blockProcessor, null, null, null, transactionGateway, RskMockFactory.getPeerScoringManager(),
                mock(StatusResolver.class));

        final SimplePeer sender = new SimplePeer(new NodeID(new byte[] {1}));
        final SimplePeer sender2 = new SimplePeer(new NodeID(new byte[] {2}));

        final List<Transaction> txs = TransactionUtils.getTransactions(10);
        final TransactionsMessage message = new TransactionsMessage(txs);

        handler.processMessage(sender, message);

        Mockito.verify(transactionGateway, times(1)).receiveTransactionsFrom(txs, Collections.singleton(sender.getPeerNodeID()));

        handler.processMessage(sender2, message);

        Mockito.verify(transactionGateway, times(1)).receiveTransactionsFrom(txs, Collections.singleton(sender2.getPeerNodeID()));
    }

    @Test
    void processBlockByHashRequestMessageUsingProcessor() {
        SimpleBlockProcessor sbp = new SimpleBlockProcessor();
        NodeMessageHandler processor = new NodeMessageHandler(config, sbp, null, null, null, null, null,
                mock(StatusResolver.class));
        Block block = new BlockChainBuilder().ofSize(1, true).getBestBlock();
        Message message = new BlockRequestMessage(100, block.getHash().getBytes());

        processor.processMessage(new SimplePeer(), message);

        Assertions.assertEquals(100, sbp.getRequestId());
        Assertions.assertArrayEquals(block.getHash().getBytes(), sbp.getHash());
    }

    @Test
    void processBlockHeadersRequestMessageUsingProcessor() {
        byte[] hash = TestUtils.generateBytes("sbp",32);
        SimpleBlockProcessor sbp = new SimpleBlockProcessor();
        NodeMessageHandler processor = new NodeMessageHandler(config, sbp, null, null, null, null, null,
                mock(StatusResolver.class));
        Message message = new BlockHeadersRequestMessage(100, hash, 50);

        processor.processMessage(new SimplePeer(), message);

        Assertions.assertEquals(100, sbp.getRequestId());
        Assertions.assertArrayEquals(hash, sbp.getHash());
    }

    private static PeerScoringManager createPeerScoringManager() {
        return new PeerScoringManager(
                PeerScoring::new,
                1000,
                new PunishmentParameters(600000, 10, 10000000),
                new PunishmentParameters(600000, 10, 10000000),
                Collections.emptyList(),
                Collections.emptyList()
        );
    }

    @Test
    void fillMessageQueue_thenBlockNewMessages() {

    	TransactionGateway transactionGateway = mock(TransactionGateway.class);
        BlockProcessor blockProcessor = mock(BlockProcessor.class);
        Mockito.when(blockProcessor.hasBetterBlockToSync()).thenReturn(false);

        final NodeMessageHandler handler = new NodeMessageHandler(config, blockProcessor, null, null, null, transactionGateway, RskMockFactory.getPeerScoringManager(),
                mock(StatusResolver.class));

        final SimplePeer sender = new SimplePeer(new NodeID(new byte[] {1}));

        // Add more than the queue supports
        int numMsgToAdd = config.getMessageQueueMaxSize() + 50;
        for(int i = 0; i < numMsgToAdd; i++) {

        	final TransactionsMessage message = new TransactionsMessage(TransactionUtils.getTransactions(1));

            handler.postMessage(sender, message, null);

        }

        // assert that the surplus was not added
        Assertions.assertEquals(config.getMessageQueueMaxSize(), handler.getMessageQueueSize(sender));
    }

    @Test
    void testTooLongIdle() {
        final SimplePeer sender = new SimplePeer();

        final ChannelManager channelManager = mock(ChannelManager.class);
        when(channelManager.getActivePeers()).thenReturn(Collections.singletonList(sender));

        final NodeMessageHandler handler = NodeMessageHandlerUtil.createHandlerWithSyncProcessor(SyncConfiguration.IMMEDIATE_FOR_TESTING, channelManager);

        BlockGenerator blockGenerator = new BlockGenerator();
        final Block block = blockGenerator.createChildBlock(blockGenerator.getGenesisBlock());
        final Status status = new Status(block.getNumber(), block.getHash().getBytes(), block.getParentHash().getBytes(), new BlockDifficulty(BigInteger.TEN));
        final Message message = new StatusMessage(status);

        handler.start();

        BooleanSupplier checkDelays = () -> TestUtils.getInternalState(handler, "recentIdleTime");

        // recentIdleTime should be originally false
        Assertions.assertFalse(checkDelays);

        // fake an old creationTime and wait for recentIdleTime to become true on message processing
        long oldCreationTime = System.currentTimeMillis() - Duration.ofSeconds(3).toMillis();
        handler.addMessage(sender, message, 100, new NodeMsgTraceInfo("testMsg", "testSession", oldCreationTime));
        Awaitility.await().atMost(2, TimeUnit.SECONDS).until(checkDelays::getAsBoolean);

        // fake an expired lastIdleWarn and wait for recentIdleTime to become false on #updateTimedEvents
        long oldDelayWarn = System.currentTimeMillis() - Duration.ofSeconds(11).toMillis();
        TestUtils.setInternalState(handler, "lastIdleWarn", oldDelayWarn);
        Awaitility.await().atMost(2, TimeUnit.SECONDS).until(() -> !checkDelays.getAsBoolean());

        // recentIdleTime should become true again on new message processing
        handler.addMessage(sender, message, 100, new NodeMsgTraceInfo("testMsg2", "testSession2", oldCreationTime));
        Awaitility.await().atMost(2, TimeUnit.SECONDS).until(checkDelays::getAsBoolean);
    }

    @Test
    void testTooLongProcessing() {
        final SimplePeer sender = new SimplePeer();

        final ChannelManager channelManager = mock(ChannelManager.class);
        when(channelManager.getActivePeers()).thenReturn(Collections.singletonList(sender));

        BlockGenerator blockGenerator = new BlockGenerator();
        final Block block = blockGenerator.createChildBlock(blockGenerator.getGenesisBlock());

        {
            Logger loggerNonBlock = mock(Logger.class);

            final Status status = new Status(block.getNumber(), block.getHash().getBytes(), block.getParentHash().getBytes(), new BlockDifficulty(BigInteger.TEN));
            final Message message1 = new StatusMessage(status);
            NodeMessageHandler.MessageTask task1 = new NodeMessageHandler.MessageTask(sender, message1, 100, new NodeMsgTraceInfo("testMsg", "testSession"));

            // fake old start: 1st warn
            NodeMessageHandler.logEnd(task1, System.nanoTime() - Duration.ofSeconds(3).toNanos(), loggerNonBlock);
            verify(loggerNonBlock, times(1)).warn(argThat(s -> s.contains("processing took too much")), eq(message1.getMessageType()), anyLong());

            // fake recent start: still 1 one warn
            NodeMessageHandler.logEnd(task1, System.nanoTime() - Duration.ofMillis(5).toNanos(), loggerNonBlock);
            verify(loggerNonBlock, times(1)).warn(argThat(s -> s.contains("processing took too much")), eq(message1.getMessageType()), anyLong());
        }

        {
            Logger loggerBlock = mock(Logger.class);

            final Message message2 = new BlockMessage(block);
            NodeMessageHandler.MessageTask task2 = new NodeMessageHandler.MessageTask(sender, message2, 100, new NodeMsgTraceInfo("testMsg2", "testSession2"));

            // fake old start for block: 1st warn
            NodeMessageHandler.logEnd(task2, System.nanoTime() - Duration.ofSeconds(61).toNanos(), loggerBlock);
            verify(loggerBlock, times(1)).warn(argThat(s -> s.contains("processing took too much")), eq(message2.getMessageType()), anyLong());

            // fake old start for non-block: still 1 warn, Block has higher threshold for warn
            NodeMessageHandler.logEnd(task2, System.nanoTime() - Duration.ofSeconds(3).toNanos(), loggerBlock);
            verify(loggerBlock, times(1)).warn(argThat(s -> s.contains("processing took too much")), eq(message2.getMessageType()), anyLong());
        }

    }

    @Test
    void whenPostMsgFromDiffSenders_shouldNotCountRepeatedMsgs() {
        final SimplePeer sender1 = new SimplePeer(new NodeID(new byte[] {1}));
        final SimplePeer sender2 = new SimplePeer(new NodeID(new byte[] {2}));
        PeerScoringManager scoring = createPeerScoringManager();
        SimpleBlockProcessor sbp = new SimpleBlockProcessor();
        NodeMessageHandler processor = new NodeMessageHandler(config, sbp, null, null, null, null, scoring,
                mock(StatusResolver.class));
        Block block = new BlockChainBuilder().ofSize(1, true).getBestBlock();
        Message message = new BlockMessage(block);

        processor.postMessage(sender1, message, null);
        processor.postMessage(sender2, message, null);

        PeerScoring pscoring1 = scoring.getPeerScoring(sender1.getPeerNodeID());
        PeerScoring pscoring2 = scoring.getPeerScoring(sender2.getPeerNodeID());

        Assertions.assertEquals(0, pscoring1.getEventCounter(EventType.REPEATED_MESSAGE));
        Assertions.assertEquals(0, pscoring2.getEventCounter(EventType.REPEATED_MESSAGE));
    }

    @Test
    void whenPostMsgFromSameSenders_shouldCountRepeatedMsgs() {
        final SimplePeer sender1 = new SimplePeer(new NodeID(new byte[] {1}));
        final SimplePeer sender2 = new SimplePeer(new NodeID(new byte[] {2}));
        PeerScoringManager scoring = createPeerScoringManager();
        SimpleBlockProcessor sbp = new SimpleBlockProcessor();
        NodeMessageHandler processor = new NodeMessageHandler(config, sbp, null, null, null, null, scoring,
                mock(StatusResolver.class));
        Block block = new BlockChainBuilder().ofSize(1, true).getBestBlock();
        Message message = new BlockMessage(block);

        processor.postMessage(sender1, message, null);
        processor.postMessage(sender2, message, null);
        processor.postMessage(sender2, message, null);

        PeerScoring pscoring1 = scoring.getPeerScoring(sender1.getPeerNodeID());
        PeerScoring pscoring2 = scoring.getPeerScoring(sender2.getPeerNodeID());

        Assertions.assertEquals(0, pscoring1.getEventCounter(EventType.REPEATED_MESSAGE));
        Assertions.assertEquals(1, pscoring2.getEventCounter(EventType.REPEATED_MESSAGE));
    }

    @Test
    void whenPostMsg_shouldClearRcvMsgsCache() {
        final SimplePeer sender1 = new SimplePeer(new NodeID(new byte[] {1}));
        final SimplePeer sender2 = new SimplePeer(new NodeID(new byte[] {2}));
        PeerScoringManager scoring = createPeerScoringManager();
        SimpleBlockProcessor sbp = new SimpleBlockProcessor();
        NodeMessageHandler processor = new NodeMessageHandler(config, sbp, null, null, null, null, scoring,
                mock(StatusResolver.class));
        Block block = new BlockChainBuilder().ofSize(1, true).getBestBlock();
        Message message = new BlockMessage(block);

        processor.postMessage(sender1, message, null);
        processor.postMessage(sender2, message, null);
        processor.postMessage(sender2, message, null);

        PeerScoring pscoring1 = scoring.getPeerScoring(sender1.getPeerNodeID());
        PeerScoring pscoring2 = scoring.getPeerScoring(sender2.getPeerNodeID());

        Assertions.assertEquals(0, pscoring1.getEventCounter(EventType.REPEATED_MESSAGE));
        Assertions.assertEquals(1, pscoring2.getEventCounter(EventType.REPEATED_MESSAGE));
    }

    @Test
    void whenAllowByMessageUniqueness_shouldReturnTrueForUniqueMsgs() {
        final SimplePeer sender1 = new SimplePeer(new NodeID(new byte[] {1}));
        final SimplePeer sender2 = new SimplePeer(new NodeID(new byte[] {2}));
        PeerScoringManager scoring = mock(PeerScoringManager.class);
        SimpleBlockProcessor sbp = new SimpleBlockProcessor();
        NodeMessageHandler processor = new NodeMessageHandler(config, sbp, null, null, null, null, scoring,
                mock(StatusResolver.class), 1);

        Block block = new BlockChainBuilder().ofSize(1, true).getBestBlock();
        Message message = new BlockMessage(block);

        Assertions.assertTrue(processor.allowByMessageUniqueness(sender1, message));
        verify(scoring, times(0)).recordEvent(any(), any(), any(), any(), any());
        Assertions.assertFalse(processor.allowByMessageUniqueness(sender2, message));
        verify(scoring, times(0)).recordEvent(any(), any(), any(), any(), any());
        Assertions.assertFalse(processor.allowByMessageUniqueness(sender2, message));
        verify(scoring, times(1)).recordEvent(any(), any(), any(), any(), any());
    }

    @Test
    void whenAllowByMessageUniqueness_shouldReturnTrueAfterCachedCleared() {
        final SimplePeer sender1 = new SimplePeer(new NodeID(new byte[] {1}));
        final SimplePeer sender2 = new SimplePeer(new NodeID(new byte[] {2}));
        PeerScoringManager scoring = mock(PeerScoringManager.class);
        SimpleBlockProcessor sbp = new SimpleBlockProcessor();
        NodeMessageHandler processor = new NodeMessageHandler(config, sbp, null, null, null, null, scoring,
                mock(StatusResolver.class), 0);

        Block block = new BlockChainBuilder().ofSize(1, true).getBestBlock();
        Message message = new BlockMessage(block);

        Assertions.assertTrue(processor.allowByMessageUniqueness(sender1, message));
        verify(scoring, times(0)).recordEvent(any(), any(), any(), any(), any());
        Assertions.assertTrue(processor.allowByMessageUniqueness(sender2, message));
        verify(scoring, times(0)).recordEvent(any(), any(), any(), any(), any());
        Assertions.assertTrue(processor.allowByMessageUniqueness(sender2, message));
        verify(scoring, times(0)).recordEvent(any(), any(), any(), any(), any());
    }
}

