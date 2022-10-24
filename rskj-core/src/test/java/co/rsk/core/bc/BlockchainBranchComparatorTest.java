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
import co.rsk.db.HashMapBlocksIndex;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.BlockStore;
import org.ethereum.db.IndexedBlockStore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ajlopez on 09/08/2016.
 */
class BlockchainBranchComparatorTest {

    public static final BlockDifficulty TEST_DIFFICULTY = new BlockDifficulty(BigInteger.ONE);
    private static final BlockFactory blockFactory = new BlockFactory(ActivationConfigsForTest.all());

    @Test
    void calculateParentChild() {
        BlockStore store = createBlockStore();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        Block block = blockGenerator.createChildBlock(genesis);

        store.saveBlock(genesis, TEST_DIFFICULTY, true);
        store.saveBlock(block, TEST_DIFFICULTY, true);

        BlockchainBranchComparator comparator = new BlockchainBranchComparator(store);

        BlockFork fork = comparator.calculateFork(genesis, block);

        Assertions.assertSame(genesis, fork.getCommonAncestor());
        Assertions.assertTrue(fork.getOldBlocks().isEmpty());
        Assertions.assertFalse(fork.getNewBlocks().isEmpty());
        Assertions.assertEquals(1, fork.getNewBlocks().size());
        Assertions.assertSame(block, fork.getNewBlocks().get(0));
    }

    @Test
    void calculateForkLengthTwo() {
        BlockStore store = createBlockStore();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        Block parent = blockGenerator.createChildBlock(genesis);

        store.saveBlock(genesis, TEST_DIFFICULTY, true);
        store.saveBlock(parent, TEST_DIFFICULTY, true);

        List<Block> oldBranch = makeChain(parent, 2, store, blockGenerator);
        List<Block> newBranch = makeChain(parent, 2, store, blockGenerator);

        BlockchainBranchComparator comparator = new BlockchainBranchComparator(store);

        BlockFork fork = comparator.calculateFork(oldBranch.get(1), newBranch.get(1));

        Assertions.assertEquals(parent.getHash(), fork.getCommonAncestor().getHash());

        Assertions.assertFalse(fork.getOldBlocks().isEmpty());
        Assertions.assertEquals(2, fork.getOldBlocks().size());
        Assertions.assertEquals(oldBranch.get(0).getHash(), fork.getOldBlocks().get(0).getHash());
        Assertions.assertEquals(oldBranch.get(1).getHash(), fork.getOldBlocks().get(1).getHash());

        Assertions.assertFalse(fork.getNewBlocks().isEmpty());
        Assertions.assertEquals(2, fork.getNewBlocks().size());
        Assertions.assertEquals(newBranch.get(0).getHash(), fork.getNewBlocks().get(0).getHash());
        Assertions.assertEquals(newBranch.get(1).getHash(), fork.getNewBlocks().get(1).getHash());
    }

    @Test
    void calculateForkLengthTwoOldThreeNew() {
        BlockStore store = createBlockStore();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        Block parent = blockGenerator.createChildBlock(genesis);

        store.saveBlock(genesis, TEST_DIFFICULTY, true);
        store.saveBlock(parent, TEST_DIFFICULTY, true);

        List<Block> oldBranch = makeChain(parent, 2, store, blockGenerator);
        List<Block> newBranch = makeChain(parent, 3, store, blockGenerator);

        BlockchainBranchComparator comparator = new BlockchainBranchComparator(store);

        BlockFork fork = comparator.calculateFork(oldBranch.get(1), newBranch.get(2));

        Assertions.assertEquals(parent.getHash(), fork.getCommonAncestor().getHash());

        Assertions.assertFalse(fork.getOldBlocks().isEmpty());
        Assertions.assertEquals(2, fork.getOldBlocks().size());
        Assertions.assertEquals(oldBranch.get(0).getHash(), fork.getOldBlocks().get(0).getHash());
        Assertions.assertEquals(oldBranch.get(1).getHash(), fork.getOldBlocks().get(1).getHash());

        Assertions.assertFalse(fork.getNewBlocks().isEmpty());
        Assertions.assertEquals(3, fork.getNewBlocks().size());
        Assertions.assertEquals(newBranch.get(0).getHash(), fork.getNewBlocks().get(0).getHash());
        Assertions.assertEquals(newBranch.get(1).getHash(), fork.getNewBlocks().get(1).getHash());
        Assertions.assertEquals(newBranch.get(2).getHash(), fork.getNewBlocks().get(2).getHash());
    }

    @Test
    void calculateForkLengthThreeOldTwoNew() {
        BlockStore store = createBlockStore();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        Block parent = blockGenerator.createChildBlock(genesis);

        store.saveBlock(genesis, TEST_DIFFICULTY, true);
        store.saveBlock(parent, TEST_DIFFICULTY, true);

        List<Block> oldBranch = makeChain(parent, 3, store, blockGenerator);
        List<Block> newBranch = makeChain(parent, 2, store, blockGenerator);

        BlockchainBranchComparator comparator = new BlockchainBranchComparator(store);

        BlockFork fork = comparator.calculateFork(oldBranch.get(2), newBranch.get(1));

        Assertions.assertEquals(parent.getHash(), fork.getCommonAncestor().getHash());

        Assertions.assertFalse(fork.getOldBlocks().isEmpty());
        Assertions.assertEquals(3, fork.getOldBlocks().size());
        Assertions.assertEquals(oldBranch.get(0).getHash(), fork.getOldBlocks().get(0).getHash());
        Assertions.assertEquals(oldBranch.get(1).getHash(), fork.getOldBlocks().get(1).getHash());
        Assertions.assertEquals(oldBranch.get(2).getHash(), fork.getOldBlocks().get(2).getHash());

        Assertions.assertFalse(fork.getNewBlocks().isEmpty());
        Assertions.assertEquals(2, fork.getNewBlocks().size());
        Assertions.assertEquals(newBranch.get(0).getHash(), fork.getNewBlocks().get(0).getHash());
        Assertions.assertEquals(newBranch.get(1).getHash(), fork.getNewBlocks().get(1).getHash());
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
        return new IndexedBlockStore(blockFactory, new HashMapDB(), new HashMapBlocksIndex());
    }
}
