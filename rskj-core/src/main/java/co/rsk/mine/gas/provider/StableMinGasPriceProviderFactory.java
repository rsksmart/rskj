package co.rsk.mine.gas.provider;

import co.rsk.config.mining.StableMinGasPriceSystemConfig;

public abstract class StableMinGasPriceProviderFactory {
    private final StableMinGasPriceSystemConfig config;
    private final MinGasPriceProvider fallbackProvider;

    public StableMinGasPriceProviderFactory(StableMinGasPriceSystemConfig config, MinGasPriceProvider fallbackProvider) {
        this.config = config;
        this.fallbackProvider = fallbackProvider;
    }

    protected abstract StableMinGasPriceProvider create();
}
