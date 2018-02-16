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

package co.rsk.blockchain.utils;

import org.ethereum.core.Block;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * Created by ajlopez on 5/11/2016.
 */
public class BlockGeneratorTest {
    @Test
    public void getBlocks() {
        Block block;
        BlockGenerator blockGenerator = new BlockGenerator();

        for (int k = 0; k <= 4; k++) {
            block = blockGenerator.getBlock(k);

            Assert.assertNotNull(block);
            Assert.assertEquals(k, block.getNumber());
        }
    }

    @Test
    public void getBlockChain() {
        BlockGenerator blockGenerator = new BlockGenerator();
        List<Block> chain = blockGenerator.getBlockChain(blockGenerator.getBlock(2), 10);

        Assert.assertNotNull(chain);
        Assert.assertEquals(10, chain.size());

        Block parent = blockGenerator.getBlock(2);

        for (Block b : chain) {
            Assert.assertNotNull(b);
            Assert.assertEquals(parent.getHash(), b.getParentHash());
            Assert.assertEquals(parent.getNumber() + 1, b.getNumber());
            parent = b;
        }
    }
}
