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

import co.rsk.config.TestSystemProperties;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.net.simples.SimplePeer;
import co.rsk.net.sync.SyncConfiguration;
import co.rsk.net.utils.AsyncNodeBlockProcessorListener;
import co.rsk.test.builders.BlockBuilder;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.validators.DummyBlockValidator;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class AsyncNodeBlockProcessorUnclesTest {

    private static final long WAIT_TIME = Long.MAX_VALUE;

    private BlockChainBuilder blockChainBuilder;
    private BlockChainImpl blockChain;
    private AsyncNodeBlockProcessor processor;
    private AsyncNodeBlockProcessorListener listener;

    @Before
    public void setUp() {
        blockChainBuilder = new BlockChainBuilder();
        blockChain = blockChainBuilder.build();
        listener = new AsyncNodeBlockProcessorListener();
        processor = createAsyncNodeBlockProcessor(blockChain, listener);
        processor.start();
    }

    @After
    public void tearDown() throws InterruptedException {
        processor.stopAndWait(WAIT_TIME);
    }

    @Test(timeout = WAIT_TIME)
    public void addBlockWithoutUncles() throws InterruptedException {
        Block genesis = blockChain.getBestBlock();

        Block block1 = new BlockBuilder(null, null, null).parent(genesis).build();

        processor.processBlock(null, block1);
        listener.waitForBlock(block1.getHash());

        Assert.assertEquals(1, blockChain.getBestBlock().getNumber());
        Assert.assertArrayEquals(block1.getHash().getBytes(), blockChain.getBestBlockHash());
    }

    @Test(timeout = WAIT_TIME)
    public void addBlockWithTwoKnownUncles() throws InterruptedException {
        org.ethereum.db.BlockStore blockStore = blockChainBuilder.getBlockStore();

        Block genesis = blockChain.getBestBlock();

        BlockBuilder blockBuilder = new BlockBuilder(blockChain, null, blockStore)
                .trieStore(blockChainBuilder.getTrieStore());
        blockBuilder.parent(blockChain.getBestBlock());
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

        SimplePeer sender = new SimplePeer();

        processor.processBlock(sender, block2);
        listener.waitForBlock(block2.getHash());

        Assert.assertEquals(2, blockChain.getBestBlock().getNumber());
        Assert.assertArrayEquals(block2.getHash().getBytes(), blockChain.getBestBlockHash());
        Assert.assertTrue(sender.getGetBlockMessages().isEmpty());
    }

    @Test(timeout = WAIT_TIME)
    public void addBlockWithTwoUnknownUncles() throws InterruptedException {
        org.ethereum.db.BlockStore blockStore = blockChainBuilder.getBlockStore();

        Block genesis = blockChain.getBestBlock();

        BlockBuilder blockBuilder = new BlockBuilder(blockChain, null, blockStore)
                .trieStore(blockChainBuilder.getTrieStore());
        blockBuilder.parent(blockChain.getBestBlock());
        Block block1 = blockBuilder.parent(genesis).build();
        Block uncle1 = blockBuilder.parent(genesis).build();
        Block uncle2 = blockBuilder.parent(genesis).build();

        List<BlockHeader> uncles = new ArrayList<>();
        uncles.add(uncle1.getHeader());
        uncles.add(uncle2.getHeader());

        Block block2 = blockBuilder.parent(block1).uncles(uncles).build();

        processor.processBlock(null, block1);

        SimplePeer sender = new SimplePeer();

        processor.processBlock(sender, block2);
        listener.waitForBlock(block2.getHash());

        Assert.assertEquals(2, blockChain.getBestBlock().getNumber());
        Assert.assertArrayEquals(block2.getHash().getBytes(), blockChain.getBestBlockHash());

        Assert.assertEquals(0, sender.getGetBlockMessages().size());
    }

    @Test
    public void rejectBlockWithTwoUnknownUnclesAndUnknownParent() throws InterruptedException {
        Block genesis = blockChain.getBestBlock();

        Block block1 = new BlockBuilder(null, null, null).parent(genesis).build();
        Block uncle1 = new BlockBuilder(null, null, null).parent(genesis).build();
        Block uncle2 = new BlockBuilder(null, null, null).parent(genesis).build();

        List<BlockHeader> uncles = new ArrayList<>();
        uncles.add(uncle1.getHeader());
        uncles.add(uncle2.getHeader());

        Block block2 = new BlockBuilder(null, null, null)
                .parent(block1).uncles(uncles).build();

        SimplePeer sender = new SimplePeer();

        processor.processBlock(sender, block2);

        Assert.assertEquals(0, blockChain.getBestBlock().getNumber());
        Assert.assertArrayEquals(genesis.getHash().getBytes(), blockChain.getBestBlockHash());
        Assert.assertEquals(1, sender.getGetBlockMessages().size());
        Assert.assertTrue(sender.getGetBlockMessagesHashes().contains(block1.getHash()));
    }

    private static AsyncNodeBlockProcessor createAsyncNodeBlockProcessor(BlockChainImpl blockChain, AsyncNodeBlockProcessorListener listener) {
        NetBlockStore store = new NetBlockStore();
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockChain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);

        return new AsyncNodeBlockProcessor(store, blockChain, nodeInformation, blockSyncService, syncConfiguration,
                DummyBlockValidator.VALID_RESULT_INSTANCE, DummyBlockValidator.VALID_RESULT_INSTANCE, listener);
    }
}
