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
import co.rsk.core.BlockDifficulty;
import co.rsk.crypto.Keccak256;
import co.rsk.net.messages.*;
import co.rsk.net.simples.SimplePeer;
import co.rsk.net.sync.SyncConfiguration;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.validators.DummyBlockValidator;
import org.ethereum.TestUtils;
import org.ethereum.core.Block;
import org.ethereum.core.BlockIdentifier;
import org.ethereum.core.Blockchain;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigInteger;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

/**
 * Created by ajlopez on 5/11/2016.
 */
class NodeBlockProcessorTest {
    @Test
    void processBlockSavingInStore() throws UnknownHostException {
        final NetBlockStore store = new NetBlockStore();
        final Peer sender = new SimplePeer();

        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        BlockGenerator blockGenerator = new BlockGenerator();
        final Block parent = blockGenerator.createChildBlock(blockGenerator.getGenesisBlock());
        final Block orphan = blockGenerator.createChildBlock(parent);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        processor.processBlock(sender, orphan);
        Assertions.assertEquals(1, processor.getNodeInformation().getNodesByBlock(orphan.getHash().getBytes()).size());

        Assertions.assertTrue(store.hasBlock(orphan));
        Assertions.assertEquals(1, store.size());
    }

    @Test
    void processBlockWithTooMuchHeight() throws UnknownHostException {
        final NetBlockStore store = new NetBlockStore();
        final Peer sender = new SimplePeer();

        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        final Block orphan = new BlockGenerator().createBlock(1000, 0);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        processor.processBlock(sender, orphan);

        Assertions.assertNotEquals(1, processor.getNodeInformation().getNodesByBlock(orphan.getHash().getBytes()).size());
        Assertions.assertFalse(store.hasBlock(orphan));
        Assertions.assertEquals(0, store.size());
    }

    @Test
    void advancedBlock() throws UnknownHostException {
        final NetBlockStore store = new NetBlockStore();
        final Peer sender = new SimplePeer();

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;

        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        final long advancedBlockNumber = syncConfiguration.getChunkSize() * syncConfiguration.getMaxSkeletonChunks() + blockchain.getBestBlock().getNumber() + 1;

        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        Assertions.assertTrue(processor.isAdvancedBlock(advancedBlockNumber));
        Assertions.assertFalse(processor.isAdvancedBlock(advancedBlockNumber - 1));
    }

    @Test
    void canBeIgnoredForUncles() throws UnknownHostException {
        final NetBlockStore store = new NetBlockStore();
        final Peer sender = new SimplePeer();

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;

        final Blockchain blockchain = new BlockChainBuilder().ofSize(15);
        final TestSystemProperties config = new TestSystemProperties();
        int uncleGenerationLimit = config.getNetworkConstants().getUncleGenerationLimit();
        final long blockNumberThatCanBeIgnored = blockchain.getBestBlock().getNumber() - 1 - uncleGenerationLimit;

        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        Assertions.assertTrue(processor.canBeIgnoredForUnclesRewards(blockNumberThatCanBeIgnored));
        Assertions.assertFalse(processor.canBeIgnoredForUnclesRewards(blockNumberThatCanBeIgnored + 1));
    }

    @Test
    void processBlockAddingToBlockchain() {
        Blockchain blockchain = new BlockChainBuilder().ofSize(10);

        Assertions.assertEquals(10, blockchain.getBestBlock().getNumber());

        NetBlockStore store = new NetBlockStore();
        Block genesis = blockchain.getBlockByNumber(0);
        store.saveBlock(genesis);
        Block block = new BlockGenerator().createChildBlock(blockchain.getBlockByNumber(10));

        Assertions.assertEquals(11, block.getNumber());
        Assertions.assertArrayEquals(blockchain.getBestBlockHash(), block.getParentHash().getBytes());

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        processor.processBlock(null, block);

        Assertions.assertFalse(store.hasBlock(block));
        Assertions.assertEquals(11, blockchain.getBestBlock().getNumber());
        Assertions.assertArrayEquals(block.getHash().getBytes(), blockchain.getBestBlockHash());
        Assertions.assertEquals(1, store.size());
    }

