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
import org.ethereum.core.BlockHeader;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.IndexedBlockStore;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * Created by ajlopez on 12/08/2016.
 */
public class FamilyUtilsTest {
    @Test
    public void getFamilyGetParent() {
        BlockStore store = createBlockStore();

        Block genesis = BlockGenerator.getInstance().getGenesisBlock();
        Block block1 = BlockGenerator.getInstance().createChildBlock(genesis);

        store.saveBlock(genesis, BigInteger.ONE, true);
        store.saveBlock(block1, BigInteger.ONE, true);

        Set<ByteArrayWrapper> family = FamilyUtils.getFamily(store, block1, 6);

        Assert.assertNotNull(family);
        Assert.assertFalse(family.isEmpty());
        Assert.assertEquals(1, family.size());

        Assert.assertTrue(family.contains(new ByteArrayWrapper(genesis.getHash())));
    }

    @Test
    public void getEmptyFamilyForGenesis() {
        BlockStore store = createBlockStore();

        Block genesis = BlockGenerator.getInstance().getGenesisBlock();

        store.saveBlock(genesis, BigInteger.ONE, true);

        Set<ByteArrayWrapper> family = FamilyUtils.getFamily(store, genesis, 6);

        Assert.assertNotNull(family);
        Assert.assertTrue(family.isEmpty());
    }

    @Test
    public void getFamilyGetAncestorsUpToLevel() {
        BlockStore store = createBlockStore();

        Block genesis = BlockGenerator.getInstance().getGenesisBlock();
        Block block1 = BlockGenerator.getInstance().createChildBlock(genesis);
        Block block2 = BlockGenerator.getInstance().createChildBlock(block1);
        Block block3 = BlockGenerator.getInstance().createChildBlock(block2);

        store.saveBlock(genesis, BigInteger.ONE, true);
        store.saveBlock(block1, BigInteger.ONE, true);
        store.saveBlock(block2, BigInteger.ONE, true);
        store.saveBlock(block3, BigInteger.ONE, true);

        Set<ByteArrayWrapper> family = FamilyUtils.getFamily(store, block3, 2);

        Assert.assertNotNull(family);
        Assert.assertFalse(family.isEmpty());
        Assert.assertEquals(2, family.size());

        Assert.assertFalse(family.contains(new ByteArrayWrapper(genesis.getHash())));
        Assert.assertFalse(family.contains(new ByteArrayWrapper(block3.getHash())));
        Assert.assertTrue(family.contains(new ByteArrayWrapper(block1.getHash())));
        Assert.assertTrue(family.contains(new ByteArrayWrapper(block2.getHash())));
    }

