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
import co.rsk.core.Coin;
import com.google.common.collect.Lists;
import org.ethereum.TestUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Bloom;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ajlopez on 5/11/2016.
 */
class NetBlockStoreTest {
    private static final BlockFactory blockFactory = new BlockFactory(ActivationConfigsForTest.all());

    @Test
    void getUnknownBlockAsNull() {
        NetBlockStore store = new NetBlockStore();
        Assertions.assertNull(store.getBlockByHash(TestUtils.randomBytes(32)));
    }

    @Test
    void minimalAndMaximumHeightInEmptyStore() {
        NetBlockStore store = new NetBlockStore();

        Assertions.assertEquals(0, store.minimalHeight());
        Assertions.assertEquals(0, store.maximumHeight());
    }

    @Test
    void saveAndGetBlockByHash() {
        NetBlockStore store = new NetBlockStore();
        Block block = new BlockGenerator().getGenesisBlock();

        store.saveBlock(block);

        Assertions.assertSame(block, store.getBlockByHash(block.getHash().getBytes()));
        Assertions.assertEquals(0, store.minimalHeight());
        Assertions.assertEquals(0, store.maximumHeight());
    }

    @Test
    void saveRemoveAndGetBlockByHash() {
        NetBlockStore store = new NetBlockStore();
        Block block = new BlockGenerator().getBlock(1);

        store.saveBlock(block);

        Assertions.assertEquals(1, store.minimalHeight());
        Assertions.assertEquals(1, store.maximumHeight());

        store.removeBlock(block);

        Assertions.assertNull(store.getBlockByHash(block.getHash().getBytes()));
        Assertions.assertTrue(store.getBlocksByNumber(block.getNumber()).isEmpty());
        Assertions.assertTrue(store.getBlocksByParentHash(block.getParentHash()).isEmpty());
        Assertions.assertEquals(0, store.size());
    }

    @Test
    void saveRemoveAndGetBlockByHashWithUncles() {
        NetBlockStore store = new NetBlockStore();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block parent = blockGenerator.getGenesisBlock();
        Block son1 = blockGenerator.createChildBlock(parent);
        Block son2 = blockGenerator.createChildBlock(parent);
        Block grandson = blockGenerator.createChildBlock(son1, new ArrayList<>(), Lists.newArrayList(son2.getHeader()), 1, BigInteger.ONE);

        store.saveBlock(son1);
        store.saveBlock(son2);
        store.saveBlock(grandson);

        Assertions.assertEquals(1, store.minimalHeight());
        Assertions.assertEquals(2, store.maximumHeight());

        store.removeBlock(grandson);

        Assertions.assertNull(store.getBlockByHash(grandson.getHash().getBytes()));
        Assertions.assertTrue(store.getBlocksByNumber(grandson.getNumber()).isEmpty());
        Assertions.assertTrue(store.getBlocksByParentHash(son1.getHash()).isEmpty());
        Assertions.assertTrue(store.getBlocksByParentHash(son2.getHash()).isEmpty());
        Assertions.assertEquals(2, store.size());
    }

    @Test
    void saveTwoBlocksRemoveOne() {
        NetBlockStore store = new NetBlockStore();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block parent = blockGenerator.getGenesisBlock();
        Block adam = blockGenerator.createChildBlock(parent);
        Block eve = blockGenerator.createChildBlock(adam);

        store.saveBlock(adam);
        store.saveBlock(eve);

        Assertions.assertEquals(1, store.minimalHeight());
        Assertions.assertEquals(2, store.maximumHeight());

        store.removeBlock(adam);

        Assertions.assertNull(store.getBlockByHash(adam.getHash().getBytes()));
        Assertions.assertEquals(1, store.size());
        Assertions.assertEquals(2, store.minimalHeight());
        Assertions.assertEquals(2, store.maximumHeight());

        List<Block> childrenByNumber = store.getBlocksByNumber(eve.getNumber());

        Assertions.assertNotNull(childrenByNumber);
        Assertions.assertEquals(1, childrenByNumber.size());

        Assertions.assertEquals(eve.getHash(), childrenByNumber.get(0).getHash());

        List<Block> childrenByParent = store.getBlocksByParentHash(adam.getHash());

        Assertions.assertNotNull(childrenByParent);
        Assertions.assertEquals(1, childrenByParent.size());

        Assertions.assertEquals(eve.getHash(), childrenByParent.get(0).getHash());

        Block daugther = store.getBlockByHash(eve.getHash().getBytes());

        Assertions.assertNotNull(daugther);
        Assertions.assertEquals(eve.getHash(), daugther.getHash());
    }