    @Test
    void processTenBlocksAddingToBlockchain() {
        Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        NetBlockStore store = new NetBlockStore();
        Block genesis = blockchain.getBestBlock();

        List<Block> blocks = new BlockGenerator().getBlockChain(genesis, 10);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        processor.processBlock(null, genesis);
        Assertions.assertEquals(0, store.size());

        for (Block b : blocks)
            processor.processBlock(null, b);

        Assertions.assertEquals(10, blockchain.getBestBlock().getNumber());
        Assertions.assertEquals(0, store.size());
    }

    @Test
    void processTwoBlockListsAddingToBlockchain() {
        Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        NetBlockStore store = new NetBlockStore();
        Block genesis = blockchain.getBestBlock();
        BlockGenerator blockGenerator = new BlockGenerator();
        List<Block> blocks = blockGenerator.getBlockChain(genesis, 10);
        List<Block> blocks2 = blockGenerator.getBlockChain(genesis, 20);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        processor.processBlock(null, genesis);
        Assertions.assertEquals(0, store.size());

        for (Block b : blocks)
            processor.processBlock(null, b);
        for (Block b : blocks2)
            processor.processBlock(null, b);

        Assertions.assertEquals(20, blockchain.getBestBlock().getNumber());
        Assertions.assertEquals(0, store.size());
    }

    @Test
    void processTwoBlockListsAddingToBlockchainWithFork() {
        NetBlockStore store = new NetBlockStore();
        Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        Block genesis = blockchain.getBestBlock();

        BlockGenerator blockGenerator = new BlockGenerator();
        List<Block> blocks = blockGenerator.getBlockChain(genesis, 10);
        List<Block> blocks2 = blockGenerator.getBlockChain(blocks.get(4), 20);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        processor.processBlock(null, genesis);
        Assertions.assertEquals(0, store.size());

        for (Block b : blocks)
            processor.processBlock(null, b);
        for (Block b : blocks2)
            processor.processBlock(null, b);

        Assertions.assertEquals(25, blockchain.getBestBlock().getNumber());
        Assertions.assertEquals(0, store.size());
    }

    @Test
    void noSyncingWithEmptyBlockchain() {
        NetBlockStore store = new NetBlockStore();
        Blockchain blockchain = new BlockChainBuilder().ofSize(0);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        Assertions.assertFalse(processor.hasBetterBlockToSync());
    }

    @Test
    @Disabled("Ignored when Process status deleted on block processor")
    void noSyncingWithEmptyBlockchainAndLowBestBlock() {
        NetBlockStore store = new NetBlockStore();
        Block block = new BlockGenerator().createBlock(10, 0);
        Blockchain blockchain = new BlockChainBuilder().ofSize(0);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        Assertions.assertFalse(processor.hasBetterBlockToSync());

        Status status = new Status(block.getNumber(), block.getHash().getBytes());
//        processor.processStatus(new SimpleNodeChannel(null, null), status);

        Assertions.assertFalse(processor.hasBetterBlockToSync());
    }

    @Test
    @Disabled("Ignored when Process status deleted on block processor")
    void syncingWithEmptyBlockchainAndHighBestBlock() {
        NetBlockStore store = new NetBlockStore();
        Block block = new BlockGenerator().createBlock(30, 0);
        Blockchain blockchain = new BlockChainBuilder().ofSize(0);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        Assertions.assertFalse(processor.hasBetterBlockToSync());

//        Status status = new Status(block.getNumber(), block.getHash());
//        processor.processStatus(new SimpleNodeChannel(null, null), status);

        Assertions.assertTrue(processor.hasBetterBlockToSync());
    }

    @Test
    @Disabled("Ignored when Process status deleted on block processor")
    void syncingThenNoSyncing() {
        NetBlockStore store = new NetBlockStore();
        Block block = new BlockGenerator().createBlock(30, 0);
        Blockchain blockchain = new BlockChainBuilder().ofSize(0);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        Assertions.assertFalse(processor.hasBetterBlockToSync());

//        Status status = new Status(block.getNumber(), block.getHash());
//        processor.processStatus(new SimpleNodeChannel(null, null), status);

        Assertions.assertTrue(processor.hasBetterBlockToSync());
        Assertions.assertTrue(processor.hasBetterBlockToSync());

        blockchain.setStatus(block, new BlockDifficulty(BigInteger.valueOf(30)));

        Assertions.assertFalse(processor.hasBetterBlockToSync());
        Assertions.assertFalse(processor.hasBetterBlockToSync());

        Block block2 = new BlockGenerator().createBlock(60, 0);
//        Status status2 = new Status(block2.getNumber(), block2.getHash());
//        processor.processStatus(new SimpleNodeChannel(null, null), status2);

        Assertions.assertTrue(processor.hasBetterBlockToSync());
        Assertions.assertFalse(processor.hasBetterBlockToSync());
    }

