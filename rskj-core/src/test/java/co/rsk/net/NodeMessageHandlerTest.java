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
import co.rsk.core.commons.Keccak256;
import co.rsk.config.RskSystemProperties;
import co.rsk.db.RepositoryImpl;
import co.rsk.net.handler.TxHandler;
import co.rsk.net.handler.TxHandlerImpl;
import co.rsk.net.messages.*;
import co.rsk.net.simples.SimpleBlockProcessor;
import co.rsk.net.simples.SimpleMessageChannel;
import co.rsk.net.simples.SimplePendingState;
import co.rsk.net.sync.SyncConfiguration;
import co.rsk.net.utils.TransactionUtils;
import co.rsk.scoring.EventType;
import co.rsk.scoring.PeerScoring;
import co.rsk.scoring.PeerScoringManager;
import co.rsk.scoring.PunishmentParameters;
import co.rsk.test.World;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.validators.DummyBlockValidationRule;
import co.rsk.validators.ProofOfWorkRule;
import org.ethereum.config.BlockchainNetConfig;
import org.ethereum.config.blockchain.RegTestConfig;
import org.ethereum.core.*;
import org.ethereum.crypto.HashUtil;
import org.ethereum.manager.WorldManager;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.rpc.Simples.SimpleChannelManager;
import org.ethereum.util.RskMockFactory;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;

import javax.annotation.Nonnull;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.UnknownHostException;
import java.util.*;
import java.util.stream.Collectors;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * Created by ajlopez on 5/10/2016.
 */
