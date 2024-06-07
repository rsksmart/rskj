package co.rsk.mine.gas.provider.example;

import co.rsk.config.mining.StableMinGasPriceSystemConfig;
import co.rsk.mine.gas.provider.MinGasPriceProvider;

public class ExampleProviderFactory {

    private ExampleProviderFactory() {
    }

    public static ExampleProvider create(StableMinGasPriceSystemConfig config, MinGasPriceProvider fallbackProvider) {
        ExampleProviderConfig exampleProviderConfig = new ExampleProviderConfig(config.getConfig().getConfig(ExampleProviderConfig.EXAMPLE_PROVIDER_CONFIG_PATH));
        return new ExampleProvider(fallbackProvider, exampleProviderConfig.getFixedPrice());
    }
}
