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

package org.ethereum.config;

import org.ethereum.config.blockchain.upgrades.ActivationConfig;

/**
 * Describes a set of configs for a specific blockchain depending on the block number
 * @deprecated usages of this class should be replaced by {@link ActivationConfig}
 */
@Deprecated
public class BlockchainNetConfig {
    private final Constants networkConstants;
    private final ActivationConfig activationConfig;

    public BlockchainNetConfig(Constants networkConstants, ActivationConfig activationConfig) {
        this.networkConstants = networkConstants;
        this.activationConfig = activationConfig;
    }

    /**
     * Get the config for the specific block
     */
    public ActivationConfig.ForBlock getConfigForBlock(long blockNumber) {
        return activationConfig.forBlock(blockNumber);
    }

    /**
     * Returns the constants common for all the blocks in this blockchain
     */
    public Constants getCommonConstants() {
        return networkConstants;
    }
}
