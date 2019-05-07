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

import org.ethereum.config.BlockchainConfig;
import org.ethereum.config.BlockchainNetConfig;
import org.ethereum.config.Constants;

/**
 * BlockchainForkConfig is also implemented by this class - its (mostly testing) purpose to represent
 * the specific config for all blocks on the chain (kinda constant config).
 *
 * Created by Anton Nashatyrev on 25.02.2016.
 */
public abstract class AbstractConfig implements BlockchainConfig, BlockchainNetConfig {
    protected Constants constants;

    public AbstractConfig(Constants constants) {
        this.constants = constants;
    }

    @Override
    public Constants getConstants() {
        return constants;
    }

    @Override
    public BlockchainConfig getConfigForBlock(long blockHeader) {
        return this;
    }

    @Override
    public Constants getCommonConstants() {
        return getConstants();
    }

    @Override
    public boolean areBridgeTxsFree() {
        return false;
    }

    @Override
    public boolean difficultyDropEnabled() {
        return false;
    }

    @Override
    public boolean isRskip90() {
        return false;
    }

    @Override
    public boolean isRskip89() {
        return false;
    }

    @Override
    public boolean isRskip88() {
        return false;
    }

    @Override
    public boolean isRskip91() { return false; }

    @Override
    public boolean isRskip103() { return false; }

    @Override
    public boolean isRskip87() { return false; }

    @Override
    public boolean isRskip85() { return false; }

    @Override
    public boolean isRskip92() {
        return false;
    }

    @Override
    public boolean isRskip93() { return false; }

    @Override public boolean isRskip94() { return false; }

    @Override
    public boolean isRskip98() {
        return false;
    }

    @Override
    public boolean isRskip97() {
        return false;
    }

    @Override
    public boolean isRskip120() {
        return false;
    }

    @Override
    public boolean isRskip123() {
        return false;
    }
}
