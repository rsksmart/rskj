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
import co.rsk.crypto.Keccak256;
import co.rsk.db.HashMapBlocksIndex;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockHeader;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.BlockStore;
import org.ethereum.db.IndexedBlockStore;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;

/**
 * Created by ajlopez on 12/08/2016.
 */
public class FamilyUtilsTest {

    public static final BlockDifficulty TEST_DIFFICULTY = new BlockDifficulty(BigInteger.ONE);
    private static final BlockFactory blockFactory = new BlockFactory(ActivationConfigsForTest.all());

    private final FamilyUtils familyUtils = FamilyUtils.getInstance();

    @Test
    public void getFamilyGetParent() {
        BlockStore store = createBlockStore();

        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        Block block1 = blockGenerator.createChildBlock(genesis);

        store.saveBlock(genesis, TEST_DIFFICULTY, true);
        store.saveBlock(block1, TEST_DIFFICULTY, true);

        Set<Keccak256> family = familyUtils.getFamily(store, block1, 6);

        Assert.assertNotNull(family);
        Assert.assertFalse(family.isEmpty());
        Assert.assertEquals(1, family.size());

        Assert.assertTrue(family.contains(genesis.getHash()));
    }

    @Test
    public void getEmptyFamilyForGenesis() {
        BlockStore store = createBlockStore();

        Block genesis = new BlockGenerator().getGenesisBlock();

        store.saveBlock(genesis, TEST_DIFFICULTY, true);

        Set<Keccak256> family = familyUtils.getFamily(store, genesis, 6);

        Assert.assertNotNull(family);
        Assert.assertTrue(family.isEmpty());
    }

    @Test
    public void getFamilyGetAncestorsUpToLevel() {
        BlockStore store = createBlockStore();

        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        Block block1 = blockGenerator.createChildBlock(genesis);
        Block block2 = blockGenerator.createChildBlock(block1);
        Block block3 = blockGenerator.createChildBlock(block2);

        store.saveBlock(genesis, TEST_DIFFICULTY, true);
        store.saveBlock(block1, TEST_DIFFICULTY, true);
        store.saveBlock(block2, TEST_DIFFICULTY, true);
        store.saveBlock(block3, TEST_DIFFICULTY, true);

        Set<Keccak256> family = familyUtils.getFamily(store, block3, 2);

        Assert.assertNotNull(family);
        Assert.assertFalse(family.isEmpty());
        Assert.assertEquals(2, family.size());

        Assert.assertFalse(family.contains(genesis.getHash()));
        Assert.assertFalse(family.contains(block3.getHash()));
        Assert.assertTrue(family.contains(block1.getHash()));
        Assert.assertTrue(family.contains(block2.getHash()));
    }

    @Test
    public void getFamilyGetAncestorsWithUncles() {
        BlockStore store = createBlockStore();

        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        Block block1 = blockGenerator.createChildBlock(genesis);
        Block uncle11 = blockGenerator.createChildBlock(genesis);
        Block uncle12 = blockGenerator.createChildBlock(genesis);
        Block block2 = blockGenerator.createChildBlock(block1);
        Block uncle21 = blockGenerator.createChildBlock(block1);
        Block uncle22 = blockGenerator.createChildBlock(block1);
        Block block3 = blockGenerator.createChildBlock(block2);
        Block uncle31 = blockGenerator.createChildBlock(block2);
        Block uncle32 = blockGenerator.createChildBlock(block2);

        store.saveBlock(genesis, TEST_DIFFICULTY, true);
        store.saveBlock(block1, TEST_DIFFICULTY, true);
        store.saveBlock(uncle11, TEST_DIFFICULTY, false);
        store.saveBlock(uncle12, TEST_DIFFICULTY, false);
        store.saveBlock(block2, TEST_DIFFICULTY, true);
        store.saveBlock(uncle21, TEST_DIFFICULTY, false);
        store.saveBlock(uncle22, TEST_DIFFICULTY, false);
        store.saveBlock(block3, TEST_DIFFICULTY, true);
        store.saveBlock(uncle31, TEST_DIFFICULTY, false);
        store.saveBlock(uncle32, TEST_DIFFICULTY, false);

        Set<Keccak256> family = familyUtils.getFamily(store, block3, 2);

        Assert.assertNotNull(family);
        Assert.assertFalse(family.isEmpty());
        Assert.assertEquals(4, family.size());

        Assert.assertFalse(family.contains(genesis.getHash()));
        Assert.assertTrue(family.contains(block1.getHash()));
        Assert.assertFalse(family.contains(uncle11.getHash()));
        Assert.assertFalse(family.contains(uncle12.getHash()));
        Assert.assertTrue(family.contains(block2.getHash()));
        Assert.assertTrue(family.contains(uncle21.getHash()));
        Assert.assertTrue(family.contains(uncle22.getHash()));
        Assert.assertFalse(family.contains(block3.getHash()));
        Assert.assertFalse(family.contains(uncle31.getHash()));
        Assert.assertFalse(family.contains(uncle32.getHash()));

        family = familyUtils.getFamily(store, block3, 3);

        Assert.assertNotNull(family);
        Assert.assertFalse(family.isEmpty());
        Assert.assertEquals(7, family.size());

        Assert.assertTrue(family.contains(genesis.getHash()));
        Assert.assertTrue(family.contains(block1.getHash()));
        Assert.assertTrue(family.contains(uncle11.getHash()));
        Assert.assertTrue(family.contains(uncle12.getHash()));
        Assert.assertTrue(family.contains(block2.getHash()));
        Assert.assertTrue(family.contains(uncle21.getHash()));
        Assert.assertTrue(family.contains(uncle22.getHash()));
        Assert.assertFalse(family.contains(block3.getHash()));
        Assert.assertFalse(family.contains(uncle31.getHash()));
        Assert.assertFalse(family.contains(uncle32.getHash()));
    }

