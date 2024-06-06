package co.rsk.mine.gas.provider;

import co.rsk.config.mining.StableMinGasPriceSystemConfig;
import co.rsk.mine.gas.provider.onchain.OnChainMinGasPriceProviderFactory;
import co.rsk.mine.gas.provider.web.WebStableMinGasPriceProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class MinGasPriceProviderFactory {

    private static final Logger logger = LoggerFactory.getLogger("StableMinGasPrice");

    private MinGasPriceProviderFactory() {
    }

    public static MinGasPriceProvider create(long fixedMinGasPrice, StableMinGasPriceSystemConfig stableMinGasPriceSystemConfig, StableMinGasPriceProvider.GetContextCallback getContextCallback) {
        FixedMinGasPriceProvider fixedMinGasPriceProvider = new FixedMinGasPriceProvider(fixedMinGasPrice);
        if (stableMinGasPriceSystemConfig == null) {
            logger.warn("Could not find stable min gas price system config, using {} provider", fixedMinGasPriceProvider.getType().name());
            return fixedMinGasPriceProvider;
        }
        if (!stableMinGasPriceSystemConfig.isEnabled()) {
            return fixedMinGasPriceProvider;
        }
        MinGasPriceProviderType method = stableMinGasPriceSystemConfig.getMethod();
        if (method == null) {
            logger.error("Could not find valid method in config, using fallback provider: {}", fixedMinGasPriceProvider.getType().name());
            return fixedMinGasPriceProvider;
        }


        switch (method) {
            case WEB:
                return WebStableMinGasPriceProviderFactory.create(stableMinGasPriceSystemConfig, fixedMinGasPriceProvider, getContextCallback);
            case ON_CHAIN:
                return OnChainMinGasPriceProviderFactory.create(stableMinGasPriceSystemConfig, fixedMinGasPriceProvider, getContextCallback);
            default:
                logger.debug("Could not find a valid implementation for the method {}. Returning fallback provider {}", method, fixedMinGasPriceProvider.getType().name());
                return fixedMinGasPriceProvider;
        }

    }
}