    @Test
    void processTenBlocksGenesisAtLastAddingToBlockchain() {
        NetBlockStore store = new NetBlockStore();
        Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        Block genesis = blockchain.getBestBlock();
        List<Block> blocks = new BlockGenerator().getBlockChain(genesis, 10);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        for (Block b : blocks)
            processor.processBlock(null, b);

        processor.processBlock(null, genesis);

        Assertions.assertEquals(10, blockchain.getBestBlock().getNumber());
        Assertions.assertEquals(0, store.size());
    }

    @Test
    void processTenBlocksInverseOrderAddingToBlockchain() {
        Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        NetBlockStore store = new NetBlockStore();
        Block genesis = blockchain.getBestBlock();
        List<Block> blocks = new BlockGenerator().getBlockChain(genesis, 10);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        for (int k = 0; k < 10; k++)
            processor.processBlock(null, blocks.get(9 - k));

        processor.processBlock(null, genesis);

        Assertions.assertEquals(10, blockchain.getBestBlock().getNumber());
        Assertions.assertEquals(0, store.size());
    }

    @Test
    void processTenBlocksWithHoleAddingToBlockchain() {
        Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        NetBlockStore store = new NetBlockStore();
        Block genesis = blockchain.getBestBlock();
        List<Block> blocks = new BlockGenerator().getBlockChain(genesis, 10);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        for (int k = 0; k < 10; k++)
            if (k != 5)
                processor.processBlock(null, blocks.get(9 - k));

        processor.processBlock(null, genesis);
        processor.processBlock(null, blocks.get(4));

        Assertions.assertEquals(10, blockchain.getBestBlock().getNumber());
        Assertions.assertEquals(0, store.size());
    }

    @Test
    void processBlockAddingToBlockchainUsingItsParent() {
        NetBlockStore store = new NetBlockStore();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        store.saveBlock(genesis);
        Blockchain blockchain = new BlockChainBuilder().ofSize(10);
        Block parent = blockGenerator.createChildBlock(blockchain.getBlockByNumber(10));
        Block block = blockGenerator.createChildBlock(parent);

        Assertions.assertEquals(11, parent.getNumber());
        Assertions.assertEquals(12, block.getNumber());
        Assertions.assertArrayEquals(blockchain.getBestBlockHash(), parent.getParentHash().getBytes());

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        processor.processBlock(null, block);

        Assertions.assertTrue(store.hasBlock(block));
        Assertions.assertNull(blockchain.getBlockByHash(block.getHash().getBytes()));

        processor.processBlock(null, parent);

        Assertions.assertFalse(store.hasBlock(block));
        Assertions.assertFalse(store.hasBlock(parent));

        Assertions.assertEquals(12, blockchain.getBestBlock().getNumber());
        Assertions.assertArrayEquals(block.getHash().getBytes(), blockchain.getBestBlockHash());
        Assertions.assertEquals(1, store.size());
    }

    @Test
    void processBlockRetrievingParentUsingSender() throws UnknownHostException {
        final NetBlockStore store = new NetBlockStore();
        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);
        final SimplePeer sender = new SimplePeer();

        BlockGenerator blockGenerator = new BlockGenerator();
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
    @Disabled("Ignored when Process status deleted on block processor")
    void processStatusRetrievingBestBlockUsingSender() throws UnknownHostException {
        final NetBlockStore store = new NetBlockStore();
        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);
        final SimplePeer sender = new SimplePeer();

        BlockGenerator blockGenerator = new BlockGenerator();
        final Block genesis = blockGenerator.getGenesisBlock();
        final Block block = blockGenerator.createChildBlock(genesis);
//        final Status status = new Status(block.getNumber(), block.getHash());

//        processor.processStatus(sender, status);
        Assertions.assertEquals(1, processor.getNodeInformation().getNodesByBlock(block.getHash().getBytes()).size());

