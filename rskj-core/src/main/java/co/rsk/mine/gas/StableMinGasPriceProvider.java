package co.rsk.mine.gas;

import co.rsk.core.Coin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class StableMinGasPriceProvider implements MinGasPriceProvider {
    private static final Logger logger = LoggerFactory.getLogger("StableMinGasPrice");
    private final DefaultMinGasPriceProvider fallBackProvider;


    StableMinGasPriceProvider(DefaultMinGasPriceProvider fallBackProvider) {
        this.fallBackProvider = fallBackProvider;
    }

    public abstract Long getStableMinGasPrice();

    @Override
    public long getMinGasPrice() {
        Long stableMinGasPrice = getStableMinGasPrice();
        if (stableMinGasPrice == null) {
            logger.error("Could not get stable min gas price from method {}, using fallback provider: {}", this.getType().name(), fallBackProvider.getType().name());
            return fallBackProvider.getMinGasPrice();
        }
        return stableMinGasPrice;
    }

    @Override
    public Coin getMinGasPriceAsCoin() {
        return Coin.valueOf(getMinGasPrice());
    }
}
