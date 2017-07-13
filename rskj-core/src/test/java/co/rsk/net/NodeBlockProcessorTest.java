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
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.net.messages.*;
import co.rsk.net.simples.SimpleNodeSender;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.net.simples.SimpleMessageSender;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.ImportResult;
import org.ethereum.db.ByteArrayWrapper;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Created by ajlopez on 5/11/2016.
 */
public class NodeBlockProcessorTest {
    @Test
    public void processBlockSavingInStore() throws UnknownHostException {
        final BlockStore store = new BlockStore();
        final MessageSender sender = new SimpleMessageSender();

        final Blockchain blockchain = createBlockchain(0);
        final Block parent = BlockGenerator.createChildBlock(BlockGenerator.getGenesisBlock());
        final Block orphan = BlockGenerator.createChildBlock(parent);

        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain);

        processor.processBlock(sender, orphan);
        Assert.assertTrue(processor.getNodeInformation().getNodesByBlock(orphan.getHash()).size() == 1);

        Assert.assertTrue(store.hasBlock(orphan));
        Assert.assertEquals(1, store.size());
    }

    @Test
    public void processBlockWithTooMuchHeight() throws UnknownHostException {
        final BlockStore store = new BlockStore();
        final MessageSender sender = new SimpleMessageSender();

        final Blockchain blockchain = createBlockchain(0);
        final Block orphan = BlockGenerator.createBlock(1000, 0);

        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain);

        processor.processBlock(sender, orphan);

        Assert.assertFalse(processor.getNodeInformation().getNodesByBlock(orphan.getHash()).size() == 1);
        Assert.assertFalse(store.hasBlock(orphan));
        Assert.assertEquals(0, store.size());
    }

    @Test
    public void processBlockWithTooMuchHeightAfterFilterIsRemoved() throws UnknownHostException {
        final BlockStore store = new BlockStore();
        final MessageSender sender = new SimpleMessageSender();

        final Blockchain blockchain = createBlockchain(0);
        final Block block = BlockGenerator.createBlock(1000, 0);

        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain);
        processor.acceptAnyBlock();

        processor.processBlock(sender, block);

        Assert.assertTrue(processor.getNodeInformation().getNodesByBlock(block.getHash()).size() == 1);
        Assert.assertTrue(store.hasBlock(block));
        Assert.assertEquals(1, store.size());
    }

    @Test
    public void processBlockAddingToBlockchain() {
        Blockchain blockchain = createBlockchain(10);

        Assert.assertEquals(10, blockchain.getBestBlock().getNumber());

        BlockStore store = new BlockStore();
        Block genesis = blockchain.getBlockByNumber(0);
        store.saveBlock(genesis);
        Block block = BlockGenerator.createChildBlock(blockchain.getBlockByNumber(10));

        Assert.assertEquals(11, block.getNumber());
        Assert.assertArrayEquals(blockchain.getBestBlockHash(), block.getParentHash());

        NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain);

        processor.processBlock(null, block);

        Assert.assertFalse(store.hasBlock(block));
        Assert.assertEquals(11, blockchain.getBestBlock().getNumber());
        Assert.assertArrayEquals(block.getHash(), blockchain.getBestBlockHash());
        Assert.assertEquals(1, store.size());
    }

    @Test
    public void processTenBlocksAddingToBlockchain() {
        Blockchain blockchain = createBlockchain();
        BlockStore store = new BlockStore();
        Block genesis = blockchain.getBestBlock();

        List<Block> blocks = BlockGenerator.getBlockChain(genesis, 10);

        NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain);

        processor.processBlock(null, genesis);
        Assert.assertEquals(0, store.size());

        for (Block b : blocks)
            processor.processBlock(null, b);

        Assert.assertEquals(10, blockchain.getBestBlock().getNumber());
        Assert.assertEquals(0, store.size());
    }

    @Test
    public void processTwoBlockListsAddingToBlockchain() {
        Blockchain blockchain = createBlockchain();
        BlockStore store = new BlockStore();
        Block genesis = blockchain.getBestBlock();
        List<Block> blocks = BlockGenerator.getBlockChain(genesis, 10);
        List<Block> blocks2 = BlockGenerator.getBlockChain(genesis, 20);

        NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain);

        processor.processBlock(null, genesis);
        Assert.assertEquals(0, store.size());

        for (Block b : blocks)
            processor.processBlock(null, b);
        for (Block b : blocks2)
            processor.processBlock(null, b);

        Assert.assertEquals(20, blockchain.getBestBlock().getNumber());
        Assert.assertEquals(0, store.size());
    }

    @Test
    public void processTwoBlockListsAddingToBlockchainWithFork() {
        BlockStore store = new BlockStore();
        Blockchain blockchain = createBlockchain();
        Block genesis = blockchain.getBestBlock();

        List<Block> blocks = BlockGenerator.getBlockChain(genesis, 10);
        List<Block> blocks2 = BlockGenerator.getBlockChain(blocks.get(4), 20);

        NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain);

        processor.processBlock(null, genesis);
        Assert.assertEquals(0, store.size());

        for (Block b : blocks)
            processor.processBlock(null, b);
        for (Block b : blocks2)
            processor.processBlock(null, b);

        Assert.assertEquals(25, blockchain.getBestBlock().getNumber());
        Assert.assertEquals(0, store.size());
    }

    @Test
    public void noSyncingWithEmptyBlockchain() {
        BlockStore store = new BlockStore();
        Blockchain blockchain = createBlockchain();

        NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain);

        Assert.assertFalse(processor.isSyncingBlocks());
    }

    @Test
    public void noSyncingWithEmptyBlockchainAndLowBestBlock() {
        BlockStore store = new BlockStore();
        Block block = BlockGenerator.createBlock(10, 0);
        Blockchain blockchain = createBlockchain();

        NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain);

        Assert.assertFalse(processor.isSyncingBlocks());

        Status status = new Status(block.getNumber(), block.getHash());
        processor.processStatus(new SimpleNodeSender(null, null), status);

        Assert.assertFalse(processor.isSyncingBlocks());
    }

    @Test
    public void syncingWithEmptyBlockchainAndHighBestBlock() {
        BlockStore store = new BlockStore();
        Block block = BlockGenerator.createBlock(30, 0);
        Blockchain blockchain = createBlockchain();

        NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain);

        Assert.assertFalse(processor.isSyncingBlocks());

        Status status = new Status(block.getNumber(), block.getHash());
        processor.processStatus(new SimpleNodeSender(null, null), status);

        Assert.assertTrue(processor.isSyncingBlocks());
    }

    @Test
    public void syncingThenNoSyncing() {
        BlockStore store = new BlockStore();
        Block block = BlockGenerator.createBlock(30, 0);
        Blockchain blockchain = createBlockchain();

        NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain);

        Assert.assertFalse(processor.isSyncingBlocks());

        Status status = new Status(block.getNumber(), block.getHash());
        processor.processStatus(new SimpleNodeSender(null, null), status);

        Assert.assertTrue(processor.hasBetterBlockToSync());
        Assert.assertTrue(processor.isSyncingBlocks());

        blockchain.setBestBlock(block);
        blockchain.setTotalDifficulty(BigInteger.valueOf(30));

        Assert.assertFalse(processor.hasBetterBlockToSync());
        Assert.assertFalse(processor.isSyncingBlocks());

        Block block2 = BlockGenerator.createBlock(60, 0);
        Status status2 = new Status(block2.getNumber(), block2.getHash());
        processor.processStatus(new SimpleNodeSender(null, null), status2);

        Assert.assertTrue(processor.hasBetterBlockToSync());
        Assert.assertFalse(processor.isSyncingBlocks());
    }

    @Test
    public void processTenBlocksGenesisAtLastAddingToBlockchain() {
        BlockStore store = new BlockStore();
        Blockchain blockchain = createBlockchain();
        Block genesis = blockchain.getBestBlock();
        List<Block> blocks = BlockGenerator.getBlockChain(genesis, 10);

        NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain);

        for (Block b : blocks)
            processor.processBlock(null, b);

        processor.processBlock(null, genesis);

        Assert.assertEquals(10, blockchain.getBestBlock().getNumber());
        Assert.assertEquals(0, store.size());
    }

    @Test
    public void processTenBlocksInverseOrderAddingToBlockchain() {
        Blockchain blockchain = createBlockchain();
        BlockStore store = new BlockStore();
        Block genesis = blockchain.getBestBlock();
        List<Block> blocks = BlockGenerator.getBlockChain(genesis, 10);

        NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain);

        for (int k = 0; k < 10; k++)
            processor.processBlock(null, blocks.get(9 - k));

        processor.processBlock(null, genesis);

        Assert.assertEquals(10, blockchain.getBestBlock().getNumber());
        Assert.assertEquals(0, store.size());
    }

    @Test
    public void processTenBlocksWithHoleAddingToBlockchain() {
        Blockchain blockchain = createBlockchain();
        BlockStore store = new BlockStore();
        Block genesis = blockchain.getBestBlock();
        List<Block> blocks = BlockGenerator.getBlockChain(genesis, 10);

        NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain);

        for (int k = 0; k < 10; k++)
            if (k != 5)
                processor.processBlock(null, blocks.get(9 - k));

        processor.processBlock(null, genesis);
        processor.processBlock(null, blocks.get(4));

        Assert.assertEquals(10, blockchain.getBestBlock().getNumber());
        Assert.assertEquals(0, store.size());
    }

    @Test
    public void processBlockAddingToBlockchainUsingItsParent() {
        BlockStore store = new BlockStore();
        Block genesis = BlockGenerator.getGenesisBlock();
        store.saveBlock(genesis);
        Blockchain blockchain = createBlockchain(10);
        Block parent = BlockGenerator.createChildBlock(blockchain.getBlockByNumber(10));
        Block block = BlockGenerator.createChildBlock(parent);

        Assert.assertEquals(11, parent.getNumber());
        Assert.assertEquals(12, block.getNumber());
        Assert.assertArrayEquals(blockchain.getBestBlockHash(), parent.getParentHash());

        NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain);

        processor.processBlock(null, block);

        Assert.assertTrue(store.hasBlock(block));
        Assert.assertNull(blockchain.getBlockByHash(block.getHash()));

        processor.processBlock(null, parent);

        Assert.assertFalse(store.hasBlock(block));
        Assert.assertFalse(store.hasBlock(parent));

        Assert.assertEquals(12, blockchain.getBestBlock().getNumber());
        Assert.assertArrayEquals(block.getHash(), blockchain.getBestBlockHash());
        Assert.assertEquals(1, store.size());
    }

    @Test
    public void processBlockRetrievingParentUsingSender() throws UnknownHostException {
        final BlockStore store = new BlockStore();
        final Blockchain blockchain = createBlockchain(0);

        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain);
        final SimpleMessageSender sender = new SimpleMessageSender();

        final Block genesis = BlockGenerator.getGenesisBlock();
        final Block parent = BlockGenerator.createChildBlock(genesis);
        final Block block = BlockGenerator.createChildBlock(parent);

        processor.processBlock(sender, block);

        Assert.assertTrue(processor.getNodeInformation().getNodesByBlock(block.getHash()).size() == 1);
        Assert.assertTrue(store.hasBlock(block));
        Assert.assertEquals(1, sender.getMessages().size());
        Assert.assertEquals(1, store.size());

        final Message message = sender.getMessages().get(0);

        Assert.assertNotNull(message);
        Assert.assertEquals(MessageType.GET_BLOCK_MESSAGE, message.getMessageType());

        final GetBlockMessage gbMessage = (GetBlockMessage) message;

        Assert.assertArrayEquals(block.getParentHash(), gbMessage.getBlockHash());
    }

    @Test
    public void processStatusRetrievingBestBlockUsingSender() throws UnknownHostException {
        final BlockStore store = new BlockStore();
        final Blockchain blockchain = createBlockchain(0);

        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain);
        final SimpleMessageSender sender = new SimpleMessageSender();

        final Block genesis = BlockGenerator.getGenesisBlock();
        final Block block = BlockGenerator.createChildBlock(genesis);
        final Status status = new Status(block.getNumber(), block.getHash());

        processor.processStatus(sender, status);
        Assert.assertTrue(processor.getNodeInformation().getNodesByBlock(block.getHash()).size() == 1);

        Assert.assertEquals(1, sender.getGetBlockMessages().size());

        final Message message = sender.getGetBlockMessages().get(0);

        Assert.assertNotNull(message);
        Assert.assertEquals(MessageType.GET_BLOCK_MESSAGE, message.getMessageType());

        final GetBlockMessage gbMessage = (GetBlockMessage) message;

        Assert.assertArrayEquals(block.getHash(), gbMessage.getBlockHash());
        Assert.assertEquals(0, store.size());
    }

    @Test
    public void processStatusHavingBestBlockInStore() throws UnknownHostException {
        final BlockStore store = new BlockStore();
        final Blockchain blockchain = createBlockchain(0);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain);
        final SimpleMessageSender sender = new SimpleMessageSender();

        final Block genesis = BlockGenerator.getGenesisBlock();
        final Block block = BlockGenerator.createChildBlock(genesis);

        store.saveBlock(block);
        final Status status = new Status(block.getNumber(), block.getHash());

        processor.processStatus(sender, status);
        Assert.assertTrue(processor.getNodeInformation().getNodesByBlock(block.getHash()).size() == 1);
        Assert.assertEquals(1, store.size());
    }

    @Test
    public void processStatusHavingBestBlockAsBestBlockInBlockchain() throws UnknownHostException {
        final BlockStore store = new BlockStore();
        final Blockchain blockchain = createBlockchain(2);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain);
        final BlockNodeInformation nodeInformation = processor.getNodeInformation();

        final SimpleMessageSender sender = new SimpleMessageSender();

        final Block block = blockchain.getBestBlock();
        final ByteArrayWrapper blockHash = new ByteArrayWrapper(block.getHash());

        final Status status = new Status(block.getNumber(), block.getHash());

        processor.processStatus(sender, status);
        Assert.assertTrue(processor.getNodeInformation().getNodesByBlock(block.getHash()).size() == 1);
        Assert.assertTrue(nodeInformation.getBlocksByNode(sender.getNodeID()).contains(blockHash));

        Assert.assertEquals(0, sender.getGetBlockMessages().size());
        Assert.assertEquals(0, store.size());
    }

    @Test
    public void processStatusHavingBestBlockInBlockchainStore() throws UnknownHostException {
        final BlockStore store = new BlockStore();
        final Blockchain blockchain = createBlockchain(2);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain);
        final BlockNodeInformation nodeInformation = processor.getNodeInformation();

        final SimpleMessageSender sender = new SimpleMessageSender();

        final Block block = blockchain.getBlockByNumber(1);
        final ByteArrayWrapper blockHash = new ByteArrayWrapper(block.getHash());

        store.saveBlock(block);
        final Status status = new Status(block.getNumber(), block.getHash());

        Assert.assertTrue(nodeInformation.getBlocksByNode(sender.getNodeID()).isEmpty());

        processor.processStatus(sender, status);
        Assert.assertTrue(nodeInformation.getBlocksByNode(sender.getNodeID()).contains(blockHash));

        Assert.assertEquals(0, sender.getGetBlockMessages().size());
    }

    @Test
    public void processGetBlockHeaderMessageUsingBlockInStore() throws UnknownHostException {
        final Block block = BlockGenerator.getBlock(3);

        final BlockStore store = new BlockStore();
        store.saveBlock(block);

        final Blockchain blockchain = createBlockchain(0);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain);

        final SimpleMessageSender sender = new SimpleMessageSender();

        processor.processGetBlockHeaders(sender, block.getHash());

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.BLOCK_HEADERS_MESSAGE, message.getMessageType());

        final BlockHeadersMessage bMessage = (BlockHeadersMessage) message;

        Assert.assertArrayEquals(block.getHeader().getHash(), bMessage.getBlockHeaders().get(0).getHash());
    }

    @Test
    public void processGetBlockHeaderMessageUsingEmptyStore() throws UnknownHostException {
        final Block block = BlockGenerator.getBlock(3);
        final BlockStore store = new BlockStore();
        final Blockchain blockchain = createBlockchain(0);

        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain);
        final BlockNodeInformation nodeInformation = processor.getNodeInformation();

        final SimpleMessageSender sender = new SimpleMessageSender();

        Assert.assertTrue(nodeInformation.getBlocksByNode(sender.getNodeID()).isEmpty());

        processor.processGetBlockHeaders(sender, block.getHash());

        Assert.assertTrue(nodeInformation.getBlocksByNode(sender.getNodeID()).isEmpty());

        Assert.assertTrue(sender.getMessages().isEmpty());
    }

    @Test
    public void processGetBlockHeaderMessageUsingBlockInBlockchain() throws UnknownHostException {
        final Blockchain blockchain = createBlockchain(10);
        final Block block = blockchain.getBlockByNumber(5);
        final BlockStore store = new BlockStore();

        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain);

        final SimpleMessageSender sender = new SimpleMessageSender();

        processor.processGetBlockHeaders(sender, block.getHash());

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.BLOCK_HEADERS_MESSAGE, message.getMessageType());

        final BlockHeadersMessage bMessage = (BlockHeadersMessage) message;

        Assert.assertArrayEquals(block.getHeader().getHash(), bMessage.getBlockHeaders().get(0).getHash());
    }

    @Test
    public void processGetBlockMessageUsingBlockInStore() throws UnknownHostException {
        final Block block = BlockGenerator.getBlock(3);
        final ByteArrayWrapper blockHash = new ByteArrayWrapper(block.getHash());

        final BlockStore store = new BlockStore();
        store.saveBlock(block);

        final Blockchain blockchain = createBlockchain(0);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain);
        final BlockNodeInformation nodeInformation = processor.getNodeInformation();

        final SimpleMessageSender sender = new SimpleMessageSender();

        Assert.assertTrue(nodeInformation.getBlocksByNode(sender.getNodeID()).isEmpty());

        processor.processGetBlock(sender, block.getHash());

        Assert.assertTrue(nodeInformation.getBlocksByNode(sender.getNodeID()).contains(blockHash));

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.BLOCK_MESSAGE, message.getMessageType());

        final BlockMessage bMessage = (BlockMessage) message;

        Assert.assertArrayEquals(block.getHash(), bMessage.getBlock().getHash());
    }

    @Test
    public void processGetBlockMessageUsingEmptyStore() throws UnknownHostException {
        final Block block = BlockGenerator.getBlock(3);
        final BlockStore store = new BlockStore();
        final Blockchain blockchain = createBlockchain(0);

        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain);
        final BlockNodeInformation nodeInformation = processor.getNodeInformation();

        final SimpleMessageSender sender = new SimpleMessageSender();

        Assert.assertTrue(nodeInformation.getBlocksByNode(sender.getNodeID()).isEmpty());

        processor.processGetBlock(sender, block.getHash());

        Assert.assertTrue(nodeInformation.getBlocksByNode(sender.getNodeID()).isEmpty());

        Assert.assertTrue(sender.getMessages().isEmpty());
    }

    @Test
    public void processGetBlockMessageUsingBlockInBlockchain() throws UnknownHostException {
        final Blockchain blockchain = createBlockchain(10);
        final Block block = blockchain.getBlockByNumber(5);
        final ByteArrayWrapper blockHash = new ByteArrayWrapper(block.getHash());
        final BlockStore store = new BlockStore();

        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain);
        final BlockNodeInformation nodeInformation = processor.getNodeInformation();

        final SimpleMessageSender sender = new SimpleMessageSender();

        Assert.assertTrue(nodeInformation.getBlocksByNode(sender.getNodeID()).isEmpty());

        processor.processGetBlock(sender, block.getHash());

        Assert.assertTrue(nodeInformation.getBlocksByNode(sender.getNodeID()).contains(blockHash));

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.BLOCK_MESSAGE, message.getMessageType());

        final BlockMessage bMessage = (BlockMessage) message;

        Assert.assertArrayEquals(block.getHash(), bMessage.getBlock().getHash());
    }

    private static Blockchain createBlockchain() {
        return createBlockchain(0);
    }

    private static Blockchain createBlockchain(int size) {
        BlockChainBuilder builder = new BlockChainBuilder();
        BlockChainImpl blockChain = builder.build();

        Block genesis = BlockGenerator.getGenesisBlock();
        genesis.setStateRoot(blockChain.getRepository().getRoot());
        genesis.flushRLP();

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));

        if (size > 0) {
            List<Block> blocks = BlockGenerator.getBlockChain(genesis, size);

            for (Block block: blocks)
                blockChain.tryToConnect(block);
        }

        return blockChain;
    }
}
