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
import co.rsk.core.commons.Keccak256;
import co.rsk.core.commons.RskAddress;
import com.google.common.collect.Lists;
import org.ethereum.core.Block;
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
public class BlockStoreTest {
    @Test
    public void getUnknownBlockAsNull() {
        BlockStore store = new BlockStore();

        Assert.assertNull(store.getBlockByHash(new Keccak256(new byte[] { 0x01, 0x20 })));
    }

    @Test
    public void minimalAndMaximumHeightInEmptyStore() {
        BlockStore store = new BlockStore();

        Assert.assertEquals(0, store.minimalHeight());
        Assert.assertEquals(0, store.maximumHeight());
    }

    @Test
    public void saveAndGetBlockByHash() {
        BlockStore store = new BlockStore();
        Block block = BlockGenerator.getInstance().getGenesisBlock();

        store.saveBlock(block);

        Assert.assertSame(block, store.getBlockByHash(block.getHash()));
        Assert.assertEquals(0, store.minimalHeight());
        Assert.assertEquals(0, store.maximumHeight());
    }

    @Test
    public void saveRemoveAndGetBlockByHash() {
        BlockStore store = new BlockStore();
        Block block = BlockGenerator.getInstance().getBlock(1);

        store.saveBlock(block);

        Assert.assertEquals(1, store.minimalHeight());
        Assert.assertEquals(1, store.maximumHeight());

        store.removeBlock(block);

        Assert.assertNull(store.getBlockByHash(block.getHash()));
        Assert.assertTrue(store.getBlocksByNumber(block.getNumber()).isEmpty());
        Assert.assertTrue(store.getBlocksByParentHash(block.getParentHash()).isEmpty());
        Assert.assertEquals(0, store.size());
    }

    @Test
    public void saveRemoveAndGetBlockByHashWithUncles() {
        BlockStore store = new BlockStore();
        Block parent = BlockGenerator.getInstance().getGenesisBlock();
        Block son1 = BlockGenerator.getInstance().createChildBlock(parent);
        Block son2 = BlockGenerator.getInstance().createChildBlock(parent);
        Block grandson = BlockGenerator.getInstance().createChildBlock(son1, new ArrayList<>(), Lists.newArrayList(son2.getHeader()), 1, BigInteger.ONE);

        store.saveBlock(son1);
        store.saveBlock(son2);
        store.saveBlock(grandson);

        Assert.assertEquals(1, store.minimalHeight());
        Assert.assertEquals(2, store.maximumHeight());

        store.removeBlock(grandson);

        Assert.assertNull(store.getBlockByHash(grandson.getHash()));
        Assert.assertTrue(store.getBlocksByNumber(grandson.getNumber()).isEmpty());
        Assert.assertTrue(store.getBlocksByParentHash(son1.getHash()).isEmpty());
        Assert.assertTrue(store.getBlocksByParentHash(son2.getHash()).isEmpty());
        Assert.assertEquals(2, store.size());
    }

    @Test
    public void saveTwoBlocksRemoveOne() {
        BlockStore store = new BlockStore();
        Block parent = BlockGenerator.getInstance().getGenesisBlock();
        Block adam = BlockGenerator.getInstance().createChildBlock(parent);
        Block eve = BlockGenerator.getInstance().createChildBlock(adam);

        store.saveBlock(adam);
        store.saveBlock(eve);

        Assert.assertEquals(1, store.minimalHeight());
        Assert.assertEquals(2, store.maximumHeight());

        store.removeBlock(adam);

        Assert.assertNull(store.getBlockByHash(adam.getHash()));
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

        Block daugther = store.getBlockByHash(eve.getHash());

        Assert.assertNotNull(daugther);
        Assert.assertEquals(eve.getHash(), daugther.getHash());
    }

    @Test
    public void saveAndGetBlocksByNumber() {
        BlockStore store = new BlockStore();
        Block genesis = BlockGenerator.getInstance().getGenesisBlock();
        Block block1 = BlockGenerator.getInstance().createChildBlock(genesis);
        Block block2 = BlockGenerator.getInstance().createChildBlock(genesis);

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
        BlockStore store = new BlockStore();
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
        BlockStore store = new BlockStore();
        Block genesis = BlockGenerator.getInstance().getGenesisBlock();
        Block block1 = BlockGenerator.getInstance().createChildBlock(genesis);
        Block block2 = BlockGenerator.getInstance().createChildBlock(genesis);

        store.saveBlock(block1);
        store.saveBlock(block2);

        List<Block> blocks = store.getBlocksByParentHash(genesis.getHash());

        Assert.assertTrue(blocks.contains(block1));
        Assert.assertTrue(blocks.contains(block2));
        Assert.assertEquals(2, store.size());
    }

    @Test
    public void getNoBlocksByNumber() {
        BlockStore store = new BlockStore();

        List<Block> blocks = store.getBlocksByNumber(42);

        Assert.assertNotNull(blocks);
        Assert.assertEquals(0, blocks.size());
    }

    @Test
    public void saveHeader() {
        BlockStore store = new BlockStore();

        BlockHeader blockHeader = new BlockHeader(new Keccak256(new byte[]{}),
                new Keccak256(new byte[]{}),
                RskAddress.nullAddress().getBytes(),
                new Bloom().getData(),
                new byte[]{},
                1,
                new byte[]{},
                0,
                0,
                new byte[]{},
                new byte[]{},
                new byte[]{},
                new byte[]{},
                new byte[]{},
                0
        );

        store.saveHeader(blockHeader);
        Assert.assertTrue(store.hasHeader(blockHeader.getHash()));
    }

    @Test
    public void removeHeader() {
        BlockStore store = new BlockStore();
        BlockHeader blockHeader = new BlockHeader(new Keccak256(new byte[]{}),
                new Keccak256(new byte[]{}),
                RskAddress.nullAddress().getBytes(),
                new Bloom().getData(),
                new byte[]{},
                1,
                new byte[]{},
                0,
                0,
                new byte[]{},
                new byte[]{},
                new byte[]{},
                new byte[]{},
                new byte[]{},
                0
        );

        store.saveHeader(blockHeader);
        store.removeHeader(blockHeader.getHash());
        Assert.assertFalse(store.hasHeader(blockHeader.getHash()));
    }
}

