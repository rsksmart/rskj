/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package org.ethereum.config.blockchain;

import co.rsk.core.BlockDifficulty;
import co.rsk.peg.Federation;
import org.ethereum.config.Constants;
import org.ethereum.core.BlockHeader;

import java.math.BigInteger;

/**
 * Created by Anton Nashatyrev on 25.02.2016.
 */
public class GenesisConfig extends AbstractConfig {

    // IMPORTANT NOTICE
    // This class contains two methods "NEW_*" containing the latest hard-forks in Ethereum
    // Before launch, they must be removed or exchanged with the current non-prefixed methods.

    public GenesisConfig() {
        this(new GenesisConstants());
    }

    public GenesisConfig(Constants constants) {
        super(constants);
    }

    public static class GenesisConstants extends Constants {
        @Override
        public int getDurationLimit() {
            return 13;
        }
    }

    @Override
    public Federation getGenesisFederation() {
        return getConstants().getBridgeConstants().getGenesisFederation();
    }

    @Override
    protected BigInteger getCalcDifficultyMultiplier(BlockHeader curBlock, BlockHeader parent) {
        return BigInteger.valueOf(curBlock.getTimestamp() >= parent.getTimestamp() +
                getConstants().getDurationLimit() ? -1 : 1);
    }

    @Override
    public BlockDifficulty calcDifficulty(BlockHeader curBlock, BlockHeader parent) {
        // If more than 10 minutes, reset to minimum difficulty to allow private mining
        if (curBlock.getTimestamp() >= parent.getTimestamp() + 600) {
            return getConstants().getMinimumDifficulty();
        }

        return super.calcDifficulty(curBlock, parent);
    }

}
