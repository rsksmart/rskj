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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

/**
 * Created by ajlopez on 8/6/2016.
 */
public class BlockBuilderTest {
    @Test
    public void buildBlockWithGenesisAsParent() {
        Block genesis = new BlockGenerator().getGenesisBlock();

        BlockBuilder builder = new BlockBuilder(null, null, null);

        Block block = builder
                .parent(genesis)
                .build();

        Assertions.assertNotNull(block);
        Assertions.assertEquals(1, block.getNumber());
        // Assertions.assertTrue(genesis.getCumulativeDifficulty().compareTo(block.getDifficultyBI()) < 0);
        Assertions.assertEquals(genesis.getHash(), block.getParentHash());
    }

    @Test
    public void buildBlockWithDifficulty() {
        Block genesis = new BlockGenerator().getGenesisBlock();

        BlockBuilder builder = new BlockBuilder(null, null, null);

        Block block = builder
                .parent(genesis)
                .difficulty(1)
                .build();

        Assertions.assertNotNull(block);
        Assertions.assertEquals(1, block.getNumber());
        Assertions.assertEquals(BigInteger.ONE, block.getDifficulty().asBigInteger());
        Assertions.assertEquals(genesis.getHash(), block.getParentHash());
    }
}
