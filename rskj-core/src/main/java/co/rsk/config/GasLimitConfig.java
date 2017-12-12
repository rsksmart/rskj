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

package co.rsk.config;

/**
 * Wraps configuration for Mining, which is usually derived from configuration files.
 */
public class GasLimitConfig {
    private final int minGasLimit;
    private final long targetGasLimit;
    private final boolean isTargetGasLimitForced;

    public GasLimitConfig(int minGasLimit, long targetGasLimit, boolean isTargetGasLimitForced) {
        this.minGasLimit = minGasLimit;
        this.targetGasLimit = targetGasLimit;
        this.isTargetGasLimitForced = isTargetGasLimitForced;
    }

    public int getMininimum() {
        return minGasLimit;
    }

    public long getTarget() {
        return targetGasLimit;
    }

    public boolean isTargetForced() {
        return isTargetGasLimitForced;
    }
}
