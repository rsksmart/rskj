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

package co.rsk.core;

import org.ethereum.config.Constants;
import org.ethereum.core.Block;

import java.math.BigInteger;

public class SuperDifficultyCalculator {
    private final Constants constants;

    public SuperDifficultyCalculator(Constants constants) {
        this.constants = constants;
    }

    public BlockDifficulty calcSuperDifficulty(Block block, Block superParentBlock) {
        final var shouldUpdateDifficulty = block.getNumber() % constants.getSuperBlockRetargetingInterval().longValue() == 0;

        if (shouldUpdateDifficulty) {
            return getSuperBlockDifficulty(block, superParentBlock);
        }

        return superParentBlock.getSuperBlockFields().getSuperDifficulty();
    }

    private BlockDifficulty getSuperBlockDifficulty(Block curBlock, Block retargetingBlock) {
        var currentTimestamp = curBlock.getTimestamp() - retargetingBlock.getTimestamp();

        if (currentTimestamp <= 0) {
            currentTimestamp = 1;
        }

        final var correctionFactor = constants.getSuperBlockExpectedRetargetIntervalInSeconds()
                .divide(BigInteger.valueOf(currentTimestamp));

        final var multipliedCorrectionFactor = correctionFactor.shiftLeft(constants.getSuperBlockDifficultyCorrectionFactorShift());

        return calcSuperDifficultyWithTimeStamps(
                retargetingBlock.getSuperBlockFields().getSuperDifficulty(),
                multipliedCorrectionFactor,
                constants.getSuperBlockDifficultyMinCorrectionFactor(),
                constants.getSuperBlockDifficultyMaxCorrectionFactor(),
                constants.getSuperBlockDifficultyCorrectionFactorShift());
    }

    private static BlockDifficulty calcSuperDifficultyWithTimeStamps(
            BlockDifficulty oldDifficulty,
            BigInteger correctionFactor,
            BigInteger superBlockDifficultyMinCorrectionFactor,
            BigInteger superBlockDifficultyMaxCorrectionFactor,
            Integer superBlockDifficultyCorrectionFactorShift) {

        var correctionFactorToUse = BigInteger.ZERO;

        if (correctionFactor.compareTo(superBlockDifficultyMinCorrectionFactor) < 0) {
            correctionFactorToUse = superBlockDifficultyMinCorrectionFactor;
        } else if (correctionFactor.compareTo(superBlockDifficultyMaxCorrectionFactor) > 0) {
            correctionFactorToUse = superBlockDifficultyMaxCorrectionFactor;
        } else {
            correctionFactorToUse = correctionFactor.shiftRight(superBlockDifficultyCorrectionFactorShift);
        }

        return computeSuperBlockDifficulty(oldDifficulty, correctionFactorToUse);
    }

    private static BlockDifficulty computeSuperBlockDifficulty(
            BlockDifficulty oldDifficulty,
            BigInteger correctionFactor
    ) {
        var newSuperDifficulty = oldDifficulty
                .multiply(correctionFactor)
                .asBigInteger();

        return new BlockDifficulty(newSuperDifficulty);
    }
}
