package co.rsk.mine.minGasPrice;

import co.rsk.core.Coin;
import com.typesafe.config.ConfigList;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

public class MinGasPriceProvider {
    private final boolean enabled;
    private final long minFixedGasPriceTarget;
    private final long minStableGasPrice;
    private final Duration refreshRate;
    private final List<ExchangeRateProvider> providers;

    private long cachedPrice = 0;
    private long lastRefreshInSeconds = 0;

    public MinGasPriceProvider(
            boolean isStableMinGasPrice,
            long minFixedGasPriceTarget,
            long minStableGasPrice,
            Duration refreshRate,
            ConfigList exchangeRateSources
    ) {
        this.enabled = isStableMinGasPrice;
        this.minFixedGasPriceTarget = minFixedGasPriceTarget;
        this.minStableGasPrice = minStableGasPrice;
        this.refreshRate = refreshRate;
        this.providers = ExchangeRateProviderFactory.getProvidersFromSourceConfig(exchangeRateSources);
    }

    public MinGasPriceProvider(long minFixedGasPriceTarget) {
        this(false, minFixedGasPriceTarget, 0, Duration.ZERO, null);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long getMinStableGasPrice() {
        return minStableGasPrice;
    }

    public Duration getRefreshRate() {
        return refreshRate;
    }

    public List<ExchangeRateProvider> getProviders() {
        return providers;
    }

    public long getMinGasPrice() {
        if (!enabled) {
            return minFixedGasPriceTarget;
        }
        long timeNowInSeconds = Instant.now().getEpochSecond();
        if (timeNowInSeconds - lastRefreshInSeconds < refreshRate.getSeconds()) {
            return cachedPrice;
        }
        cachedPrice = findFirstNonZeroExchangeRate();
        lastRefreshInSeconds = Instant.now().getEpochSecond();

        return cachedPrice;
    }

    public Coin getMinGasPriceAsCoin() {
        return Coin.valueOf(getMinGasPrice());
    }

    private long findFirstNonZeroExchangeRate() {
        for (ExchangeRateProvider provider : providers) {
            long gasPrice = provider.getPrice();
            if (gasPrice > 0) {

                return gasPrice;
            }
        }

        return minFixedGasPriceTarget;
    }
}
