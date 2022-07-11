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
import org.ethereum.core.Block;
import org.ethereum.core.BlockIdentifier;
import org.ethereum.core.Blockchain;
import org.ethereum.crypto.HashUtil;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.math.BigInteger;
import java.net.UnknownHostException;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * Created by ajlopez on 5/11/2016.
 */
public class NodeBlockProcessorTest {
    @Test
    public void processBlockSavingInStore() throws UnknownHostException {
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
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

        processor.processBlock(sender, orphan);
        Assert.assertTrue(processor.getNodeInformation().getNodesByBlock(orphan.getHash().getBytes()).size() == 1);

        Assert.assertTrue(store.hasBlock(orphan));
        Assert.assertEquals(1, store.size());
    }

    @Test
    public void processBlockWithTooMuchHeight() throws UnknownHostException {
        final NetBlockStore store = new NetBlockStore();
        final Peer sender = new SimplePeer();

        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        final Block orphan = new BlockGenerator().createBlock(1000, 0);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

        processor.processBlock(sender, orphan);

        Assert.assertFalse(processor.getNodeInformation().getNodesByBlock(orphan.getHash().getBytes()).size() == 1);
        Assert.assertFalse(store.hasBlock(orphan));
        Assert.assertEquals(0, store.size());
    }

    @Test
    public void advancedBlock() throws UnknownHostException {
        final NetBlockStore store = new NetBlockStore();
        final Peer sender = new SimplePeer();

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;

        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        final long advancedBlockNumber = syncConfiguration.getChunkSize() * syncConfiguration.getMaxSkeletonChunks() + blockchain.getBestBlock().getNumber() + 1;

        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

        Assert.assertTrue(processor.isAdvancedBlock(advancedBlockNumber));
        Assert.assertFalse(processor.isAdvancedBlock(advancedBlockNumber - 1));
    }

    @Test
    public void canBeIgnoredForUncles() throws UnknownHostException {
        final NetBlockStore store = new NetBlockStore();
        final Peer sender = new SimplePeer();

        final BlockNodeInformation nodeInformation = new BlockNodeInformation();
        final SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;

        final Blockchain blockchain = new BlockChainBuilder().ofSize(15);
        final TestSystemProperties config = new TestSystemProperties();
        int uncleGenerationLimit = config.getNetworkConstants().getUncleGenerationLimit();
        final long blockNumberThatCanBeIgnored = blockchain.getBestBlock().getNumber() - 1 - uncleGenerationLimit;

        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

        Assert.assertTrue(processor.canBeIgnoredForUnclesRewards(blockNumberThatCanBeIgnored));
        Assert.assertFalse(processor.canBeIgnoredForUnclesRewards(blockNumberThatCanBeIgnored + 1));
    }

    @Test
    public void processBlockAddingToBlockchain() {
        Blockchain blockchain = new BlockChainBuilder().ofSize(10);

        Assert.assertEquals(10, blockchain.getBestBlock().getNumber());

        NetBlockStore store = new NetBlockStore();
        Block genesis = blockchain.getBlockByNumber(0);
        store.saveBlock(genesis);
        Block block = new BlockGenerator().createChildBlock(blockchain.getBlockByNumber(10));

        Assert.assertEquals(11, block.getNumber());
        Assert.assertArrayEquals(blockchain.getBestBlockHash(), block.getParentHash().getBytes());

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

        processor.processBlock(null, block);

        Assert.assertFalse(store.hasBlock(block));
        Assert.assertEquals(11, blockchain.getBestBlock().getNumber());
        Assert.assertArrayEquals(block.getHash().getBytes(), blockchain.getBestBlockHash());
        Assert.assertEquals(1, store.size());
    }

    @Test
    public void processTenBlocksAddingToBlockchain() {
        Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        NetBlockStore store = new NetBlockStore();
        Block genesis = blockchain.getBestBlock();

        List<Block> blocks = new BlockGenerator().getBlockChain(genesis, 10);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

        processor.processBlock(null, genesis);
        Assert.assertEquals(0, store.size());

        for (Block b : blocks)
            processor.processBlock(null, b);

        Assert.assertEquals(10, blockchain.getBestBlock().getNumber());
        Assert.assertEquals(0, store.size());
    }

