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
import co.rsk.crypto.Keccak256;
import org.ethereum.TestUtils;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Created by mario on 26/12/16.
 */
public class PrevMinGasPriceValidatorTest {

    private static final Coin BLOCK_MGP = Coin.valueOf(1050L);
    private static final Coin PARENT_BLOCK_MGP = Coin.valueOf(1000L);
    private static final byte[] PARENT_HASH = TestUtils.randomBytes(32);

    @Test
    public void noParentBlock() {
        BlockHeader header = Mockito.mock(BlockHeader.class);
        Block block = Mockito.mock(Block.class);
        Mockito.when(block.getHeader()).thenReturn(header);

        Mockito.when(header.getParentHash()).thenReturn(new Keccak256(PARENT_HASH));
        Mockito.when(header.getMinimumGasPrice()).thenReturn(BLOCK_MGP);

        PrevMinGasPriceRule pmgpv = new PrevMinGasPriceRule();

        Assertions.assertFalse(pmgpv.isValid(header, null));
    }

    @Test
    public void genesisBlock() {
        BlockHeader header = Mockito.mock(BlockHeader.class);
        Block block = Mockito.mock(Block.class);
        Mockito.when(block.getHeader()).thenReturn(header);

        Mockito.when(header.isGenesis()).thenReturn(true);
        Mockito.when(header.getMinimumGasPrice()).thenReturn(BLOCK_MGP);

        PrevMinGasPriceRule pmgpv = new PrevMinGasPriceRule();

        Assertions.assertTrue(pmgpv.isValid(header, null));
    }

    @Test
    public void noMinGasPrice() {
        BlockHeader header = Mockito.mock(BlockHeader.class);
        Block block = Mockito.mock(Block.class);
        Mockito.when(block.getHeader()).thenReturn(header);
        Block parent = Mockito.mock(Block.class);

        Mockito.when(header.isGenesis()).thenReturn(false);
        Mockito.when(header.getMinimumGasPrice()).thenReturn(null);

        PrevMinGasPriceRule pmgpv = new PrevMinGasPriceRule();

        Assertions.assertFalse(pmgpv.isValid(header, parent));
    }

    @Test
    public void outOfValidMGPRangeBlock() {
        BlockHeader header = Mockito.mock(BlockHeader.class);
        Block block = Mockito.mock(Block.class);
        Mockito.when(block.getHeader()).thenReturn(header);
        Block parent = Mockito.mock(Block.class);

        Mockito.when(header.getParentHash()).thenReturn(new Keccak256(PARENT_HASH));
        Mockito.when(header.getMinimumGasPrice()).thenReturn(BLOCK_MGP);
        Mockito.when(parent.getMinimumGasPrice()).thenReturn(Coin.valueOf(10L));

        PrevMinGasPriceRule pmgpv = new PrevMinGasPriceRule();

        Assertions.assertFalse(pmgpv.isValid(header, parent));

    }

    @Test
    public void validMGPInNewBlock() {
        BlockHeader header = Mockito.mock(BlockHeader.class);
        Block block = Mockito.mock(Block.class);
        Mockito.when(block.getHeader()).thenReturn(header);
        Block parent = Mockito.mock(Block.class);

        Mockito.when(header.getParentHash()).thenReturn(new Keccak256(PARENT_HASH));
        Mockito.when(header.getMinimumGasPrice()).thenReturn(BLOCK_MGP);
        Mockito.when(parent.getMinimumGasPrice()).thenReturn(PARENT_BLOCK_MGP);

        PrevMinGasPriceRule pmgpv = new PrevMinGasPriceRule();

        Assertions.assertFalse(pmgpv.isValid(header, parent));
    }
}
