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
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ajlopez on 5/11/2016.
 */
public class NetBlockStoreTest {
    private static final BlockFactory blockFactory = new BlockFactory(ActivationConfigsForTest.all());

    @Test
    public void getUnknownBlockAsNull() {
        NetBlockStore store = new NetBlockStore();
        Assert.assertNull(store.getBlockByHash(TestUtils.randomBytes(32)));
    }

    @Test
    public void minimalAndMaximumHeightInEmptyStore() {
        NetBlockStore store = new NetBlockStore();

        Assert.assertEquals(0, store.minimalHeight());
        Assert.assertEquals(0, store.maximumHeight());
    }

    @Test
    public void saveAndGetBlockByHash() {
        NetBlockStore store = new NetBlockStore();
        Block block = new BlockGenerator().getGenesisBlock();

        store.saveBlock(block);

        Assert.assertSame(block, store.getBlockByHash(block.getHash().getBytes()));
        Assert.assertEquals(0, store.minimalHeight());
        Assert.assertEquals(0, store.maximumHeight());
    }

    @Test
    public void saveRemoveAndGetBlockByHash() {
        NetBlockStore store = new NetBlockStore();
        Block block = new BlockGenerator().getBlock(1);

        store.saveBlock(block);

        Assert.assertEquals(1, store.minimalHeight());
        Assert.assertEquals(1, store.maximumHeight());

        store.removeBlock(block);

        Assert.assertNull(store.getBlockByHash(block.getHash().getBytes()));
        Assert.assertTrue(store.getBlocksByNumber(block.getNumber()).isEmpty());
        Assert.assertTrue(store.getBlocksByParentHash(block.getParentHash()).isEmpty());
        Assert.assertEquals(0, store.size());
    }

    @Test
    public void saveRemoveAndGetBlockByHashWithUncles() {
        NetBlockStore store = new NetBlockStore();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block parent = blockGenerator.getGenesisBlock();
        Block son1 = blockGenerator.createChildBlock(parent);
        Block son2 = blockGenerator.createChildBlock(parent);
        Block grandson = blockGenerator.createChildBlock(son1, new ArrayList<>(), Lists.newArrayList(son2.getHeader()), 1, BigInteger.ONE);

        store.saveBlock(son1);
        store.saveBlock(son2);
        store.saveBlock(grandson);

        Assert.assertEquals(1, store.minimalHeight());
        Assert.assertEquals(2, store.maximumHeight());

        store.removeBlock(grandson);

        Assert.assertNull(store.getBlockByHash(grandson.getHash().getBytes()));
        Assert.assertTrue(store.getBlocksByNumber(grandson.getNumber()).isEmpty());
        Assert.assertTrue(store.getBlocksByParentHash(son1.getHash()).isEmpty());
        Assert.assertTrue(store.getBlocksByParentHash(son2.getHash()).isEmpty());
        Assert.assertEquals(2, store.size());
    }

    @Test
    public void saveTwoBlocksRemoveOne() {
        NetBlockStore store = new NetBlockStore();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block parent = blockGenerator.getGenesisBlock();
        Block adam = blockGenerator.createChildBlock(parent);
        Block eve = blockGenerator.createChildBlock(adam);

        store.saveBlock(adam);
        store.saveBlock(eve);

        Assert.assertEquals(1, store.minimalHeight());
        Assert.assertEquals(2, store.maximumHeight());

        store.removeBlock(adam);

        Assert.assertNull(store.getBlockByHash(adam.getHash().getBytes()));
        Assert.assertEquals(1, store.size());
        Assert.assertEquals(2, store.minimalHeight());
        Assert.assertEquals(2, store.maximumHeight());

        List<Block> childrenByNumber = store.getBlocksByNumber(eve.getNumber());

        Assert.assertNotNull(childrenByNumber);
        Assert.assertEquals(1, childrenByNumber.size());

        Assert.assertEquals(eve.getHash(), childrenByNumber.get(0).getHash());

        List<Block> childrenByParent = store.getBlocksByParentHash(adam.getHash());

        Assert.assertNotNull(childrenByParent);
        Assert.assertEquals(1, childrenByParent.size());

        Assert.assertEquals(eve.getHash(), childrenByParent.get(0).getHash());

        Block daugther = store.getBlockByHash(eve.getHash().getBytes());

        Assert.assertNotNull(daugther);
        Assert.assertEquals(eve.getHash(), daugther.getHash());
    }

    @Test
    public void saveAndGetBlocksByNumber() {
        NetBlockStore store = new NetBlockStore();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        Block block1 = blockGenerator.createChildBlock(genesis);
        Block block2 = blockGenerator.createChildBlock(genesis);

        store.saveBlock(block1);
        store.saveBlock(block2);

        List<Block> blocks = store.getBlocksByNumber(1);

        Assert.assertTrue(blocks.contains(block1));
        Assert.assertTrue(blocks.contains(block2));
        Assert.assertEquals(2, store.size());
        Assert.assertEquals(1, store.minimalHeight());
        Assert.assertEquals(1, store.maximumHeight());
    }

    @Test
    public void releaseRange() {
        NetBlockStore store = new NetBlockStore();
        final BlockGenerator generator = new BlockGenerator();
        Block genesis = generator.getGenesisBlock();

        List<Block> blocks1 = generator.getBlockChain(genesis, 1000);
        List<Block> blocks2 = generator.getBlockChain(genesis, 1000);

        for (Block b : blocks1)
            store.saveBlock(b);
        for (Block b : blocks2)
            store.saveBlock(b);

        Assert.assertEquals(2000, store.size());

        store.releaseRange(1, 1000);

        Assert.assertEquals(0, store.size());
    }

    @Test
    public void saveAndGetBlocksByParentHash() {
        NetBlockStore store = new NetBlockStore();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        Block block1 = blockGenerator.createChildBlock(genesis);
        Block block2 = blockGenerator.createChildBlock(genesis);

        store.saveBlock(block1);
        store.saveBlock(block2);

        List<Block> blocks = store.getBlocksByParentHash(genesis.getHash());

        Assert.assertTrue(blocks.contains(block1));
        Assert.assertTrue(blocks.contains(block2));
        Assert.assertEquals(2, store.size());
    }

    @Test
    public void getNoBlocksByNumber() {
        NetBlockStore store = new NetBlockStore();

        List<Block> blocks = store.getBlocksByNumber(42);

        Assert.assertNotNull(blocks);
        Assert.assertEquals(0, blocks.size());
    }

    @Test
    public void saveHeader() {
        NetBlockStore store = new NetBlockStore();
        BlockHeader blockHeader = blockFactory.getBlockHeaderBuilder()
                .setParentHash(new byte[0])
                .setCoinbase(TestUtils.randomAddress())
                .setNumber(1)
                .setMinimumGasPrice(Coin.ZERO)
                .build();

        store.saveHeader(blockHeader);
        Assert.assertTrue(store.hasHeader(blockHeader.getHash()));
    }

    @Test
    public void removeHeader() {
        NetBlockStore store = new NetBlockStore();
        BlockHeader blockHeader = blockFactory.getBlockHeaderBuilder()
                .setParentHash(new byte[0])
                .setCoinbase(TestUtils.randomAddress())
                .setNumber(1)
                .setMinimumGasPrice(Coin.ZERO)
                .build();

        store.saveHeader(blockHeader);
        store.removeHeader(blockHeader);
        Assert.assertFalse(store.hasHeader(blockHeader.getHash()));
    }
}

