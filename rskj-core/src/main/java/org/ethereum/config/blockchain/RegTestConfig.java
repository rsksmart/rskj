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

import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.core.BlockDifficulty;

import java.math.BigInteger;


public class RegTestConfig extends GenesisConfig {

    public static class RegTestConstants extends GenesisConstants {

        private final BlockDifficulty minimumDifficulty = new BlockDifficulty(BigInteger.valueOf(1));
        private static final byte CHAIN_ID = 33;

        @Override
        public BlockDifficulty getFallbackMiningDifficulty() { return BlockDifficulty.ZERO; }

        @Override
        public BridgeConstants getBridgeConstants() {
            return BridgeRegTestConstants.getInstance();
        }

        @Override
        public BlockDifficulty getMinimumDifficulty() {
            return minimumDifficulty;
        }

        @Override
        public int getDurationLimit() {
            return 10;
        }

        @Override
        public int getNewBlockMaxSecondsInTheFuture() {
            return 0;
        }

        @Override
        public byte getChainId() {
            return RegTestConstants.CHAIN_ID;
        }
    };

    public RegTestConfig() {
        super(new RegTestConstants());
    }

    @Override
    public boolean areBridgeTxsFree() {
        return true;
    }

    @Override
    public boolean isRfs50() {
        return true;
    }

    @Override
    public boolean isRfs55() {
        return true;
    }

    @Override
    public boolean isRfs90() {
        return true;
    }

    @Override
    public boolean isRfs94() {
        return true;
    }

    @Override
    public boolean isRcs230() {
        return true;
    }
}
