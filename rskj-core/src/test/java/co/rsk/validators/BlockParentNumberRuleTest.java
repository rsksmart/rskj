/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BlockParentNumberRuleTest {
    private Block parent;
    private BlockHeader parentHeader;
    private Block block;
    private BlockHeader blockHeader;
    private BlockParentNumberRule rule;

    @Before
    public void setup() {
        parent = mock(Block.class);
        parentHeader = mock(BlockHeader.class);
        when(parent.getHeader()).thenReturn(parentHeader);
        block = mock(Block.class);
        blockHeader = mock(BlockHeader.class);
        when(block.getHeader()).thenReturn(blockHeader);
        rule = new BlockParentNumberRule();
    }

    @Test
    public void validWhenNumberIsOneMore() {
        whenBlockNumber(parentHeader, 451);
        whenBlockNumber(blockHeader, 452);

        Assert.assertTrue(rule.isValid(block, parent));
    }

    @Test
    public void invalidWhenNumberIsTheSame() {
        whenBlockNumber(parentHeader, 451);
        whenBlockNumber(blockHeader, 451);

        Assert.assertFalse(rule.isValid(block, parent));
    }

    @Test
    public void invalidWhenNumberIsLess() {
        whenBlockNumber(parentHeader, 451);
        whenBlockNumber(blockHeader, 450);

        Assert.assertFalse(rule.isValid(block, parent));
    }

    @Test
    public void invalidWhenNumberIsMoreThanOne() {
        whenBlockNumber(parentHeader, 451);
        whenBlockNumber(blockHeader, 999);

        Assert.assertFalse(rule.isValid(block, parent));
    }

    private void whenBlockNumber(BlockHeader header, long number) {
        when(header.getNumber()).thenReturn(number);
    }
}