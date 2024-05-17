package co.rsk.mine.gas.provider.onchain;

import co.rsk.config.mining.OnChainMinGasPriceSystemConfig;
import co.rsk.mine.gas.provider.MinGasPriceProvider;
import co.rsk.mine.gas.provider.MinGasPriceProviderType;
import co.rsk.mine.gas.provider.StableMinGasPriceProvider;

public class OnChainMinGasPriceProvider extends StableMinGasPriceProvider {
    private final String address;

    protected OnChainMinGasPriceProvider(MinGasPriceProvider fallBackProvider, OnChainMinGasPriceSystemConfig config) {
        super(fallBackProvider);
        this.address = config.getAddress();
    }

    @Override
    public MinGasPriceProviderType getType() {
        return MinGasPriceProviderType.ON_CHAIN;
    }

    @Override
    public Long getStableMinGasPrice() {
        return null;
    }

    public String getAddress() {
        return address;
    }
}
