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

package co.rsk.remasc;

import co.rsk.blockchain.utils.BlockGenerator;
import org.ethereum.core.Block;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Created by ajlopez on 14/04/2017.
 */
public class RemascStateTest {
    @Test
    public void serializeAndDeserializeWithNoValues() {
        RemascState state = new RemascState(BigInteger.ZERO, BigInteger.ZERO, new TreeMap<>(), false);

        byte[] bytes = state.getEncoded();

        RemascState result = RemascState.create(bytes);

        Assert.assertNotNull(result);
        Assert.assertEquals(BigInteger.ZERO, result.getRewardBalance());
        Assert.assertEquals(BigInteger.ZERO, result.getBurnedBalance());
        Assert.assertNotNull(result.getSiblings());
        Assert.assertTrue(result.getSiblings().isEmpty());
        Assert.assertFalse(result.getBrokenSelectionRule());
    }

    @Test
    public void serializeAndDeserializeWithSomeValues() {
        Block genesis = BlockGenerator.getInstance().getGenesisBlock();
        Block block1 = BlockGenerator.getInstance().createChildBlock(genesis);
        Block block2 = BlockGenerator.getInstance().createChildBlock(block1);
        Block block3 = BlockGenerator.getInstance().createChildBlock(block2);
        Block block4 = BlockGenerator.getInstance().createChildBlock(block3);
        Block block5 = BlockGenerator.getInstance().createChildBlock(block4);

        Sibling sibling1 = new Sibling(genesis.getHeader(), genesis.getCoinbase(), 1);
        Sibling sibling2 = new Sibling(block1.getHeader(), block1.getCoinbase(), 2);
        Sibling sibling3 = new Sibling(block2.getHeader(), block2.getCoinbase(), 3);
        Sibling sibling4 = new Sibling(block3.getHeader(), block3.getCoinbase(), 4);
        Sibling sibling5 = new Sibling(block4.getHeader(), block4.getCoinbase(), 5);
        Sibling sibling6 = new Sibling(block5.getHeader(), block5.getCoinbase(), 6);

        List<Sibling> siblings0 = new ArrayList<>();
        List<Sibling> siblings1 = new ArrayList<>();
        List<Sibling> siblings2 = new ArrayList<>();

        siblings0.add(sibling1);
        siblings0.add(sibling2);

        siblings1.add(sibling3);
        siblings1.add(sibling4);

        siblings2.add(sibling5);
        siblings2.add(sibling6);

        SortedMap<Long, List<Sibling>> siblings = new TreeMap<>();

        siblings.put(Long.valueOf(0), siblings0);
        siblings.put(Long.valueOf(1), siblings1);
        siblings.put(Long.valueOf(2), siblings2);

        RemascState state = new RemascState(BigInteger.ONE, BigInteger.TEN, siblings, true);

        byte[] bytes = state.getEncoded();

        RemascState result = RemascState.create(bytes);

        Assert.assertNotNull(result);
        Assert.assertEquals(BigInteger.ONE, result.getRewardBalance());
        Assert.assertEquals(BigInteger.TEN, result.getBurnedBalance());
        Assert.assertNotNull(result.getSiblings());
        Assert.assertFalse(result.getSiblings().isEmpty());
        Assert.assertTrue(result.getBrokenSelectionRule());
    }
}
