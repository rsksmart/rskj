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
public class MiningConfig {

    private final boolean isMiningEnabled;
    private final byte[] coinbaseAddress;
    private final double minFeesNotifyInDollars;
    private final double minerGasUnitInDollars;
    private final long minGasPriceTarget;
    private final int minGasLimit;
    private final long targetGasLimit;
    private final boolean isTargetGasLimitForced;
    private final int uncleListLimit;
    private final int uncleGenerationLimit;

    public MiningConfig(boolean isMiningEnabled, byte[] coinbaseAddress, double minFeesNotifyInDollars, double minerGasUnitInDollars, long minGasPriceTarget, int minGasLimit, long targetGasLimit, boolean isTargetGasLimitForced, int uncleListLimit, int uncleGenerationLimit) {
        this.isMiningEnabled = isMiningEnabled;
        this.coinbaseAddress = coinbaseAddress;
        this.minFeesNotifyInDollars = minFeesNotifyInDollars;
        this.minerGasUnitInDollars = minerGasUnitInDollars;
        this.minGasPriceTarget= minGasPriceTarget;
        this.minGasLimit = minGasLimit;
        this.targetGasLimit = targetGasLimit;
        this.isTargetGasLimitForced = isTargetGasLimitForced;
        this.uncleListLimit = uncleListLimit;
        this.uncleGenerationLimit = uncleGenerationLimit;
    }

    public boolean isMiningEnabled() {
        return isMiningEnabled;
    }

    public byte[] getCoinbaseAddress() {
        return coinbaseAddress;
    }

    public double getMinFeesNotifyInDollars() {
        return minFeesNotifyInDollars;
    }

    public double getGasUnitInDollars() {
        return minerGasUnitInDollars;
    }

    public long getMinGasPriceTarget() {
        return minGasPriceTarget;
    }

    public int getMinGasLimit() {
        return minGasLimit;
    }

    public long getTargetGasLimit() {
        return targetGasLimit;
    }

    public boolean isTargetGasLimitForced() {
        return isTargetGasLimitForced;
    }

    public int getUncleListLimit() {
        return uncleListLimit;
    }

    public int getUncleGenerationLimit() {
        return uncleGenerationLimit;
    }
}