    @Test
    public void processTwoBlockListsAddingToBlockchain() {
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
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

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
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

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
        NetBlockStore store = new NetBlockStore();
        Blockchain blockchain = new BlockChainBuilder().ofSize(0);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

        Assert.assertFalse(processor.isReadyToAcceptBlocks());
    }

    @Test @Ignore("Ignored when Process status deleted on block processor")
    public void noSyncingWithEmptyBlockchainAndLowBestBlock() {
        NetBlockStore store = new NetBlockStore();
        Block block = new BlockGenerator().createBlock(10, 0);
        Blockchain blockchain = new BlockChainBuilder().ofSize(0);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

        Assert.assertFalse(processor.hasBetterBlockToSync());

        Status status = new Status(block.getNumber(), block.getHash().getBytes());
//        processor.processStatus(new SimpleNodeChannel(null, null), status);

        Assert.assertFalse(processor.hasBetterBlockToSync());
    }

    @Test @Ignore("Ignored when Process status deleted on block processor")
    public void syncingWithEmptyBlockchainAndHighBestBlock() {
        NetBlockStore store = new NetBlockStore();
        Block block = new BlockGenerator().createBlock(30, 0);
        Blockchain blockchain = new BlockChainBuilder().ofSize(0);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

        Assert.assertFalse(processor.hasBetterBlockToSync());

//        Status status = new Status(block.getNumber(), block.getHash());
//        processor.processStatus(new SimpleNodeChannel(null, null), status);

        Assert.assertTrue(processor.hasBetterBlockToSync());
    }

    @Test @Ignore("Ignored when Process status deleted on block processor")
    public void syncingThenNoSyncing() {
        NetBlockStore store = new NetBlockStore();
        Block block = new BlockGenerator().createBlock(30, 0);
        Blockchain blockchain = new BlockChainBuilder().ofSize(0);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

        Assert.assertFalse(processor.hasBetterBlockToSync());

//        Status status = new Status(block.getNumber(), block.getHash());
//        processor.processStatus(new SimpleNodeChannel(null, null), status);

        Assert.assertTrue(processor.hasBetterBlockToSync());
        Assert.assertTrue(processor.hasBetterBlockToSync());

        blockchain.setStatus(block, new BlockDifficulty(BigInteger.valueOf(30)));

        Assert.assertFalse(processor.hasBetterBlockToSync());
        Assert.assertFalse(processor.hasBetterBlockToSync());

        Block block2 = new BlockGenerator().createBlock(60, 0);
//        Status status2 = new Status(block2.getNumber(), block2.getHash());
//        processor.processStatus(new SimpleNodeChannel(null, null), status2);

        Assert.assertTrue(processor.hasBetterBlockToSync());
        Assert.assertFalse(processor.hasBetterBlockToSync());
    }

    @Test
    public void processTenBlocksGenesisAtLastAddingToBlockchain() {
        NetBlockStore store = new NetBlockStore();
        Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        Block genesis = blockchain.getBestBlock();
        List<Block> blocks = new BlockGenerator().getBlockChain(genesis, 10);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

        for (Block b : blocks)
            processor.processBlock(null, b);

        processor.processBlock(null, genesis);

        Assert.assertEquals(10, blockchain.getBestBlock().getNumber());
        Assert.assertEquals(0, store.size());
    }

    @Test
    public void processTenBlocksInverseOrderAddingToBlockchain() {
        Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        NetBlockStore store = new NetBlockStore();
        Block genesis = blockchain.getBestBlock();
        List<Block> blocks = new BlockGenerator().getBlockChain(genesis, 10);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

        for (int k = 0; k < 10; k++)
            processor.processBlock(null, blocks.get(9 - k));

        processor.processBlock(null, genesis);

        Assert.assertEquals(10, blockchain.getBestBlock().getNumber());
        Assert.assertEquals(0, store.size());
    }

