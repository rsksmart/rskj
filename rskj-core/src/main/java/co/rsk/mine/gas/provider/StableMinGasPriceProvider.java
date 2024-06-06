package co.rsk.mine.gas.provider;

import co.rsk.core.Coin;
import co.rsk.rpc.modules.eth.EthModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class StableMinGasPriceProvider implements MinGasPriceProvider {
    private static final Logger logger = LoggerFactory.getLogger("StableMinGasPrice");
    protected final MinGasPriceProvider fallBackProvider;
    protected final GetContextCallback getContextCallback;

    protected StableMinGasPriceProvider(MinGasPriceProvider fallBackProvider, GetContextCallback getContextCallback) {
        this.fallBackProvider = fallBackProvider;
        this.getContextCallback = getContextCallback;
    }


    @FunctionalInterface
    public interface GetContextCallback {
        EthModule getEthModule();
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
