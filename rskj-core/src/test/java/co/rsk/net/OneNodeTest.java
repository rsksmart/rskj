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
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ajlopez on 5/14/2016.
 */
public class OneNodeTest {
    private static Block getGenesis() {
        final World world = new World();

        return world.getBlockChain().getBestBlock();
    }

    @Test
    public void buildBlockchain() {
        SimpleNode node = SimpleNode.createNode();

        List<Block> blocks = BlockGenerator.getInstance().getBlockChain(getGenesis(), 10);

        for (Block block : blocks)
            node.receiveMessageFrom(null, new BlockMessage(block));

        Assert.assertEquals(blocks.size(), node.getBestBlock().getNumber());
        Assert.assertArrayEquals(blocks.get(blocks.size() - 1).getHash(), node.getBestBlock().getHash());
    }

    @Test
    public void buildBlockchainInReverse() {
        SimpleNode node = SimpleNode.createNode();

        List<Block> blocks = BlockGenerator.getInstance().getBlockChain(getGenesis(), 10);
        List<Block> reverse = new ArrayList<>();

        for (Block block : blocks)
            reverse.add(0, block);

        for (Block block : reverse)
            node.receiveMessageFrom(null, new BlockMessage(block));

        Assert.assertEquals(blocks.size(), node.getBestBlock().getNumber());
        Assert.assertArrayEquals(blocks.get(blocks.size() - 1).getHash(), node.getBestBlock().getHash());
    }
}
