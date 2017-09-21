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
import co.rsk.net.messages.BlockMessage;
import co.rsk.net.simples.SimpleNode;
import co.rsk.test.World;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ajlopez on 5/14/2016.
 */
public class OneNodeTest {
    private static SimpleNode createNode() {
        final World world = new World();
        final BlockStore store = new BlockStore();
        final Blockchain blockchain = world.getBlockChain();

        BlockProcessor processor = new NodeBlockProcessor(store, blockchain);
        NodeMessageHandler handler = new NodeMessageHandler(processor, null, null, null, null).disablePoWValidation();

        return new SimpleNode(handler);
    }

    private static Block getGenesis() {
        final World world = new World();

        return world.getBlockChain().getBestBlock();
    }

    @Test
    public void buildBlockchain() {
        SimpleNode node = createNode();

        List<Block> blocks = BlockGenerator.getBlockChain(getGenesis(), 10);

        for (Block block : blocks)
            node.sendMessage(null, new BlockMessage(block));

        Assert.assertEquals(blocks.size(), node.getBestBlock().getNumber());
        Assert.assertArrayEquals(blocks.get(blocks.size() - 1).getHash(), node.getBestBlock().getHash());
    }

    @Test
    public void buildBlockchainInReverse() {
        SimpleNode node = createNode();

        List<Block> blocks = BlockGenerator.getBlockChain(getGenesis(), 10);
        List<Block> reverse = new ArrayList<>();

        for (Block block : blocks)
            reverse.add(0, block);

        for (Block block : reverse)
            node.sendMessage(null, new BlockMessage(block));

        Assert.assertEquals(blocks.size(), node.getBestBlock().getNumber());
        Assert.assertArrayEquals(blocks.get(blocks.size() - 1).getHash(), node.getBestBlock().getHash());
    }
}
