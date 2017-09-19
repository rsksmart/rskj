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
import co.rsk.net.simples.SimpleAsyncNode;
import co.rsk.test.World;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * Created by ajlopez on 5/14/2016.
 */
public class TwoAsyncNodeTest {
    private static SimpleAsyncNode createNode(int size) {
        final World world = new World();
        final BlockStore store = new BlockStore();
        final Blockchain blockchain = world.getBlockChain();

        List<Block> blocks = BlockGenerator.getBlockChain(blockchain.getBestBlock(), size);

        for (Block b: blocks)
            blockchain.tryToConnect(b);

        BlockProcessor processor = new NodeBlockProcessor(store, blockchain);
        NodeMessageHandler handler = new NodeMessageHandler(processor, null, null, null, null).disablePoWValidation();

        handler.disablePoWValidation();

        return new SimpleAsyncNode(handler);
    }

    private static SimpleAsyncNode createNodeWithUncles(int size) {
        final World world = new World();
        final BlockStore store = new BlockStore();
        final Blockchain blockchain = world.getBlockChain();

        List<Block> blocks = BlockGenerator.getBlockChain(blockchain.getBestBlock(), size, 0, true);

        for (Block b: blocks)
            blockchain.tryToConnect(b);

        BlockProcessor processor = new NodeBlockProcessor(store, blockchain);
        NodeMessageHandler handler = new NodeMessageHandler(processor, null, null, null, null).disablePoWValidation();

        handler.disablePoWValidation();

        return new SimpleAsyncNode(handler);
    }

    private static Block getGenesis() {
        final World world = new World();

        return world.getBlockChain().getBestBlock();
    }

    @Test
    public void buildBlockchainAndSynchronize() throws InterruptedException {
        SimpleAsyncNode node1 = createNode(100);
        SimpleAsyncNode node2 = createNode(0);

        // TODO better synchronization
        Thread.sleep(1000);

        node1.sendStatus(node2);

        // TODO better synchronization
        Thread.sleep(2000);

        node1.stop();
        node2.stop();

        Assert.assertEquals(100, node1.getBestBlock().getNumber());
        Assert.assertEquals(100, node2.getBestBlock().getNumber());
        Assert.assertArrayEquals(node1.getBestBlock().getHash(), node2.getBestBlock().getHash());
    }

    @Test
    public void buildBlockchainWithUnclesAndSynchronize() throws InterruptedException {
        SimpleAsyncNode node1 = createNodeWithUncles(10);
        SimpleAsyncNode node2 = createNode(0);

        // TODO better synchronization
        Thread.sleep(1000);

        node1.sendStatus(node2);
        node2.sendStatus(node1);

        // TODO better synchronization
        Thread.sleep(2000);

        node1.stop();
        node2.stop();

        Assert.assertEquals(10, node1.getBestBlock().getNumber());
        Assert.assertEquals(10, node2.getBestBlock().getNumber());
        Assert.assertArrayEquals(node1.getBestBlock().getHash(), node2.getBestBlock().getHash());
    }

    @Test
    public void buildBlockchainPartialAndSynchronize() throws InterruptedException {
        SimpleAsyncNode node1 = createNode(0);
        SimpleAsyncNode node2 = createNode(0);

        List<Block> blocks = BlockGenerator.getBlockChain(getGenesis(), 10);

        for (Block block : blocks) {
            BlockMessage message = new BlockMessage(block);
            node1.sendMessage(null, message);

            if (block.getNumber() <= 5)
                node2.sendMessage(null, message);
        }

        // TODO better synchronization
        Thread.sleep(1000);

        node1.sendStatus(node2);

        // TODO better synchronization
        Thread.sleep(2000);

        node1.stop();
        node2.stop();

        Assert.assertEquals(10, node1.getBestBlock().getNumber());
        Assert.assertEquals(10, node2.getBestBlock().getNumber());
        Assert.assertArrayEquals(node1.getBestBlock().getHash(), node2.getBestBlock().getHash());
    }
}
