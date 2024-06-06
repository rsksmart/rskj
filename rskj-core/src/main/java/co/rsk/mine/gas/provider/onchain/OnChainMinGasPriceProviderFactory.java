package co.rsk.mine.gas.provider.onchain;

import co.rsk.config.mining.StableMinGasPriceSystemConfig;
import co.rsk.mine.gas.provider.MinGasPriceProvider;

public class OnChainMinGasPriceProviderFactory {

    private OnChainMinGasPriceProviderFactory() {
    }

    public static OnChainMinGasPriceProvider create(StableMinGasPriceSystemConfig config, MinGasPriceProvider fallbackProvider, OnChainMinGasPriceProvider.GetContextCallback getContextCallback) {
        return new OnChainMinGasPriceProvider(fallbackProvider, config.getOnChainConfig(), getContextCallback);
    }
}
