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
import co.rsk.net.handler.TxHandler;
import co.rsk.net.messages.*;
import co.rsk.net.simples.SimpleMessageSender;
import co.rsk.net.utils.TransactionUtils;
import co.rsk.net.simples.SimpleBlockProcessor;
import co.rsk.net.simples.SimplePendingState;
import co.rsk.scoring.EventType;
import co.rsk.scoring.PeerScoring;
import co.rsk.scoring.PeerScoringManager;
import co.rsk.scoring.PunishmentParameters;
import co.rsk.test.World;
import org.ethereum.config.BlockchainNetConfig;
import org.ethereum.config.SystemProperties;
import org.ethereum.config.blockchain.RegTestConfig;
import org.ethereum.core.*;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.rpc.Simples.SimpleChannelManager;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.spongycastle.util.encoders.Hex;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Created by ajlopez on 5/10/2016.
 */
public class NodeMessageHandlerTest {
    private static BlockchainNetConfig blockchainNetConfigOriginal;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        blockchainNetConfigOriginal = SystemProperties.CONFIG.getBlockchainConfig();
        SystemProperties.CONFIG.setBlockchainConfig(new RegTestConfig());
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        SystemProperties.CONFIG.setBlockchainConfig(blockchainNetConfigOriginal);
    }

    private static String rlp = "f9030ff902eca079431a9262b600ef32a6817030453633d47d46d4c007b685d77a94a1c38048aca01dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d493479428fdc38c327f4a3bbdf9501fd3a01ac7228c7af7a06c5369d2b4bab07027fd384c9a533518e9145fa60e6e033643a93496976573c1a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421a03cbc7bed3852ab6e09537cef2f70e329251ba6b5accbf495b574059c31efc0f4b90100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000101832dc6c08084591c5e1e80800080b850711101000000000000000000000000000000000000000000000000000000000000000000afdcab14f0b9a7139c9146334699e8dde74694da35e30ab3759b59498d135d2e215e1c59ffff7f2103000000a70100000001afdcab14f0b9a7139c9146334699e8dde74694da35e30ab3759b59498d135d2e0101b8a300000000000003403daa1e6ab8bba82f3805006ae5d995da40f784faf67f1c38e97e8f6470d8a8a862a1d6e8a6110e8dd6cf28eb422b2e3f999f9293d150839debb15e131c52534b424c4f434b3ab1831f63f32d8ca273e09ac392694434f6fc1733f0622fb82119f9fcd5bf4b1cffffffff0100f2052a01000000232103232d7db808a1e895a810a12a86e4e9fcfd2a51b80589393a1344fa0614f6c829ac00000000dedd8000009400000000000000000000000000000000010000088080808080c0";

    @Test
    public void processBlockMessageUsingProcessor() {
        MessageSender sender = new SimpleMessageSender();
        PeerScoringManager scoring = createPeerScoringManager();
        SimpleBlockProcessor sbp = new SimpleBlockProcessor();
        NodeMessageHandler processor = new NodeMessageHandler(sbp, null, null, null, scoring);
        Block block = new Block(Hex.decode(rlp));
        Message message = new BlockMessage(block);

        processor.processMessage(sender, message);

        Assert.assertNotNull(sbp.getBlocks());
        Assert.assertEquals(1, sbp.getBlocks().size());
        Assert.assertSame(block, sbp.getBlocks().get(0));

        Assert.assertFalse(scoring.isEmpty());

        PeerScoring pscoring = scoring.getPeerScoring(sender.getNodeID());

        Assert.assertNotNull(pscoring);
        Assert.assertFalse(pscoring.isEmpty());
        Assert.assertEquals(1, pscoring.getTotalEventCounter());
        Assert.assertEquals(1, pscoring.getEventCounter(EventType.VALID_BLOCK));
    }

    @Test
    public void postBlockMessageUsingProcessor() throws InterruptedException {
        SimpleBlockProcessor sbp = new SimpleBlockProcessor();
        NodeMessageHandler processor = new NodeMessageHandler(sbp, null, null, null, null);
        Block block = new Block(Hex.decode(rlp));
        Message message = new BlockMessage(block);

        processor.start();
        processor.postMessage(new SimpleMessageSender(), message);

        Thread.sleep(1000);

        processor.stop();

        Assert.assertNotNull(sbp.getBlocks());
        Assert.assertEquals(1, sbp.getBlocks().size());
        Assert.assertSame(block, sbp.getBlocks().get(0));
    }

    @Test
    public void processInvalidPoWMessageUsingProcessor() {
        MessageSender sender = new SimpleMessageSender();
        PeerScoringManager scoring = createPeerScoringManager();
        SimpleBlockProcessor sbp = new SimpleBlockProcessor();
        NodeMessageHandler processor = new NodeMessageHandler(sbp, null, null, null, scoring);
        Block block = new Block(Hex.decode(rlp));
        byte[] mergedMiningHeader = block.getBitcoinMergedMiningHeader();
        mergedMiningHeader[76] += 3; //change merged mining nonce.
        Message message = new BlockMessage(block);

        processor.processMessage(sender, message);

        Assert.assertNotNull(sbp.getBlocks());
        Assert.assertEquals(0, sbp.getBlocks().size());

        Assert.assertFalse(scoring.isEmpty());

        PeerScoring pscoring = scoring.getPeerScoring(sender.getNodeID());

        Assert.assertNotNull(pscoring);
        Assert.assertFalse(pscoring.isEmpty());
        Assert.assertEquals(1, pscoring.getTotalEventCounter());
        Assert.assertEquals(1, pscoring.getEventCounter(EventType.INVALID_BLOCK));
    }

    @Test
    public void processMissingPoWBlockMessageUsingProcessor() {
        MessageSender sender = new SimpleMessageSender();
        PeerScoringManager scoring = createPeerScoringManager();
        SimpleBlockProcessor sbp = new SimpleBlockProcessor();
        NodeMessageHandler processor = new NodeMessageHandler(sbp, null, null, null, scoring).disablePoWValidation();
        Block block = BlockGenerator.getGenesisBlock();
        Message message = new BlockMessage(block);
        processor.processMessage(sender, message);

        for (int i = 0; i < 50; i++) {
            block = BlockGenerator.createChildBlock(block);
        }

        message = new BlockMessage(block);
        processor.processMessage(sender, message);

        Assert.assertNotNull(sbp.getBlocks());
        Assert.assertEquals(2, sbp.getBlocks().size());

        Assert.assertFalse(scoring.isEmpty());

        PeerScoring pscoring = scoring.getPeerScoring(sender.getNodeID());

        Assert.assertNotNull(pscoring);
        Assert.assertFalse(pscoring.isEmpty());
        Assert.assertEquals(2, pscoring.getTotalEventCounter());
        Assert.assertEquals(2, pscoring.getEventCounter(EventType.VALID_BLOCK));
        Assert.assertEquals(0, pscoring.getEventCounter(EventType.INVALID_BLOCK));
    }

    @Test
    public void processFutureBlockMessageUsingProcessor() {
        SimpleBlockProcessor sbp = new SimpleBlockProcessor();
        NodeMessageHandler processor = new NodeMessageHandler(sbp, null, null, null, null);
        Block block = BlockGenerator.getGenesisBlock();
        Message message = new BlockMessage(block);
        SimpleMessageSender sender = new SimpleMessageSender();
        processor.processMessage(sender, message);

        Assert.assertNotNull(sbp.getBlocks());
        Assert.assertEquals(0, sbp.getBlocks().size());
    }

    @Test
    public void processStatusMessageUsingNodeBlockProcessor() {
        final World world = new World();
        final BlockStore store = new BlockStore();
        final Blockchain blockchain = world.getBlockChain();
        final NodeBlockProcessor bp = new NodeBlockProcessor(store, blockchain);
        final SimpleMessageSender sender = new SimpleMessageSender();
        final NodeMessageHandler handler = new NodeMessageHandler(bp, null, null, null, null);

        final Block block = BlockGenerator.createChildBlock(BlockGenerator.getGenesisBlock());
        final Status status = new Status(block.getNumber(), block.getHash());
        final Message message = new StatusMessage(status);

        handler.processMessage(sender, message);

        Assert.assertNotNull(sender.getGetBlockMessages());
        Assert.assertEquals(1, sender.getGetBlockMessages().size());

        final Message msg = sender.getGetBlockMessages().get(0);

        Assert.assertEquals(MessageType.GET_BLOCK_MESSAGE, msg.getMessageType());

        final GetBlockMessage gbMessage = (GetBlockMessage) msg;

        Assert.assertArrayEquals(block.getHash(), gbMessage.getBlockHash());
    }

    @Test
    public void processStatusMessageWithKnownBestBlock() {
        final World world = new World();
        final BlockStore store = new BlockStore();
        final Blockchain blockchain = world.getBlockChain();

        final NodeBlockProcessor bp = new NodeBlockProcessor(store, blockchain);
        final SimpleMessageSender sender = new SimpleMessageSender();
        final NodeMessageHandler handler = new NodeMessageHandler(bp, null, null, null, null);

        final Block block = BlockGenerator.createChildBlock(BlockGenerator.getGenesisBlock());
        final Status status = new Status(block.getNumber(), block.getHash());
        final Message message = new StatusMessage(status);

        store.saveBlock(block);

        handler.processMessage(sender, message);

        Assert.assertNotNull(sender.getMessages());
    }

    @Test
    public void processGetBlockMessageUsingBlockInStore() {
        final Block block = BlockGenerator.getBlock(3);
        final World world = new World();
        final BlockStore store = new BlockStore();
        final Blockchain blockchain = world.getBlockChain();

        store.saveBlock(block);

        final NodeBlockProcessor bp = new NodeBlockProcessor(store, blockchain);

        final NodeMessageHandler handler = new NodeMessageHandler(bp, null, null, null, null);

        final SimpleMessageSender sender = new SimpleMessageSender();

        handler.processMessage(sender, new GetBlockMessage(block.getHash()));

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.BLOCK_MESSAGE, message.getMessageType());

        final BlockMessage bMessage = (BlockMessage) message;

        Assert.assertArrayEquals(block.getHash(), bMessage.getBlock().getHash());
    }

    @Test
    public void processGetBlockMessageUsingBlockInBlockchain() {
        final World world = new World();
        final BlockStore store = new BlockStore();
        final Blockchain blockchain = world.getBlockChain();

        List<Block> blocks = BlockGenerator.getBlockChain(blockchain.getBestBlock(), 10);

        for (Block b: blocks)
            blockchain.tryToConnect(b);

        NodeBlockProcessor bp = new NodeBlockProcessor(store, blockchain);

        NodeMessageHandler handler = new NodeMessageHandler(bp, null, null, null, null);

        SimpleMessageSender sender = new SimpleMessageSender();

        handler.processMessage(sender, new GetBlockMessage(blocks.get(4).getHash()));

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.BLOCK_MESSAGE, message.getMessageType());

        BlockMessage bmessage = (BlockMessage) message;

        Assert.assertArrayEquals(blocks.get(4).getHash(), bmessage.getBlock().getHash());
    }

    @Test
    public void processGetBlockMessageUsingEmptyStore() {
        final Block block = BlockGenerator.getBlock(3);

        final World world = new World();
        final BlockStore store = new BlockStore();
        final Blockchain blockchain = world.getBlockChain();

        final NodeBlockProcessor bp = new NodeBlockProcessor(store, blockchain);

        final NodeMessageHandler handler = new NodeMessageHandler(bp, null, null, null, null);
        handler.disablePoWValidation();

        final SimpleMessageSender sender = new SimpleMessageSender();

        handler.processMessage(sender, new GetBlockMessage(block.getHash()));

        Assert.assertTrue(sender.getMessages().isEmpty());
    }

    @Test
    public void processGetBlockHeaderMessageUsingBlockInStore() {
        final Block block = BlockGenerator.getBlock(3);

        final World world = new World();
        final BlockStore store = new BlockStore();
        final Blockchain blockchain = world.getBlockChain();

        store.saveBlock(block);

        final NodeBlockProcessor bp = new NodeBlockProcessor(store, blockchain);

        final NodeMessageHandler handler = new NodeMessageHandler(bp, null, null, null, null);

        final SimpleMessageSender sender = new SimpleMessageSender();

        handler.processMessage(sender, new GetBlockHeadersMessage(block.getHash(), 1));

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.BLOCK_HEADERS_MESSAGE, message.getMessageType());

        final BlockHeadersMessage bMessage = (BlockHeadersMessage) message;

        Assert.assertArrayEquals(block.getHash(), bMessage.getBlockHeaders().get(0).getHash());
    }

    @Test
    public void processGetBlockHeaderMessageUsingBlockInBlockchain() {
        final World world = new World();
        final BlockStore store = new BlockStore();
        final Blockchain blockchain = world.getBlockChain();

        List<Block> blocks = BlockGenerator.getBlockChain(blockchain.getBestBlock(), 10);

        for (Block b: blocks)
            blockchain.tryToConnect(b);

        NodeBlockProcessor bp = new NodeBlockProcessor(store, blockchain);

        NodeMessageHandler handler = new NodeMessageHandler(bp, null, null, null, null);

        SimpleMessageSender sender = new SimpleMessageSender();

        handler.processMessage(sender, new GetBlockHeadersMessage(blocks.get(4).getHash(), 1));

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.BLOCK_HEADERS_MESSAGE, message.getMessageType());

        BlockHeadersMessage bMessage = (BlockHeadersMessage) message;

        Assert.assertArrayEquals(blocks.get(4).getHash(), bMessage.getBlockHeaders().get(0).getHash());
    }

    @Test
    public void processNewBlockHashesMessage() {
        final World world = new World();
        final BlockStore store = new BlockStore();
        final Blockchain blockchain = world.getBlockChain();

        final List<Block> blocks = BlockGenerator.getBlockChain(blockchain.getBestBlock(), 15);
        final List<Block> bcBlocks = blocks.subList(0, 10);

        for (Block b: bcBlocks)
            blockchain.tryToConnect(b);

        final NodeBlockProcessor bp = new NodeBlockProcessor(store, blockchain);
        final NodeMessageHandler handler = new NodeMessageHandler(bp, null, null, null, null);

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
            final SimpleMessageSender sender = new SimpleMessageSender();

            handler.processMessage(sender, testCase.message);

            if (testCase.expected == null) {
                Assert.assertTrue(sender.getMessages().isEmpty());
                continue;
            }

            Assert.assertEquals(testCase.expected.size(), sender.getMessages().size());

            Assert.assertTrue(sender.getMessages().stream().allMatch(m -> m.getMessageType() == MessageType.GET_BLOCK_MESSAGE));

            List<ByteArrayWrapper> msgs = sender.getMessages().stream()
                    .map(m -> (GetBlockMessage) m)
                    .map(m -> m.getBlockHash())
                    .map(h -> new ByteArrayWrapper(h))
                    .collect(Collectors.toList());

            Set<ByteArrayWrapper> expected = testCase.expected.stream()
                    .map(b -> b.getHash())
                    .map(h -> new ByteArrayWrapper(h))
                    .collect(Collectors.toSet());

            for (ByteArrayWrapper h : msgs) {
                Assert.assertTrue(expected.contains(h));
            }

            for (ByteArrayWrapper h : expected) {
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
        TxHandler txHandler = Mockito.mock(TxHandler.class);
        BlockProcessor blockProcessor = Mockito.mock(BlockProcessor.class);
        Mockito.when(blockProcessor.hasBetterBlockToSync()).thenReturn(true);

        final NodeMessageHandler handler = new NodeMessageHandler(blockProcessor, null, null, txHandler, null);

        Message message = Mockito.mock(Message.class);
        Mockito.when(message.getMessageType()).thenReturn(MessageType.NEW_BLOCK_HASHES);

        handler.processMessage(null, message);

        verify(blockProcessor, never()).processNewBlockHashesMessage(any(), any());
    }

    @Test
    public void processGetBlockHeadersMessage() {
        final World world = new World();
        final BlockStore store = new BlockStore();
        final Blockchain blockchain = world.getBlockChain();

        final List<Block> blocks = BlockGenerator.getBlockChain(blockchain.getBestBlock(), 10);

        for (Block b: blocks) {
            ImportResult result = blockchain.tryToConnect(b);
            System.out.println(result);
        }


        final NodeBlockProcessor bp = new NodeBlockProcessor(store, blockchain);
        final NodeMessageHandler handler = new NodeMessageHandler(bp, null, null, null, null);

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
            final SimpleMessageSender sender = new SimpleMessageSender();

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
            Assert.assertArrayEquals(expected.get(i).getHash(), received.get(i).getHash());
            Assert.assertArrayEquals(expected.get(i).getParentHash(), received.get(i).getParentHash());
        }
    }

    @Test
    public void processGetBlockHeaderMessageUsingEmptyStore() {
        final Block block = BlockGenerator.getBlock(3);

        final World world = new World();
        final BlockStore store = new BlockStore();
        final Blockchain blockchain = world.getBlockChain();

        final NodeBlockProcessor bp = new NodeBlockProcessor(store, blockchain);

        final NodeMessageHandler handler = new NodeMessageHandler(bp, null, null, null, null);
        handler.disablePoWValidation();

        final SimpleMessageSender sender = new SimpleMessageSender();

        handler.processMessage(sender, new GetBlockHeadersMessage(block.getHash(), 1));

        Assert.assertTrue(sender.getMessages().isEmpty());
    }

    @Test
    public void processTransactionsMessage() {
        PeerScoringManager scoring = createPeerScoringManager();
        final SimpleChannelManager channelManager = new SimpleChannelManager();
        TxHandler txmock = Mockito.mock(TxHandler.class);
        BlockProcessor blockProcessor = Mockito.mock(BlockProcessor.class);
        Mockito.when(blockProcessor.hasBetterBlockToSync()).thenReturn(false);

        final NodeMessageHandler handler = new NodeMessageHandler(blockProcessor, channelManager, null, txmock, scoring);

        final SimpleMessageSender sender = new SimpleMessageSender();
        final SimpleMessageSender sender2 = new SimpleMessageSender();

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
        Assert.assertTrue(channelManager.getLastSkip().contains(sender.getNodeID()));

        handler.processMessage(sender2, message);

        Assert.assertNotNull(channelManager.getLastSkip());
        Assert.assertEquals(2, channelManager.getLastSkip().size());
        Assert.assertTrue(channelManager.getLastSkip().contains(sender.getNodeID()));
        Assert.assertTrue(channelManager.getLastSkip().contains(sender2.getNodeID()));

        Assert.assertFalse(scoring.isEmpty());

        PeerScoring pscoring = scoring.getPeerScoring(sender.getNodeID());

        Assert.assertNotNull(pscoring);
        Assert.assertFalse(pscoring.isEmpty());
        Assert.assertEquals(1, pscoring.getTotalEventCounter());
        Assert.assertEquals(1, pscoring.getEventCounter(EventType.VALID_TRANSACTION));

        pscoring = scoring.getPeerScoring(sender2.getNodeID());

        Assert.assertNotNull(pscoring);
        Assert.assertFalse(pscoring.isEmpty());
        Assert.assertEquals(1, pscoring.getTotalEventCounter());
        Assert.assertEquals(1, pscoring.getEventCounter(EventType.VALID_TRANSACTION));
    }

    @Test
    public void processRejectedTransactionsMessage() {
        PeerScoringManager scoring = createPeerScoringManager();
        final SimpleChannelManager channelManager = new SimpleChannelManager();
        TxHandler txmock = Mockito.mock(TxHandler.class);
        BlockProcessor blockProcessor = Mockito.mock(BlockProcessor.class);
        Mockito.when(blockProcessor.hasBetterBlockToSync()).thenReturn(false);

        final NodeMessageHandler handler = new NodeMessageHandler(blockProcessor, channelManager, null, txmock, scoring);

        final SimpleMessageSender sender = new SimpleMessageSender();

        final List<Transaction> txs = TransactionUtils.getTransactions(0);
        Mockito.when(txmock.retrieveValidTxs(any(List.class))).thenReturn(txs);
        final TransactionsMessage message = new TransactionsMessage(txs);

        handler.processMessage(sender, message);

        Assert.assertNotNull(channelManager.getTransactions());
        Assert.assertEquals(0, channelManager.getTransactions().size());

        Assert.assertFalse(scoring.isEmpty());

        PeerScoring pscoring = scoring.getPeerScoring(sender.getNodeID());

        Assert.assertNotNull(pscoring);
        Assert.assertFalse(pscoring.isEmpty());
        Assert.assertEquals(1, pscoring.getTotalEventCounter());
        Assert.assertEquals(1, pscoring.getEventCounter(EventType.INVALID_TRANSACTION));
    }

    @Test
    public void processTransactionsMessageDoesNothingBecauseNodeIsSyncing() {
        ChannelManager channelManager = Mockito.mock(ChannelManager.class);
        TxHandler txHandler = Mockito.mock(TxHandler.class);
        BlockProcessor blockProcessor = Mockito.mock(BlockProcessor.class);
        Mockito.when(blockProcessor.hasBetterBlockToSync()).thenReturn(true);

        final NodeMessageHandler handler = new NodeMessageHandler(blockProcessor, channelManager, null, txHandler, null);

        Message message = Mockito.mock(Message.class);
        Mockito.when(message.getMessageType()).thenReturn(MessageType.TRANSACTIONS);

        handler.processMessage(null, message);

        verify(channelManager, never()).broadcastTransaction(any(), any());
    }

    @Test
    public void processTransactionsMessageUsingPendingState() {
        final SimplePendingState pendingState = new SimplePendingState();
        final SimpleChannelManager channelManager = new SimpleChannelManager();
        TxHandler txmock = Mockito.mock(TxHandler.class);
        BlockProcessor blockProcessor = Mockito.mock(BlockProcessor.class);
        Mockito.when(blockProcessor.hasBetterBlockToSync()).thenReturn(false);

        final NodeMessageHandler handler = new NodeMessageHandler(blockProcessor, channelManager, pendingState, txmock, null);

        final SimpleMessageSender sender = new SimpleMessageSender();
        sender.setNodeID(new byte[] {1});
        final SimpleMessageSender sender2 = new SimpleMessageSender();
        sender2.setNodeID(new byte[] {2});

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
        Assert.assertTrue(channelManager.getLastSkip().contains(sender.getNodeID()));

        channelManager.setLastSkip(null);
        handler.processMessage(sender2, message);

        Assert.assertNull(channelManager.getLastSkip());
    }

    private static PeerScoringManager createPeerScoringManager() {
        return new PeerScoringManager(1000, new PunishmentParameters(600000, 10, 10000000), new PunishmentParameters(600000, 10, 10000000));
    }
}

