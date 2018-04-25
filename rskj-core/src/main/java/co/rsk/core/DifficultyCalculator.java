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

package co.rsk.core;

import co.rsk.config.RskSystemProperties;
import org.ethereum.core.BlockHeader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DifficultyCalculator {
    private final RskSystemProperties config;

    @Autowired
    public DifficultyCalculator(RskSystemProperties config) {
        this.config = config;
    }

    public BlockDifficulty calcDifficulty(BlockHeader header, BlockHeader parentHeader) {
        return config.getBlockchainConfig().getConfigForBlock(header.getNumber()).
                calcDifficulty(header, parentHeader);
    }
}
