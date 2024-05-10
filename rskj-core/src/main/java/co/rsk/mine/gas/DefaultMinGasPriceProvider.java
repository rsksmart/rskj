package co.rsk.mine.gas;

import co.rsk.core.Coin;

public class DefaultMinGasPriceProvider implements MinGasPriceProvider {


    private final long minGasPrice;

    public DefaultMinGasPriceProvider(long minGasPrice) {
        this.minGasPrice = minGasPrice;
    }

    @Override
    public long getMinGasPrice() {
        return minGasPrice;
    }

    @Override
    public MinGasPriceProviderType getType() {
        return MinGasPriceProviderType.DEFAULT;
    }

    @Override
    public Coin getMinGasPriceAsCoin() {
        return Coin.valueOf(minGasPrice);
    }
}