    @Test
    public void processTenBlocksWithHoleAddingToBlockchain() {
        Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        NetBlockStore store = new NetBlockStore();
        Block genesis = blockchain.getBestBlock();
        List<Block> blocks = new BlockGenerator().getBlockChain(genesis, 10);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

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
        NetBlockStore store = new NetBlockStore();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        store.saveBlock(genesis);
        Blockchain blockchain = new BlockChainBuilder().ofSize(10);
        Block parent = blockGenerator.createChildBlock(blockchain.getBlockByNumber(10));
        Block block = blockGenerator.createChildBlock(parent);

        Assert.assertEquals(11, parent.getNumber());
        Assert.assertEquals(12, block.getNumber());
        Assert.assertArrayEquals(blockchain.getBestBlockHash(), parent.getParentHash().getBytes());

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

        processor.processBlock(null, block);

        Assert.assertTrue(store.hasBlock(block));
        Assert.assertNull(blockchain.getBlockByHash(block.getHash().getBytes()));

        processor.processBlock(null, parent);

        Assert.assertFalse(store.hasBlock(block));
        Assert.assertFalse(store.hasBlock(parent));

        Assert.assertEquals(12, blockchain.getBestBlock().getNumber());
        Assert.assertArrayEquals(block.getHash().getBytes(), blockchain.getBestBlockHash());
        Assert.assertEquals(1, store.size());
    }

    @Test
    public void processBlockRetrievingParentUsingSender() throws UnknownHostException {
        final NetBlockStore store = new NetBlockStore();
        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);
        final SimplePeer sender = new SimplePeer();

        BlockGenerator blockGenerator = new BlockGenerator();
        final Block genesis = blockGenerator.getGenesisBlock();
        final Block parent = blockGenerator.createChildBlock(genesis);
        final Block block = blockGenerator.createChildBlock(parent);

        processor.processBlock(sender, block);

        Assert.assertTrue(processor.getNodeInformation().getNodesByBlock(block.getHash().getBytes()).size() == 1);
        Assert.assertTrue(store.hasBlock(block));
        Assert.assertEquals(1, sender.getMessages().size());
        Assert.assertEquals(1, store.size());

        final Message message = sender.getMessages().get(0);

        Assert.assertNotNull(message);
        Assert.assertEquals(MessageType.GET_BLOCK_MESSAGE, message.getMessageType());

        final GetBlockMessage gbMessage = (GetBlockMessage) message;

        Assert.assertArrayEquals(block.getParentHash().getBytes(), gbMessage.getBlockHash());
    }

    @Test @Ignore("Ignored when Process status deleted on block processor")
    public void processStatusRetrievingBestBlockUsingSender() throws UnknownHostException {
        final NetBlockStore store = new NetBlockStore();
        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);
        final SimplePeer sender = new SimplePeer();

        BlockGenerator blockGenerator = new BlockGenerator();
        final Block genesis = blockGenerator.getGenesisBlock();
        final Block block = blockGenerator.createChildBlock(genesis);
//        final Status status = new Status(block.getNumber(), block.getHash());

//        processor.processStatus(sender, status);
        Assert.assertTrue(processor.getNodeInformation().getNodesByBlock(block.getHash().getBytes()).size() == 1);

        Assert.assertEquals(1, sender.getGetBlockMessages().size());

        final Message message = sender.getGetBlockMessages().get(0);

        Assert.assertNotNull(message);
        Assert.assertEquals(MessageType.GET_BLOCK_MESSAGE, message.getMessageType());

        final GetBlockMessage gbMessage = (GetBlockMessage) message;

        Assert.assertArrayEquals(block.getHash().getBytes(), gbMessage.getBlockHash());
        Assert.assertEquals(0, store.size());
    }

    @Test @Ignore("Ignored when Process status deleted on block processor")
    public void processStatusHavingBestBlockInStore() throws UnknownHostException {
        final NetBlockStore store = new NetBlockStore();
        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);
        final SimplePeer sender = new SimplePeer();

        BlockGenerator blockGenerator = new BlockGenerator();
        final Block genesis = blockGenerator.getGenesisBlock();
        final Block block = blockGenerator.createChildBlock(genesis);

        store.saveBlock(block);
//        final Status status = new Status(block.getNumber(), block.getHash());