    @Test
    public void getFamilyGetAncestorsWithUncles() {
        BlockStore store = createBlockStore();

        Block genesis = BlockGenerator.getInstance().getGenesisBlock();
        Block block1 = BlockGenerator.getInstance().createChildBlock(genesis);
        Block uncle11 = BlockGenerator.getInstance().createChildBlock(genesis);
        Block uncle12 = BlockGenerator.getInstance().createChildBlock(genesis);
        Block block2 = BlockGenerator.getInstance().createChildBlock(block1);
        Block uncle21 = BlockGenerator.getInstance().createChildBlock(block1);
        Block uncle22 = BlockGenerator.getInstance().createChildBlock(block1);
        Block block3 = BlockGenerator.getInstance().createChildBlock(block2);
        Block uncle31 = BlockGenerator.getInstance().createChildBlock(block2);
        Block uncle32 = BlockGenerator.getInstance().createChildBlock(block2);

        store.saveBlock(genesis, BigInteger.ONE, true);
        store.saveBlock(block1, BigInteger.ONE, true);
        store.saveBlock(uncle11, BigInteger.ONE, false);
        store.saveBlock(uncle12, BigInteger.ONE, false);
        store.saveBlock(block2, BigInteger.ONE, true);
        store.saveBlock(uncle21, BigInteger.ONE, false);
        store.saveBlock(uncle22, BigInteger.ONE, false);
        store.saveBlock(block3, BigInteger.ONE, true);
        store.saveBlock(uncle31, BigInteger.ONE, false);
        store.saveBlock(uncle32, BigInteger.ONE, false);

        Set<ByteArrayWrapper> family = FamilyUtils.getFamily(store, block3, 2);

        Assert.assertNotNull(family);
        Assert.assertFalse(family.isEmpty());
        Assert.assertEquals(4, family.size());

        Assert.assertFalse(family.contains(new ByteArrayWrapper(genesis.getHash())));
        Assert.assertTrue(family.contains(new ByteArrayWrapper(block1.getHash())));
        Assert.assertFalse(family.contains(new ByteArrayWrapper(uncle11.getHash())));
        Assert.assertFalse(family.contains(new ByteArrayWrapper(uncle12.getHash())));
        Assert.assertTrue(family.contains(new ByteArrayWrapper(block2.getHash())));
        Assert.assertTrue(family.contains(new ByteArrayWrapper(uncle21.getHash())));
        Assert.assertTrue(family.contains(new ByteArrayWrapper(uncle22.getHash())));
        Assert.assertFalse(family.contains(new ByteArrayWrapper(block3.getHash())));
        Assert.assertFalse(family.contains(new ByteArrayWrapper(uncle31.getHash())));
        Assert.assertFalse(family.contains(new ByteArrayWrapper(uncle32.getHash())));

        family = FamilyUtils.getFamily(store, block3, 3);

        Assert.assertNotNull(family);
        Assert.assertFalse(family.isEmpty());
        Assert.assertEquals(7, family.size());

        Assert.assertTrue(family.contains(new ByteArrayWrapper(genesis.getHash())));
        Assert.assertTrue(family.contains(new ByteArrayWrapper(block1.getHash())));
        Assert.assertTrue(family.contains(new ByteArrayWrapper(uncle11.getHash())));
        Assert.assertTrue(family.contains(new ByteArrayWrapper(uncle12.getHash())));
        Assert.assertTrue(family.contains(new ByteArrayWrapper(block2.getHash())));
        Assert.assertTrue(family.contains(new ByteArrayWrapper(uncle21.getHash())));
        Assert.assertTrue(family.contains(new ByteArrayWrapper(uncle22.getHash())));
        Assert.assertFalse(family.contains(new ByteArrayWrapper(block3.getHash())));
        Assert.assertFalse(family.contains(new ByteArrayWrapper(uncle31.getHash())));
        Assert.assertFalse(family.contains(new ByteArrayWrapper(uncle32.getHash())));
    }

