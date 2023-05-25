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
import co.rsk.net.messages.BlockMessage;
import co.rsk.net.simples.SimpleAsyncNode;
import co.rsk.net.sync.SyncConfiguration;
import co.rsk.test.World;
import co.rsk.validators.DummyBlockValidator;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;

/**
 * Created by ajlopez on 5/14/2016.
 */
class TwoAsyncNodeTest {

    private static final TestSystemProperties config = new TestSystemProperties();

    private static SimpleAsyncNode createNode(int size) {
        final World world = new World();
        final NetBlockStore store = new NetBlockStore();
        final Blockchain blockchain = world.getBlockChain();

        List<Block> blocks = new BlockGenerator().getBlockChain(blockchain.getBestBlock(), size);

        for (Block b: blocks)
            blockchain.tryToConnect(b);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);
        NodeMessageHandler handler = new NodeMessageHandler(config, processor, null, null, null, null, mock(StatusResolver.class));

        return new SimpleAsyncNode(handler, blockchain);
    }

    private static SimpleAsyncNode createNodeWithUncles(int size) {
        final World world = new World();
        final NetBlockStore store = new NetBlockStore();
        final Blockchain blockchain = world.getBlockChain();

        List<Block> blocks = new BlockGenerator().getBlockChain(blockchain.getBestBlock(), size, 0, true);

        for (Block b: blocks)
            blockchain.tryToConnect(b);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, null);
        NodeMessageHandler handler = new NodeMessageHandler(config, processor, null, null, null, null, mock(StatusResolver.class));

        return new SimpleAsyncNode(handler, blockchain);
    }

    private static Block getGenesis() {
        final World world = new World();

        return world.getBlockChain().getBestBlock();
    }

    @Test @Disabled("This should be reviewed with sync processor or deleted")
    void buildBlockchainAndSynchronize() throws InterruptedException {
        SimpleAsyncNode node1 = createNode(100);
        SimpleAsyncNode node2 = createNode(0);

        node1.sendStatusTo(node2);
        // status
        node2.waitUntilNTasksWithTimeout(1);
        // get blocks
        node2.waitExactlyNTasksWithTimeout(100);

        node1.joinWithTimeout();
        node2.joinWithTimeout();

        Assertions.assertEquals(100, node1.getBestBlock().getNumber());
        Assertions.assertEquals(100, node2.getBestBlock().getNumber());
        Assertions.assertEquals(node1.getBestBlock().getHash(), node2.getBestBlock().getHash());
    }

    @Test @Disabled("This should be reviewed with sync processor or deleted")
    void buildBlockchainWithUnclesAndSynchronize() throws InterruptedException {
        SimpleAsyncNode node1 = createNodeWithUncles(10);
        SimpleAsyncNode node2 = createNode(0);

        node1.sendStatusTo(node2);
        // status
        node2.waitUntilNTasksWithTimeout(1);
        // get blocks
        node2.waitExactlyNTasksWithTimeout(10);

        node2.sendStatusTo(node1);
        // status
        node1.waitUntilNTasksWithTimeout(1);
        // get blocks
        node1.waitExactlyNTasksWithTimeout(10);

        node1.joinWithTimeout();
        node2.joinWithTimeout();

        Assertions.assertEquals(10, node1.getBestBlock().getNumber());
        Assertions.assertEquals(10, node2.getBestBlock().getNumber());
        Assertions.assertEquals(node1.getBestBlock().getHash(), node2.getBestBlock().getHash());
    }

    @Test @Disabled("This should be reviewed with sync processor or deleted")
    void buildBlockchainPartialAndSynchronize() throws InterruptedException {
        SimpleAsyncNode node1 = createNode(0);
        SimpleAsyncNode node2 = createNode(0);

        List<Block> blocks = new BlockGenerator().getBlockChain(getGenesis(), 10);

        for (Block block : blocks) {
            BlockMessage message = new BlockMessage(block);
            node1.receiveMessageFrom(null, message);
            node1.waitExactlyNTasksWithTimeout(1);

            if (block.getNumber() <= 5) {
                node2.receiveMessageFrom(null, message);
                node2.waitExactlyNTasksWithTimeout(1);
            }
        }

        node1.sendStatusTo(node2);
        node2.waitUntilNTasksWithTimeout(1);
        node1.waitExactlyNTasksWithTimeout(5);

        node1.joinWithTimeout();
        node2.joinWithTimeout();

        Assertions.assertEquals(10, node1.getBestBlock().getNumber());
        Assertions.assertEquals(10, node2.getBestBlock().getNumber());
        Assertions.assertEquals(node1.getBestBlock().getHash(), node2.getBestBlock().getHash());
    }
}