//        processor.processStatus(sender, status);
        Assert.assertTrue(processor.getNodeInformation().getNodesByBlock(block.getHash().getBytes()).size() == 1);
        Assert.assertEquals(1, store.size());
    }

    @Test @Ignore("Ignored when Process status deleted on block processor")
    public void processStatusHavingBestBlockAsBestBlockInBlockchain() throws UnknownHostException {
        final NetBlockStore store = new NetBlockStore();
        final Blockchain blockchain = new BlockChainBuilder().ofSize(2);
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

        final SimplePeer sender = new SimplePeer();

        final Block block = blockchain.getBestBlock();
        final Keccak256 blockHash = block.getHash();

//        final Status status = new Status(block.getNumber(), block.getHash());

//        processor.processStatus(sender, status);
        Assert.assertTrue(processor.getNodeInformation().getNodesByBlock(block.getHash().getBytes()).size() == 1);
        Assert.assertTrue(processor.getNodeInformation().getNodesByBlock(block.getHash().getBytes()).contains(sender.getPeerNodeID()));

        Assert.assertEquals(0, sender.getGetBlockMessages().size());
        Assert.assertEquals(0, store.size());
    }

    @Test @Ignore("Ignored when Process status deleted on block processor")
    public void processStatusHavingBestBlockInBlockchainStore() throws UnknownHostException {
        final NetBlockStore store = new NetBlockStore();
        final Blockchain blockchain = new BlockChainBuilder().ofSize(2);
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

        final SimplePeer sender = new SimplePeer();

        final Block block = blockchain.getBlockByNumber(1);
        final Keccak256 blockHash = block.getHash();

        store.saveBlock(block);
//        final Status status = new Status(block.getNumber(), block.getHash());

        Assert.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).isEmpty());


