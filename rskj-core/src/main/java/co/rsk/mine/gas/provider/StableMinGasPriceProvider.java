/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
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
package co.rsk.mine.gas.provider;

import co.rsk.core.Coin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public abstract class StableMinGasPriceProvider implements MinGasPriceProvider {
    private static final Logger logger = LoggerFactory.getLogger("StableMinGasPrice");
    private final MinGasPriceProvider fallBackProvider;
    private final long minStableGasPrice;

    protected StableMinGasPriceProvider(MinGasPriceProvider fallBackProvider, long minStableGasPrice) {
        this.minStableGasPrice = minStableGasPrice;
        this.fallBackProvider = fallBackProvider;
    }

    protected abstract Optional<Long> getBtcExchangeRate();

    @Override
    public long getMinGasPrice() {
        Optional<Long> btcValueOpt = getBtcExchangeRate();
        if (btcValueOpt.isPresent()) {
            long btcValue = btcValueOpt.get();
            if (btcValue >= 0) {
                return calculateMinGasPriceBasedOnBtcPrice(btcValue);
            }
        }
        logger.error("Could not get stable min gas price from method {}, using fallback provider: {}", this.getType().name(), fallBackProvider.getType().name());
        return fallBackProvider.getMinGasPrice();
    }

    private long calculateMinGasPriceBasedOnBtcPrice(long btcValue) {
        if (minStableGasPrice == 0 || btcValue == 0) {
            return 0;
        }
        return minStableGasPrice / btcValue;
    }

    @Override
    public Coin getMinGasPriceAsCoin() {
        return Coin.valueOf(getMinGasPrice());
    }
}
