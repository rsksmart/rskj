/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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
package co.rsk.config;

import co.rsk.core.RskAddress;

public class ConfigUtils {
    public static MiningConfig getDefaultMiningConfig() {
        final byte[] coinbaseAddress = new byte[]{-120, 95, -109, -18, -43, 119, -14, -4, 52, 30, -69, -102, 92, -101, 44, -28, 70, 93, -106, -60};
        return new MiningConfig(
                new RskAddress(coinbaseAddress),
                0.0,
                0.0,
                0,
                10,
                7,
                new GasLimitConfig(3000000, 500000, true),
                true,
                0L
        );
    }
}
