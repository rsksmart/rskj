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

package co.rsk.core.bc;

import co.rsk.blockchain.utils.BlockGenerator;
import org.ethereum.core.Block;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.BlockStore;
import org.ethereum.db.IndexedBlockStore;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by ajlopez on 09/08/2016.
 */
public class BlockForkTest {
    @Test
    public void calculateParentChild() {
        BlockStore store = createBlockStore();
        Block genesis = BlockGenerator.getInstance().getGenesisBlock();
        Block block = BlockGenerator.getInstance().createChildBlock(genesis);

        store.saveBlock(genesis, BigInteger.ONE, true);
        store.saveBlock(block, BigInteger.ONE, true);

        BlockFork fork = new BlockFork();

        fork.calculate(genesis, block, store);

        Assert.assertSame(genesis, fork.getCommonAncestor());
        Assert.assertTrue(fork.getOldBlocks().isEmpty());
        Assert.assertFalse(fork.getNewBlocks().isEmpty());
        Assert.assertEquals(1, fork.getNewBlocks().size());
        Assert.assertSame(block, fork.getNewBlocks().get(0));
    }

    @Test
    public void calculateForkLengthTwo() {
        BlockStore store = createBlockStore();
        Block genesis = BlockGenerator.getInstance().getGenesisBlock();
        Block parent = BlockGenerator.getInstance().createChildBlock(genesis);

        store.saveBlock(genesis, BigInteger.ONE, true);
        store.saveBlock(parent, BigInteger.ONE, true);

        List<Block> oldBranch = makeChain(parent, 2, store);
        List<Block> newBranch = makeChain(parent, 2, store);

        BlockFork fork = new BlockFork();

        fork.calculate(oldBranch.get(1), newBranch.get(1) , store);

        Assert.assertArrayEquals(parent.getHash(), fork.getCommonAncestor().getHash());

        Assert.assertFalse(fork.getOldBlocks().isEmpty());
        Assert.assertEquals(2, fork.getOldBlocks().size());
        Assert.assertArrayEquals(oldBranch.get(0).getHash(), fork.getOldBlocks().get(0).getHash());
        Assert.assertArrayEquals(oldBranch.get(1).getHash(), fork.getOldBlocks().get(1).getHash());

        Assert.assertFalse(fork.getNewBlocks().isEmpty());
        Assert.assertEquals(2, fork.getNewBlocks().size());
        Assert.assertArrayEquals(newBranch.get(0).getHash(), fork.getNewBlocks().get(0).getHash());
        Assert.assertArrayEquals(newBranch.get(1).getHash(), fork.getNewBlocks().get(1).getHash());
    }

    @Test
    public void calculateForkLengthTwoOldThreeNew() {
        BlockStore store = createBlockStore();
        Block genesis = BlockGenerator.getInstance().getGenesisBlock();
        Block parent = BlockGenerator.getInstance().createChildBlock(genesis);

        store.saveBlock(genesis, BigInteger.ONE, true);
        store.saveBlock(parent, BigInteger.ONE, true);

        List<Block> oldBranch = makeChain(parent, 2, store);
        List<Block> newBranch = makeChain(parent, 3, store);

        BlockFork fork = new BlockFork();

        fork.calculate(oldBranch.get(1), newBranch.get(2) , store);

        Assert.assertArrayEquals(parent.getHash(), fork.getCommonAncestor().getHash());

        Assert.assertFalse(fork.getOldBlocks().isEmpty());
        Assert.assertEquals(2, fork.getOldBlocks().size());
        Assert.assertArrayEquals(oldBranch.get(0).getHash(), fork.getOldBlocks().get(0).getHash());
        Assert.assertArrayEquals(oldBranch.get(1).getHash(), fork.getOldBlocks().get(1).getHash());

        Assert.assertFalse(fork.getNewBlocks().isEmpty());
        Assert.assertEquals(3, fork.getNewBlocks().size());
        Assert.assertArrayEquals(newBranch.get(0).getHash(), fork.getNewBlocks().get(0).getHash());
        Assert.assertArrayEquals(newBranch.get(1).getHash(), fork.getNewBlocks().get(1).getHash());
        Assert.assertArrayEquals(newBranch.get(2).getHash(), fork.getNewBlocks().get(2).getHash());
    }

    @Test
    public void calculateForkLengthThreeOldTwoNew() {
        BlockStore store = createBlockStore();
        Block genesis = BlockGenerator.getInstance().getGenesisBlock();
        Block parent = BlockGenerator.getInstance().createChildBlock(genesis);

        store.saveBlock(genesis, BigInteger.ONE, true);
        store.saveBlock(parent, BigInteger.ONE, true);

        List<Block> oldBranch = makeChain(parent, 3, store);
        List<Block> newBranch = makeChain(parent, 2, store);

        BlockFork fork = new BlockFork();

        fork.calculate(oldBranch.get(2), newBranch.get(1) , store);

        Assert.assertArrayEquals(parent.getHash(), fork.getCommonAncestor().getHash());

        Assert.assertFalse(fork.getOldBlocks().isEmpty());
        Assert.assertEquals(3, fork.getOldBlocks().size());
        Assert.assertArrayEquals(oldBranch.get(0).getHash(), fork.getOldBlocks().get(0).getHash());
        Assert.assertArrayEquals(oldBranch.get(1).getHash(), fork.getOldBlocks().get(1).getHash());
        Assert.assertArrayEquals(oldBranch.get(2).getHash(), fork.getOldBlocks().get(2).getHash());

        Assert.assertFalse(fork.getNewBlocks().isEmpty());
        Assert.assertEquals(2, fork.getNewBlocks().size());
        Assert.assertArrayEquals(newBranch.get(0).getHash(), fork.getNewBlocks().get(0).getHash());
        Assert.assertArrayEquals(newBranch.get(1).getHash(), fork.getNewBlocks().get(1).getHash());
    }

    private static List<Block> makeChain(Block parent, int length, BlockStore store) {
        List<Block> blocks = new ArrayList<>();

        for (int k = 0; k < length; k++) {
            Block block = BlockGenerator.getInstance().createChildBlock(parent);
            blocks.add(block);
            store.saveBlock(block, BigInteger.ONE, false);
            parent = block;
        }

        return blocks;
    }

    private static BlockStore createBlockStore() {
        IndexedBlockStore blockStore = new IndexedBlockStore();
        blockStore.init(new HashMap<>(), new HashMapDB(), null);
        return blockStore;
    }
}
