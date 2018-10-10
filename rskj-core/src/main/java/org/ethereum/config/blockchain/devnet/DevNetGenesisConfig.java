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

package org.ethereum.config.blockchain.devnet;

import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeDevNetConstants;
import co.rsk.core.BlockDifficulty;
import org.ethereum.config.blockchain.GenesisConfig;

import java.math.BigInteger;

/**
 * Created by Oscar Guindzberg on 25.10.2016.
 */
public class DevNetGenesisConfig extends GenesisConfig {

    public static class DevNetConstants extends GenesisConstants {

        private static final byte CHAIN_ID = 32;
        private static final BigInteger DIFFICULTY_BOUND_DIVISOR = BigInteger.valueOf(50);
        private final BlockDifficulty minimumDifficulty = new BlockDifficulty(BigInteger.valueOf(131072));

        @Override
        public BridgeConstants getBridgeConstants() {
            return BridgeDevNetConstants.getInstance();
        }

        @Override
        public byte getChainId() {
            return DevNetConstants.CHAIN_ID;
        }

        @Override
        public BlockDifficulty getMinimumDifficulty() {
            return minimumDifficulty;
        }

        @Override
        public int getDurationLimit() {
            return 14;
        }

        @Override
        public BigInteger getDifficultyBoundDivisor() {
            return DIFFICULTY_BOUND_DIVISOR;
        }
    }

    public DevNetGenesisConfig() {
        super(new DevNetConstants());
    }

    @Override
    public boolean areBridgeTxsFree() {
        return true;
    }

}
