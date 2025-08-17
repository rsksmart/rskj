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

import com.google.common.annotations.VisibleForTesting;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.BlockHeader;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;

import static org.ethereum.util.BIUtil.max;

public class DifficultyCalculator {
    public static final double BLOCK_COUNT_WINDOW = 30.0; // last N blocks
 
    private static final double BLOCK_TARGET = 20.0; // target time between blocks
    private static final double UNCLE_TRESHOLD = 0.7; // 70 %

    private static final BigDecimal ALPHA_POS = BigDecimal.valueOf(1.005); // 1 + 0.005
    private static final BigDecimal ALPHA_NEG = BigDecimal.valueOf(0.995);  // 1 - 0.005

    // private static final BigInteger ALPHA_POS = BigInteger.valueOf(1029); // 1 + 0.005 == 1029/(2^10)
    // private static final BigInteger ALPHA_NEG = BigInteger.valueOf(1019); // 1 - 0.005 == 1019/(2^10)
    // private static final BigInteger SCALE = BigInteger.valueOf(2<<10);
 
    private final ActivationConfig activationConfig;
    private final Constants constants;

    // todo(fede) there are tons of NON RELATED tests that should be modified to add the new difficulty algorithm
    //   they will be tackled when we move to the next stage
    public static boolean TEST_NEW_DIFFICULTY = false;
    @VisibleForTesting
    public static void enableTesting() {
        TEST_NEW_DIFFICULTY = true;
    }

    @VisibleForTesting
    public static void disableTesting() {
        TEST_NEW_DIFFICULTY = false;
    }

    public DifficultyCalculator(ActivationConfig activationConfig, Constants constants) {
        this.activationConfig = activationConfig;
        this.constants = constants;
    }

    public BlockDifficulty calcDifficulty(BlockHeader header, BlockHeader parentHeader, List<BlockHeader> blockWindow) {
        long blockNumber = header.getNumber();

        boolean rskip517Active = activationConfig.isActive(ConsensusRule.RSKIP517, blockNumber);
        if(rskip517Active && TEST_NEW_DIFFICULTY) {
            if (blockWindow == null || blockWindow.isEmpty()) {
                throw new IllegalArgumentException("block window shouldn't be null or empty");
            }

            return getBlockDifficultyRskip517(header, parentHeader, blockWindow);
        }

        boolean rskip97Active = activationConfig.isActive(ConsensusRule.RSKIP97, blockNumber);
        if (!rskip97Active) {
            // If more than 10 minutes, reset to minimum difficulty to allow private mining
            if (header.getTimestamp() >= parentHeader.getTimestamp() + 600) {
                return constants.getMinimumDifficulty(blockNumber);
            }
        }

        return getBlockDifficulty(header, parentHeader);
    }

    private BlockDifficulty getBlockDifficultyRskip517(BlockHeader header, BlockHeader parentHeader, List<BlockHeader> blockWindow) {
        if (blockWindow.size() != BLOCK_COUNT_WINDOW) {
            throw new IllegalStateException("block window should match the expected size");
        }

        double blockTimeAverage = calcBlockTimeAverage(blockWindow);
        double uncleRate = calcUncleRate(blockWindow);

        BigDecimal factor;
        if (uncleRate > UNCLE_TRESHOLD) {
            factor = ALPHA_POS;
        } else if (uncleRate <= UNCLE_TRESHOLD && blockTimeAverage > (BLOCK_TARGET * 1.1)) {
            factor = ALPHA_POS;
        } else if (uncleRate <= UNCLE_TRESHOLD && blockTimeAverage < (BLOCK_TARGET * 0.9)) {
            factor = ALPHA_NEG;
        } else {
            throw new IllegalStateException("this shouldn't happen");
        }

        BigInteger newDifficulty = new BigDecimal(parentHeader.getDifficulty().asBigInteger())
            .multiply(factor)
            .setScale(0, RoundingMode.DOWN)
            .toBigInteger();
        BigInteger minDifficulty = constants.getMinimumDifficulty(header.getNumber()).asBigInteger();

        return new BlockDifficulty(minDifficulty.max(newDifficulty));
    }

    public double calcBlockTimeAverage(List<BlockHeader> blockWindow) {
        if (blockWindow.size() != BLOCK_COUNT_WINDOW) {
            throw new IllegalArgumentException("block window has a different size");
        }

        long firstBlockTimestamp = blockWindow.get(0).getTimestamp();
        long lastBlockTimestamp = blockWindow.get(blockWindow.size() - 1).getTimestamp();

        if (firstBlockTimestamp > lastBlockTimestamp) {
            throw new IllegalArgumentException("first block should have a lower timestamp");
        }

        return (lastBlockTimestamp - firstBlockTimestamp) / BLOCK_COUNT_WINDOW;
    }

    public double calcUncleRate(List<BlockHeader> blockWindow) {
        if (blockWindow.size() != BLOCK_COUNT_WINDOW) {
            throw new IllegalArgumentException("block window has a different size");
        }

        double totalUncles = 0;
        for(BlockHeader header : blockWindow) {
            totalUncles += header.getUncleCount();
        }

        return totalUncles / blockWindow.size();
    }

    private BlockDifficulty getBlockDifficulty(
            BlockHeader curBlockHeader,
            BlockHeader parent) {
        BlockDifficulty pd = parent.getDifficulty();
        long parentBlockTS = parent.getTimestamp();
        int uncleCount = curBlockHeader.getUncleCount();
        long curBlockTS = curBlockHeader.getTimestamp();
        int duration = constants.getDurationLimit();
        BigInteger difDivisor = constants.getDifficultyBoundDivisor(activationConfig.forBlock(curBlockHeader.getNumber()));
        BlockDifficulty minDif = constants.getMinimumDifficulty(curBlockHeader.getNumber());
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
