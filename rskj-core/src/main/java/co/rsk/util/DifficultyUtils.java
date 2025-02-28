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

package co.rsk.util;

import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.core.BlockDifficulty;
import org.ethereum.config.Constants;
import org.ethereum.core.BlockHeader;

import java.math.BigInteger;
import java.util.Optional;

/**
 * Created by martin.medina on 4/8/16.
 */
public class DifficultyUtils {

    public static final BigInteger MAX = BigInteger.valueOf(2).pow(256);

    public static BigInteger difficultyToTarget(BlockDifficulty difficulty) {

        BigInteger resultDifficulty = difficulty.asBigInteger();

        if (resultDifficulty.compareTo(BigInteger.valueOf(2)) < 1) {
            // minDifficulty is 3 because target needs to be of length 256
            // and not have 1 in the position 255 (count start from 0)
            resultDifficulty = BigInteger.valueOf(3);
        }

        return MAX.divide(resultDifficulty);
    }

    /**
     * PoW factor, which is defined as the ratio between the Rootstock block difficulty target
     * and the associated bitcoin merged mining block hash
     *
     * @param constants network specific constants
     * @param header block header
     * @return PoW factor for a Rootstock block header, or {@code Optional.empty()} if the Rootstock header does not
     * contain the bitcoin merged mining header, or it cannot be parsed
     */
    public static Optional<BigInteger> difficultyToPoWFactor(Constants constants, BlockHeader header) {
        byte[] bitcoinMergedMiningHeaderBytes = header.getBitcoinMergedMiningHeader();
        if (bitcoinMergedMiningHeaderBytes == null) {
            return Optional.empty();
        }

        BtcBlock bitcoinMergedMiningBlock;
        try {
            bitcoinMergedMiningBlock = constants
                    .getBridgeConstants()
                    .getBtcParams()
                    .getDefaultSerializer()
                    .makeBlock(bitcoinMergedMiningHeaderBytes);
        } catch (Exception e) {
            return Optional.empty();
        }
        BigInteger bitcoinMergedMiningBlockHashBI = bitcoinMergedMiningBlock.getHash().toBigInteger();

        BlockDifficulty difficulty = header.getDifficulty();
        BigInteger difficultyTargetBI = DifficultyUtils.difficultyToTarget(difficulty);

        BigInteger factor = difficultyTargetBI.divide(bitcoinMergedMiningBlockHashBI);

        return Optional.of(factor);
    }
}