    @Test
    public void getUnclesHeaders() {
        BlockStore store = createBlockStore();

        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        Block block1 = blockGenerator.createChildBlock(genesis);
        Block uncle11 = blockGenerator.createChildBlock(genesis);
        Block uncle111 = blockGenerator.createChildBlock(uncle11);
        Block uncle12 = blockGenerator.createChildBlock(genesis);
        Block uncle121 = blockGenerator.createChildBlock(uncle12);
        Block block2 = blockGenerator.createChildBlock(block1);
        Block uncle21 = blockGenerator.createChildBlock(block1);
        Block uncle22 = blockGenerator.createChildBlock(block1);
        Block block3 = blockGenerator.createChildBlock(block2);
        Block uncle31 = blockGenerator.createChildBlock(block2);
        Block uncle32 = blockGenerator.createChildBlock(block2);

        store.saveBlock(genesis, TEST_DIFFICULTY, true);
        store.saveBlock(block1, TEST_DIFFICULTY, true);
        store.saveBlock(uncle11, TEST_DIFFICULTY, false);
        store.saveBlock(uncle12, TEST_DIFFICULTY, false);
        store.saveBlock(uncle111, TEST_DIFFICULTY, false);
        store.saveBlock(uncle121, TEST_DIFFICULTY, false);
        store.saveBlock(block2, TEST_DIFFICULTY, true);
        store.saveBlock(uncle21, TEST_DIFFICULTY, false);
        store.saveBlock(uncle22, TEST_DIFFICULTY, false);
        store.saveBlock(block3, TEST_DIFFICULTY, true);
        store.saveBlock(uncle31, TEST_DIFFICULTY, false);
        store.saveBlock(uncle32, TEST_DIFFICULTY, false);

        List<BlockHeader> list = familyUtils.getUnclesHeaders(store, block3, 3);

        Assert.assertNotNull(list);
        Assert.assertFalse(list.isEmpty());
        Assert.assertEquals(4, list.size());

        Assert.assertTrue(containsHash(uncle11.getHash(), list));
        Assert.assertTrue(containsHash(uncle12.getHash(), list));
        Assert.assertTrue(containsHash(uncle21.getHash(), list));
        Assert.assertTrue(containsHash(uncle22.getHash(), list));
    }

    @Test
    public void getUncles() {
        BlockStore store = createBlockStore();

        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        Block block1 = blockGenerator.createChildBlock(genesis);
        Block uncle11 = blockGenerator.createChildBlock(genesis);
        Block uncle12 = blockGenerator.createChildBlock(genesis);
        Block block2 = blockGenerator.createChildBlock(block1);
        Block uncle21 = blockGenerator.createChildBlock(block1);
        Block uncle22 = blockGenerator.createChildBlock(block1);
        Block block3 = blockGenerator.createChildBlock(block2);
        Block uncle31 = blockGenerator.createChildBlock(block2);
        Block uncle32 = blockGenerator.createChildBlock(block2);

        store.saveBlock(genesis, TEST_DIFFICULTY, true);
        store.saveBlock(block1, TEST_DIFFICULTY, true);
        store.saveBlock(uncle11, TEST_DIFFICULTY, false);
        store.saveBlock(uncle12, TEST_DIFFICULTY, false);
        store.saveBlock(block2, TEST_DIFFICULTY, true);
        store.saveBlock(uncle21, TEST_DIFFICULTY, false);
        store.saveBlock(uncle22, TEST_DIFFICULTY, false);
        store.saveBlock(block3, TEST_DIFFICULTY, true);
        store.saveBlock(uncle31, TEST_DIFFICULTY, false);
        store.saveBlock(uncle32, TEST_DIFFICULTY, false);

        Set<Keccak256> family = familyUtils.getUncles(store, block3, 3);

        Assert.assertNotNull(family);
        Assert.assertFalse(family.isEmpty());
        Assert.assertEquals(4, family.size());

        Assert.assertFalse(family.contains(genesis.getHash()));
        Assert.assertFalse(family.contains(block1.getHash()));
        Assert.assertTrue(family.contains(uncle11.getHash()));
        Assert.assertTrue(family.contains(uncle12.getHash()));
        Assert.assertFalse(family.contains(block2.getHash()));
        Assert.assertTrue(family.contains(uncle21.getHash()));
        Assert.assertTrue(family.contains(uncle22.getHash()));
        Assert.assertFalse(family.contains(block3.getHash()));
        Assert.assertFalse(family.contains(uncle31.getHash()));
        Assert.assertFalse(family.contains(uncle32.getHash()));
    }

    private static BlockStore createBlockStore() {
        return new IndexedBlockStore(blockFactory, new HashMapDB(), new HashMapBlocksIndex());
    }

    private static boolean containsHash(Keccak256 hash, List<BlockHeader> headers) {
        for (BlockHeader header : headers) {
            if (hash.equals(header.getHash())) {
                return true;
            }
        }
        return false;
    }
}
