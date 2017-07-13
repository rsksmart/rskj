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

import co.rsk.test.builders.BlockBuilder;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.net.simples.SimpleMessageSender;
import co.rsk.test.builders.BlockChainBuilder;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Blockchain;
import org.ethereum.core.ImportResult;
import org.ethereum.db.ByteArrayWrapper;
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
        NodeBlockProcessor processor = createNodeBlockProcessor();

        Block genesis = processor.getBlockchain().getBestBlock();

        Block block1 = new BlockBuilder().parent(genesis).build();

        processor.processBlock(null, block1);

        Assert.assertEquals(1, processor.getBlockchain().getBestBlock().getNumber());
        Assert.assertArrayEquals(block1.getHash(), processor.getBlockchain().getBestBlockHash());
    }

    @Test
    public void addBlockWithTwoKnownUncles() throws UnknownHostException {
        NodeBlockProcessor processor = createNodeBlockProcessor();

        Block genesis = processor.getBlockchain().getBestBlock();

        Block block1 = new BlockBuilder().parent(genesis).build();
        Block uncle1 = new BlockBuilder().parent(genesis).build();
        Block uncle2 = new BlockBuilder().parent(genesis).build();

        List<BlockHeader> uncles = new ArrayList<>();
        uncles.add(uncle1.getHeader());
        uncles.add(uncle2.getHeader());

        Block block2 = new BlockBuilder().parent(block1).uncles(uncles).build();

        processor.processBlock(null, block1);
        processor.processBlock(null, uncle1);
        processor.processBlock(null, uncle2);

        SimpleMessageSender sender = new SimpleMessageSender();

        processor.processBlock(sender, block2);

        Assert.assertEquals(2, processor.getBlockchain().getBestBlock().getNumber());
        Assert.assertArrayEquals(block2.getHash(), processor.getBlockchain().getBestBlockHash());
        Assert.assertTrue(sender.getGetBlockMessages().isEmpty());
    }

    @Test
    public void addBlockWithTwoUnknownUncles() throws UnknownHostException {
        NodeBlockProcessor processor = createNodeBlockProcessor();

        Block genesis = processor.getBlockchain().getBestBlock();

        Block block1 = new BlockBuilder().parent(genesis).build();
        Block uncle1 = new BlockBuilder().parent(genesis).build();
        Block uncle2 = new BlockBuilder().parent(genesis).build();

        List<BlockHeader> uncles = new ArrayList<>();
        uncles.add(uncle1.getHeader());
        uncles.add(uncle2.getHeader());

        Block block2 = new BlockBuilder().parent(block1).uncles(uncles).build();

        processor.processBlock(null, block1);

        SimpleMessageSender sender = new SimpleMessageSender();

        processor.processBlock(sender, block2);

        Assert.assertEquals(2, processor.getBlockchain().getBestBlock().getNumber());
        Assert.assertArrayEquals(block2.getHash(), processor.getBlockchain().getBestBlockHash());

        Assert.assertEquals(0, sender.getGetBlockMessages().size());
    }

    @Test
    public void rejectBlockWithTwoUnknownUnclesAndUnknownParent() throws UnknownHostException {
        NodeBlockProcessor processor = createNodeBlockProcessor();

        Block genesis = processor.getBlockchain().getBestBlock();

        Block block1 = new BlockBuilder().parent(genesis).build();
        Block uncle1 = new BlockBuilder().parent(genesis).build();
        Block uncle2 = new BlockBuilder().parent(genesis).build();

        List<BlockHeader> uncles = new ArrayList<>();
        uncles.add(uncle1.getHeader());
        uncles.add(uncle2.getHeader());

        Block block2 = new BlockBuilder().parent(block1).uncles(uncles).build();

        SimpleMessageSender sender = new SimpleMessageSender();

        processor.processBlock(sender, block2);

        Assert.assertEquals(0, processor.getBlockchain().getBestBlock().getNumber());
        Assert.assertArrayEquals(genesis.getHash(), processor.getBlockchain().getBestBlockHash());
        Assert.assertEquals(1, sender.getGetBlockMessages().size());
        Assert.assertTrue(sender.getGetBlockMessagesHashes().contains(new ByteArrayWrapper(block1.getHash())));
    }

    private static NodeBlockProcessor createNodeBlockProcessor() {
        Blockchain blockChain = new BlockChainBuilder().build();

        Block genesis = BlockGenerator.getGenesisBlock();
        genesis.setStateRoot(blockChain.getRepository().getRoot());
        genesis.flushRLP();

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));

        NodeBlockProcessor processor = new NodeBlockProcessor(new BlockStore(), blockChain);

        return processor;
    }
}
