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
 * Describes constants and algorithms used for a specific blockchain at specific stage
 * @deprecated usages of this class should be replaced by {@link ActivationConfig}.
 *             if you need the constants, you should inject them.
 */
@Deprecated
public interface BlockchainConfig {
    Constants getConstants();

    boolean areBridgeTxsFree();

    boolean difficultyDropEnabled();

    boolean isRskip90();
    
    boolean isRskip85();

    boolean isRskip89();

    boolean isRskip88();

    boolean isRskip91();

    boolean isRskip103();

    boolean isRskip87();

    boolean isRskip92();

    boolean isRskip93();

    boolean isRskip94();

    boolean isRskip97();

    boolean isRskip98();

    boolean isRskip119();

    boolean isRskip120();

    boolean isRskip123();
}
