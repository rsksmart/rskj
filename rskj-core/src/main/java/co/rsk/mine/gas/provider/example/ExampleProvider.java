package co.rsk.mine.gas.provider.example;

import co.rsk.mine.gas.provider.MinGasPriceProvider;
import co.rsk.mine.gas.provider.MinGasPriceProviderType;
import co.rsk.mine.gas.provider.StableMinGasPriceProvider;

public class ExampleProvider extends StableMinGasPriceProvider {

    private final long fixedPrice;
    public ExampleProvider(MinGasPriceProvider fallBackProvider, long fixedPrice) {
        super(fallBackProvider);
        this.fixedPrice = fixedPrice;
    }

    @Override
    public MinGasPriceProviderType getType() {
        return MinGasPriceProviderType.EXAMPLE_PROVIDER;
    }

    @Override
    public Long getStableMinGasPrice() {
        return fixedPrice;
    }
}
