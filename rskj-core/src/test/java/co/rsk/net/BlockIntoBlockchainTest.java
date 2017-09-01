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

import co.rsk.blockchain.utils.BlockchainBuilder;
import co.rsk.blockchain.utils.BlockGenerator;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ajlopez on 5/13/2016.
 */
public class BlockIntoBlockchainTest {
    @Test
    @Ignore
    public void sendBlockMessageAndAddItToBlockchain() {
        List<Block> storechain = BlockGenerator.getBlockChain(11);
        List<Block> chain = BlockGenerator.getBlockChain(10);

        for (int k = 1; k <= storechain.size(); k++) {
            BlockStore store = createBlockStore(storechain, k);
            Blockchain blockchain = createBlockchain(chain);
            BlockNodeInformation nodeInformation = new BlockNodeInformation();
            BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
            final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);
            Assert.assertEquals(10, blockchain.getBestBlock().getNumber());

            for (int j = k; j > 0; j--)
                processor.processBlock(null, storechain.get(j - 1));

            if (k > 10) {
                Assert.assertEquals(k, blockchain.getBestBlock().getNumber());
                Assert.assertArrayEquals(storechain.get(k - 1).getHash(), blockchain.getBestBlockHash());
            }
        }
    }

    @Test
    @Ignore
    public void sendBlockMessageAndAddItToBlockchainWithCommonAncestors() {
        List<Block> commonchain = BlockGenerator.getBlockChain(10);
        List<Block> storechain = BlockGenerator.getBlockChain(commonchain.get(9), 11);
        List<Block> chain = BlockGenerator.getBlockChain(commonchain.get(9), 10);

        for (int k = 1; k <= storechain.size(); k++) {
            BlockStore store = createBlockStore(storechain, k + commonchain.size());
            List<Block> bcchain = new ArrayList<>();
            bcchain.addAll(commonchain);
            bcchain.addAll(chain);
            Blockchain blockchain = createBlockchain(bcchain);
            BlockNodeInformation nodeInformation = new BlockNodeInformation();
            BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
            final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);

            processor.processBlock(null, storechain.get(k - 1));

            Assert.assertEquals(storechain.size() + commonchain.size(), blockchain.getBestBlock().getNumber());
            Assert.assertArrayEquals(storechain.get(storechain.size() - 1).getHash(), blockchain.getBestBlockHash());
        }
    }

    private static BlockStore createBlockStore(List<Block> blocks, int skip) {
        BlockStore store = new BlockStore();

        for (Block block : blocks)
            if (block.getNumber() != skip)
                store.saveBlock(block);

        return store;
    }

    private static Blockchain createBlockchain(List<Block> blocks) {
        return new BlockchainBuilder().setTesting(true).setRsk(true).setBlocks(blocks).build();
    }
}
