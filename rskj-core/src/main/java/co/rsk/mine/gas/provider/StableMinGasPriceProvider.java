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

import java.time.Instant;
import java.util.Optional;

public abstract class StableMinGasPriceProvider implements MinGasPriceProvider {
    private static final Logger logger = LoggerFactory.getLogger("StableMinGasPrice");
    private final MinGasPriceProvider fallBackProvider;
    private final long minStableGasPrice;
    private Long lastMinGasPrice;
    private long lastUpdateTimeStamp;
    private final int refreshRateMillis;


    protected StableMinGasPriceProvider(MinGasPriceProvider fallBackProvider, long minStableGasPrice, int refreshRateInSeconds) {
        this.minStableGasPrice = minStableGasPrice;
        this.fallBackProvider = fallBackProvider;
        this.lastMinGasPrice = 0L;
        this.refreshRateMillis = refreshRateInSeconds * 1000;
    }

    protected abstract Optional<Long> getBtcExchangeRate();

    @Override
    public long getMinGasPrice() {
        long currentTime = Instant.now().getEpochSecond();
        if (currentTime - lastUpdateTimeStamp >= refreshRateMillis) {
            fetchPrice();
        }
        // time it is always updated, it is not taken into account if the price was really updated or not.
        lastUpdateTimeStamp = currentTime;
        if (lastMinGasPrice != null && lastMinGasPrice > 0) {
            return lastMinGasPrice;
        }
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

    private void fetchPrice() {
        Optional<Long> priceResponse = getBtcExchangeRate();
        if (priceResponse.isPresent() && priceResponse.get() > 0) {
            lastMinGasPrice = calculateMinGasPriceBasedOnBtcPrice(priceResponse.get());
        } else {
            logger.warn("Gas price was not updated as it was not possible to obtain valid price from provider");
        }
    }
}
