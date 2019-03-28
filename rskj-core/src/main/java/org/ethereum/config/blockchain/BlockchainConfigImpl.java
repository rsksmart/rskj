/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;

import static org.ethereum.config.blockchain.upgrades.ConsensusRule.*;

/**
 * @deprecated usages of this class should be replaced by {@link ActivationConfig}.
 *             if you need the constants, you should inject them.
 */
@Deprecated
public class BlockchainConfigImpl implements BlockchainConfig {
    private final Constants networkConstants;
    private final ActivationConfig activationConfig;
    private final long blockNumber;

    public BlockchainConfigImpl(Constants networkConstants, ActivationConfig activationConfig, long blockNumber) {
        this.networkConstants = networkConstants;
        this.activationConfig = activationConfig;
        this.blockNumber = blockNumber;
    }

    @Override
    public Constants getConstants() {
        return networkConstants;
    }

    @Override
    public boolean areBridgeTxsFree() {
        return !activationConfig.isActive(ARE_BRIDGE_TXS_PAID, blockNumber);
    }

    @Override
    public boolean difficultyDropEnabled() {
        return activationConfig.isActive(DIFFICULTY_DROP_ENABLED, blockNumber);
    }

    @Override
    public boolean isRskip85() {
        return activationConfig.isActive(RSKIP85, blockNumber);
    }

    @Override
    public boolean isRskip87() {
        return activationConfig.isActive(RSKIP87, blockNumber);
    }

    @Override
    public boolean isRskip88() {
        return activationConfig.isActive(RSKIP88, blockNumber);
    }

    @Override
    public boolean isRskip89() {
        return activationConfig.isActive(RSKIP89, blockNumber);
    }

    @Override
    public boolean isRskip90() {
        return activationConfig.isActive(RSKIP90, blockNumber);
    }

    @Override
    public boolean isRskip91() {
        return activationConfig.isActive(RSKIP91, blockNumber);
    }

    @Override
    public boolean isRskip92() {
        return activationConfig.isActive(RSKIP92, blockNumber);
    }

    @Override
    public boolean isRskip93() {
        return activationConfig.isActive(RSKIP93, blockNumber);
    }

    @Override
    public boolean isRskip94() {
        return activationConfig.isActive(RSKIP94, blockNumber);
    }

    @Override
    public boolean isRskip97() {
        return activationConfig.isActive(RSKIP97, blockNumber);
    }

    @Override
    public boolean isRskip98() {
        return activationConfig.isActive(RSKIP98, blockNumber);
    }

    @Override
    public boolean isRskip103() {
        return activationConfig.isActive(RSKIP103, blockNumber);
    }

    @Override
    public boolean isRskip119() {
        return activationConfig.isActive(RSKIP119, blockNumber);
    }

    @Override
    public boolean isRskip120() {
        return activationConfig.isActive(RSKIP120, blockNumber);
    }

    @Override
    public boolean isRskip123() {
        return activationConfig.isActive(RSKIP123, blockNumber);
    }
}