public class NodeMessageHandlerTest {
    private static BlockchainNetConfig blockchainNetConfigOriginal;
    private static RskSystemProperties config;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        config = new RskSystemProperties();
        config.setBlockchainConfig(new RegTestConfig());
    }

    @Test
    public void processBlockMessageUsingProcessor() throws UnknownHostException {
        SimpleMessageChannel sender = new SimpleMessageChannel();
        PeerScoringManager scoring = createPeerScoringManager();
        SimpleBlockProcessor sbp = new SimpleBlockProcessor();
        NodeMessageHandler processor = new NodeMessageHandler(config, sbp, null, null, null, null, scoring, new ProofOfWorkRule(config).setFallbackMiningEnabled(false));
        Block block = BlockChainBuilder.ofSize(1, true).getBestBlock();
        Message message = new BlockMessage(block);

        processor.processMessage(sender, message);

        Assert.assertNotNull(sbp.getBlocks());
        Assert.assertEquals(1, sbp.getBlocks().size());
        Assert.assertSame(block, sbp.getBlocks().get(0));

        Assert.assertFalse(scoring.isEmpty());

        PeerScoring pscoring = scoring.getPeerScoring(sender.getPeerNodeID());

        Assert.assertNotNull(pscoring);
        Assert.assertFalse(pscoring.isEmpty());
        Assert.assertEquals(1, pscoring.getTotalEventCounter());
        Assert.assertEquals(1, pscoring.getEventCounter(EventType.VALID_BLOCK));

        pscoring = scoring.getPeerScoring(sender.getAddress());

        Assert.assertNotNull(pscoring);
        Assert.assertFalse(pscoring.isEmpty());
        Assert.assertEquals(1, pscoring.getTotalEventCounter());
        Assert.assertEquals(1, pscoring.getEventCounter(EventType.VALID_BLOCK));
    }

    @Test
    public void skipProcessGenesisBlock() throws UnknownHostException {
        SimpleMessageChannel sender = new SimpleMessageChannel();
        PeerScoringManager scoring = createPeerScoringManager();
        SimpleBlockProcessor sbp = new SimpleBlockProcessor();
        NodeMessageHandler processor = new NodeMessageHandler(config, sbp, null, null, null,null, scoring, new DummyBlockValidationRule());
        Block block = BlockGenerator.getInstance().getGenesisBlock();
        Message message = new BlockMessage(block);

        processor.processMessage(sender, message);

        Assert.assertNotNull(sbp.getBlocks());
        Assert.assertEquals(0, sbp.getBlocks().size());
        Assert.assertTrue(scoring.isEmpty());

        PeerScoring pscoring = scoring.getPeerScoring(sender.getPeerNodeID());

        Assert.assertNotNull(pscoring);
        Assert.assertTrue(pscoring.isEmpty());
    }

    @Test
    public void postBlockMessageTwice() throws InterruptedException, UnknownHostException {
        MessageChannel sender = new SimpleMessageChannel();
        PeerScoringManager scoring = createPeerScoringManager();
        SimpleBlockProcessor sbp = new SimpleBlockProcessor();
        NodeMessageHandler processor = new NodeMessageHandler(config, sbp, null, null, null, null, scoring,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false));
        Block block = BlockChainBuilder.ofSize(1, true).getBestBlock();
        Message message = new BlockMessage(block);

        processor.postMessage(sender, message);
        processor.postMessage(sender, message);

        PeerScoring pscoring = scoring.getPeerScoring(sender.getPeerNodeID());

        Assert.assertNotNull(pscoring);
        Assert.assertFalse(pscoring.isEmpty());
        Assert.assertEquals(1, pscoring.getTotalEventCounter());
        Assert.assertEquals(1, pscoring.getEventCounter(EventType.REPEATED_MESSAGE));
    }

    @Test
    public void postBlockMessageUsingProcessor() throws InterruptedException, UnknownHostException {
        SimpleBlockProcessor sbp = new SimpleBlockProcessor();
        NodeMessageHandler processor = new NodeMessageHandler(config, sbp, null, null, null, null,
                null,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false));
        Block block = BlockChainBuilder.ofSize(1, true).getBestBlock();
        Message message = new BlockMessage(block);

        processor.start();
        processor.postMessage(new SimpleMessageChannel(), message);

        Thread.sleep(1000);

        processor.stop();

        Assert.assertNotNull(sbp.getBlocks());
        Assert.assertEquals(1, sbp.getBlocks().size());
        Assert.assertSame(block, sbp.getBlocks().get(0));
    }

    // TODO: Difficulty in RegTest is so small that this test will sometimes pass and other times fail
    // This should be executed in a special mode where difficulty is high.
    @Ignore
    public void processInvalidPoWMessageUsingProcessor() throws UnknownHostException {
        SimpleMessageChannel sender = new SimpleMessageChannel();
        PeerScoringManager scoring = createPeerScoringManager();
        SimpleBlockProcessor sbp = new SimpleBlockProcessor();
        NodeMessageHandler processor = new NodeMessageHandler(config, sbp, null, null, null,
                null, scoring,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false));
        Block block = BlockChainBuilder.ofSize(1, true).getBestBlock();
        byte[] mergedMiningHeader = block.getBitcoinMergedMiningHeader();
        mergedMiningHeader[76] += 3; //change merged mining nonce.
        Message message = new BlockMessage(block);

        processor.processMessage(sender, message);

        Assert.assertNotNull(sbp.getBlocks());
        Assert.assertEquals(0, sbp.getBlocks().size());

        Assert.assertFalse(scoring.isEmpty());

        PeerScoring pscoring = scoring.getPeerScoring(sender.getPeerNodeID());

        Assert.assertNotNull(pscoring);
        Assert.assertFalse(pscoring.isEmpty());
        Assert.assertEquals(1, pscoring.getTotalEventCounter());
        Assert.assertEquals(1, pscoring.getEventCounter(EventType.INVALID_BLOCK));
    }

    @Test
    public void processMissingPoWBlockMessageUsingProcessor() throws UnknownHostException {
        SimpleMessageChannel sender = new SimpleMessageChannel();
        PeerScoringManager scoring = createPeerScoringManager();
        SimpleBlockProcessor sbp = new SimpleBlockProcessor();
        NodeMessageHandler processor = new NodeMessageHandler(config, sbp, null, null, null, null, scoring, new DummyBlockValidationRule());
        Block block = BlockGenerator.getInstance().getGenesisBlock();

        for (int i = 0; i < 50; i++) {
            block = BlockGenerator.getInstance().createChildBlock(block);
        }

        Message message = new BlockMessage(block);
        processor.processMessage(sender, message);

        Assert.assertNotNull(sbp.getBlocks());
        Assert.assertEquals(1, sbp.getBlocks().size());

        Assert.assertFalse(scoring.isEmpty());

        PeerScoring pscoring = scoring.getPeerScoring(sender.getPeerNodeID());

        Assert.assertNotNull(pscoring);
        Assert.assertFalse(pscoring.isEmpty());
        Assert.assertEquals(1, pscoring.getTotalEventCounter());
        Assert.assertEquals(1, pscoring.getEventCounter(EventType.VALID_BLOCK));
        Assert.assertEquals(0, pscoring.getEventCounter(EventType.INVALID_BLOCK));
    }

    @Test
    public void processFutureBlockMessageUsingProcessor() throws UnknownHostException {
        SimpleBlockProcessor sbp = new SimpleBlockProcessor();
        NodeMessageHandler processor = new NodeMessageHandler(config, sbp, null, null, null, null, null,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false));
        Block block = BlockGenerator.getInstance().getGenesisBlock();
        Message message = new BlockMessage(block);
        SimpleMessageChannel sender = new SimpleMessageChannel();
        processor.processMessage(sender, message);

        Assert.assertNotNull(sbp.getBlocks());
        Assert.assertEquals(0, sbp.getBlocks().size());
    }

    @Test @Ignore("This should be reviewed with sync processor or deleted")
    public void processStatusMessageUsingNodeBlockProcessor() throws UnknownHostException {
        final World world = new World();
        final Blockchain blockchain = world.getBlockChain();
        final BlockStore store = new BlockStore();
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, syncConfiguration);
        final NodeBlockProcessor bp = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);
        final SimpleMessageChannel sender = new SimpleMessageChannel();
        final NodeMessageHandler handler = new NodeMessageHandler(config, bp, null, null, null, null, null,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false));

        final Block block = BlockGenerator.getInstance().createChildBlock(BlockGenerator.getInstance().getGenesisBlock());
        final Status status = new Status(block.getNumber(), block.getHash());
        final Message message = new StatusMessage(status);

        handler.processMessage(sender, message);

        Assert.assertNotNull(sender.getGetBlockMessages());
        Assert.assertEquals(1, sender.getGetBlockMessages().size());

        final Message msg = sender.getGetBlockMessages().get(0);

        Assert.assertEquals(MessageType.GET_BLOCK_MESSAGE, msg.getMessageType());

        final GetBlockMessage gbMessage = (GetBlockMessage) msg;

        Assert.assertEquals(block.getHash(), gbMessage.getBlockHash());
    }

    @Test
    public void processStatusMessageUsingSyncProcessor() throws UnknownHostException {
        final SimpleMessageChannel sender = new SimpleMessageChannel();
        final NodeMessageHandler handler = NodeMessageHandlerUtil.createHandlerWithSyncProcessor();

        final Block block = BlockGenerator.getInstance().createChildBlock(BlockGenerator.getInstance().getGenesisBlock());
        final Status status = new Status(block.getNumber(), block.getHash(), block.getParentHash(), BigInteger.TEN);
        final Message message = new StatusMessage(status);

        handler.processMessage(sender, message);

        Assert.assertNotNull(sender.getGetBlockMessages());
        Assert.assertTrue(sender.getGetBlockMessages().isEmpty());
        Assert.assertNotNull(sender.getMessages());
        Assert.assertEquals(1, sender.getMessages().size());

        Message request = sender.getMessages().get(0);

        Assert.assertNotNull(request);
        Assert.assertEquals(MessageType.BLOCK_HEADERS_REQUEST_MESSAGE, request.getMessageType());
    }

    @Test
    public void processStatusMessageWithKnownBestBlock() throws UnknownHostException {
        final World world = new World();
        final Blockchain blockchain = world.getBlockChain();
        final BlockStore store = new BlockStore();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, syncConfiguration);
        final NodeBlockProcessor bp = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);
        final SimpleMessageChannel sender = new SimpleMessageChannel();
        final SyncProcessor syncProcessor = new SyncProcessor(config, blockchain, blockSyncService, RskMockFactory.getPeerScoringManager(), syncConfiguration, new DummyBlockValidationRule(), null);
        final NodeMessageHandler handler = new NodeMessageHandler(config, bp, syncProcessor, null, null, null, null,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false));

        final Block block = BlockGenerator.getInstance().createChildBlock(BlockGenerator.getInstance().getGenesisBlock());
        final Status status = new Status(block.getNumber(), block.getHash(), block.getParentHash(), blockchain.getTotalDifficulty());
        final Message message = new StatusMessage(status);

        store.saveBlock(block);

        handler.processMessage(sender, message);

        Assert.assertNotNull(sender.getMessages());
    }

    @Test
    public void processGetBlockMessageUsingBlockInStore() throws UnknownHostException {
        final Block block = BlockGenerator.getInstance().getBlock(3);
        final World world = new World();
        final Blockchain blockchain = world.getBlockChain();
        final BlockStore store = new BlockStore();


        store.saveBlock(block);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, syncConfiguration);
        final NodeBlockProcessor bp = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

        final NodeMessageHandler handler = new NodeMessageHandler(config, bp, null, null, null, null,
                null, new ProofOfWorkRule(config).setFallbackMiningEnabled(false));

        final SimpleMessageChannel sender = new SimpleMessageChannel();

        handler.processMessage(sender, new GetBlockMessage(block.getHash()));

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.BLOCK_MESSAGE, message.getMessageType());

        final BlockMessage bMessage = (BlockMessage) message;

        Assert.assertEquals(block.getHash(), bMessage.getBlock().getHash());
    }

    @Test
    public void processGetBlockMessageUsingBlockInBlockchain() throws UnknownHostException {
        final World world = new World();
        final Blockchain blockchain = world.getBlockChain();
        final BlockStore store = new BlockStore();

        List<Block> blocks = BlockGenerator.getInstance().getBlockChain(blockchain.getBestBlock(), 10);

        for (Block b: blocks)
            blockchain.tryToConnect(b);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, syncConfiguration);
        NodeBlockProcessor bp = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

        NodeMessageHandler handler = new NodeMessageHandler(config, bp, null, null, null, null,
                null,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false));

        SimpleMessageChannel sender = new SimpleMessageChannel();

        handler.processMessage(sender, new GetBlockMessage(blocks.get(4).getHash()));

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.BLOCK_MESSAGE, message.getMessageType());

        BlockMessage bmessage = (BlockMessage) message;

        Assert.assertEquals(blocks.get(4).getHash(), bmessage.getBlock().getHash());
    }

    @Test
    public void processGetBlockMessageUsingEmptyStore() throws UnknownHostException {
        final Block block = BlockGenerator.getInstance().getBlock(3);

        final World world = new World();
        final Blockchain blockchain = world.getBlockChain();
        final BlockStore store = new BlockStore();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, syncConfiguration);
        final NodeBlockProcessor bp = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

        final NodeMessageHandler handler = new NodeMessageHandler(config, bp, null, null, null, null, null, new DummyBlockValidationRule());

        final SimpleMessageChannel sender = new SimpleMessageChannel();

        handler.processMessage(sender, new GetBlockMessage(block.getHash()));

        Assert.assertTrue(sender.getMessages().isEmpty());
    }

    @Test
    public void processBlockHeaderRequestMessageUsingBlockInStore() throws UnknownHostException {
        final Block block = BlockGenerator.getInstance().getBlock(3);

        final World world = new World();
        final Blockchain blockchain = world.getBlockChain();
        final BlockStore store = new BlockStore();

        store.saveBlock(block);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, syncConfiguration);
        final NodeBlockProcessor bp = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

        final NodeMessageHandler handler = new NodeMessageHandler(config, bp, null, null, null, null,
                null,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false));

        final SimpleMessageChannel sender = new SimpleMessageChannel();

        handler.processMessage(sender, new BlockHeadersRequestMessage(1, block.getHash(), 1));

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.BLOCK_HEADERS_RESPONSE_MESSAGE, message.getMessageType());

        final BlockHeadersResponseMessage bMessage = (BlockHeadersResponseMessage) message;

        Assert.assertEquals(block.getHash(), bMessage.getBlockHeaders().get(0).getHash());
    }

    @Test
    public void processBlockHeaderRequestMessageUsingBlockInBlockchain() throws UnknownHostException {
        final World world = new World();
        final Blockchain blockchain = world.getBlockChain();
        final BlockStore store = new BlockStore();

        List<Block> blocks = BlockGenerator.getInstance().getBlockChain(blockchain.getBestBlock(), 10);

        for (Block b: blocks)
            blockchain.tryToConnect(b);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, syncConfiguration);
        NodeBlockProcessor bp = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

        NodeMessageHandler handler = new NodeMessageHandler(config, bp, null, null, null, null,
                null,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false));

        SimpleMessageChannel sender = new SimpleMessageChannel();

        handler.processMessage(sender, new BlockHeadersRequestMessage(1, blocks.get(4).getHash(), 1));

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.BLOCK_HEADERS_RESPONSE_MESSAGE, message.getMessageType());

        BlockHeadersResponseMessage bMessage = (BlockHeadersResponseMessage) message;

        Assert.assertEquals(blocks.get(4).getHash(), bMessage.getBlockHeaders().get(0).getHash());
    }

    @Test
    public void processNewBlockHashesMessage() throws UnknownHostException {
        final World world = new World();
        final Blockchain blockchain = world.getBlockChain();
        final BlockStore store = new BlockStore();

        final List<Block> blocks = BlockGenerator.getInstance().getBlockChain(blockchain.getBestBlock(), 15);
        final List<Block> bcBlocks = blocks.subList(0, 10);

        for (Block b: bcBlocks)
            blockchain.tryToConnect(b);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, syncConfiguration);
        final NodeBlockProcessor bp = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);
        final NodeMessageHandler handler = new NodeMessageHandler(config, bp, null, null, null, null, null,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false));

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
                                        new BlockIdentifier(blocks.get(5).getHash(), blocks.get(5).getNumber())
                                )
                        ),
                        null
                ),
                new TestCase(
                        new NewBlockHashesMessage(
                                Arrays.asList(
                                        new BlockIdentifier(blocks.get(11).getHash(), blocks.get(5).getNumber())
                                )
                        ),
                        Arrays.asList(blocks.get(11))
                ),
                new TestCase(
                        new NewBlockHashesMessage(
                                Arrays.asList(
                                        new BlockIdentifier(blocks.get(11).getHash(), blocks.get(5).getNumber()),
                                        new BlockIdentifier(blocks.get(5).getHash(), blocks.get(5).getNumber())
                                )
                        ),
                        Arrays.asList(blocks.get(11))
                ),
                new TestCase(
                        new NewBlockHashesMessage(
                                Arrays.asList(
                                        new BlockIdentifier(blocks.get(11).getHash(), blocks.get(5).getNumber()),
                                        new BlockIdentifier(blocks.get(12).getHash(), blocks.get(5).getNumber())
                                )
                        ),
                        Arrays.asList(blocks.get(11), blocks.get(12))
                ),
                new TestCase(
                        new NewBlockHashesMessage(
                                Arrays.asList(
                                        new BlockIdentifier(blocks.get(11).getHash(), blocks.get(5).getNumber()),
                                        new BlockIdentifier(blocks.get(11).getHash(), blocks.get(5).getNumber())
                                )
                        ),
                        Arrays.asList(blocks.get(11))
                )
        };

        for (int i = 0; i < testCases.length; i += 1) {
            final TestCase testCase = testCases[i];
            final SimpleMessageChannel sender = new SimpleMessageChannel();

            handler.processMessage(sender, testCase.message);

            if (testCase.expected == null) {
                Assert.assertTrue(sender.getMessages().isEmpty());
                continue;
            }

            Assert.assertEquals(testCase.expected.size(), sender.getMessages().size());

            Assert.assertTrue(sender.getMessages().stream().allMatch(m -> m.getMessageType() == MessageType.GET_BLOCK_MESSAGE));

            List<Keccak256> msgs = sender.getMessages().stream()
                    .map(m -> (GetBlockMessage) m)
                    .map(m -> m.getBlockHash())
                    .collect(Collectors.toList());

            Set<Keccak256> expected = testCase.expected.stream()
                    .map(b -> b.getHash())
                    .collect(Collectors.toSet());

            for (Keccak256 h : msgs) {
                Assert.assertTrue(expected.contains(h));
            }

            for (Keccak256 h : expected) {
                Assert.assertTrue(
                        msgs.stream()
                                .filter(h1 -> h.equals(h1))
                                .count() == 1
                );
            }

        }
    }

    @Test
    public void processNewBlockHashesMessageDoesNothingBecauseNodeIsSyncing() {
        TxHandler txHandler = mock(TxHandler.class);
        BlockProcessor blockProcessor = mock(BlockProcessor.class);
        Mockito.when(blockProcessor.hasBetterBlockToSync()).thenReturn(true);

        final NodeMessageHandler handler = new NodeMessageHandler(config, blockProcessor, null, null, null, txHandler, null,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false));

        Message message = mock(Message.class);
        Mockito.when(message.getMessageType()).thenReturn(MessageType.NEW_BLOCK_HASHES);

        handler.processMessage(null, message);

        verify(blockProcessor, never()).processNewBlockHashesMessage(any(), any());
    }

    @Test @Ignore("Block headers message is deprecated must be rewrited or deleted")
    public void processGetBlockHeadersMessage() throws UnknownHostException {
        final World world = new World();
        final Blockchain blockchain = world.getBlockChain();
        final BlockStore store = new BlockStore();

        final List<Block> blocks = BlockGenerator.getInstance().getBlockChain(blockchain.getBestBlock(), 10);

        for (Block b: blocks) {
            ImportResult result = blockchain.tryToConnect(b);
            System.out.println(result);
        }


        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, syncConfiguration);
        final NodeBlockProcessor bp = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);
        final NodeMessageHandler handler = new NodeMessageHandler(config, bp, null, null, null, null, null,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false));

        int baseBlock = 9;

        /*
            TestCase represents a GetBlockHeaderMessage test case: it receives an input and the expected BlockHeaders.
            if expected == null, we are not expecting to receive any message.
        */
        class TestCase {
            protected final GetBlockHeadersMessage gbhMessage;
            protected final List<BlockHeader> expected;

            public TestCase(@Nonnull final GetBlockHeadersMessage gbhMessage, final List<BlockHeader> expected) {
                this.gbhMessage = gbhMessage;
                this.expected = expected;
            }
        }

        TestCase[] testCases = {
                new TestCase(
                        new GetBlockHeadersMessage(blocks.get(baseBlock).getHash(), 0),
                        null
                ),
                new TestCase(
                        new GetBlockHeadersMessage(blocks.get(baseBlock).getHash(), 1),
                        Arrays.asList(
                                blocks.get(baseBlock).getHeader()
                        )
                ),
                new TestCase(
                        new GetBlockHeadersMessage(blocks.get(baseBlock).getHash(), 5),
                        Arrays.asList(
                                blocks.get(baseBlock).getHeader(),
                                blocks.get(baseBlock - 1).getHeader(),
                                blocks.get(baseBlock - 2).getHeader(),
                                blocks.get(baseBlock - 3).getHeader(),
                                blocks.get(baseBlock - 4).getHeader()
                        )
                ),
                new TestCase(
                        new GetBlockHeadersMessage(0, blocks.get(baseBlock).getHash(), 5, 1, false),
                        Arrays.asList(
                                blocks.get(baseBlock).getHeader(),
                                blocks.get(baseBlock - 2).getHeader(),
                                blocks.get(baseBlock - 4).getHeader(),
                                blocks.get(baseBlock - 6).getHeader(),
                                blocks.get(baseBlock - 8).getHeader()
                        )
                ),
                new TestCase(
                        new GetBlockHeadersMessage(blocks.get(baseBlock).getNumber(), 1),
                        Arrays.asList(
                                blocks.get(baseBlock).getHeader()
                        )
                ),
                new TestCase(
                        new GetBlockHeadersMessage(blocks.get(baseBlock).getNumber(), null, 5, 1, false),
                        Arrays.asList(
                                blocks.get(baseBlock).getHeader(),
                                blocks.get(baseBlock - 2).getHeader(),
                                blocks.get(baseBlock - 4).getHeader(),
                                blocks.get(baseBlock - 6).getHeader(),
                                blocks.get(baseBlock - 8).getHeader()
                        )
                )
        };

        for (int i = 0; i < testCases.length; i += 1) {
            final TestCase testCase = testCases[i];
            final SimpleMessageChannel sender = new SimpleMessageChannel();

            handler.processMessage(sender, testCase.gbhMessage);

            if (testCase.expected == null) {
                Assert.assertEquals(0, sender.getMessages().size());
                continue;
            }
            Assert.assertEquals(1, sender.getMessages().size());

            final Message message = sender.getMessages().get(0);
            Assert.assertEquals(MessageType.BLOCK_HEADERS_MESSAGE, message.getMessageType());

            final List<BlockHeader> headers = ((BlockHeadersMessage) message).getBlockHeaders();
            equalBlockHeaders(testCase.expected, headers);
        }
    }

    private void equalBlockHeaders(@Nonnull final List<BlockHeader> expected, @Nonnull final List<BlockHeader> received) {
        Assert.assertEquals(expected.size(), received.size());
        for (int i = 0; i < received.size(); i += 1) {
            Assert.assertEquals(expected.get(i).getNumber(), received.get(i).getNumber());
            Assert.assertEquals(expected.get(i).getHash(), received.get(i).getHash());
            Assert.assertEquals(expected.get(i).getParentHash(), received.get(i).getParentHash());
        }
    }

    @Test
    public void processGetBlockHeaderMessageUsingEmptyStore() throws UnknownHostException {
        final Block block = BlockGenerator.getInstance().getBlock(3);

        final World world = new World();
        final Blockchain blockchain = world.getBlockChain();
        final BlockStore store = new BlockStore();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, syncConfiguration);
        final NodeBlockProcessor bp = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

        final NodeMessageHandler handler = new NodeMessageHandler(config, bp, null, null, null, null, null, new DummyBlockValidationRule());

        final SimpleMessageChannel sender = new SimpleMessageChannel();

        handler.processMessage(sender, new GetBlockHeadersMessage(block.getHash(), 1));

        Assert.assertTrue(sender.getMessages().isEmpty());
    }

    @Test
    public void processTransactionsMessage() throws UnknownHostException {
        PeerScoringManager scoring = createPeerScoringManager();
        final SimpleChannelManager channelManager = new SimpleChannelManager();
        TxHandler txmock = mock(TxHandler.class);
        PendingState state = mock(PendingState.class);
        Mockito.when(state.addWireTransactions(any())).thenAnswer(i -> i.getArguments()[0]);
        BlockProcessor blockProcessor = mock(BlockProcessor.class);
        Mockito.when(blockProcessor.hasBetterBlockToSync()).thenReturn(false);

        final NodeMessageHandler handler = new NodeMessageHandler(config, blockProcessor, null, channelManager, state, txmock, scoring,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false));

        final SimpleMessageChannel sender = new SimpleMessageChannel();
        sender.setPeerNodeID(new byte[] {1});
        final SimpleMessageChannel sender2 = new SimpleMessageChannel();
        sender2.setPeerNodeID(new byte[] {2});

        final List<Transaction> txs = TransactionUtils.getTransactions(10);
        Mockito.when(txmock.retrieveValidTxs(any(List.class))).thenReturn(txs);
        final TransactionsMessage message = new TransactionsMessage(txs);

        handler.processMessage(sender, message);

        Assert.assertNotNull(channelManager.getTransactions());
        Assert.assertEquals(10, channelManager.getTransactions().size());

        for (int k = 0; k < 10; k++)
            Assert.assertSame(txs.get(k), channelManager.getTransactions().get(k));

        Assert.assertNotNull(channelManager.getLastSkip());
        Assert.assertEquals(1, channelManager.getLastSkip().size());
        Assert.assertTrue(channelManager.getLastSkip().contains(sender.getPeerNodeID()));

        handler.processMessage(sender2, message);

        Assert.assertNotNull(channelManager.getLastSkip());
        Assert.assertEquals(2, channelManager.getLastSkip().size());
        Assert.assertTrue(channelManager.getLastSkip().contains(sender.getPeerNodeID()));
        Assert.assertTrue(channelManager.getLastSkip().contains(sender2.getPeerNodeID()));

        Assert.assertFalse(scoring.isEmpty());

        PeerScoring pscoring = scoring.getPeerScoring(sender.getPeerNodeID());

        Assert.assertNotNull(pscoring);
        Assert.assertFalse(pscoring.isEmpty());
        Assert.assertEquals(10, pscoring.getTotalEventCounter());
        Assert.assertEquals(10, pscoring.getEventCounter(EventType.VALID_TRANSACTION));

        pscoring = scoring.getPeerScoring(sender2.getPeerNodeID());

        Assert.assertNotNull(pscoring);
        Assert.assertFalse(pscoring.isEmpty());
        Assert.assertEquals(10, pscoring.getTotalEventCounter());
        Assert.assertEquals(10, pscoring.getEventCounter(EventType.VALID_TRANSACTION));
    }

    @Test
    public void processRejectedTransactionsMessage() throws UnknownHostException {
        PeerScoringManager scoring = createPeerScoringManager();
        final SimpleChannelManager channelManager = new SimpleChannelManager();
        TxHandler txmock = mock(TxHandler.class);
        PendingState state = mock(PendingState.class);
        BlockProcessor blockProcessor = mock(BlockProcessor.class);
        Mockito.when(blockProcessor.hasBetterBlockToSync()).thenReturn(false);

        final NodeMessageHandler handler = new NodeMessageHandler(config, blockProcessor, null, channelManager, state, txmock, scoring,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false));

        final SimpleMessageChannel sender = new SimpleMessageChannel();

        final List<Transaction> txs = TransactionUtils.getTransactions(0);
        Mockito.when(txmock.retrieveValidTxs(any(List.class))).thenReturn(txs);
        final TransactionsMessage message = new TransactionsMessage(txs);

        handler.processMessage(sender, message);

        Assert.assertNotNull(channelManager.getTransactions());
        Assert.assertEquals(0, channelManager.getTransactions().size());

        Assert.assertTrue(scoring.isEmpty());

        PeerScoring pscoring = scoring.getPeerScoring(sender.getPeerNodeID());

        Assert.assertNotNull(pscoring);
        Assert.assertTrue(pscoring.isEmpty());
        Assert.assertEquals(0, pscoring.getTotalEventCounter());
        Assert.assertEquals(0, pscoring.getEventCounter(EventType.INVALID_TRANSACTION));
    }

    @Test
    public void processTooMuchGasTransactionMessage() throws UnknownHostException {
        PeerScoringManager scoring = createPeerScoringManager();
        final SimpleChannelManager channelManager = new SimpleChannelManager();
        final World world = new World();
        final Blockchain blockchain = world.getBlockChain();
        PendingState state = mock(PendingState.class);
        BlockProcessor blockProcessor = mock(BlockProcessor.class);
        Mockito.when(blockProcessor.hasBetterBlockToSync()).thenReturn(false);
        TxHandler txHandler = new TxHandlerImpl(config, mock(WorldManager.class), mock(RepositoryImpl.class), blockchain);

        final NodeMessageHandler handler = new NodeMessageHandler(config, blockProcessor, null, channelManager, state, txHandler, scoring,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false));

        final SimpleMessageChannel sender = new SimpleMessageChannel();

        final List<Transaction> txs = new ArrayList<>();
        BigInteger value = BigInteger.ONE;
        BigInteger nonce = BigInteger.ZERO;
        BigInteger gasPrice = BigInteger.ONE;
        BigInteger gasLimit = BigDecimal.valueOf(Math.pow(2, 60)).add(BigDecimal.ONE).toBigInteger();
        txs.add(TransactionUtils.createTransaction(TransactionUtils.getPrivateKeyBytes(),
                TransactionUtils.getAddress(), value, nonce, gasPrice, gasLimit));
        final TransactionsMessage message = new TransactionsMessage(txs);

        handler.processMessage(sender, message);

        Assert.assertNotNull(channelManager.getTransactions());
        Assert.assertEquals(0, channelManager.getTransactions().size());

        Assert.assertFalse(scoring.isEmpty());

        PeerScoring pscoring = scoring.getPeerScoring(sender.getPeerNodeID());

        Assert.assertNotNull(pscoring);
        Assert.assertFalse(pscoring.isEmpty());
        // besides this
        Assert.assertEquals(1, pscoring.getTotalEventCounter());
        Assert.assertEquals(1, pscoring.getEventCounter(EventType.VALID_TRANSACTION));
    }

    @Test
    public void processTransactionsMessageDoesNothingBecauseNodeIsSyncing() {
        ChannelManager channelManager = mock(ChannelManager.class);
        TxHandler txHandler = mock(TxHandler.class);
        BlockProcessor blockProcessor = mock(BlockProcessor.class);
        Mockito.when(blockProcessor.hasBetterBlockToSync()).thenReturn(true);

        final NodeMessageHandler handler = new NodeMessageHandler(config, blockProcessor, null, channelManager, null, txHandler, null,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false));

        Message message = mock(Message.class);
        Mockito.when(message.getMessageType()).thenReturn(MessageType.TRANSACTIONS);

        handler.processMessage(null, message);

        verify(channelManager, never()).broadcastTransaction(any(), any());
    }

    @Test
    public void processTransactionsMessageUsingPendingState() throws UnknownHostException {
        final SimplePendingState pendingState = new SimplePendingState();
        final SimpleChannelManager channelManager = new SimpleChannelManager();
        TxHandler txmock = mock(TxHandler.class);
        BlockProcessor blockProcessor = mock(BlockProcessor.class);
        Mockito.when(blockProcessor.hasBetterBlockToSync()).thenReturn(false);

        final NodeMessageHandler handler = new NodeMessageHandler(config, blockProcessor, null, channelManager, pendingState, txmock, RskMockFactory.getPeerScoringManager(),
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false));

        final SimpleMessageChannel sender = new SimpleMessageChannel();
        sender.setPeerNodeID(new byte[] {1});
        final SimpleMessageChannel sender2 = new SimpleMessageChannel();
        sender2.setPeerNodeID(new byte[] {2});

        final List<Transaction> txs = TransactionUtils.getTransactions(10);
        Mockito.when(txmock.retrieveValidTxs(any(List.class))).thenReturn(txs);
        final TransactionsMessage message = new TransactionsMessage(txs);

        handler.processMessage(sender, message);

        Assert.assertNotNull(channelManager.getTransactions());
        Assert.assertEquals(10, channelManager.getTransactions().size());

        for (int k = 0; k < 10; k++)
            Assert.assertSame(txs.get(k), channelManager.getTransactions().get(k));

        Assert.assertNotNull(channelManager.getLastSkip());
        Assert.assertEquals(1, channelManager.getLastSkip().size());
        Assert.assertTrue(channelManager.getLastSkip().contains(sender.getPeerNodeID()));

        channelManager.setLastSkip(null);
        handler.processMessage(sender2, message);

        Assert.assertNull(channelManager.getLastSkip());
    }

    @Test
    public void processBlockByHashRequestMessageUsingProcessor() throws UnknownHostException {
        SimpleBlockProcessor sbp = new SimpleBlockProcessor();
        NodeMessageHandler processor = new NodeMessageHandler(config, sbp, null, null, null, null,
                null,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false));
        Block block = BlockChainBuilder.ofSize(1, true).getBestBlock();
        Message message = new BlockRequestMessage(100, block.getHash());

        processor.processMessage(new SimpleMessageChannel(), message);

        Assert.assertEquals(100, sbp.getRequestId());
        Assert.assertEquals(block.getHash(), sbp.getHash());
    }

    @Test
    public void processBlockHeadersRequestMessageUsingProcessor() throws UnknownHostException {
        Keccak256 hash = HashUtil.randomSha3Hash();
        SimpleBlockProcessor sbp = new SimpleBlockProcessor();
        NodeMessageHandler processor = new NodeMessageHandler(config, sbp, null, null, null, null, null,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false));
        Message message = new BlockHeadersRequestMessage(100, hash, 50);

        processor.processMessage(new SimpleMessageChannel(), message);

        Assert.assertEquals(100, sbp.getRequestId());
        Assert.assertEquals(hash, sbp.getHash());
    }

    private static PeerScoringManager createPeerScoringManager() {
        return new PeerScoringManager(1000, new PunishmentParameters(600000, 10, 10000000), new PunishmentParameters(600000, 10, 10000000));
    }
}