//        processor.processStatus(sender, status);
        Assert.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).contains(sender.getPeerNodeID()));

        Assert.assertEquals(0, sender.getGetBlockMessages().size());
    }

    @Test
    public void processGetBlockHeaderMessageUsingBlockInStore() throws UnknownHostException {
        final Block block = new BlockGenerator().getBlock(3);

        final NetBlockStore store = new NetBlockStore();
        store.saveBlock(block);

        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

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
    public void processGetBlockHeaderMessageUsingEmptyStore() throws UnknownHostException {
        final Block block = new BlockGenerator().getBlock(3);
        final NetBlockStore store = new NetBlockStore();
        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

        final SimplePeer sender = new SimplePeer();

        Assert.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).isEmpty());

        processor.processBlockHeadersRequest(sender, 1, block.getHash().getBytes(), 1);

        Assert.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).isEmpty());

        Assert.assertTrue(sender.getMessages().isEmpty());
    }

    @Test
    public void processGetBlockHeaderMessageUsingBlockInBlockchain() throws UnknownHostException {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(10);
        final Block block = blockchain.getBlockByNumber(5);
        final NetBlockStore store = new NetBlockStore();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

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
    public void processGetBlockMessageUsingBlockInStore() throws UnknownHostException {
        final Block block = new BlockGenerator().getBlock(3);
        final Keccak256 blockHash = block.getHash();

        final NetBlockStore store = new NetBlockStore();
        store.saveBlock(block);

        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

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
    public void processGetBlockMessageUsingEmptyStore() throws UnknownHostException {
        final Block block = new BlockGenerator().getBlock(3);
        final NetBlockStore store = new NetBlockStore();
        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

        final SimplePeer sender = new SimplePeer();

        Assert.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).isEmpty());

        processor.processGetBlock(sender, block.getHash().getBytes());

        Assert.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).isEmpty());


        Assert.assertTrue(sender.getMessages().isEmpty());
    }

    @Test
    public void processGetBlockMessageUsingBlockInBlockchain() throws UnknownHostException {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(10);
        final Block block = blockchain.getBlockByNumber(5);
        final Keccak256 blockHash = block.getHash();
        final NetBlockStore store = new NetBlockStore();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

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
    public void processBlockRequestMessageUsingBlockInStore() throws UnknownHostException {
        final Block block = new BlockGenerator().getBlock(3);
        final Keccak256 blockHash = block.getHash();

        final NetBlockStore store = new NetBlockStore();
        store.saveBlock(block);

        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

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
    public void processBodyRequestMessageUsingBlockInBlockchain() throws UnknownHostException {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(10);
        final Block block = blockchain.getBlockByNumber(3);
        final NetBlockStore store = new NetBlockStore();
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

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
    public void processBlockHashRequestMessageUsingEmptyStore() throws UnknownHostException {
        final Block block = new BlockGenerator().getBlock(3);
        final Keccak256 blockHash = block.getHash();
        final NetBlockStore store = new NetBlockStore();
        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

        final SimplePeer sender = new SimplePeer();

        Assert.assertTrue(nodeInformation.getNodesByBlock(block.getHash()).isEmpty());

        processor.processBlockRequest(sender, 100, block.getHash().getBytes());

        Assert.assertFalse(nodeInformation.getNodesByBlock(block.getHash()).contains(sender.getPeerNodeID()));


        Assert.assertTrue(sender.getMessages().isEmpty());
    }

    @Test
    public void processBlockHashRequestMessageUsingBlockInBlockchain() throws UnknownHostException {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(10);
        final Block block = blockchain.getBlockByNumber(5);
        final Keccak256 blockHash = block.getHash();
        final NetBlockStore store = new NetBlockStore();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

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
    public void processBlockHashRequestMessageUsingOutOfBoundsHeight() throws UnknownHostException {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(10);
        final NetBlockStore store = new NetBlockStore();
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

        final SimplePeer sender = new SimplePeer();

        processor.processBlockHashRequest(sender, 100, 99999);

        Assert.assertTrue(sender.getMessages().isEmpty());
    }

    @Test
    public void processBlockHeadersRequestMessageUsingBlockInBlockchain() throws UnknownHostException {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(100);
        final Block block = blockchain.getBlockByNumber(60);
        final NetBlockStore store = new NetBlockStore();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

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

        for (int k = 0; k < 20; k++)
            Assert.assertEquals(blockchain.getBlockByNumber(60 - k).getHash(), response.getBlockHeaders().get(k).getHash());
    }

    @Test
    public void processBlockHeadersRequestMessageUsingUnknownHash() throws UnknownHostException {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(100);
        final NetBlockStore store = new NetBlockStore();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

        final SimplePeer sender = new SimplePeer();

        processor.processBlockHeadersRequest(sender, 100, HashUtil.randomHash(), 20);

        Assert.assertTrue(sender.getMessages().isEmpty());
    }

    @Test
    public void processSkeletonRequestWithGenesisPlusBestBlockInSkeleton() throws UnknownHostException {
        int skeletonStep = 192;
        final Blockchain blockchain = new BlockChainBuilder().ofSize(skeletonStep / 2);
        final Block blockStart = blockchain.getBlockByNumber(5);
        final Block blockEnd = blockchain.getBlockByNumber(skeletonStep / 2);
        final NetBlockStore store = new NetBlockStore();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

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
    public void processSkeletonRequestWithThreeResults() throws UnknownHostException {
        int skeletonStep = 192;
        final Blockchain blockchain = new BlockChainBuilder().ofSize(300);
        final NetBlockStore store = new NetBlockStore();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

        final SimplePeer sender = new SimplePeer();

        processor.processSkeletonRequest(sender, 100, 5);

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.SKELETON_RESPONSE_MESSAGE, message.getMessageType());

        final SkeletonResponseMessage bMessage = (SkeletonResponseMessage) message;

        Assert.assertEquals(100, bMessage.getId());

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
    public void processSkeletonRequestNotIncludingGenesis() throws UnknownHostException {
        int skeletonStep = 192;
        final Blockchain blockchain = new BlockChainBuilder().ofSize(400);
        final NetBlockStore store = new NetBlockStore();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

        final SimplePeer sender = new SimplePeer();

        processor.processSkeletonRequest(sender, 100, skeletonStep + 5);

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.SKELETON_RESPONSE_MESSAGE, message.getMessageType());

        final SkeletonResponseMessage bMessage = (SkeletonResponseMessage) message;

        Assert.assertEquals(100, bMessage.getId());

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
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

        final Integer size = syncConfiguration.getChunkSize() + 1;
        processor.processBlockHeadersRequest(sender, 1, block.getHash().getBytes(), size);

        verify(sender, never()).sendMessage(any());

    }
}