    @Test
    void saveAndGetBlocksByNumber() {
        NetBlockStore store = new NetBlockStore();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        Block block1 = blockGenerator.createChildBlock(genesis);
        Block block2 = blockGenerator.createChildBlock(genesis);

        store.saveBlock(block1);
        store.saveBlock(block2);

        List<Block> blocks = store.getBlocksByNumber(1);

        Assertions.assertTrue(blocks.contains(block1));
        Assertions.assertTrue(blocks.contains(block2));
        Assertions.assertEquals(2, store.size());
        Assertions.assertEquals(1, store.minimalHeight());
        Assertions.assertEquals(1, store.maximumHeight());
    }

    @Test
    void releaseRange() {
        NetBlockStore store = new NetBlockStore();
        final BlockGenerator generator = new BlockGenerator();
        Block genesis = generator.getGenesisBlock();

        List<Block> blocks1 = generator.getBlockChain(genesis, 1000);
        List<Block> blocks2 = generator.getBlockChain(genesis, 1000);

        for (Block b : blocks1)
            store.saveBlock(b);
        for (Block b : blocks2)
            store.saveBlock(b);

        Assertions.assertEquals(2000, store.size());

        store.releaseRange(1, 1000);

        Assertions.assertEquals(0, store.size());
    }

    @Test
    void saveAndGetBlocksByParentHash() {
        NetBlockStore store = new NetBlockStore();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        Block block1 = blockGenerator.createChildBlock(genesis);
        Block block2 = blockGenerator.createChildBlock(genesis);

        store.saveBlock(block1);
        store.saveBlock(block2);

        List<Block> blocks = store.getBlocksByParentHash(genesis.getHash());

        Assertions.assertTrue(blocks.contains(block1));
        Assertions.assertTrue(blocks.contains(block2));
        Assertions.assertEquals(2, store.size());
    }

    @Test
    void getNoBlocksByNumber() {
        NetBlockStore store = new NetBlockStore();

        List<Block> blocks = store.getBlocksByNumber(42);

        Assertions.assertNotNull(blocks);
        Assertions.assertEquals(0, blocks.size());
    }

    @Test
    void saveHeader() {
        NetBlockStore store = new NetBlockStore();
        BlockHeader blockHeader = blockFactory.getBlockHeaderBuilder()
                .setParentHash(new byte[0])
                .setCoinbase(TestUtils.randomAddress())
                .setNumber(1)
                .setMinimumGasPrice(Coin.ZERO)
                .build();

        store.saveHeader(blockHeader);
        Assertions.assertTrue(store.hasHeader(blockHeader.getHash()));
    }

    @Test
    void removeHeader() {
        NetBlockStore store = new NetBlockStore();
        BlockHeader blockHeader = blockFactory.getBlockHeaderBuilder()
                .setParentHash(new byte[0])
                .setCoinbase(TestUtils.randomAddress())
                .setNumber(1)
                .setMinimumGasPrice(Coin.ZERO)
                .build();

        store.saveHeader(blockHeader);
        store.removeHeader(blockHeader);
        Assertions.assertFalse(store.hasHeader(blockHeader.getHash()));
    }
}

