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

import co.rsk.config.RskSystemProperties;
import co.rsk.core.DifficultyCalculator;
import co.rsk.core.commons.RskAddress;
import org.ethereum.core.BlockHeader;
import org.ethereum.vm.DataWord;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Angel J Lopez
 * @since 02.23.2016
 */
public class DifficultyRuleTest {
    private final DifficultyRule rule = new DifficultyRule(new DifficultyCalculator(new RskSystemProperties()));

    @Ignore
    @Test // pass rule
    public void parentDifficultyLessHeaderDifficulty() {
        BlockHeader header = getHeader(10004);
        BlockHeader parent = getHeader(10000);
        assertTrue(rule.validate(header, parent));
    }

    @Test // no pass rule
    public void parentDifficultyEqualHeaderDifficulty() {
        BlockHeader header = getHeader(10000);
        BlockHeader parent = getHeader(10000);
        assertFalse(rule.validate(header, parent));
    }

    private static BlockHeader getHeader(long difficultyValue) {
        byte[] difficulty = new DataWord(difficultyValue).getData();

        BlockHeader header = new BlockHeader(null, null, RskAddress.nullAddress().getBytes(), null, difficulty, 0,
                null, 0,
                0, null, null, 0);

        return header;
    }
}