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

import co.rsk.core.DifficultyCalculator;
import org.ethereum.TestUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockHeader;
import org.ethereum.vm.DataWord;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Angel J Lopez
 * @since 02.23.2016
 */
class DifficultyRuleTest {
    private final ActivationConfig activationConfig = ActivationConfigsForTest.all();
    private final BlockFactory blockFactory = new BlockFactory(activationConfig);
    private final DifficultyRule rule = new DifficultyRule(new DifficultyCalculator(activationConfig, Constants.regtest()));

    @Disabled("???")
    @Test // pass rule
    void parentDifficultyLessHeaderDifficulty() {
        BlockHeader header = getHeader(10004);
        BlockHeader parent = getHeader(10000);
        assertTrue(rule.validate(header, parent));
    }

    @Test // no pass rule
    void parentDifficultyEqualHeaderDifficulty() {
        BlockHeader header = getHeader(10000);
        BlockHeader parent = getHeader(10000);
        assertFalse(rule.validate(header, parent));
    }

    private BlockHeader getHeader(long difficultyValue) {
        byte[] difficulty = DataWord.valueOf(difficultyValue).getData();

        return blockFactory.getBlockHeaderBuilder()
                .setCoinbase(TestUtils.randomAddress(String.valueOf(difficultyValue)))
                .setDifficultyFromBytes(difficulty)
                .build();
    }
}
