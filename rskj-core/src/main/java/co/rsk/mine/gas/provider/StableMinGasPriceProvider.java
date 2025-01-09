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
import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public abstract class StableMinGasPriceProvider implements MinGasPriceProvider {
    private static final Logger logger = LoggerFactory.getLogger("StableMinGasPrice");
    private static final int ERR_NUM_OF_FAILURES = 20;
    private final MinGasPriceProvider fallBackProvider;
    private final long minStableGasPrice;
    private final long refreshRateInMillis;
    private final long minValidPrice;
    private final long maxValidPrice;
    private final AtomicInteger numOfFailures = new AtomicInteger();
    private final AtomicReference<Future<Long>> priceFuture = new AtomicReference<>();

    private volatile long lastMinGasPrice;
    private volatile long lastUpdateTimeMillis;

    protected StableMinGasPriceProvider(MinGasPriceProvider fallBackProvider, long minStableGasPrice, Duration refreshRate, long minValidPrice, long maxValidPrice) {
        this.minStableGasPrice = minStableGasPrice;
        this.fallBackProvider = fallBackProvider;
        this.refreshRateInMillis = refreshRate.toMillis();
        this.minValidPrice = minValidPrice;
        this.maxValidPrice = maxValidPrice;
    }

    protected abstract Optional<Long> getBtcExchangeRate();

    @Override
    public long getMinGasPrice() {
        return getMinGasPrice(false);
    }

    @VisibleForTesting
    public long getMinGasPrice(boolean wait) {
        long currentTimeMillis = System.currentTimeMillis();
        if (currentTimeMillis - lastUpdateTimeMillis >= refreshRateInMillis) {
            Future<Long> priceFuture = fetchPriceAsync();
            if (wait || priceFuture.isDone()) {
                try {
                    logger.debug("getMinGasPrice returning fetched minGasPrice: {}", priceFuture.get());
                    return priceFuture.get();
                } catch (InterruptedException e) {
                    logger.error("Min gas price fetching was interrupted", e);
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    logger.error("Min gas price fetching was failed", e);
                }
            }
        }

        long minGasPrice = getLastMinGasPrice();
        logger.debug("getMinGasPrice returning cached minGasPrice: {}", minGasPrice);

        return minGasPrice;
    }

    @Override
    public Coin getMinGasPriceAsCoin() {
        return Coin.valueOf(getMinGasPrice());
    }

    @VisibleForTesting
    Coin getMinGasPriceAsCoin(boolean wait) {
        return Coin.valueOf(getMinGasPrice(wait));
    }

    private long getLastMinGasPrice() {
        if (lastMinGasPrice > 0) {
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

    private synchronized Future<Long> fetchPriceAsync() {
        Future<Long> future = priceFuture.get();
        if (future != null) {
            logger.debug("fetchPriceAsync skipped as there is already price fetching in progress...");
            return future;
        }

        CompletableFuture<Long> newFuture = new CompletableFuture<>();
        priceFuture.set(newFuture);

        logger.debug("fetchPriceAsync...");
        new Thread(() -> {
            Optional<Long> priceResponse = fetchPriceSync();
            newFuture.complete(priceResponse.orElse(getLastMinGasPrice()));
            priceFuture.set(null);
        }).start();

        return newFuture;
    }

    private Optional<Long> fetchPriceSync() {
        logger.debug("fetchPriceSync...");
        Optional<Long> priceResponse = getBtcExchangeRate();
        if (priceResponse.isPresent() && isIntoRange(priceResponse.get())) {
            long result = calculateMinGasPriceBasedOnBtcPrice(priceResponse.get());
            lastMinGasPrice = result;
            lastUpdateTimeMillis = System.currentTimeMillis();
            numOfFailures.set(0);

            logger.debug("fetchPriceSync completed with priceResponse: {}, lastMinGasPrice: {}", priceResponse, lastMinGasPrice);

            return Optional.of(result);
        }

        int failedAttempts = numOfFailures.incrementAndGet();
        if (failedAttempts >= ERR_NUM_OF_FAILURES) {
            logger.error("Gas price was not updated as it was not possible to obtain valid price from provider. Check your provider setup. Number of failed attempts: {}", failedAttempts);
        } else {
            logger.warn("Gas price was not updated as it was not possible to obtain valid price from provider. Number of failed attempts: {}", failedAttempts);
        }

        return Optional.empty();
    }

    @VisibleForTesting
    boolean isIntoRange(long value) {
        if (value >= minValidPrice && value <= maxValidPrice) {
            return true;
        }
        logger.warn("Gas price value {} is not in the valid range {} - {}", value, minValidPrice, maxValidPrice);
        return false;
    }
}
