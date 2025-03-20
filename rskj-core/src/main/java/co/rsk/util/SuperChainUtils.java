/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
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
import co.rsk.core.bc.BlockUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.BlockHeader;

import java.math.BigInteger;
import java.util.Optional;

public class SuperChainUtils {

    private SuperChainUtils() { /* hidden */ }

    public static Optional<Boolean> isSuperBlock(Constants constants, ActivationConfig activationConfig, BlockHeader header) {
        if (!activationConfig.isActive(ConsensusRule.RSKIP481, header.getNumber())) {
            return Optional.of(false);
        }

        BtcBlock btcBlock = BlockUtils.makeBtcBlock(constants.getBridgeConstants().getBtcParams(), header.getBitcoinMergedMiningHeader());
        if (btcBlock == null) {
            return Optional.empty();
        }

        return Optional.of(isSuperBlock(constants, activationConfig, header.getNumber(), header.getDifficulty(), btcBlock));
    }

    public static boolean isSuperBlock(Constants constants, ActivationConfig activationConfig,
                                       long blockNumber, BlockDifficulty difficulty, BtcBlock bitcoinMergedMiningBlock) {
        if (!activationConfig.isActive(ConsensusRule.RSKIP481, blockNumber)) {
            return false;
        }

        BigInteger minSuperBlockPoWFactorBI = constants.getMinSuperBlockPoWFactor();

        BigInteger superTargetBI = DifficultyUtils.difficultyToTarget(difficulty.multiply(minSuperBlockPoWFactorBI));

        BigInteger bitcoinMergedMiningBlockHashBI = bitcoinMergedMiningBlock.getHash().toBigInteger();

        return bitcoinMergedMiningBlockHashBI.compareTo(superTargetBI) <= 0;
    }
}
