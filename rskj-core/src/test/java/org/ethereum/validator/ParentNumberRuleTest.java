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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Angel J Lopez
 * @since 02.23.2016
 */
class ParentNumberRuleTest {
    private static final BlockFactory blockFactory = new BlockFactory(ActivationConfigsForTest.all());
    private ParentNumberRule rule = new ParentNumberRule();

    @Test // pass rule
    void parentNumberEqualBlockNumberMinusOne() {
        BlockHeader header = getHeader(10000);
        BlockHeader parent = getHeader(9999);
        assertTrue(rule.validate(header, parent));
    }

    @Test // no pass rule
    void parentNumberEqualBlockNumber() {
        BlockHeader header = getHeader(100);
        BlockHeader parent = getHeader(100);
        assertFalse(rule.validate(header, parent));
    }

    @Test // no pass rule
    void parentNumberGreaterThanBlockNumber() {
        BlockHeader header = getHeader(100);
        BlockHeader parent = getHeader(101);
        assertFalse(rule.validate(header, parent));
    }

    private static BlockHeader getHeader(long number) {
        return blockFactory.getBlockHeaderBuilder()
                .setCoinbase(TestUtils.generateAddress(String.valueOf(number)))
                .setDifficulty(BlockDifficulty.ZERO)
                .setNumber(number)
                .build();
    }
}
