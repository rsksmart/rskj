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

import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigInteger;

/**
 * Created by martin.medina on 07/02/17.
 */
class ValidGasUsedValidatorTest {

    private BlockHeader blockHeader;
    private Block block;

    @BeforeEach
    void setUp() {
        blockHeader = Mockito.mock(BlockHeader.class);
        block = Mockito.mock(Block.class);
        Mockito.when(block.getHeader()).thenReturn(blockHeader);
    }

    @Test
    void blockWithValidGasUsed() {
        Mockito.when(blockHeader.getGasUsed()).thenReturn(20L);
        Mockito.when(blockHeader.getGasLimit()).thenReturn(BigInteger.valueOf(107L).toByteArray());

        ValidGasUsedRule gasUsedRule = new ValidGasUsedRule();

        Assertions.assertTrue(gasUsedRule.isValid(block, null));
    }

    @Test
    void blockWithInvalidGasUsedBiggerThanGasLimit() {
        Mockito.when(blockHeader.getGasUsed()).thenReturn(120L);
        Mockito.when(blockHeader.getGasLimit()).thenReturn(BigInteger.valueOf(107L).toByteArray());

        ValidGasUsedRule gasUsedRule = new ValidGasUsedRule();

        Assertions.assertFalse(gasUsedRule.isValid(block, null));
    }

    @Test
    void blockWithInvalidGasUsedLessThanZero() {
        Mockito.when(blockHeader.getGasUsed()).thenReturn(-120L);
        Mockito.when(blockHeader.getGasLimit()).thenReturn(BigInteger.valueOf(107L).toByteArray());

        ValidGasUsedRule gasUsedRule = new ValidGasUsedRule();

        Assertions.assertFalse(gasUsedRule.isValid(block, null));
    }
}
