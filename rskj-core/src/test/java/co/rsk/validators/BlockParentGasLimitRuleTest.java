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

import org.bouncycastle.util.BigIntegers;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BlockParentGasLimitRuleTest {
    private Block parent;
    private BlockHeader parentHeader;
    private Block block;
    private BlockHeader blockHeader;
    private BlockParentGasLimitRule rule;

    @BeforeEach
    public void setup() {
        parent = mock(Block.class);
        parentHeader = mock(BlockHeader.class);
        when(parent.getHeader()).thenReturn(parentHeader);
        block = mock(Block.class);
        blockHeader = mock(BlockHeader.class);
        when(block.getHeader()).thenReturn(blockHeader);
    }

    @Test
    public void cantConstructRuleWithZeroGasLimitBoundDivisor() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> whenGasLimitBoundDivisor(0));
    }

    @Test
    public void cantConstructRuleWithNegativeGasLimitBoundDivisor() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> whenGasLimitBoundDivisor(-1));
    }

    @Test
    public void validWhenGasIsTheSame() {
        whenGasLimitBoundDivisor(10);
        whenGasLimit(parentHeader, 1000);
        whenGasLimit(blockHeader, 1000);

        Assertions.assertTrue(rule.isValid(block, parent));
    }

    @Test
    public void validWhenGasIsOnLeftLimit() {
        whenGasLimitBoundDivisor(10);
        whenGasLimit(parentHeader, 1000);
        whenGasLimit(blockHeader, 900);

        Assertions.assertTrue(rule.isValid(block, parent));
    }

    @Test
    public void validWhenGasIsOnRightLimit() {
        whenGasLimitBoundDivisor(20);
        whenGasLimit(parentHeader, 1000);
        whenGasLimit(blockHeader, 1050);

        Assertions.assertTrue(rule.isValid(block, parent));
    }

    @Test
    public void invalidWhenGasIsOnLeftLimit() {
        whenGasLimitBoundDivisor(10);
        whenGasLimit(parentHeader, 1000);
        whenGasLimit(blockHeader, 899);

        Assertions.assertFalse(rule.isValid(block, parent));
    }

    @Test
    public void invalidWhenGasIsOnRightLimit() {
        whenGasLimitBoundDivisor(20);
        whenGasLimit(parentHeader, 1000);
        whenGasLimit(blockHeader, 1051);

        Assertions.assertFalse(rule.isValid(block, parent));
    }

    private void whenGasLimitBoundDivisor(int gasLimitBoundDivisor) {
        rule = new BlockParentGasLimitRule(gasLimitBoundDivisor);
    }

    private void whenGasLimit(BlockHeader header, long gasLimit) {
        when(header.getGasLimit()).thenReturn(BigIntegers.asUnsignedByteArray(BigInteger.valueOf(gasLimit)));
    }
}
