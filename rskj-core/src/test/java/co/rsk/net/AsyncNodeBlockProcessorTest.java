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
import co.rsk.validators.BlockValidator;
import co.rsk.validators.DummyBlockValidator;
import org.ethereum.TestUtils;
import org.ethereum.core.Block;
import org.ethereum.core.BlockIdentifier;
import org.ethereum.core.Blockchain;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AsyncNodeBlockProcessorTest {

    private static final long WAIT_TIME = 60_000L;

    @Test
    void processBlockSavingInStore() {
        final NetBlockStore store = new NetBlockStore();
        final Peer sender = new SimplePeer();

        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        final BlockGenerator blockGenerator = new BlockGenerator();
        final Block parent = blockGenerator.createChildBlock(blockGenerator.getGenesisBlock());
        final Block orphan = blockGenerator.createChildBlock(parent);

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration,
                DummyBlockValidator.VALID_RESULT_INSTANCE, DummyBlockValidator.VALID_RESULT_INSTANCE);

        processor.processBlock(sender, orphan);

        Assertions.assertEquals(1, processor.getNodeInformation().getNodesByBlock(orphan.getHash().getBytes()).size());

        Assertions.assertTrue(store.hasBlock(orphan));
        Assertions.assertEquals(1, store.size());
    }

    @Test
    void processBlockWithTooMuchHeight() {
        final NetBlockStore store = new NetBlockStore();
        final Peer sender = new SimplePeer();

        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        final Block orphan = new BlockGenerator().createBlock(1000, 0);

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration,
                DummyBlockValidator.VALID_RESULT_INSTANCE, DummyBlockValidator.VALID_RESULT_INSTANCE);

        processor.processBlock(sender, orphan);

        Assertions.assertNotEquals(1, processor.getNodeInformation().getNodesByBlock(orphan.getHash().getBytes()).size());
        Assertions.assertFalse(store.hasBlock(orphan));
        Assertions.assertEquals(0, store.size());
    }

    @Test
    void advancedBlock() {
        final NetBlockStore store = new NetBlockStore();

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;

        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        final long advancedBlockNumber = syncConfiguration.getChunkSize() * syncConfiguration.getMaxSkeletonChunks() + blockchain.getBestBlock().getNumber() + 1;

        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration,
                DummyBlockValidator.VALID_RESULT_INSTANCE, DummyBlockValidator.VALID_RESULT_INSTANCE);

        Assertions.assertTrue(processor.isAdvancedBlock(advancedBlockNumber));
        Assertions.assertFalse(processor.isAdvancedBlock(advancedBlockNumber - 1));
    }

    @Test
    void canBeIgnoredForUncles() {
        final NetBlockStore store = new NetBlockStore();

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;

        final Blockchain blockchain = new BlockChainBuilder().ofSize(15);
        final TestSystemProperties config = new TestSystemProperties();
        final int uncleGenerationLimit = config.getNetworkConstants().getUncleGenerationLimit();
        final long blockNumberThatCanBeIgnored = blockchain.getBestBlock().getNumber() - 1 - uncleGenerationLimit;

        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration,
                DummyBlockValidator.VALID_RESULT_INSTANCE, DummyBlockValidator.VALID_RESULT_INSTANCE);

        Assertions.assertTrue(processor.canBeIgnoredForUnclesRewards(blockNumberThatCanBeIgnored));
        Assertions.assertFalse(processor.canBeIgnoredForUnclesRewards(blockNumberThatCanBeIgnored + 1));
    }

    @Test
    @Timeout(WAIT_TIME)
    void processBlockAddingToBlockchain() throws InterruptedException {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(10);

        Assertions.assertEquals(10, blockchain.getBestBlock().getNumber());

        final NetBlockStore store = new NetBlockStore();
        final Block genesis = blockchain.getBlockByNumber(0);
        store.saveBlock(genesis);

        final Block block = new BlockGenerator().createChildBlock(blockchain.getBlockByNumber(10));

        Assertions.assertEquals(11, block.getNumber());
        Assertions.assertArrayEquals(blockchain.getBestBlockHash(), block.getParentHash().getBytes());

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final AsyncNodeBlockProcessorListener listener = new AsyncNodeBlockProcessorListener();
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration,
                DummyBlockValidator.VALID_RESULT_INSTANCE, DummyBlockValidator.VALID_RESULT_INSTANCE, listener);
        processor.start();

        BlockProcessResult blockProcessResult = processor.processBlock(null, block);
        Assertions.assertFalse(blockProcessResult.isInvalidBlock(), "Block #" + block.getNumber() + " is invalid");

        if (blockProcessResult.isScheduledForProcessing()) {
            listener.waitForBlock(block.getHash());
        }

        Assertions.assertFalse(store.hasBlock(block));
        Assertions.assertEquals(11, blockchain.getBestBlock().getNumber());
        Assertions.assertArrayEquals(block.getHash().getBytes(), blockchain.getBestBlockHash());
        Assertions.assertEquals(1, store.size());

        processor.stopAndWait(WAIT_TIME);
    }

    @Test
    @Timeout(WAIT_TIME)
    void processTenBlocksAddingToBlockchain() throws InterruptedException {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        final NetBlockStore store = new NetBlockStore();
        final Block genesis = blockchain.getBestBlock();

        final List<Block> blocks = new BlockGenerator().getBlockChain(genesis, 10);

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final AsyncNodeBlockProcessorListener listener = new AsyncNodeBlockProcessorListener();
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration,
                DummyBlockValidator.VALID_RESULT_INSTANCE, DummyBlockValidator.VALID_RESULT_INSTANCE, listener);
        processor.start();

        BlockProcessResult blockProcessResult = processor.processBlock(null, genesis);
        if (blockProcessResult.isScheduledForProcessing()) {
            listener.waitForBlock(genesis.getHash());
        }

        Assertions.assertEquals(0, store.size());

        Block blockToWait = null;
        for (Block b : blocks) {
            blockProcessResult = processor.processBlock(null, b);
            Assertions.assertFalse(blockProcessResult.isInvalidBlock(), "Block #" + b.getNumber() + " is invalid");

            if (blockProcessResult.isScheduledForProcessing()) {
                blockToWait = b;
            }
        }

        if (blockToWait != null) {
            listener.waitForBlock(blockToWait.getHash());
        }

        Assertions.assertEquals(10, blockchain.getBestBlock().getNumber());
        Assertions.assertEquals(0, store.size());

        processor.stopAndWait(WAIT_TIME);
    }

    @Test
    @Timeout(WAIT_TIME)
    void processFutureBlocksAddingToBlockchain() throws InterruptedException {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        final NetBlockStore store = new NetBlockStore();
        final Block genesis = blockchain.getBestBlock();

        final List<Block> blocks = new ArrayList<>();
        Block parent = genesis;
        for (int i = 0; i < 10; i++) {
            Block block = new BlockGenerator().createChildBlock(parent);
            blocks.add(block);
            parent = block;
        }

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final BlockValidator blockValidator = block -> blockchain.hasBlockInSomeBlockchain(block.getParentHash().getBytes());
        final AsyncNodeBlockProcessorListener listener = new AsyncNodeBlockProcessorListener();
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration,
                DummyBlockValidator.VALID_RESULT_INSTANCE, blockValidator, listener);
        processor.start();

        Block blockToWait = null;
        for (Block b : blocks) {
            BlockProcessResult blockProcessResult = processor.processBlock(null, b);
            Assertions.assertFalse(blockProcessResult.isInvalidBlock(), "Block #" + b.getNumber() + " is invalid");

            if (blockProcessResult.isScheduledForProcessing()) {
                blockToWait = b;
            }
        }

        if (blockToWait != null) {
            listener.waitForBlock(blockToWait.getHash());
        }

        Assertions.assertEquals(10, blockchain.getBestBlock().getNumber());
        Assertions.assertEquals(0, store.size());

        processor.stopAndWait(WAIT_TIME);
    }

    @Test
    @Timeout(WAIT_TIME)
    void processTwoBlockListsAddingToBlockchain() throws InterruptedException {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        final NetBlockStore store = new NetBlockStore();
        final Block genesis = blockchain.getBestBlock();
        final BlockGenerator blockGenerator = new BlockGenerator();
        final List<Block> blocks = blockGenerator.getBlockChain(genesis, 10);
        final List<Block> blocks2 = blockGenerator.getBlockChain(genesis, 20);

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final AsyncNodeBlockProcessorListener listener = new AsyncNodeBlockProcessorListener();
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration,
                DummyBlockValidator.VALID_RESULT_INSTANCE, DummyBlockValidator.VALID_RESULT_INSTANCE, listener);
        processor.start();

        BlockProcessResult blockProcessResult = processor.processBlock(null, genesis);
        if (blockProcessResult.isScheduledForProcessing()) {
            listener.waitForBlock(genesis.getHash());
        }

        Assertions.assertEquals(0, store.size());

        Block blockToWait = null;
        for (Block b : blocks) {
            blockProcessResult = processor.processBlock(null, b);
            Assertions.assertFalse(blockProcessResult.isInvalidBlock(), "Block #" + b.getNumber() + " is invalid");

            if (blockProcessResult.isScheduledForProcessing()) {
                blockToWait = b;
            }
        }
        for (Block b : blocks2) {
            blockProcessResult = processor.processBlock(null, b);
            Assertions.assertFalse(blockProcessResult.isInvalidBlock(), "Block #" + b.getNumber() + " is invalid");

            if (blockProcessResult.isScheduledForProcessing()) {
                blockToWait = b;
            }
        }

        if (blockToWait != null) {
            listener.waitForBlock(blockToWait.getHash());
        }

        Assertions.assertEquals(20, blockchain.getBestBlock().getNumber());
        Assertions.assertEquals(0, store.size());

        processor.stopAndWait(WAIT_TIME);
    }

    @Test
    @Timeout(WAIT_TIME)
    void processTwoBlockListsAddingToBlockchainWithFork() throws InterruptedException {
        final NetBlockStore store = new NetBlockStore();
        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        final Block genesis = blockchain.getBestBlock();

        final BlockGenerator blockGenerator = new BlockGenerator();
        final List<Block> blocks = blockGenerator.getBlockChain(genesis, 10);
        final List<Block> blocks2 = blockGenerator.getBlockChain(blocks.get(4), 20);

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final AsyncNodeBlockProcessorListener listener = new AsyncNodeBlockProcessorListener();
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration,
                DummyBlockValidator.VALID_RESULT_INSTANCE, DummyBlockValidator.VALID_RESULT_INSTANCE, listener);
        processor.start();

        BlockProcessResult blockProcessResult = processor.processBlock(null, genesis);
        if (blockProcessResult.isScheduledForProcessing()) {
            listener.waitForBlock(genesis.getHash());
        }

        Assertions.assertEquals(0, store.size());

        Block blockToWait = null;
        for (Block b : blocks) {
            blockProcessResult = processor.processBlock(null, b);
            Assertions.assertFalse(blockProcessResult.isInvalidBlock(), "Block #" + b.getNumber() + " is invalid");

            if (blockProcessResult.isScheduledForProcessing()) {
                blockToWait = b;
            }
        }
        for (Block b : blocks2) {
            blockProcessResult = processor.processBlock(null, b);
            Assertions.assertFalse(blockProcessResult.isInvalidBlock(), "Block #" + b.getNumber() + " is invalid");

            if (blockProcessResult.isScheduledForProcessing()) {
                blockToWait = b;
            }
        }

        if (blockToWait != null) {
            listener.waitForBlock(blockToWait.getHash());
        }

        Assertions.assertEquals(25, blockchain.getBestBlock().getNumber());
        Assertions.assertEquals(0, store.size());

        processor.stopAndWait(WAIT_TIME);
    }

    @Test
    void noSyncingWithEmptyBlockchain() {
        final NetBlockStore store = new NetBlockStore();
        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration,
                DummyBlockValidator.VALID_RESULT_INSTANCE, DummyBlockValidator.VALID_RESULT_INSTANCE);

        Assertions.assertFalse(processor.hasBetterBlockToSync());
    }

    @Test
    @Timeout(WAIT_TIME)
    void processTenBlocksGenesisAtLastAddingToBlockchain() throws InterruptedException {
        final NetBlockStore store = new NetBlockStore();
        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        final Block genesis = blockchain.getBestBlock();
        final List<Block> blocks = new BlockGenerator().getBlockChain(genesis, 10);

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final AsyncNodeBlockProcessorListener listener = new AsyncNodeBlockProcessorListener();
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration,
                DummyBlockValidator.VALID_RESULT_INSTANCE, DummyBlockValidator.VALID_RESULT_INSTANCE, listener);
        processor.start();

        BlockProcessResult blockProcessResult;
        Block blockToWait = null;
        for (Block b : blocks) {
            blockProcessResult = processor.processBlock(null, b);
            Assertions.assertFalse(blockProcessResult.isInvalidBlock(), "Block #" + b.getNumber() + " is invalid");

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

        Assertions.assertEquals(10, blockchain.getBestBlock().getNumber());
        Assertions.assertEquals(0, store.size());
        processor.stopAndWait(WAIT_TIME);
    }

    @Test
    @Timeout(WAIT_TIME)
    void processTenBlocksInverseOrderAddingToBlockchain() throws InterruptedException {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        final NetBlockStore store = new NetBlockStore();
        final Block genesis = blockchain.getBestBlock();
        final List<Block> blocks = new BlockGenerator().getBlockChain(genesis, 10);

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final AsyncNodeBlockProcessorListener listener = new AsyncNodeBlockProcessorListener();
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration,
                DummyBlockValidator.VALID_RESULT_INSTANCE, DummyBlockValidator.VALID_RESULT_INSTANCE, listener);
        processor.start();

        BlockProcessResult blockProcessResult;
        Block blockToWait = null;
        for (int k = 0; k < 10; k++) {
            Block b = blocks.get(9 - k);
            blockProcessResult = processor.processBlock(null, b);
            Assertions.assertFalse(blockProcessResult.isInvalidBlock(), "Block #" + b.getNumber() + " is invalid");

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

        Assertions.assertEquals(10, blockchain.getBestBlock().getNumber());
        Assertions.assertEquals(0, store.size());

        processor.stopAndWait(WAIT_TIME);
    }

    @Test
    @Timeout(WAIT_TIME)
    void processTenBlocksWithHoleAddingToBlockchain() throws InterruptedException {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        final NetBlockStore store = new NetBlockStore();
        final Block genesis = blockchain.getBestBlock();
        final List<Block> blocks = new BlockGenerator().getBlockChain(genesis, 10);

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final AsyncNodeBlockProcessorListener listener = new AsyncNodeBlockProcessorListener();
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration,
                DummyBlockValidator.VALID_RESULT_INSTANCE, DummyBlockValidator.VALID_RESULT_INSTANCE, listener);
        processor.start();

        BlockProcessResult blockProcessResult;
        Block blockToWait = null;
        for (int k = 0; k < 10; k++) {
            if (k != 5) {
                Block b = blocks.get(9 - k);
                blockProcessResult = processor.processBlock(null, b);
                Assertions.assertFalse(blockProcessResult.isInvalidBlock(), "Block #" + b.getNumber() + " is invalid");

                if (blockProcessResult.isScheduledForProcessing()) {
                    blockToWait = b;
                }
            }
        }

        blockProcessResult = processor.processBlock(null, genesis);
        if (blockProcessResult.isScheduledForProcessing()) {
            blockToWait = genesis;
        }

        Block block4 = blocks.get(4);
        blockProcessResult = processor.processBlock(null, block4);
        Assertions.assertFalse(blockProcessResult.isInvalidBlock(), "Block #" + block4.getNumber() + " is invalid");

        if (blockProcessResult.isScheduledForProcessing()) {
            blockToWait = blocks.get(4);
        }

        if (blockToWait != null) {
            listener.waitForBlock(blockToWait.getHash());
        }

        Assertions.assertEquals(10, blockchain.getBestBlock().getNumber());
        Assertions.assertEquals(0, store.size());

        processor.stopAndWait(WAIT_TIME);
    }

    @Test
    @Timeout(WAIT_TIME)
    void processBlockAddingToBlockchainUsingItsParent() throws InterruptedException {
        final NetBlockStore store = new NetBlockStore();
        final BlockGenerator blockGenerator = new BlockGenerator();

        final Block genesis = blockGenerator.getGenesisBlock();
        store.saveBlock(genesis);

        final Blockchain blockchain = new BlockChainBuilder().ofSize(10);
        final Block parent = blockGenerator.createChildBlock(blockchain.getBlockByNumber(10));
        final Block block = blockGenerator.createChildBlock(parent);

        Assertions.assertEquals(11, parent.getNumber());
        Assertions.assertEquals(12, block.getNumber());
        Assertions.assertArrayEquals(blockchain.getBestBlockHash(), parent.getParentHash().getBytes());

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final AsyncNodeBlockProcessorListener listener = new AsyncNodeBlockProcessorListener();
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration,
                DummyBlockValidator.VALID_RESULT_INSTANCE, DummyBlockValidator.VALID_RESULT_INSTANCE, listener);
        processor.start();

        BlockProcessResult blockProcessResult = processor.processBlock(null, block);
        Assertions.assertFalse(blockProcessResult.isInvalidBlock(), "Block #" + block.getNumber() + " is invalid");

        if (blockProcessResult.isScheduledForProcessing()) {
            listener.waitForBlock(block.getHash());
        }

        Assertions.assertTrue(store.hasBlock(block));
        Assertions.assertNull(blockchain.getBlockByHash(block.getHash().getBytes()));

        blockProcessResult = processor.processBlock(null, parent);
        Assertions.assertFalse(blockProcessResult.isInvalidBlock(), "Block #" + parent.getNumber() + " is invalid");

        if (blockProcessResult.isScheduledForProcessing()) {
            listener.waitForBlock(parent.getHash());
        }

        Assertions.assertFalse(store.hasBlock(block));
        Assertions.assertFalse(store.hasBlock(parent));

        Assertions.assertEquals(12, blockchain.getBestBlock().getNumber());
        Assertions.assertArrayEquals(block.getHash().getBytes(), blockchain.getBestBlockHash());
        Assertions.assertEquals(1, store.size());

        processor.stopAndWait(WAIT_TIME);
    }

    @Test
    void processBlockRetrievingParentUsingSender() {
        final NetBlockStore store = new NetBlockStore();
        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration,
                DummyBlockValidator.VALID_RESULT_INSTANCE, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final SimplePeer sender = new SimplePeer();

        final BlockGenerator blockGenerator = new BlockGenerator();
        final Block genesis = blockGenerator.getGenesisBlock();
        final Block parent = blockGenerator.createChildBlock(genesis);
        final Block block = blockGenerator.createChildBlock(parent);

        processor.processBlock(sender, block);

        Assertions.assertEquals(1, processor.getNodeInformation().getNodesByBlock(block.getHash().getBytes()).size());
        Assertions.assertTrue(store.hasBlock(block));
        Assertions.assertEquals(1, sender.getMessages().size());
        Assertions.assertEquals(1, store.size());

        final Message message = sender.getMessages().get(0);

        Assertions.assertNotNull(message);
        Assertions.assertEquals(MessageType.GET_BLOCK_MESSAGE, message.getMessageType());

        final GetBlockMessage gbMessage = (GetBlockMessage) message;

        Assertions.assertArrayEquals(block.getParentHash().getBytes(), gbMessage.getBlockHash());
    }

    @Test
    void processGetBlockHeaderMessageUsingBlockInStore() {
        final Block block = new BlockGenerator().getBlock(3);

        final NetBlockStore store = new NetBlockStore();
        store.saveBlock(block);

        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration,
                DummyBlockValidator.VALID_RESULT_INSTANCE, DummyBlockValidator.VALID_RESULT_INSTANCE);

        final SimplePeer sender = new SimplePeer();

        processor.processBlockHeadersRequest(sender, 1, block.getHash().getBytes(), 1);

        Assertions.assertFalse(sender.getMessages().isEmpty());
        Assertions.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assertions.assertEquals(MessageType.BLOCK_HEADERS_RESPONSE_MESSAGE, message.getMessageType());

        final BlockHeadersResponseMessage bMessage = (BlockHeadersResponseMessage) message;

        Assertions.assertEquals(block.getHeader().getHash(), bMessage.getBlockHeaders().get(0).getHash());
    }

    @Test
    void processGetBlockHeaderMessageUsingEmptyStore() {
        final Block block = new BlockGenerator().getBlock(3);
        final NetBlockStore store = new NetBlockStore();
        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration,
                DummyBlockValidator.VALID_RESULT_INSTANCE, DummyBlockValidator.VALID_RESULT_INSTANCE);

        final SimplePeer sender = new SimplePeer();

        Assertions.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).isEmpty());

        processor.processBlockHeadersRequest(sender, 1, block.getHash().getBytes(), 1);

        Assertions.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).isEmpty());

        Assertions.assertTrue(sender.getMessages().isEmpty());
    }

    @Test
    void processGetBlockHeaderMessageUsingBlockInBlockchain() {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(10);
        final Block block = blockchain.getBlockByNumber(5);
        final NetBlockStore store = new NetBlockStore();

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration,
                DummyBlockValidator.VALID_RESULT_INSTANCE, DummyBlockValidator.VALID_RESULT_INSTANCE);

        final SimplePeer sender = new SimplePeer();

        processor.processBlockHeadersRequest(sender, 1, block.getHash().getBytes(), 1);

        Assertions.assertFalse(sender.getMessages().isEmpty());
        Assertions.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assertions.assertEquals(MessageType.BLOCK_HEADERS_RESPONSE_MESSAGE, message.getMessageType());

        final BlockHeadersResponseMessage bMessage = (BlockHeadersResponseMessage) message;

        Assertions.assertEquals(block.getHeader().getHash(), bMessage.getBlockHeaders().get(0).getHash());
    }

    @Test
    void processGetBlockMessageUsingBlockInStore() {
        final Block block = new BlockGenerator().getBlock(3);
        final Keccak256 blockHash = block.getHash();

        final NetBlockStore store = new NetBlockStore();
        store.saveBlock(block);

        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration,
                DummyBlockValidator.VALID_RESULT_INSTANCE, DummyBlockValidator.VALID_RESULT_INSTANCE);

        final SimplePeer sender = new SimplePeer();

        Assertions.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).isEmpty());

        processor.processGetBlock(sender, block.getHash().getBytes());

        Assertions.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).contains(sender.getPeerNodeID()));

        Assertions.assertFalse(sender.getMessages().isEmpty());
        Assertions.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assertions.assertEquals(MessageType.BLOCK_MESSAGE, message.getMessageType());

        final BlockMessage bMessage = (BlockMessage) message;

        Assertions.assertEquals(block.getHash(), bMessage.getBlock().getHash());
    }

    @Test
    void processGetBlockMessageUsingEmptyStore() {
        final Block block = new BlockGenerator().getBlock(3);
        final NetBlockStore store = new NetBlockStore();
        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration,
                DummyBlockValidator.VALID_RESULT_INSTANCE, DummyBlockValidator.VALID_RESULT_INSTANCE);

        final SimplePeer sender = new SimplePeer();

        Assertions.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).isEmpty());

        processor.processGetBlock(sender, block.getHash().getBytes());

        Assertions.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).isEmpty());

        Assertions.assertTrue(sender.getMessages().isEmpty());
    }

    @Test
    void processGetBlockMessageUsingBlockInBlockchain() {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(10);
        final Block block = blockchain.getBlockByNumber(5);
        final Keccak256 blockHash = block.getHash();
        final NetBlockStore store = new NetBlockStore();

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration,
                DummyBlockValidator.VALID_RESULT_INSTANCE, DummyBlockValidator.VALID_RESULT_INSTANCE);

        final SimplePeer sender = new SimplePeer();

        Assertions.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).isEmpty());

        processor.processGetBlock(sender, block.getHash().getBytes());

        Assertions.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).contains(sender.getPeerNodeID()));

        Assertions.assertFalse(sender.getMessages().isEmpty());
        Assertions.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assertions.assertEquals(MessageType.BLOCK_MESSAGE, message.getMessageType());

        final BlockMessage bMessage = (BlockMessage) message;

        Assertions.assertEquals(block.getHash(), bMessage.getBlock().getHash());
    }

    @Test
    void processBlockRequestMessageUsingBlockInStore() {
        final Block block = new BlockGenerator().getBlock(3);
        final Keccak256 blockHash = block.getHash();

        final NetBlockStore store = new NetBlockStore();
        store.saveBlock(block);

        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration,
                DummyBlockValidator.VALID_RESULT_INSTANCE, DummyBlockValidator.VALID_RESULT_INSTANCE);

        final SimplePeer sender = new SimplePeer();

        Assertions.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).isEmpty());

        processor.processBlockRequest(sender, 100, block.getHash().getBytes());

        Assertions.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).contains(sender.getPeerNodeID()));

        Assertions.assertFalse(sender.getMessages().isEmpty());
        Assertions.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assertions.assertEquals(MessageType.BLOCK_RESPONSE_MESSAGE, message.getMessageType());

        final BlockResponseMessage bMessage = (BlockResponseMessage) message;

        Assertions.assertEquals(100, bMessage.getId());
        Assertions.assertEquals(block.getHash(), bMessage.getBlock().getHash());
    }

    @Test
    void processBodyRequestMessageUsingBlockInBlockchain() {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(10);
        final Block block = blockchain.getBlockByNumber(3);
        final NetBlockStore store = new NetBlockStore();
        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration,
                DummyBlockValidator.VALID_RESULT_INSTANCE, DummyBlockValidator.VALID_RESULT_INSTANCE);

        final SimplePeer sender = new SimplePeer();

        processor.processBodyRequest(sender, 100, block.getHash().getBytes());

        Assertions.assertFalse(sender.getMessages().isEmpty());
        Assertions.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assertions.assertEquals(MessageType.BODY_RESPONSE_MESSAGE, message.getMessageType());

        final BodyResponseMessage bMessage = (BodyResponseMessage) message;

        Assertions.assertEquals(100, bMessage.getId());
        Assertions.assertEquals(block.getTransactionsList(), bMessage.getTransactions());
        Assertions.assertEquals(block.getUncleList(), bMessage.getUncles());
    }

    @Test
    void processBlockHashRequestMessageUsingEmptyStore() {
        final Block block = new BlockGenerator().getBlock(3);
        final NetBlockStore store = new NetBlockStore();
        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration,
                DummyBlockValidator.VALID_RESULT_INSTANCE, DummyBlockValidator.VALID_RESULT_INSTANCE);

        final SimplePeer sender = new SimplePeer();

        Assertions.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).isEmpty());

        processor.processBlockRequest(sender, 100, block.getHash().getBytes());

        Assertions.assertFalse(nodeInformation.getNodesByBlock(block.getHash()).contains(sender.getPeerNodeID()));

        Assertions.assertTrue(sender.getMessages().isEmpty());
    }

    @Test
    void processBlockHashRequestMessageUsingBlockInBlockchain() {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(10);
        final Block block = blockchain.getBlockByNumber(5);
        final Keccak256 blockHash = block.getHash();
        final NetBlockStore store = new NetBlockStore();

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration,
                DummyBlockValidator.VALID_RESULT_INSTANCE, DummyBlockValidator.VALID_RESULT_INSTANCE);

        final SimplePeer sender = new SimplePeer();

        Assertions.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).isEmpty());

        processor.processBlockRequest(sender, 100, block.getHash().getBytes());

        Assertions.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).contains(sender.getPeerNodeID()));

        Assertions.assertFalse(sender.getMessages().isEmpty());
        Assertions.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assertions.assertEquals(MessageType.BLOCK_RESPONSE_MESSAGE, message.getMessageType());

        final BlockResponseMessage bMessage = (BlockResponseMessage) message;

        Assertions.assertEquals(100, bMessage.getId());
        Assertions.assertEquals(block.getHash(), bMessage.getBlock().getHash());
    }

    @Test
    void processBlockHashRequestMessageUsingOutOfBoundsHeight() {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(10);
        final NetBlockStore store = new NetBlockStore();
        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration,
                DummyBlockValidator.VALID_RESULT_INSTANCE, DummyBlockValidator.VALID_RESULT_INSTANCE);

        final SimplePeer sender = new SimplePeer();

        processor.processBlockHashRequest(sender, 100, 99999);

        Assertions.assertTrue(sender.getMessages().isEmpty());
    }

    @Test
    void processBlockHeadersRequestMessageUsingBlockInBlockchain() {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(100);
        final Block block = blockchain.getBlockByNumber(60);
        final NetBlockStore store = new NetBlockStore();

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration,
                DummyBlockValidator.VALID_RESULT_INSTANCE, DummyBlockValidator.VALID_RESULT_INSTANCE);

        final SimplePeer sender = new SimplePeer();

        processor.processBlockHeadersRequest(sender, 100, block.getHash().getBytes(), 20);

        Assertions.assertFalse(sender.getMessages().isEmpty());
        Assertions.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assertions.assertEquals(MessageType.BLOCK_HEADERS_RESPONSE_MESSAGE, message.getMessageType());

        final BlockHeadersResponseMessage response = (BlockHeadersResponseMessage) message;

        Assertions.assertEquals(100, response.getId());
        Assertions.assertNotNull(response.getBlockHeaders());
        Assertions.assertEquals(20, response.getBlockHeaders().size());

        for (int k = 0; k < 20; k++) {
            Assertions.assertEquals(blockchain.getBlockByNumber(60 - k).getHash(), response.getBlockHeaders().get(k).getHash());
        }
    }

    @Test
    void processBlockHeadersRequestMessageUsingUnknownHash() {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(100);
        final NetBlockStore store = new NetBlockStore();

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration,
                DummyBlockValidator.VALID_RESULT_INSTANCE, DummyBlockValidator.VALID_RESULT_INSTANCE);

        final SimplePeer sender = new SimplePeer();

        processor.processBlockHeadersRequest(sender, 100, TestUtils.generateBytes("senderHash",32), 20);

        Assertions.assertTrue(sender.getMessages().isEmpty());
    }

    @Test
    void processSkeletonRequestWithGenesisPlusBestBlockInSkeleton() {
        int skeletonStep = 192;
        final Blockchain blockchain = new BlockChainBuilder().ofSize(skeletonStep / 2);
        final NetBlockStore store = new NetBlockStore();

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration,
                DummyBlockValidator.VALID_RESULT_INSTANCE, DummyBlockValidator.VALID_RESULT_INSTANCE);

        final SimplePeer sender = new SimplePeer();

        processor.processSkeletonRequest(sender, 100, 5);

        Assertions.assertFalse(sender.getMessages().isEmpty());
        Assertions.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assertions.assertEquals(MessageType.SKELETON_RESPONSE_MESSAGE, message.getMessageType());

        final SkeletonResponseMessage bMessage = (SkeletonResponseMessage) message;

        Assertions.assertEquals(100, bMessage.getId());

        Block genesis = blockchain.getBlockByNumber(0);
        Block bestBlock = blockchain.getBestBlock();
        BlockIdentifier[] expected = {
                new BlockIdentifier(genesis.getHash().getBytes(), genesis.getNumber()),
                new BlockIdentifier(bestBlock.getHash().getBytes(), bestBlock.getNumber()),
        };
        assertBlockIdentifiers(expected, bMessage.getBlockIdentifiers());
    }

    @Test
    void processSkeletonRequestWithThreeResults() {
        final int skeletonStep = 192;
        final Blockchain blockchain = new BlockChainBuilder().ofSize(300);
        final NetBlockStore store = new NetBlockStore();

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration,
                DummyBlockValidator.VALID_RESULT_INSTANCE, DummyBlockValidator.VALID_RESULT_INSTANCE);

        final SimplePeer sender = new SimplePeer();

        processor.processSkeletonRequest(sender, 100, 5);

        Assertions.assertFalse(sender.getMessages().isEmpty());
        Assertions.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assertions.assertEquals(MessageType.SKELETON_RESPONSE_MESSAGE, message.getMessageType());

        final SkeletonResponseMessage bMessage = (SkeletonResponseMessage) message;

        Assertions.assertEquals(100, bMessage.getId());

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
    void processSkeletonRequestNotIncludingGenesis() {
        final int skeletonStep = 192;
        final Blockchain blockchain = new BlockChainBuilder().ofSize(400);
        final NetBlockStore store = new NetBlockStore();

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration,
                DummyBlockValidator.VALID_RESULT_INSTANCE, DummyBlockValidator.VALID_RESULT_INSTANCE);

        final SimplePeer sender = new SimplePeer();

        processor.processSkeletonRequest(sender, 100, skeletonStep + 5);

        Assertions.assertFalse(sender.getMessages().isEmpty());
        Assertions.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assertions.assertEquals(MessageType.SKELETON_RESPONSE_MESSAGE, message.getMessageType());

        final SkeletonResponseMessage bMessage = (SkeletonResponseMessage) message;

        Assertions.assertEquals(100, bMessage.getId());

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
        Assertions.assertEquals(expected.length, actual.size());

        for (int i = 0; i < expected.length; i++) {
            Assertions.assertEquals(expected[i].getNumber(), actual.get(i).getNumber());
            Assertions.assertArrayEquals(expected[i].getHash(), actual.get(i).getHash());
        }
    }

    @Test
    void failIfProcessBlockHeadersRequestCountHigher()  {
        final Peer sender = mock(Peer.class);

        final Block block = new BlockGenerator().getBlock(3);

        final NetBlockStore store = new NetBlockStore();
        store.saveBlock(block);

        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration,
                DummyBlockValidator.VALID_RESULT_INSTANCE, DummyBlockValidator.VALID_RESULT_INSTANCE);

        final int size = syncConfiguration.getChunkSize() + 1;
        processor.processBlockHeadersRequest(sender, 1, block.getHash().getBytes(), size);

        verify(sender, never()).sendMessage(any());
    }

    @Test
    void duplicatedBlock() {
        final NetBlockStore store = new NetBlockStore();
        final Peer sender = new SimplePeer();

        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        final BlockGenerator blockGenerator = new BlockGenerator();
        final Block block = blockGenerator.createChildBlock(blockGenerator.getGenesisBlock());
        final Block sameBlock = new Block(block.getHeader(), block.getTransactionsList(), block.getUncleList(), true, true);

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration,
                DummyBlockValidator.VALID_RESULT_INSTANCE, DummyBlockValidator.VALID_RESULT_INSTANCE);

        processor.processBlock(sender, block);
        BlockProcessResult blockProcessResult = processor.processBlock(sender, sameBlock);

        Assertions.assertFalse(blockProcessResult.isScheduledForProcessing());
        Assertions.assertFalse(blockProcessResult.wasBlockAdded(block));
    }

    @Test
    void invalidBlock() {
        final NetBlockStore store = new NetBlockStore();
        final Peer sender = new SimplePeer();

        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        final BlockGenerator blockGenerator = new BlockGenerator();
        final Block block = blockGenerator.createChildBlock(blockGenerator.getGenesisBlock());

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final TestSystemProperties config = new TestSystemProperties();
        final BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final AsyncNodeBlockProcessor processor = new AsyncNodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration,
                DummyBlockValidator.INVALID_RESULT_INSTANCE, DummyBlockValidator.INVALID_RESULT_INSTANCE);

        BlockProcessResult blockProcessResult = processor.processBlock(sender, block);

        Assertions.assertFalse(blockProcessResult.isScheduledForProcessing());
        Assertions.assertFalse(blockProcessResult.wasBlockAdded(block));
        Assertions.assertTrue(blockProcessResult.isInvalidBlock());
    }
}