    @Test
    public void getUnclesHeaders() {
        BlockStore store = createBlockStore();

        Block genesis = BlockGenerator.getInstance().getGenesisBlock();
        Block block1 = BlockGenerator.getInstance().createChildBlock(genesis);
        Block uncle11 = BlockGenerator.getInstance().createChildBlock(genesis);
        Block uncle111 = BlockGenerator.getInstance().createChildBlock(uncle11);
        Block uncle12 = BlockGenerator.getInstance().createChildBlock(genesis);
        Block uncle121 = BlockGenerator.getInstance().createChildBlock(uncle12);
        Block block2 = BlockGenerator.getInstance().createChildBlock(block1);
        Block uncle21 = BlockGenerator.getInstance().createChildBlock(block1);
        Block uncle22 = BlockGenerator.getInstance().createChildBlock(block1);
        Block block3 = BlockGenerator.getInstance().createChildBlock(block2);
        Block uncle31 = BlockGenerator.getInstance().createChildBlock(block2);
        Block uncle32 = BlockGenerator.getInstance().createChildBlock(block2);

        store.saveBlock(genesis, BigInteger.ONE, true);
        store.saveBlock(block1, BigInteger.ONE, true);
        store.saveBlock(uncle11, BigInteger.ONE, false);
        store.saveBlock(uncle12, BigInteger.ONE, false);
        store.saveBlock(uncle111, BigInteger.ONE, false);
        store.saveBlock(uncle121, BigInteger.ONE, false);
        store.saveBlock(block2, BigInteger.ONE, true);
        store.saveBlock(uncle21, BigInteger.ONE, false);
        store.saveBlock(uncle22, BigInteger.ONE, false);
        store.saveBlock(block3, BigInteger.ONE, true);
        store.saveBlock(uncle31, BigInteger.ONE, false);
        store.saveBlock(uncle32, BigInteger.ONE, false);

        List<BlockHeader> list = FamilyUtils.getUnclesHeaders(store, block3, 3);

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

        Block genesis = BlockGenerator.getInstance().getGenesisBlock();
        Block block1 = BlockGenerator.getInstance().createChildBlock(genesis);
        Block uncle11 = BlockGenerator.getInstance().createChildBlock(genesis);
        Block uncle12 = BlockGenerator.getInstance().createChildBlock(genesis);
        Block block2 = BlockGenerator.getInstance().createChildBlock(block1);
        Block uncle21 = BlockGenerator.getInstance().createChildBlock(block1);
        Block uncle22 = BlockGenerator.getInstance().createChildBlock(block1);
        Block block3 = BlockGenerator.getInstance().createChildBlock(block2);
        Block uncle31 = BlockGenerator.getInstance().createChildBlock(block2);
        Block uncle32 = BlockGenerator.getInstance().createChildBlock(block2);

        store.saveBlock(genesis, BigInteger.ONE, true);
        store.saveBlock(block1, BigInteger.ONE, true);
        store.saveBlock(uncle11, BigInteger.ONE, false);
        store.saveBlock(uncle12, BigInteger.ONE, false);
        store.saveBlock(block2, BigInteger.ONE, true);
        store.saveBlock(uncle21, BigInteger.ONE, false);
        store.saveBlock(uncle22, BigInteger.ONE, false);
        store.saveBlock(block3, BigInteger.ONE, true);
        store.saveBlock(uncle31, BigInteger.ONE, false);
        store.saveBlock(uncle32, BigInteger.ONE, false);

        Set<ByteArrayWrapper> family = FamilyUtils.getUncles(store, block3, 3);

        Assert.assertNotNull(family);
        Assert.assertFalse(family.isEmpty());
        Assert.assertEquals(4, family.size());

        Assert.assertFalse(family.contains(new ByteArrayWrapper(genesis.getHash())));
        Assert.assertFalse(family.contains(new ByteArrayWrapper(block1.getHash())));
        Assert.assertTrue(family.contains(new ByteArrayWrapper(uncle11.getHash())));
        Assert.assertTrue(family.contains(new ByteArrayWrapper(uncle12.getHash())));
        Assert.assertFalse(family.contains(new ByteArrayWrapper(block2.getHash())));
        Assert.assertTrue(family.contains(new ByteArrayWrapper(uncle21.getHash())));
        Assert.assertTrue(family.contains(new ByteArrayWrapper(uncle22.getHash())));
        Assert.assertFalse(family.contains(new ByteArrayWrapper(block3.getHash())));
        Assert.assertFalse(family.contains(new ByteArrayWrapper(uncle31.getHash())));
        Assert.assertFalse(family.contains(new ByteArrayWrapper(uncle32.getHash())));
    }

    private static BlockStore createBlockStore() {
        IndexedBlockStore blockStore = new IndexedBlockStore();
        blockStore.init(new HashMap<>(), new HashMapDB(), null);

        return blockStore;
    }

    private static boolean containsHash(byte[] hash, List<BlockHeader> headers) {
        for (BlockHeader header : headers)
            if (Arrays.equals(hash, header.getHash()))
                return true;

        return false;
    }
}
