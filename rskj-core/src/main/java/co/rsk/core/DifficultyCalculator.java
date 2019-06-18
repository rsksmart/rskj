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
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.BlockHeader;

import java.math.BigInteger;

import static org.ethereum.util.BIUtil.max;

public class DifficultyCalculator {
    private final ActivationConfig activationConfig;
    private final Constants constants;

    public DifficultyCalculator(ActivationConfig activationConfig, Constants constants) {
        this.activationConfig = activationConfig;
        this.constants = constants;
    }

    public BlockDifficulty calcDifficulty(BlockHeader header, BlockHeader parentHeader) {
        boolean rskip97Active = activationConfig.isActive(ConsensusRule.RSKIP97, header.getNumber());
        if (!rskip97Active) {
            // If more than 10 minutes, reset to minimum difficulty to allow private mining
            if (header.getTimestamp() >= parentHeader.getTimestamp() + 600) {
                return constants.getMinimumDifficulty();
            }
        }

        return getBlockDifficulty(header, parentHeader, constants);
    }

    private static BlockDifficulty getBlockDifficulty(
            BlockHeader curBlockHeader,
            BlockHeader parent,
            Constants constants) {
        BlockDifficulty pd = parent.getDifficulty();
        long parentBlockTS = parent.getTimestamp();
        int uncleCount = curBlockHeader.getUncleCount();
        long curBlockTS = curBlockHeader.getTimestamp();
        int duration = constants.getDurationLimit();
        BigInteger difDivisor = constants.getDifficultyBoundDivisor();
        BlockDifficulty minDif = constants.getMinimumDifficulty();
        return calcDifficultyWithTimeStamps(curBlockTS, parentBlockTS, pd, uncleCount, duration, difDivisor, minDif);
    }

    private static BlockDifficulty calcDifficultyWithTimeStamps(
            long curBlockTS,
            long parentBlockTS,
            BlockDifficulty pd,
            int uncleCount,
            int duration,
            BigInteger difDivisor,
            BlockDifficulty minDif) {
        long delta = curBlockTS - parentBlockTS;
        if (delta < 0) {
            return pd;
        }

        int calcDur = (1 + uncleCount) * duration;
        int sign = 0;
        if (calcDur > delta) {
            sign = 1;
        } else if (calcDur < delta) {
            sign = -1;
        }

        if (sign == 0) {
            return pd;
        }

        BigInteger pdValue = pd.asBigInteger();
        BigInteger quotient = pdValue.divide(difDivisor);

        BigInteger fromParent;
        if (sign == 1) {
            fromParent = pdValue.add(quotient);
        } else {
            fromParent = pdValue.subtract(quotient);
        }

        // If parent difficulty is zero (maybe a genesis block),
        // then the first child difficulty MUST
        // be greater or equal getMinimumDifficulty(). That's why the max() is applied in both the add and the sub
        // cases.
        // Note that we have to apply max() first in case fromParent ended up being negative.
        return new BlockDifficulty(max(minDif.asBigInteger(), fromParent));
    }
}
