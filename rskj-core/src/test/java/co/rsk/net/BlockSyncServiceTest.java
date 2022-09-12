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
import co.rsk.net.sync.SyncConfiguration;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.validators.DummyBlockValidator;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

class BlockSyncServiceTest {
    @Test
    void sendBlockMessagesAndAddThemToBlockchain() {
        for (int i = 0; i < 50; i += 5) {
            Blockchain blockchain = new BlockChainBuilder().ofSize(10 * i);
            NetBlockStore store = new NetBlockStore();
            BlockNodeInformation nodeInformation = new BlockNodeInformation();
            TestSystemProperties config = new TestSystemProperties();
            BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING, DummyBlockValidator.VALID_RESULT_INSTANCE);
            Assertions.assertEquals(10 * i, blockchain.getBestBlock().getNumber());

            List<Block> extendedChain = new BlockGenerator().getBlockChain(blockchain.getBestBlock(), i);
            for (Block block : extendedChain) {
                blockSyncService.processBlock(block, null, false);
                Assertions.assertEquals(block.getNumber(), blockchain.getBestBlock().getNumber());
                Assertions.assertEquals(block.getHash(), blockchain.getBestBlock().getHash());
            }
        }
    }

    @Test
    void sendBlockMessagesAndAddThemToBlockchainInReverseOrder() {
        for (int i = 1; i < 52; i += 5) {
            Blockchain blockchain = new BlockChainBuilder().ofSize(10 * i);
            NetBlockStore store = new NetBlockStore();
            BlockNodeInformation nodeInformation = new BlockNodeInformation();
            TestSystemProperties config = new TestSystemProperties();
            BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING, DummyBlockValidator.VALID_RESULT_INSTANCE);
            Assertions.assertEquals(10 * i, blockchain.getBestBlock().getNumber());

            Block initialBestBlock = blockchain.getBestBlock();
            List<Block> extendedChain = new BlockGenerator().getBlockChain(blockchain.getBestBlock(), i);
            Collections.reverse(extendedChain);
            for (int j = 0; j < extendedChain.size() - 1; j++) {
                Block block = extendedChain.get(j);
                blockSyncService.processBlock(block, null, false);
                // we don't have all the parents, so we wait to update the best chain
                Assertions.assertEquals(initialBestBlock.getNumber(), blockchain.getBestBlock().getNumber());
                Assertions.assertEquals(initialBestBlock.getHash(), blockchain.getBestBlock().getHash());
            }

            // the chain is complete, we have a new best block
            Block closingBlock = extendedChain.get(extendedChain.size() - 1);
            Block newBestBlock = extendedChain.get(0);
            blockSyncService.processBlock(closingBlock, null, false);
            Assertions.assertEquals(newBestBlock.getNumber(), blockchain.getBestBlock().getNumber());
            Assertions.assertEquals(newBestBlock.getHash(), blockchain.getBestBlock().getHash());
        }
    }

    @Test
    void sendBlockMessageAndAddItToBlockchainWithCommonAncestors() {
        Blockchain blockchain = new BlockChainBuilder().ofSize(10);
        NetBlockStore store = new NetBlockStore();
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING, DummyBlockValidator.VALID_RESULT_INSTANCE);

        Block initialBestBlock = blockchain.getBestBlock();
        Assertions.assertEquals(10, initialBestBlock.getNumber());
        Block branchingPoint = blockchain.getBlockByNumber(7);

        BlockGenerator blockGenerator = new BlockGenerator();
        List<Block> extendedChain = blockGenerator.getBlockChain(branchingPoint, 10, 1000000l);
        // we have just surpassed the best branch
        for (int i = 0; i < extendedChain.size(); i++) {
            Block newBestBlock = extendedChain.get(i);
            blockSyncService.processBlock(newBestBlock, null, false);
            Assertions.assertEquals(newBestBlock.getNumber(), blockchain.getBestBlock().getNumber());
            Assertions.assertEquals(newBestBlock.getHash(), blockchain.getBestBlock().getHash());
        }
    }
}
