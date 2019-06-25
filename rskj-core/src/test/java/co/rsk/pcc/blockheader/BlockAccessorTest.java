/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package co.rsk.pcc.blockheader;

import static org.mockito.Mockito.mock;

import co.rsk.pcc.ExecutionEnvironment;
import co.rsk.pcc.NativeContractIllegalArgumentException;
import java.util.Optional;
import org.ethereum.core.Block;
import org.ethereum.db.BlockStore;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class BlockAccessorTest {
    private static short MAXIMUM_BLOCK_DEPTH = 100;
    private static short NEGATIVE_BLOCK_DEPTH = -1;
    private static short ZERO_BLOCK_DEPTH = 0;
    private static short ONE_BLOCK_DEPTH = 1;

    private BlockStore blockStore;
    private BlockAccessor blockAccessor;
    private ExecutionEnvironment executionEnvironment;

    @Before
    public void createBlockAccessor() {
        blockAccessor = new BlockAccessor(MAXIMUM_BLOCK_DEPTH);
    }

    @Test
    public void getBlockBeyondMaximumBlockDepth() {
        executionEnvironment = mock(ExecutionEnvironment.class);

        Assert.assertFalse(
                blockAccessor.getBlock(MAXIMUM_BLOCK_DEPTH, executionEnvironment).isPresent());
        Assert.assertFalse(
                blockAccessor
                        .getBlock((short) (MAXIMUM_BLOCK_DEPTH + 1), executionEnvironment)
                        .isPresent());
    }

    @Test(expected = NativeContractIllegalArgumentException.class)
    public void getBlockWithNegativeDepth() {
        executionEnvironment = mock(ExecutionEnvironment.class);

        blockAccessor.getBlock(NEGATIVE_BLOCK_DEPTH, executionEnvironment);
    }

    @Test
    public void getGenesisBlock() {
        ExecutionEnvironment executionEnvironment =
                EnvironmentUtils.getEnvironmentWithBlockchainOfLength(1);

        Optional<Block> genesis = blockAccessor.getBlock(ZERO_BLOCK_DEPTH, executionEnvironment);
        Optional<Block> firstBlock = blockAccessor.getBlock(ONE_BLOCK_DEPTH, executionEnvironment);

        Assert.assertTrue(genesis.isPresent());
        Assert.assertFalse(firstBlock.isPresent());

        Assert.assertEquals(0, genesis.get().getNumber());
    }

    @Test
    public void getTenBlocksFromTheTip() {
        ExecutionEnvironment executionEnvironment =
                EnvironmentUtils.getEnvironmentWithBlockchainOfLength(100);

        for (short i = 0; i < 10; i++) {
            Optional<Block> block = blockAccessor.getBlock(i, executionEnvironment);
            Assert.assertTrue(block.isPresent());
            Assert.assertEquals(99 - i, block.get().getNumber());
        }
    }
}
