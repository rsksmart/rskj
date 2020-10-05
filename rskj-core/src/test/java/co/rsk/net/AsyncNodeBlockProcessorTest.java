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
import co.rsk.config.TestSystemProperties;
import co.rsk.crypto.Keccak256;
import co.rsk.net.messages.*;
import co.rsk.net.simples.SimplePeer;
import co.rsk.net.sync.SyncConfiguration;
import co.rsk.net.utils.AsyncNodeBlockProcessorListener;
import co.rsk.test.builders.BlockChainBuilder;
import org.ethereum.core.Block;
import org.ethereum.core.BlockIdentifier;
import org.ethereum.core.Blockchain;
import org.ethereum.crypto.HashUtil;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

import static org.mockito.Mockito.*;

public class AsyncNodeBlockProcessorTest {

    private static final long WAIT_TIME = Long.MAX_VALUE;
    
    @Test
    public void processBlockSavingInStore() {
        final NetBlockStore store = new NetBlockStore();
        final Peer sender = new SimplePeer();

        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        final BlockGenerator blockGenerator = new BlockGenerator();
        final Block parent = blockGenerator.createChildBlock(blockGenerator.getGenesisBlock());
        final Block orphan = blockGenerator.createChildBlock(parent);

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        processor.processBlock(sender, orphan);

        Assert.assertEquals(1, processor.getNodeInformation().getNodesByBlock(orphan.getHash().getBytes()).size());

        Assert.assertTrue(store.hasBlock(orphan));
        Assert.assertEquals(1, store.size());
    }

    @Test
    public void processBlockWithTooMuchHeight() {
        final NetBlockStore store = new NetBlockStore();
        final Peer sender = new SimplePeer();

        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        final Block orphan = new BlockGenerator().createBlock(1000, 0);

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        processor.processBlock(sender, orphan);

        Assert.assertNotEquals(1, processor.getNodeInformation().getNodesByBlock(orphan.getHash().getBytes()).size());
        Assert.assertFalse(store.hasBlock(orphan));
        Assert.assertEquals(0, store.size());
    }

    @Test
    public void advancedBlock() {
        final NetBlockStore store = new NetBlockStore();

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;

        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        final long advancedBlockNumber = syncConfiguration.getChunkSize() * syncConfiguration.getMaxSkeletonChunks() + blockchain.getBestBlock().getNumber() + 1;

        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        Assert.assertTrue(processor.isAdvancedBlock(advancedBlockNumber));
        Assert.assertFalse(processor.isAdvancedBlock(advancedBlockNumber - 1));
    }

    @Test
    public void canBeIgnoredForUncles() {
        final NetBlockStore store = new NetBlockStore();

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;

        final Blockchain blockchain = new BlockChainBuilder().ofSize(15);
        final TestSystemProperties config = new TestSystemProperties();
        final int uncleGenerationLimit = config.getNetworkConstants().getUncleGenerationLimit();
        final long blockNumberThatCanBeIgnored = blockchain.getBestBlock().getNumber() - 1 - uncleGenerationLimit;

        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        Assert.assertTrue(processor.canBeIgnoredForUnclesRewards(blockNumberThatCanBeIgnored));
        Assert.assertFalse(processor.canBeIgnoredForUnclesRewards(blockNumberThatCanBeIgnored + 1));
    }

