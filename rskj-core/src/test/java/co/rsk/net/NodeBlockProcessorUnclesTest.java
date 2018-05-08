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
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.net.simples.SimpleMessageChannel;
import co.rsk.net.sync.SyncConfiguration;
import co.rsk.test.builders.BlockBuilder;
import co.rsk.test.builders.BlockChainBuilder;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.ImportResult;
import org.junit.Assert;
import org.junit.Test;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ajlopez on 16/08/2016.
 */
public class NodeBlockProcessorUnclesTest {
    @Test
    public void addBlockWithoutUncles() {
        NodeBlockProcessor processor = createNodeBlockProcessor(new BlockChainBuilder().build());

        Block genesis = processor.getBlockchain().getBestBlock();

        Block block1 = new BlockBuilder().parent(genesis).build();

        processor.processBlock(null, block1);

        Assert.assertEquals(1, processor.getBlockchain().getBestBlock().getNumber());
        Assert.assertArrayEquals(block1.getHash().getBytes(), processor.getBlockchain().getBestBlockHash());
    }

    @Test
    public void addBlockWithTwoKnownUncles() throws UnknownHostException {
        BlockChainImpl blockChain = new BlockChainBuilder().build();
        NodeBlockProcessor processor = createNodeBlockProcessor(blockChain);

        Block genesis = blockChain.getBestBlock();

        BlockBuilder blockBuilder = new BlockBuilder(blockChain, new BlockGenerator());
        Block block1 = blockBuilder.parent(genesis).build();
        Block uncle1 = blockBuilder.parent(genesis).build();
        Block uncle2 = blockBuilder.parent(genesis).build();

        List<BlockHeader> uncles = new ArrayList<>();
        uncles.add(uncle1.getHeader());
        uncles.add(uncle2.getHeader());

        Block block2 = blockBuilder.parent(block1).uncles(uncles).build();

        processor.processBlock(null, block1);
        processor.processBlock(null, uncle1);
        processor.processBlock(null, uncle2);

        SimpleMessageChannel sender = new SimpleMessageChannel();

        processor.processBlock(sender, block2);

        Assert.assertEquals(2, processor.getBlockchain().getBestBlock().getNumber());
        Assert.assertArrayEquals(block2.getHash().getBytes(), processor.getBlockchain().getBestBlockHash());
        Assert.assertTrue(sender.getGetBlockMessages().isEmpty());
    }

    @Test
    public void addBlockWithTwoUnknownUncles() throws UnknownHostException {
        BlockChainImpl blockChain = new BlockChainBuilder().build();
        NodeBlockProcessor processor = createNodeBlockProcessor(blockChain);

        Block genesis = processor.getBlockchain().getBestBlock();

        BlockBuilder blockBuilder = new BlockBuilder(blockChain, new BlockGenerator());
        Block block1 = blockBuilder.parent(genesis).build();
        Block uncle1 = blockBuilder.parent(genesis).build();
        Block uncle2 = blockBuilder.parent(genesis).build();

        List<BlockHeader> uncles = new ArrayList<>();
        uncles.add(uncle1.getHeader());
        uncles.add(uncle2.getHeader());

        Block block2 = blockBuilder.parent(block1).uncles(uncles).build();

        processor.processBlock(null, block1);

        SimpleMessageChannel sender = new SimpleMessageChannel();

        processor.processBlock(sender, block2);

        Assert.assertEquals(2, processor.getBlockchain().getBestBlock().getNumber());
        Assert.assertArrayEquals(block2.getHash().getBytes(), processor.getBlockchain().getBestBlockHash());

        Assert.assertEquals(0, sender.getGetBlockMessages().size());
    }

    @Test
    public void rejectBlockWithTwoUnknownUnclesAndUnknownParent() throws UnknownHostException {
        NodeBlockProcessor processor = createNodeBlockProcessor(new BlockChainBuilder().build());

        Block genesis = processor.getBlockchain().getBestBlock();

        Block block1 = new BlockBuilder().parent(genesis).build();
        Block uncle1 = new BlockBuilder().parent(genesis).build();
        Block uncle2 = new BlockBuilder().parent(genesis).build();

        List<BlockHeader> uncles = new ArrayList<>();
        uncles.add(uncle1.getHeader());
        uncles.add(uncle2.getHeader());

        Block block2 = new BlockBuilder().parent(block1).uncles(uncles).build();

        SimpleMessageChannel sender = new SimpleMessageChannel();

        processor.processBlock(sender, block2);

        Assert.assertEquals(0, processor.getBlockchain().getBestBlock().getNumber());
        Assert.assertArrayEquals(genesis.getHash().getBytes(), processor.getBlockchain().getBestBlockHash());
        Assert.assertEquals(1, sender.getGetBlockMessages().size());
        Assert.assertTrue(sender.getGetBlockMessagesHashes().contains(block1.getHash()));
    }

    private static NodeBlockProcessor createNodeBlockProcessor(BlockChainImpl blockChain) {
        Block genesis = new BlockGenerator().getGenesisBlock();
        genesis.setStateRoot(blockChain.getRepository().getRoot());
        genesis.flushRLP();

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));

        BlockStore store = new BlockStore();
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockChain, nodeInformation, syncConfiguration);
        NodeBlockProcessor processor = new NodeBlockProcessor(store, blockChain, nodeInformation, blockSyncService, syncConfiguration);

        return processor;
    }
}
