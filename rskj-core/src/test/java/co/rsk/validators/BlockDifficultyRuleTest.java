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

import co.rsk.core.BlockDifficulty;
import co.rsk.core.DifficultyCalculator;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlockDifficultyRuleTest {
    private Block parent;
    private BlockHeader parentHeader;
    private Block block;
    private BlockHeader blockHeader;
    private DifficultyCalculator difficultyCalculator;
    private BlockDifficultyRule rule;

    @BeforeEach
    void setup() {
        parent = mock(Block.class);
        parentHeader = mock(BlockHeader.class);
        when(parent.getHeader()).thenReturn(parentHeader);
        block = mock(Block.class);
        blockHeader = mock(BlockHeader.class);
        when(block.getHeader()).thenReturn(blockHeader);
        difficultyCalculator = mock(DifficultyCalculator.class);
        rule = new BlockDifficultyRule(difficultyCalculator);
    }

    @Test
    void validWhenCalculatedDifficultyMatches() {
        whenCalculatedDifficulty(452);
        whenBlockDifficulty(452);

        Assertions.assertTrue(rule.isValid(block, parent));
    }

    @Test
    void invalidWhenCalculatedDifficultyDoesntMatch() {
        whenCalculatedDifficulty(452);
        whenBlockDifficulty(999);

        Assertions.assertFalse(rule.isValid(block, parent));
    }

    private void whenBlockDifficulty(int difficulty) {
        when(blockHeader.getDifficulty()).thenReturn(new BlockDifficulty(BigInteger.valueOf(difficulty)));
    }

    private void whenCalculatedDifficulty(int difficulty) {
        when(difficultyCalculator.calcDifficulty(blockHeader, parentHeader))
                .thenReturn(new BlockDifficulty(BigInteger.valueOf(difficulty)));
    }
}
