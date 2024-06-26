package co.rsk.mine.gas.provider;

import co.rsk.core.Coin;

public class FixedMinGasPriceProvider implements MinGasPriceProvider {


    private final long minGasPrice;

    public FixedMinGasPriceProvider(long minGasPrice) {
        this.minGasPrice = minGasPrice;
    }

    @Override
    public long getMinGasPrice() {
        return minGasPrice;
    }

    @Override
    public MinGasPriceProviderType getType() {
        return MinGasPriceProviderType.FIXED;
    }

    @Override
    public Coin getMinGasPriceAsCoin() {
        return Coin.valueOf(minGasPrice);
    }
}
