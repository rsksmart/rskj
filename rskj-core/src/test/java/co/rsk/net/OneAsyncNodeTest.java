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
import co.rsk.core.DifficultyCalculator;
import co.rsk.core.bc.ConsensusValidationMainchainView;
import co.rsk.net.messages.BlockMessage;
import co.rsk.net.simples.SimpleAsyncNode;
import co.rsk.net.sync.PeersInformation;
import co.rsk.net.sync.SyncConfiguration;
import co.rsk.test.World;
import co.rsk.validators.*;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Genesis;
import org.ethereum.listener.EthereumListener;
import org.ethereum.rpc.Simples.SimpleChannelManager;
import org.ethereum.util.RskMockFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;

/**
 * Created by ajlopez on 5/14/2016.
 */
class OneAsyncNodeTest {
    private static SimpleAsyncNode createNode() {
        final World world = new World();
        final NetBlockStore store = new NetBlockStore();
        final Blockchain blockchain = world.getBlockChain();

        TestSystemProperties config = new TestSystemProperties();
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);
        SimpleChannelManager channelManager = new SimpleChannelManager();
        SyncProcessor syncProcessor = new SyncProcessor(
                blockchain, mock(org.ethereum.db.BlockStore.class), mock(ConsensusValidationMainchainView.class), blockSyncService, syncConfiguration,
                new BlockFactory(config.getActivationConfig()),
                new DummyBlockValidationRule(),
                new SyncBlockValidatorRule(new BlockUnclesHashValidationRule(), new BlockRootValidationRule(config.getActivationConfig())),
                new DifficultyCalculator(config.getActivationConfig(), config.getNetworkConstants()),
                new PeersInformation(channelManager, syncConfiguration, blockchain, RskMockFactory.getPeerScoringManager(), config.getPercentageOfPeersToConsiderInRandomSelection()),
                mock(Genesis.class),
                mock(EthereumListener.class)
        );
        NodeMessageHandler handler = new NodeMessageHandler(config, processor, syncProcessor, channelManager, null, RskMockFactory.getPeerScoringManager(), mock(StatusResolver.class));

        return new SimpleAsyncNode(handler, blockchain, syncProcessor, channelManager);
    }

    private static Block getGenesis() {
        final World world = new World();

        return world.getBlockChain().getBestBlock();
    }

    @Test
    void buildBlockchain() {
        SimpleAsyncNode node = createNode();

        List<Block> blocks = new BlockGenerator().getBlockChain(getGenesis(), 10);

        for (Block block : blocks)
            node.receiveMessageFrom(null, new BlockMessage(block));

        node.waitExactlyNTasksWithTimeout(10);
        node.joinWithTimeout();

        Assertions.assertEquals(blocks.size(), node.getBestBlock().getNumber());
        Assertions.assertEquals(blocks.get(blocks.size() - 1).getHash(), node.getBestBlock().getHash());
    }

    @Test
    void buildBlockchainInReverse() {
        SimpleAsyncNode node = createNode();

        List<Block> blocks = new BlockGenerator().getBlockChain(getGenesis(), 10);

        List<Block> reverse = new ArrayList<>();

        for (Block block : blocks)
            reverse.add(0, block);

        for (Block block : reverse)
            node.receiveMessageFrom(null, new BlockMessage(block));

        node.waitExactlyNTasksWithTimeout(10);
        node.joinWithTimeout();

        Assertions.assertEquals(blocks.size(), node.getBestBlock().getNumber());
        Assertions.assertEquals(blocks.get(blocks.size() - 1).getHash(), node.getBestBlock().getHash());
    }
}
