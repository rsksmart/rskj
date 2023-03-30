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
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.math.BigInteger;
import java.util.stream.Stream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlockParentGasLimitRuleTest {
    private Block parent;
    private BlockHeader parentHeader;
    private Block block;
    private BlockHeader blockHeader;
    private BlockParentGasLimitRule rule;

    @BeforeEach
    void setup() {
        parent = mock(Block.class);
        parentHeader = mock(BlockHeader.class);
        when(parent.getHeader()).thenReturn(parentHeader);
        block = mock(Block.class);
        blockHeader = mock(BlockHeader.class);
        when(block.getHeader()).thenReturn(blockHeader);
    }

    @Test
    void cantConstructRuleWithZeroGasLimitBoundDivisor() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> whenGasLimitBoundDivisor(0));
    }

    @Test
    void cantConstructRuleWithNegativeGasLimitBoundDivisor() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> whenGasLimitBoundDivisor(-1));
    }

    @ParameterizedTest(name = "when gas {0} (divisor {1} and limits [{2}, {3}]) then expect valid {4}")
    @ArgumentsSource(GasLimitsArgumentsProvider.class)
    void validityWhenGas(String situation, int gasLimitBoundDivisor, int gasLimitParent, int gasLimit, boolean valid) {
        whenGasLimitBoundDivisor(gasLimitBoundDivisor);
        whenGasLimit(parentHeader, gasLimitParent);
        whenGasLimit(blockHeader, gasLimit);

        Assertions.assertEquals(valid, rule.isValid(block, parent, null));
    }

    private void whenGasLimitBoundDivisor(int gasLimitBoundDivisor) {
        rule = new BlockParentGasLimitRule(gasLimitBoundDivisor);
    }

    private void whenGasLimit(BlockHeader header, long gasLimit) {
        when(header.getGasLimit()).thenReturn(BigIntegers.asUnsignedByteArray(BigInteger.valueOf(gasLimit)));
    }

    private static class GasLimitsArgumentsProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                    Arguments.of("is the same", 10, 1000, 1000, true),
                    Arguments.of("on left limit", 10, 1000, 900, true),
                    Arguments.of("on right limit", 20, 1000, 1050, true),
                    Arguments.of("on left limit", 10, 1000, 899, false),
                    Arguments.of("on right limit", 20, 1000, 1051, false)
            );
        }
    }
}