        Assertions.assertEquals(1, sender.getGetBlockMessages().size());

        final Message message = sender.getGetBlockMessages().get(0);

        Assertions.assertNotNull(message);
        Assertions.assertEquals(MessageType.GET_BLOCK_MESSAGE, message.getMessageType());

        final GetBlockMessage gbMessage = (GetBlockMessage) message;

        Assertions.assertArrayEquals(block.getHash().getBytes(), gbMessage.getBlockHash());
        Assertions.assertEquals(0, store.size());
    }

    @Test
    @Disabled("Ignored when Process status deleted on block processor")
    void processStatusHavingBestBlockInStore() throws UnknownHostException {
        final NetBlockStore store = new NetBlockStore();
        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);
        final SimplePeer sender = new SimplePeer();

        BlockGenerator blockGenerator = new BlockGenerator();
        final Block genesis = blockGenerator.getGenesisBlock();
        final Block block = blockGenerator.createChildBlock(genesis);

        store.saveBlock(block);
//        final Status status = new Status(block.getNumber(), block.getHash());

//        processor.processStatus(sender, status);
        Assertions.assertEquals(1, processor.getNodeInformation().getNodesByBlock(block.getHash().getBytes()).size());
        Assertions.assertEquals(1, store.size());
    }

    @Test
    @Disabled("Ignored when Process status deleted on block processor")
    void processStatusHavingBestBlockAsBestBlockInBlockchain() throws UnknownHostException {
        final NetBlockStore store = new NetBlockStore();
        final Blockchain blockchain = new BlockChainBuilder().ofSize(2);
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        final SimplePeer sender = new SimplePeer();

        final Block block = blockchain.getBestBlock();
        final Keccak256 blockHash = block.getHash();

//        final Status status = new Status(block.getNumber(), block.getHash());

//        processor.processStatus(sender, status);
        Assertions.assertEquals(1, processor.getNodeInformation().getNodesByBlock(block.getHash().getBytes()).size());
        Assertions.assertTrue(processor.getNodeInformation().getNodesByBlock(block.getHash().getBytes()).contains(sender.getPeerNodeID()));

        Assertions.assertEquals(0, sender.getGetBlockMessages().size());
        Assertions.assertEquals(0, store.size());
    }

    @Test
    @Disabled("Ignored when Process status deleted on block processor")
    void processStatusHavingBestBlockInBlockchainStore() throws UnknownHostException {
        final NetBlockStore store = new NetBlockStore();
        final Blockchain blockchain = new BlockChainBuilder().ofSize(2);
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        final SimplePeer sender = new SimplePeer();

        final Block block = blockchain.getBlockByNumber(1);
        final Keccak256 blockHash = block.getHash();

        store.saveBlock(block);
//        final Status status = new Status(block.getNumber(), block.getHash());

        Assertions.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).isEmpty());


