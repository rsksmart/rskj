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

package org.ethereum.config.net;

import org.ethereum.config.BlockchainConfig;
import org.ethereum.config.BlockchainNetConfig;
import org.ethereum.config.Constants;

/**
 * Created by Anton Nashatyrev on 25.02.2016.
 */
public class AbstractNetConfig implements BlockchainNetConfig {
    private long[] blockNumbers = new long[64];
    private BlockchainConfig[] configs = new BlockchainConfig[64];
    private int count;

    public final void add(long startBlockNumber, BlockchainConfig config) {
        if (count >= blockNumbers.length) {
            throw new RuntimeException();
        }
        if (count > 0 && blockNumbers[count] >= startBlockNumber) {
            throw new RuntimeException("Block numbers should increase");
        }
        if (count == 0 && startBlockNumber > 0) {
            throw new RuntimeException("First config should start from block 0");
        }

        blockNumbers[count] = startBlockNumber;
        configs[count] = config;
        count++;
    }

    @Override
    public BlockchainConfig getConfigForBlock(long blockNumber) {
        for (int i = 0; i < count; i++) {
            if (blockNumber < blockNumbers[i]) {
                return configs[i - 1];
            }
        }
        return configs[count - 1];
    }

    @Override
    public Constants getCommonConstants() {
        // TODO make a guard wrapper which throws exception if the requested constant differs among configs
        return configs[0].getConstants();
    }
}
