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

package co.rsk.test.builderstest;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.test.builders.BlockBuilder;
import org.ethereum.core.Block;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;

/**
 * Created by ajlopez on 8/6/2016.
 */
public class BlockBuilderTest {
    @Test
    public void buildBlockWithGenesisAsParent() {
        Block genesis = new BlockGenerator().getGenesisBlock();

        BlockBuilder builder = new BlockBuilder();

        Block block = builder
                .parent(genesis)
                .build();

        Assert.assertNotNull(block);
        Assert.assertEquals(1, block.getNumber());
        // Assert.assertTrue(genesis.getCumulativeDifficulty().compareTo(block.getDifficultyBI()) < 0);
        Assert.assertEquals(genesis.getHash(), block.getParentHash());
    }

    @Test
    public void buildBlockWithDifficulty() {
        Block genesis = new BlockGenerator().getGenesisBlock();

        BlockBuilder builder = new BlockBuilder();

        Block block = builder
                .parent(genesis)
                .difficulty(1)
                .build();

        Assert.assertNotNull(block);
        Assert.assertEquals(1, block.getNumber());
        Assert.assertEquals(BigInteger.ONE, block.getDifficulty().asBigInteger());
        Assert.assertEquals(genesis.getHash(), block.getParentHash());
    }
}
