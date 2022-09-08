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

package org.ethereum.validator;

import co.rsk.core.BlockDifficulty;
import org.ethereum.TestUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockHeader;
import org.ethereum.vm.DataWord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Angel J Lopez
 * @since 02.23.2016
 */
public class ParentGasLimitRuleTest {
    private final BlockFactory blockFactory = new BlockFactory(ActivationConfigsForTest.all());
    private ParentGasLimitRule rule = new ParentGasLimitRule(1024);

    @Test // pass rule
    public void parentGasLimitLessThanGasLimit() {
        BlockHeader header = getHeader(10000);
        BlockHeader parent = getHeader(9999);
        assertTrue(rule.validate(header, parent));
    }

    @Test // no pass rule
    public void parentGasLimitTooLessThanGasLimit() {
        BlockHeader header = getHeader(100);
        BlockHeader parent = getHeader(9);
        assertFalse(rule.validate(header, parent));
    }

    @Test // pass rule
    public void parentGasLimitGreaterThanGasLimit() {
        BlockHeader header = getHeader(10000);
        BlockHeader parent = getHeader(10001);
        assertTrue(rule.validate(header, parent));
    }

    @Test // no pass rule
    public void parentGasLimitTooGreaterThanGasLimit() {
        BlockHeader header = getHeader(9);
        BlockHeader parent = getHeader(100);
        assertFalse(rule.validate(header, parent));
    }

    @Test // no pass rule
    public void parentGasLimitOfBy1Tests() {
        BlockHeader parent = getHeader(2049);
        BlockHeader headerGGood = getHeader(2051);
        BlockHeader headerGBad = getHeader(2052);
        BlockHeader headerLGood = getHeader(2047);
        BlockHeader headerLBad = getHeader(2046);
        assertTrue(rule.validate(headerGGood, parent));
        assertTrue(rule.validate(headerLGood, parent));
        assertFalse(rule.validate(headerGBad, parent));
        assertFalse(rule.validate(headerLBad, parent));
    }


    // Used also by GasLimitCalculatorTest
    public BlockHeader getHeader(long gasLimitValue) {
        return getHeader(blockFactory, gasLimitValue);
    }

    // Used also by GasLimitCalculatorTest
    public static BlockHeader getHeader(BlockFactory blockFactory, long gasLimitValue) {
        byte[] gasLimit = DataWord.valueOf(gasLimitValue).getData();

        BlockHeader header = blockFactory.getBlockHeaderBuilder()
                .setCoinbase(TestUtils.randomAddress())
                .setDifficulty(BlockDifficulty.ZERO)
                .setGasLimit(gasLimit)
                .build();

        return header;
    }
}