//        processor.processStatus(sender, status);
        Assertions.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).contains(sender.getPeerNodeID()));

        Assertions.assertEquals(0, sender.getGetBlockMessages().size());
    }

    @Test
    void processGetBlockHeaderMessageUsingBlockInStore() throws UnknownHostException {
        final Block block = new BlockGenerator().getBlock(3);

        final NetBlockStore store = new NetBlockStore();
        store.saveBlock(block);

        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

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
    void processGetBlockHeaderMessageUsingEmptyStore() throws UnknownHostException {
        final Block block = new BlockGenerator().getBlock(3);
        final NetBlockStore store = new NetBlockStore();
        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        final SimplePeer sender = new SimplePeer();

        Assertions.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).isEmpty());

        processor.processBlockHeadersRequest(sender, 1, block.getHash().getBytes(), 1);

        Assertions.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).isEmpty());

        Assertions.assertTrue(sender.getMessages().isEmpty());
    }

    @Test
    void processGetBlockHeaderMessageUsingBlockInBlockchain() throws UnknownHostException {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(10);
        final Block block = blockchain.getBlockByNumber(5);
        final NetBlockStore store = new NetBlockStore();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

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
    void processGetBlockMessageUsingBlockInStore() throws UnknownHostException {
        final Block block = new BlockGenerator().getBlock(3);
        final Keccak256 blockHash = block.getHash();

        final NetBlockStore store = new NetBlockStore();
        store.saveBlock(block);

        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

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
    void processGetBlockMessageUsingEmptyStore() throws UnknownHostException {
        final Block block = new BlockGenerator().getBlock(3);
        final NetBlockStore store = new NetBlockStore();
        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        final SimplePeer sender = new SimplePeer();

        Assertions.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).isEmpty());

        processor.processGetBlock(sender, block.getHash().getBytes());

        Assertions.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).isEmpty());


        Assertions.assertTrue(sender.getMessages().isEmpty());
    }

    @Test
    void processGetBlockMessageUsingBlockInBlockchain() throws UnknownHostException {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(10);
        final Block block = blockchain.getBlockByNumber(5);
        final Keccak256 blockHash = block.getHash();
        final NetBlockStore store = new NetBlockStore();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

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
    void processBlockRequestMessageUsingBlockInStore() throws UnknownHostException {
        final Block block = new BlockGenerator().getBlock(3);
        final Keccak256 blockHash = block.getHash();

        final NetBlockStore store = new NetBlockStore();
        store.saveBlock(block);

        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

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
    void processBodyRequestMessageUsingBlockInBlockchain() throws UnknownHostException {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(10);
        final Block block = blockchain.getBlockByNumber(3);
        final NetBlockStore store = new NetBlockStore();
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

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
    void processBlockHashRequestMessageUsingEmptyStore() throws UnknownHostException {
        final Block block = new BlockGenerator().getBlock(3);
        final Keccak256 blockHash = block.getHash();
        final NetBlockStore store = new NetBlockStore();
        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        final SimplePeer sender = new SimplePeer();

        Assertions.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).isEmpty());

        processor.processBlockRequest(sender, 100, block.getHash().getBytes());

        Assertions.assertFalse(nodeInformation.getNodesByBlock(block.getHash()).contains(sender.getPeerNodeID()));


        Assertions.assertTrue(sender.getMessages().isEmpty());
    }

    @Test
    void processBlockHashRequestMessageUsingBlockInBlockchain() throws UnknownHostException {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(10);
        final Block block = blockchain.getBlockByNumber(5);
        final Keccak256 blockHash = block.getHash();
        final NetBlockStore store = new NetBlockStore();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

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
    void processBlockHashRequestMessageUsingOutOfBoundsHeight() throws UnknownHostException {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(10);
        final NetBlockStore store = new NetBlockStore();
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        final SimplePeer sender = new SimplePeer();

        processor.processBlockHashRequest(sender, 100, 99999);

        Assertions.assertTrue(sender.getMessages().isEmpty());
    }

    @Test
    void processBlockHeadersRequestMessageUsingBlockInBlockchain() throws UnknownHostException {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(100);
        final Block block = blockchain.getBlockByNumber(60);
        final NetBlockStore store = new NetBlockStore();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

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

        for (int k = 0; k < 20; k++)
            Assertions.assertEquals(blockchain.getBlockByNumber(60 - k).getHash(), response.getBlockHeaders().get(k).getHash());
    }

    @Test
    void processBlockHeadersRequestMessageUsingUnknownHash() throws UnknownHostException {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(100);
        final NetBlockStore store = new NetBlockStore();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        final SimplePeer sender = new SimplePeer();

        processor.processBlockHeadersRequest(sender, 100, TestUtils.generateBytes("processor", 32), 20);

        Assertions.assertTrue(sender.getMessages().isEmpty());
    }

    @Test
    void processSkeletonRequestWithGenesisPlusBestBlockInSkeleton() throws UnknownHostException {
        int skeletonStep = 192;
        final Blockchain blockchain = new BlockChainBuilder().ofSize(skeletonStep / 2);
        final Block blockStart = blockchain.getBlockByNumber(5);
        final Block blockEnd = blockchain.getBlockByNumber(skeletonStep / 2);
        final NetBlockStore store = new NetBlockStore();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

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
    void processSkeletonRequestWithThreeResults() throws UnknownHostException {
        int skeletonStep = 192;
        final Blockchain blockchain = new BlockChainBuilder().ofSize(300);
        final NetBlockStore store = new NetBlockStore();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        final SimplePeer sender = new SimplePeer();

        processor.processSkeletonRequest(sender, 100, 5);

        Assertions.assertFalse(sender.getMessages().isEmpty());
        Assertions.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assertions.assertEquals(MessageType.SKELETON_RESPONSE_MESSAGE, message.getMessageType());

        final SkeletonResponseMessage bMessage = (SkeletonResponseMessage) message;

        Assertions.assertEquals(100, bMessage.getId());

        Block b1 = blockchain.getBlockByNumber(0);
        Block b2 = blockchain.getBlockByNumber(skeletonStep);
        Block b3 = blockchain.getBestBlock();
        BlockIdentifier[] expected = {
                new BlockIdentifier(b1.getHash().getBytes(), b1.getNumber()),
                new BlockIdentifier(b2.getHash().getBytes(), b2.getNumber()),
                new BlockIdentifier(b3.getHash().getBytes(), b3.getNumber()),
        };
        assertBlockIdentifiers(expected, bMessage.getBlockIdentifiers());
    }

    @Test
    void processSkeletonRequestNotIncludingGenesis() throws UnknownHostException {
        int skeletonStep = 192;
        final Blockchain blockchain = new BlockChainBuilder().ofSize(400);
        final NetBlockStore store = new NetBlockStore();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        final SimplePeer sender = new SimplePeer();

        processor.processSkeletonRequest(sender, 100, skeletonStep + 5);

        Assertions.assertFalse(sender.getMessages().isEmpty());
        Assertions.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assertions.assertEquals(MessageType.SKELETON_RESPONSE_MESSAGE, message.getMessageType());

        final SkeletonResponseMessage bMessage = (SkeletonResponseMessage) message;

        Assertions.assertEquals(100, bMessage.getId());

        Block b1 = blockchain.getBlockByNumber(skeletonStep);
        Block b2 = blockchain.getBlockByNumber(2 * skeletonStep);
        Block b3 = blockchain.getBestBlock();
        BlockIdentifier[] expected = {
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
    void failIfProcessBlockHeadersRequestCountHigher() {

        final Peer sender = mock(Peer.class);


        final Block block = new BlockGenerator().getBlock(3);

        final NetBlockStore store = new NetBlockStore();
        store.saveBlock(block);

        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        final Integer size = syncConfiguration.getChunkSize() + 1;
        processor.processBlockHeadersRequest(sender, 1, block.getHash().getBytes(), size);

        verify(sender, never()).sendMessage(any());

    }

    @Test
    void processNewBlockHashesMessageOnlyRequestsNonAdvancedBlock() {
        // create mocks and test objects
        final NetBlockStore store = new NetBlockStore();
        final SimplePeer sender = mock(SimplePeer.class);
        final BlockNodeInformation nodeInformation = mock(BlockNodeInformation.class);
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);

        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);

        final long advancedBlockNumber = (long) syncConfiguration.getChunkSize() * syncConfiguration.getMaxSkeletonChunks() + blockchain.getBestBlock().getNumber() + 1;

        BlockIdentifier advancedBlockIdentifier = new BlockIdentifier(new byte[32], advancedBlockNumber);
        BlockIdentifier nonAdvancedBlockIdentifier = new BlockIdentifier(new byte[32], advancedBlockNumber - 1);

        // create list of block identifiers with advanced and non-advanced blocks
        List<BlockIdentifier> blockIdentifierList = new ArrayList<>();
        blockIdentifierList.add(nonAdvancedBlockIdentifier);
        blockIdentifierList.add(advancedBlockIdentifier);

        NewBlockHashesMessage message = new NewBlockHashesMessage(blockIdentifierList);

        // use ArgumentCaptor to capture arguments passed to addBlockToNode()
        ArgumentCaptor<Keccak256> blockHashCaptor = ArgumentCaptor.forClass(Keccak256.class);

        // call the method being tested
        processor.processNewBlockHashesMessage(sender, message);

        // verify that just one block is requested and added to node information
        verify(sender, times(1)).sendMessage(any(GetBlockMessage.class));
        verify(nodeInformation, times(1)).addBlockToNode(blockHashCaptor.capture(), any());

        // assert that the block that got requested was the non-advanced one
        Assertions.assertEquals(nonAdvancedBlockIdentifier.getHash(), blockHashCaptor.getValue().getBytes());
        Assertions.assertNotEquals(advancedBlockIdentifier.getHash(), blockHashCaptor.getValue().getBytes());
    }
}
