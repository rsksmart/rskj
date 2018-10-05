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

package org.ethereum.config.blockchain.testnet;


import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeTestNetConstants;
import co.rsk.core.BlockDifficulty;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.GenesisConfig;
import org.ethereum.config.blockchain.testnet.TestNetAfterBridgeSyncConfig;
import org.ethereum.core.BlockHeader;

import java.math.BigInteger;

public class TestNetBeforeBridgeSyncConfig extends GenesisConfig {

    public static class TestNetConstants extends GenesisConstants {

        private static final BigInteger DIFFICULTY_BOUND_DIVISOR = BigInteger.valueOf(50);
        private static final byte CHAIN_ID = 31;
        private final BlockDifficulty minimumDifficulty = new BlockDifficulty(BigInteger.valueOf(131072));

        @Override
        public BridgeConstants getBridgeConstants() {
            return BridgeTestNetConstants.getInstance();
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

        @Override
        public int getNewBlockMaxSecondsInTheFuture() {
            return 540;
        }

        @Override
        public byte getChainId() {
            return TestNetConstants.CHAIN_ID;
        }
    }

    public TestNetBeforeBridgeSyncConfig() {
        super(new TestNetConstants());
    }

    protected TestNetBeforeBridgeSyncConfig(Constants constants) {
        super(constants);
    }

    @Override
    public boolean areBridgeTxsFree() {
        return true;
    }

    @Override
    public boolean isRskip85() { return true; }

    @Override
    public boolean isRskip87() { return true; }

    @Override
    public boolean isRskip88() {
        return true;
    }

    @Override
    public boolean isRskip89() {
        return true;
    }

    @Override
    public boolean isRskip90() {
        return true;
    }

    @Override
    public boolean isRskip91() {
        return true;
    }

    @Override
    public boolean isRskip92() {
        return true;
    }

    @Override
    public boolean isRskip93() {
        return true;
    }

    @Override
    public boolean isRskip94() {
        return true;
    }

    @Override
    public boolean isRskip98() {
        return true;
    }

    @Override //Rskip97
    public BlockDifficulty calcDifficulty(BlockHeader curBlock, BlockHeader parent) {
        return getBlockDifficulty(curBlock, parent, getConstants());
    }
}
