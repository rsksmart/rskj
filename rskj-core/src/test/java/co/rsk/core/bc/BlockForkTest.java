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
import co.rsk.core.BlockDifficulty;
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

    public static final BlockDifficulty TEST_DIFFICULTY = new BlockDifficulty(BigInteger.ONE);

    @Test
    public void calculateParentChild() {
        BlockStore store = createBlockStore();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        Block block = blockGenerator.createChildBlock(genesis);

        store.saveBlock(genesis, TEST_DIFFICULTY, true);
        store.saveBlock(block, TEST_DIFFICULTY, true);

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
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        Block parent = blockGenerator.createChildBlock(genesis);

        store.saveBlock(genesis, TEST_DIFFICULTY, true);
        store.saveBlock(parent, TEST_DIFFICULTY, true);

        List<Block> oldBranch = makeChain(parent, 2, store, blockGenerator);
        List<Block> newBranch = makeChain(parent, 2, store, blockGenerator);

        BlockFork fork = new BlockFork();

        fork.calculate(oldBranch.get(1), newBranch.get(1) , store);

        Assert.assertEquals(parent.getHash(), fork.getCommonAncestor().getHash());

        Assert.assertFalse(fork.getOldBlocks().isEmpty());
        Assert.assertEquals(2, fork.getOldBlocks().size());
        Assert.assertEquals(oldBranch.get(0).getHash(), fork.getOldBlocks().get(0).getHash());
        Assert.assertEquals(oldBranch.get(1).getHash(), fork.getOldBlocks().get(1).getHash());

        Assert.assertFalse(fork.getNewBlocks().isEmpty());
        Assert.assertEquals(2, fork.getNewBlocks().size());
        Assert.assertEquals(newBranch.get(0).getHash(), fork.getNewBlocks().get(0).getHash());
        Assert.assertEquals(newBranch.get(1).getHash(), fork.getNewBlocks().get(1).getHash());
    }

    @Test
    public void calculateForkLengthTwoOldThreeNew() {
        BlockStore store = createBlockStore();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        Block parent = blockGenerator.createChildBlock(genesis);

        store.saveBlock(genesis, TEST_DIFFICULTY, true);
        store.saveBlock(parent, TEST_DIFFICULTY, true);

        List<Block> oldBranch = makeChain(parent, 2, store, blockGenerator);
        List<Block> newBranch = makeChain(parent, 3, store, blockGenerator);

        BlockFork fork = new BlockFork();

        fork.calculate(oldBranch.get(1), newBranch.get(2) , store);

        Assert.assertEquals(parent.getHash(), fork.getCommonAncestor().getHash());

        Assert.assertFalse(fork.getOldBlocks().isEmpty());
        Assert.assertEquals(2, fork.getOldBlocks().size());
        Assert.assertEquals(oldBranch.get(0).getHash(), fork.getOldBlocks().get(0).getHash());
        Assert.assertEquals(oldBranch.get(1).getHash(), fork.getOldBlocks().get(1).getHash());

        Assert.assertFalse(fork.getNewBlocks().isEmpty());
        Assert.assertEquals(3, fork.getNewBlocks().size());
        Assert.assertEquals(newBranch.get(0).getHash(), fork.getNewBlocks().get(0).getHash());
        Assert.assertEquals(newBranch.get(1).getHash(), fork.getNewBlocks().get(1).getHash());
        Assert.assertEquals(newBranch.get(2).getHash(), fork.getNewBlocks().get(2).getHash());
    }

    @Test
    public void calculateForkLengthThreeOldTwoNew() {
        BlockStore store = createBlockStore();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        Block parent = blockGenerator.createChildBlock(genesis);

        store.saveBlock(genesis, TEST_DIFFICULTY, true);
        store.saveBlock(parent, TEST_DIFFICULTY, true);

        List<Block> oldBranch = makeChain(parent, 3, store, blockGenerator);
        List<Block> newBranch = makeChain(parent, 2, store, blockGenerator);

        BlockFork fork = new BlockFork();

        fork.calculate(oldBranch.get(2), newBranch.get(1) , store);

        Assert.assertEquals(parent.getHash(), fork.getCommonAncestor().getHash());

        Assert.assertFalse(fork.getOldBlocks().isEmpty());
        Assert.assertEquals(3, fork.getOldBlocks().size());
        Assert.assertEquals(oldBranch.get(0).getHash(), fork.getOldBlocks().get(0).getHash());
        Assert.assertEquals(oldBranch.get(1).getHash(), fork.getOldBlocks().get(1).getHash());
        Assert.assertEquals(oldBranch.get(2).getHash(), fork.getOldBlocks().get(2).getHash());

        Assert.assertFalse(fork.getNewBlocks().isEmpty());
        Assert.assertEquals(2, fork.getNewBlocks().size());
        Assert.assertEquals(newBranch.get(0).getHash(), fork.getNewBlocks().get(0).getHash());
        Assert.assertEquals(newBranch.get(1).getHash(), fork.getNewBlocks().get(1).getHash());
    }

    private static List<Block> makeChain(Block parent, int length, BlockStore store, BlockGenerator blockGenerator) {
        List<Block> blocks = new ArrayList<>();

        for (int k = 0; k < length; k++) {
            Block block = blockGenerator.createChildBlock(parent);
            blocks.add(block);
            store.saveBlock(block, TEST_DIFFICULTY, false);
            parent = block;
        }

        return blocks;
    }

    private static BlockStore createBlockStore() {
        return new IndexedBlockStore(new HashMap<>(), new HashMapDB(), null);
    }
}
