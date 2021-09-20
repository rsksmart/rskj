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

import co.rsk.core.RskAddress;

/**
 * Wraps configuration for Mining, which is usually derived from configuration files.
 */
public class MiningConfig {
    public static final int REQUIRED_NUMBER_OF_BLOCKS_FOR_FORK_DETECTION_CALCULATION = 449;

    private final RskAddress coinbaseAddress;
    private final double minFeesNotifyInDollars;
    private final double minerGasUnitInDollars;
    private final long minGasPriceTarget;
    private final int uncleListLimit;
    private final int uncleGenerationLimit;
    private final GasLimitConfig gasLimit;
    private final boolean isFixedClock;
    private final long workSubmissionRateLimitInMills;

    public MiningConfig(RskAddress coinbaseAddress, double minFeesNotifyInDollars, double minerGasUnitInDollars,
                        long minGasPriceTarget, int uncleListLimit, int uncleGenerationLimit, GasLimitConfig gasLimit,
                        boolean isFixedClock, long workSubmissionRateLimitInMills) {
        this.coinbaseAddress = coinbaseAddress;
        this.minFeesNotifyInDollars = minFeesNotifyInDollars;
        this.minerGasUnitInDollars = minerGasUnitInDollars;
        this.minGasPriceTarget= minGasPriceTarget;
        this.uncleListLimit = uncleListLimit;
        this.uncleGenerationLimit = uncleGenerationLimit;
        this.gasLimit = gasLimit;
        this.isFixedClock = isFixedClock;
        this.workSubmissionRateLimitInMills = workSubmissionRateLimitInMills;
    }

    public RskAddress getCoinbaseAddress() {
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

    public int getUncleListLimit() {
        return uncleListLimit;
    }

    public int getUncleGenerationLimit() {
        return uncleGenerationLimit;
    }

    public GasLimitConfig getGasLimit() {
        return gasLimit;
    }

    public boolean isFixedClock() {
        return isFixedClock;
    }

    public long getWorkSubmissionRateLimitInMills() {
        return workSubmissionRateLimitInMills;
    }
}
