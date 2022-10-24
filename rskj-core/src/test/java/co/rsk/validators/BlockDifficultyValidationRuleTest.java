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

import co.rsk.core.BlockDifficulty;
import co.rsk.core.DifficultyCalculator;
import org.ethereum.TestUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockHeader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigInteger;

/**
 * Created by sergio on 23/01/17.
 */
class BlockDifficultyValidationRuleTest {

    private BlockFactory blockFactory;
    private ActivationConfig activationConfig;
    private Constants networkConstants;

    @BeforeEach
    void setUp() {
        activationConfig = ActivationConfigsForTest.all();
        networkConstants = Constants.regtest();
        blockFactory = new BlockFactory(activationConfig);
    }

    private BlockHeader getEmptyHeader(BlockDifficulty difficulty, long blockTimestamp, int uCount) {
        return blockFactory.getBlockHeaderBuilder()
                .setCoinbase(TestUtils.randomAddress())
                .setDifficulty(difficulty)
                .setTimestamp(blockTimestamp)
                .setUncleCount(uCount)
                .build();
    }

    @Test
    void testDifficulty() {
        DifficultyCalculator difficultyCalculator = new DifficultyCalculator(activationConfig, networkConstants);
        BlockDifficultyRule validationRule = new BlockDifficultyRule(difficultyCalculator);

        Block block = Mockito.mock(Block.class);
        Block parent= Mockito.mock(Block.class);
        long parentTimestamp = 0;
        long blockTimeStamp  = 10;

        BlockDifficulty parentDifficulty = new BlockDifficulty(new BigInteger("2048"));
        BlockDifficulty blockDifficulty = new BlockDifficulty(new BigInteger("2049"));

        //blockDifficulty = blockDifficulty.add(AbstractConfig.getConstants().getDifficultyBoundDivisor());

        Mockito.when(block.getDifficulty())
                .thenReturn(blockDifficulty);

        BlockHeader blockHeader =getEmptyHeader(blockDifficulty, blockTimeStamp ,1);

        BlockHeader parentHeader = Mockito.mock(BlockHeader.class);

        Mockito.when(parentHeader.getDifficulty())
                .thenReturn(parentDifficulty);

        Mockito.when(block.getHeader())
                .thenReturn(blockHeader);

        Mockito.when(parent.getHeader())
                .thenReturn(parentHeader);

        Mockito.when(parent.getDifficulty())
                .thenReturn(parentDifficulty);

        Mockito.when(parent.getTimestamp())
                .thenReturn(parentTimestamp);

        Assertions.assertEquals(true,validationRule.isValid(block,parent));
    }

}
