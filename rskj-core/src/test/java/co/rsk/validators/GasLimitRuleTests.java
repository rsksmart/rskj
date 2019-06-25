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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.vm.DataWord;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * @author Angel J Lopez
 * @since 02.23.2016
 */
public class GasLimitRuleTests {
    private GasLimitRule rule = new GasLimitRule(3000000);

    private BlockHeader blockHeader;
    private Block block;

    @Before
    public void setUp() {
        blockHeader = Mockito.mock(BlockHeader.class);
        block = Mockito.mock(Block.class);
        Mockito.when(block.getHeader()).thenReturn(blockHeader);
    }

    @Test // pass rule
    public void gasLimitGreaterThanMinimumGasLimit() {
        Mockito.when(blockHeader.getGasLimit()).thenReturn(DataWord.valueOf(3000000 + 1).getData());
        assertTrue(rule.isValid(block));
    }

    @Test // pass rule
    public void gasLimitEqualMinimumGasLimit() {
        Mockito.when(blockHeader.getGasLimit()).thenReturn(DataWord.valueOf(3000000).getData());
        assertTrue(rule.isValid(block));
    }

    @Test // no pass rule
    public void gasLimitLessThanMinimumGasLimit() {
        Mockito.when(blockHeader.getGasLimit()).thenReturn(DataWord.valueOf(3000000 - 1).getData());
        assertFalse(rule.isValid(block));
    }
}
