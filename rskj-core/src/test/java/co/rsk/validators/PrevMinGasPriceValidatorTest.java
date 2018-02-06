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

package co.rsk.validators;

import co.rsk.core.Coin;
import org.ethereum.core.Block;
import org.ethereum.db.BlockStore;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Created by mario on 26/12/16.
 */
public class PrevMinGasPriceValidatorTest {

    private static final Coin BLOCK_MGP = Coin.valueOf(1050L);
    private static final Coin PARENT_BLOCK_MGP = Coin.valueOf(1000L);
    private static final byte[] PARENT_HASH = {00, 01, 02, 03};

    @Test
    public void noParentBlock() {
        Block block = Mockito.mock(Block.class);

        Mockito.when(block.getParentHash()).thenReturn(PARENT_HASH);
        Mockito.when(block.getMinimumGasPrice()).thenReturn(BLOCK_MGP);

        PrevMinGasPriceRule pmgpv = new PrevMinGasPriceRule();

        Assert.assertFalse(pmgpv.isValid(block, null));
    }

    @Test
    public void genesisBlock() {
        Block block = Mockito.mock(Block.class);

        Mockito.when(block.isGenesis()).thenReturn(true);
        Mockito.when(block.getMinimumGasPrice()).thenReturn(BLOCK_MGP);

        PrevMinGasPriceRule pmgpv = new PrevMinGasPriceRule();

        Assert.assertTrue(pmgpv.isValid(block, null));
    }

    @Test
    public void noMinGasPrice() {
        Block block = Mockito.mock(Block.class);
        Block parent = Mockito.mock(Block.class);

        Mockito.when(block.isGenesis()).thenReturn(false);
        Mockito.when(block.getMinimumGasPrice()).thenReturn(null);

        PrevMinGasPriceRule pmgpv = new PrevMinGasPriceRule();

        Assert.assertFalse(pmgpv.isValid(block, parent));
    }

    @Test
    public void outOfValidMGPRangeBlock() {
        Block block = Mockito.mock(Block.class);
        Block parent = Mockito.mock(Block.class);

        Mockito.when(block.getParentHash()).thenReturn(PARENT_HASH);
        Mockito.when(block.getMinimumGasPrice()).thenReturn(BLOCK_MGP);
        Mockito.when(parent.getMinimumGasPrice()).thenReturn(Coin.valueOf(10L));

        PrevMinGasPriceRule pmgpv = new PrevMinGasPriceRule();

        Assert.assertFalse(pmgpv.isValid(block, parent));

    }

    @Test
    public void validMGPInNewBlock() {
        Block block = Mockito.mock(Block.class);
        Block parent = Mockito.mock(Block.class);
        BlockStore blockStore = Mockito.mock(BlockStore.class);

        Mockito.when(block.getParentHash()).thenReturn(PARENT_HASH);
        Mockito.when(block.getMinimumGasPrice()).thenReturn(BLOCK_MGP);
        Mockito.when(parent.getMinimumGasPrice()).thenReturn(PARENT_BLOCK_MGP);

        PrevMinGasPriceRule pmgpv = new PrevMinGasPriceRule();

        Assert.assertFalse(pmgpv.isValid(block, parent));
    }
}