    @Test
    public void processBlockAddingToBlockchain() throws InterruptedException {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(10);

        Assert.assertEquals(10, blockchain.getBestBlock().getNumber());

        final NetBlockStore store = new NetBlockStore();
        final Block genesis = blockchain.getBlockByNumber(0);
        store.saveBlock(genesis);

        final Block block = new BlockGenerator().createChildBlock(blockchain.getBlockByNumber(10));

        Assert.assertEquals(11, block.getNumber());
        Assert.assertArrayEquals(blockchain.getBestBlockHash(), block.getParentHash().getBytes());

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration);
        final AsyncNodeBlockProcessorListener listener = new AsyncNodeBlockProcessorListener();
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, listener);
        processor.start();

        BlockProcessResult blockProcessResult = processor.processBlock(null, block);
        if (blockProcessResult.isScheduledForProcessing()) {
            listener.waitForBlock(block.getHash());
        }

        Assert.assertFalse(store.hasBlock(block));
        Assert.assertEquals(11, blockchain.getBestBlock().getNumber());
        Assert.assertArrayEquals(block.getHash().getBytes(), blockchain.getBestBlockHash());
        Assert.assertEquals(1, store.size());

        processor.stop(WAIT_TIME);
    }

    @Test
    public void processTenBlocksAddingToBlockchain() throws InterruptedException {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        final NetBlockStore store = new NetBlockStore();
        final Block genesis = blockchain.getBestBlock();

        final List<Block> blocks = new BlockGenerator().getBlockChain(genesis, 10);

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration);
        final AsyncNodeBlockProcessorListener listener = new AsyncNodeBlockProcessorListener();
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, listener);
        processor.start();

        BlockProcessResult blockProcessResult = processor.processBlock(null, genesis);
        if (blockProcessResult.isScheduledForProcessing()) {
            listener.waitForBlock(genesis.getHash());
        }

        Assert.assertEquals(0, store.size());

        Block blockToWait = null;
        for (Block b : blocks) {
            BlockProcessResult result = processor.processBlock(null, b);
            if (result.isScheduledForProcessing()) {
                blockToWait = b;
            }
        }

        if (blockToWait != null) {
            listener.waitForBlock(blockToWait.getHash());
        }

        Assert.assertEquals(10, blockchain.getBestBlock().getNumber());
        Assert.assertEquals(0, store.size());

        processor.stop(WAIT_TIME);
    }

    @Test
    public void processTwoBlockListsAddingToBlockchain() throws InterruptedException {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        final NetBlockStore store = new NetBlockStore();
        final Block genesis = blockchain.getBestBlock();
        final BlockGenerator blockGenerator = new BlockGenerator();
        final List<Block> blocks = blockGenerator.getBlockChain(genesis, 10);
        final List<Block> blocks2 = blockGenerator.getBlockChain(genesis, 20);

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration);
        final AsyncNodeBlockProcessorListener listener = new AsyncNodeBlockProcessorListener();
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, listener);
        processor.start();

        BlockProcessResult blockProcessResult = processor.processBlock(null, genesis);
        if (blockProcessResult.isScheduledForProcessing()) {
            listener.waitForBlock(genesis.getHash());
        }

        Assert.assertEquals(0, store.size());

        Block blockToWait = null;
        for (Block b : blocks) {
            blockProcessResult = processor.processBlock(null, b);
            if (blockProcessResult.isScheduledForProcessing()) {
                blockToWait = b;
            }
        }
        for (Block b : blocks2) {
            blockProcessResult = processor.processBlock(null, b);
            if (blockProcessResult.isScheduledForProcessing()) {
                blockToWait = b;
            }
        }

        if (blockToWait != null) {
            listener.waitForBlock(blockToWait.getHash());
        }

        Assert.assertEquals(20, blockchain.getBestBlock().getNumber());
        Assert.assertEquals(0, store.size());

        processor.stop(WAIT_TIME);
    }

    @Test
    public void processTwoBlockListsAddingToBlockchainWithFork() throws InterruptedException {
        final NetBlockStore store = new NetBlockStore();
        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        final Block genesis = blockchain.getBestBlock();

        final BlockGenerator blockGenerator = new BlockGenerator();
        final List<Block> blocks = blockGenerator.getBlockChain(genesis, 10);
        final List<Block> blocks2 = blockGenerator.getBlockChain(blocks.get(4), 20);

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration);
        final AsyncNodeBlockProcessorListener listener = new AsyncNodeBlockProcessorListener();
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, listener);
        processor.start();

        BlockProcessResult blockProcessResult = processor.processBlock(null, genesis);
        if (blockProcessResult.isScheduledForProcessing()) {
            listener.waitForBlock(genesis.getHash());
        }

        Assert.assertEquals(0, store.size());

        Block blockToWait = null;
        for (Block b : blocks) {
            blockProcessResult = processor.processBlock(null, b);
            if (blockProcessResult.isScheduledForProcessing()) {
                blockToWait = b;
            }
        }
        for (Block b : blocks2) {
            blockProcessResult = processor.processBlock(null, b);
            if (blockProcessResult.isScheduledForProcessing()) {
                blockToWait = b;
            }
        }

        if (blockToWait != null) {
            listener.waitForBlock(blockToWait.getHash());
        }

        Assert.assertEquals(25, blockchain.getBestBlock().getNumber());
        Assert.assertEquals(0, store.size());

        processor.stop(WAIT_TIME);
    }

    @Test
    public void noSyncingWithEmptyBlockchain() {
        final NetBlockStore store = new NetBlockStore();
        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        Assert.assertFalse(processor.hasBetterBlockToSync());
    }

    @Test
    public void processTenBlocksGenesisAtLastAddingToBlockchain() throws InterruptedException {
        final NetBlockStore store = new NetBlockStore();
        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        final Block genesis = blockchain.getBestBlock();
        final List<Block> blocks = new BlockGenerator().getBlockChain(genesis, 10);

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration);
        final AsyncNodeBlockProcessorListener listener = new AsyncNodeBlockProcessorListener();
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, listener);
        processor.start();

        BlockProcessResult blockProcessResult;
        Block blockToWait = null;
        for (Block b : blocks) {
            blockProcessResult = processor.processBlock(null, b);
            if (blockProcessResult.isScheduledForProcessing()) {
                blockToWait = b;
            }
        }

        blockProcessResult = processor.processBlock(null, genesis);
        if (blockProcessResult.isScheduledForProcessing()) {
            blockToWait = genesis;
        }

        if (blockToWait != null) {
            listener.waitForBlock(blockToWait.getHash());
        }

        Assert.assertEquals(10, blockchain.getBestBlock().getNumber());
        Assert.assertEquals(0, store.size());
        processor.stop(WAIT_TIME);
    }

    @Test
    public void processTenBlocksInverseOrderAddingToBlockchain() throws InterruptedException {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        final NetBlockStore store = new NetBlockStore();
        final Block genesis = blockchain.getBestBlock();
        final List<Block> blocks = new BlockGenerator().getBlockChain(genesis, 10);

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration);
        final AsyncNodeBlockProcessorListener listener = new AsyncNodeBlockProcessorListener();
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, listener);
        processor.start();

        BlockProcessResult blockProcessResult;
        Block blockToWait = null;
        for (int k = 0; k < 10; k++) {
            Block b = blocks.get(9 - k);
            blockProcessResult = processor.processBlock(null, b);
            if (blockProcessResult.isScheduledForProcessing()) {
                blockToWait = b;
            }
        }

        blockProcessResult = processor.processBlock(null, genesis);
        if (blockProcessResult.isScheduledForProcessing()) {
            blockToWait = genesis;
        }

        if (blockToWait != null) {
            listener.waitForBlock(blockToWait.getHash());
        }

        Assert.assertEquals(10, blockchain.getBestBlock().getNumber());
        Assert.assertEquals(0, store.size());

        processor.stop(WAIT_TIME);
    }

    @Test
    public void processTenBlocksWithHoleAddingToBlockchain() throws InterruptedException {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        final NetBlockStore store = new NetBlockStore();
        final Block genesis = blockchain.getBestBlock();
        final List<Block> blocks = new BlockGenerator().getBlockChain(genesis, 10);

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration);
        final AsyncNodeBlockProcessorListener listener = new AsyncNodeBlockProcessorListener();
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, listener);
        processor.start();

        BlockProcessResult blockProcessResult;
        Block blockToWait = null;
        for (int k = 0; k < 10; k++) {
            if (k != 5) {
                Block b = blocks.get(9 - k);
                blockProcessResult = processor.processBlock(null, b);
                if (blockProcessResult.isScheduledForProcessing()) {
                    blockToWait = b;
                }
            }
        }

        blockProcessResult = processor.processBlock(null, genesis);
        if (blockProcessResult.isScheduledForProcessing()) {
            blockToWait = genesis;
        }
        blockProcessResult = processor.processBlock(null, blocks.get(4));
        if (blockProcessResult.isScheduledForProcessing()) {
            blockToWait = blocks.get(4);
        }

        if (blockToWait != null) {
            listener.waitForBlock(blockToWait.getHash());
        }

        Assert.assertEquals(10, blockchain.getBestBlock().getNumber());
        Assert.assertEquals(0, store.size());

        processor.stop(WAIT_TIME);
    }

    @Test
    public void processBlockAddingToBlockchainUsingItsParent() throws InterruptedException {
        final NetBlockStore store = new NetBlockStore();
        final BlockGenerator blockGenerator = new BlockGenerator();

        final Block genesis = blockGenerator.getGenesisBlock();
        store.saveBlock(genesis);

        final Blockchain blockchain = new BlockChainBuilder().ofSize(10);
        final Block parent = blockGenerator.createChildBlock(blockchain.getBlockByNumber(10));
        final Block block = blockGenerator.createChildBlock(parent);

        Assert.assertEquals(11, parent.getNumber());
        Assert.assertEquals(12, block.getNumber());
        Assert.assertArrayEquals(blockchain.getBestBlockHash(), parent.getParentHash().getBytes());

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration);
        final AsyncNodeBlockProcessorListener listener = new AsyncNodeBlockProcessorListener();
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, listener);
        processor.start();

        BlockProcessResult blockProcessResult = processor.processBlock(null, block);
        if (blockProcessResult.isScheduledForProcessing()) {
            listener.waitForBlock(block.getHash());
        }

        Assert.assertTrue(store.hasBlock(block));
        Assert.assertNull(blockchain.getBlockByHash(block.getHash().getBytes()));

        blockProcessResult = processor.processBlock(null, parent);
        if (blockProcessResult.isScheduledForProcessing()) {
            listener.waitForBlock(parent.getHash());
        }

        Assert.assertFalse(store.hasBlock(block));
        Assert.assertFalse(store.hasBlock(parent));

        Assert.assertEquals(12, blockchain.getBestBlock().getNumber());
        Assert.assertArrayEquals(block.getHash().getBytes(), blockchain.getBestBlockHash());
        Assert.assertEquals(1, store.size());

        processor.stop(WAIT_TIME);
    }

    @Test
    public void processBlockRetrievingParentUsingSender() {
        final NetBlockStore store = new NetBlockStore();
        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);
        final SimplePeer sender = new SimplePeer();

        final BlockGenerator blockGenerator = new BlockGenerator();
        final Block genesis = blockGenerator.getGenesisBlock();
        final Block parent = blockGenerator.createChildBlock(genesis);
        final Block block = blockGenerator.createChildBlock(parent);

        processor.processBlock(sender, block);

        Assert.assertEquals(1, processor.getNodeInformation().getNodesByBlock(block.getHash().getBytes()).size());
        Assert.assertTrue(store.hasBlock(block));
        Assert.assertEquals(1, sender.getMessages().size());
        Assert.assertEquals(1, store.size());

        final Message message = sender.getMessages().get(0);

        Assert.assertNotNull(message);
        Assert.assertEquals(MessageType.GET_BLOCK_MESSAGE, message.getMessageType());

        final GetBlockMessage gbMessage = (GetBlockMessage) message;

        Assert.assertArrayEquals(block.getParentHash().getBytes(), gbMessage.getBlockHash());
    }

    @Test
    public void processGetBlockHeaderMessageUsingBlockInStore() {
        final Block block = new BlockGenerator().getBlock(3);

        final NetBlockStore store = new NetBlockStore();
        store.saveBlock(block);

        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        final SimplePeer sender = new SimplePeer();

        processor.processBlockHeadersRequest(sender, 1, block.getHash().getBytes(), 1);

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.BLOCK_HEADERS_RESPONSE_MESSAGE, message.getMessageType());

        final BlockHeadersResponseMessage bMessage = (BlockHeadersResponseMessage) message;

        Assert.assertEquals(block.getHeader().getHash(), bMessage.getBlockHeaders().get(0).getHash());
    }

    @Test
    public void processGetBlockHeaderMessageUsingEmptyStore() {
        final Block block = new BlockGenerator().getBlock(3);
        final NetBlockStore store = new NetBlockStore();
        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        final SimplePeer sender = new SimplePeer();

        Assert.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).isEmpty());

        processor.processBlockHeadersRequest(sender, 1, block.getHash().getBytes(), 1);

        Assert.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).isEmpty());

        Assert.assertTrue(sender.getMessages().isEmpty());
    }

    @Test
    public void processGetBlockHeaderMessageUsingBlockInBlockchain() {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(10);
        final Block block = blockchain.getBlockByNumber(5);
        final NetBlockStore store = new NetBlockStore();

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        final SimplePeer sender = new SimplePeer();

        processor.processBlockHeadersRequest(sender, 1, block.getHash().getBytes(), 1);

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.BLOCK_HEADERS_RESPONSE_MESSAGE, message.getMessageType());

        final BlockHeadersResponseMessage bMessage = (BlockHeadersResponseMessage) message;

        Assert.assertEquals(block.getHeader().getHash(), bMessage.getBlockHeaders().get(0).getHash());
    }

    @Test
    public void processGetBlockMessageUsingBlockInStore() {
        final Block block = new BlockGenerator().getBlock(3);
        final Keccak256 blockHash = block.getHash();

        final NetBlockStore store = new NetBlockStore();
        store.saveBlock(block);

        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        final SimplePeer sender = new SimplePeer();

        Assert.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).isEmpty());

        processor.processGetBlock(sender, block.getHash().getBytes());

        Assert.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).contains(sender.getPeerNodeID()));

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.BLOCK_MESSAGE, message.getMessageType());

        final BlockMessage bMessage = (BlockMessage) message;

        Assert.assertEquals(block.getHash(), bMessage.getBlock().getHash());
    }

    @Test
    public void processGetBlockMessageUsingEmptyStore() {
        final Block block = new BlockGenerator().getBlock(3);
        final NetBlockStore store = new NetBlockStore();
        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        final SimplePeer sender = new SimplePeer();

        Assert.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).isEmpty());

        processor.processGetBlock(sender, block.getHash().getBytes());

        Assert.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).isEmpty());

        Assert.assertTrue(sender.getMessages().isEmpty());
    }

    @Test
    public void processGetBlockMessageUsingBlockInBlockchain() {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(10);
        final Block block = blockchain.getBlockByNumber(5);
        final Keccak256 blockHash = block.getHash();
        final NetBlockStore store = new NetBlockStore();

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        final SimplePeer sender = new SimplePeer();

        Assert.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).isEmpty());

        processor.processGetBlock(sender, block.getHash().getBytes());

        Assert.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).contains(sender.getPeerNodeID()));

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.BLOCK_MESSAGE, message.getMessageType());

        final BlockMessage bMessage = (BlockMessage) message;

        Assert.assertEquals(block.getHash(), bMessage.getBlock().getHash());
    }

    @Test
    public void processBlockRequestMessageUsingBlockInStore() {
        final Block block = new BlockGenerator().getBlock(3);
        final Keccak256 blockHash = block.getHash();

        final NetBlockStore store = new NetBlockStore();
        store.saveBlock(block);

        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        final SimplePeer sender = new SimplePeer();

        Assert.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).isEmpty());

        processor.processBlockRequest(sender, 100, block.getHash().getBytes());

        Assert.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).contains(sender.getPeerNodeID()));

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.BLOCK_RESPONSE_MESSAGE, message.getMessageType());

        final BlockResponseMessage bMessage = (BlockResponseMessage) message;

        Assert.assertEquals(100, bMessage.getId());
        Assert.assertEquals(block.getHash(), bMessage.getBlock().getHash());
    }

    @Test
    public void processBodyRequestMessageUsingBlockInBlockchain() {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(10);
        final Block block = blockchain.getBlockByNumber(3);
        final NetBlockStore store = new NetBlockStore();
        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        final SimplePeer sender = new SimplePeer();

        processor.processBodyRequest(sender, 100, block.getHash().getBytes());

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.BODY_RESPONSE_MESSAGE, message.getMessageType());

        final BodyResponseMessage bMessage = (BodyResponseMessage) message;

        Assert.assertEquals(100, bMessage.getId());
        Assert.assertEquals(block.getTransactionsList(), bMessage.getTransactions());
        Assert.assertEquals(block.getUncleList(), bMessage.getUncles());
    }

    @Test
    public void processBlockHashRequestMessageUsingEmptyStore() {
        final Block block = new BlockGenerator().getBlock(3);
        final NetBlockStore store = new NetBlockStore();
        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        final SimplePeer sender = new SimplePeer();

        Assert.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).isEmpty());

        processor.processBlockRequest(sender, 100, block.getHash().getBytes());

        Assert.assertFalse(nodeInformation.getNodesByBlock(block.getHash()).contains(sender.getPeerNodeID()));

        Assert.assertTrue(sender.getMessages().isEmpty());
    }

    @Test
    public void processBlockHashRequestMessageUsingBlockInBlockchain() {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(10);
        final Block block = blockchain.getBlockByNumber(5);
        final Keccak256 blockHash = block.getHash();
        final NetBlockStore store = new NetBlockStore();

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        final SimplePeer sender = new SimplePeer();

        Assert.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).isEmpty());

        processor.processBlockRequest(sender, 100, block.getHash().getBytes());

        Assert.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).contains(sender.getPeerNodeID()));

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.BLOCK_RESPONSE_MESSAGE, message.getMessageType());

        final BlockResponseMessage bMessage = (BlockResponseMessage) message;

        Assert.assertEquals(100, bMessage.getId());
        Assert.assertEquals(block.getHash(), bMessage.getBlock().getHash());
    }

    @Test
    public void processBlockHashRequestMessageUsingOutOfBoundsHeight() {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(10);
        final NetBlockStore store = new NetBlockStore();
        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        final SimplePeer sender = new SimplePeer();

        processor.processBlockHashRequest(sender, 100, 99999);

        Assert.assertTrue(sender.getMessages().isEmpty());
    }

    @Test
    public void processBlockHeadersRequestMessageUsingBlockInBlockchain() {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(100);
        final Block block = blockchain.getBlockByNumber(60);
        final NetBlockStore store = new NetBlockStore();

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        final SimplePeer sender = new SimplePeer();

        processor.processBlockHeadersRequest(sender, 100, block.getHash().getBytes(), 20);

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.BLOCK_HEADERS_RESPONSE_MESSAGE, message.getMessageType());

        final BlockHeadersResponseMessage response = (BlockHeadersResponseMessage) message;

        Assert.assertEquals(100, response.getId());
        Assert.assertNotNull(response.getBlockHeaders());
        Assert.assertEquals(20, response.getBlockHeaders().size());

        for (int k = 0; k < 20; k++) {
            Assert.assertEquals(blockchain.getBlockByNumber(60 - k).getHash(), response.getBlockHeaders().get(k).getHash());
        }
    }

    @Test
    public void processBlockHeadersRequestMessageUsingUnknownHash() {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(100);
        final NetBlockStore store = new NetBlockStore();

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        final SimplePeer sender = new SimplePeer();

        processor.processBlockHeadersRequest(sender, 100, HashUtil.randomHash(), 20);

        Assert.assertTrue(sender.getMessages().isEmpty());
    }

    @Test
    public void processSkeletonRequestWithGenesisPlusBestBlockInSkeleton() {
        int skeletonStep = 192;
        final Blockchain blockchain = new BlockChainBuilder().ofSize(skeletonStep / 2);
        final NetBlockStore store = new NetBlockStore();

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        final SimplePeer sender = new SimplePeer();

        processor.processSkeletonRequest(sender, 100, 5);

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.SKELETON_RESPONSE_MESSAGE, message.getMessageType());

        final SkeletonResponseMessage bMessage = (SkeletonResponseMessage) message;

        Assert.assertEquals(100, bMessage.getId());

        Block genesis = blockchain.getBlockByNumber(0);
        Block bestBlock = blockchain.getBestBlock();
        BlockIdentifier[] expected = {
                new BlockIdentifier(genesis.getHash().getBytes(), genesis.getNumber()),
                new BlockIdentifier(bestBlock.getHash().getBytes(), bestBlock.getNumber()),
        };
        assertBlockIdentifiers(expected, bMessage.getBlockIdentifiers());
    }

    @Test
    public void processSkeletonRequestWithThreeResults() {
        final int skeletonStep = 192;
        final Blockchain blockchain = new BlockChainBuilder().ofSize(300);
        final NetBlockStore store = new NetBlockStore();

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        final SimplePeer sender = new SimplePeer();

        processor.processSkeletonRequest(sender, 100, 5);

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.SKELETON_RESPONSE_MESSAGE, message.getMessageType());

        final SkeletonResponseMessage bMessage = (SkeletonResponseMessage) message;

        Assert.assertEquals(100, bMessage.getId());

        final Block b1 = blockchain.getBlockByNumber(0);
        final Block b2 = blockchain.getBlockByNumber(skeletonStep);
        final Block b3 = blockchain.getBestBlock();
        final BlockIdentifier[] expected = {
                new BlockIdentifier(b1.getHash().getBytes(), b1.getNumber()),
                new BlockIdentifier(b2.getHash().getBytes(), b2.getNumber()),
                new BlockIdentifier(b3.getHash().getBytes(), b3.getNumber()),
        };
        assertBlockIdentifiers(expected, bMessage.getBlockIdentifiers());
    }

    @Test
    public void processSkeletonRequestNotIncludingGenesis() {
        final int skeletonStep = 192;
        final Blockchain blockchain = new BlockChainBuilder().ofSize(400);
        final NetBlockStore store = new NetBlockStore();

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        final SimplePeer sender = new SimplePeer();

        processor.processSkeletonRequest(sender, 100, skeletonStep + 5);

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.SKELETON_RESPONSE_MESSAGE, message.getMessageType());

        final SkeletonResponseMessage bMessage = (SkeletonResponseMessage) message;

        Assert.assertEquals(100, bMessage.getId());

        final Block b1 = blockchain.getBlockByNumber(skeletonStep);
        final Block b2 = blockchain.getBlockByNumber(2 * skeletonStep);
        final Block b3 = blockchain.getBestBlock();
        final BlockIdentifier[] expected = {
                new BlockIdentifier(b1.getHash().getBytes(), b1.getNumber()),
                new BlockIdentifier(b2.getHash().getBytes(), b2.getNumber()),
                new BlockIdentifier(b3.getHash().getBytes(), b3.getNumber()),
        };
        assertBlockIdentifiers(expected, bMessage.getBlockIdentifiers());
    }

    private static void assertBlockIdentifiers(BlockIdentifier[] expected, List<BlockIdentifier> actual) {
        Assert.assertEquals(expected.length, actual.size());

        for (int i = 0; i < expected.length; i++) {
            Assert.assertEquals(expected[i].getNumber(), actual.get(i).getNumber());
            Assert.assertArrayEquals(expected[i].getHash(), actual.get(i).getHash());
        }
    }

    @Test
    public void failIfProcessBlockHeadersRequestCountHigher()  {
        final Peer sender = mock(Peer.class);

        final Block block = new BlockGenerator().getBlock(3);

        final NetBlockStore store = new NetBlockStore();
        store.saveBlock(block);

        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        final int size = syncConfiguration.getChunkSize() + 1;
        processor.processBlockHeadersRequest(sender, 1, block.getHash().getBytes(), size);

        verify(sender, never()).sendMessage(any());
    }
}
