package co.rsk.mine.gas;

import co.rsk.core.Coin;

public interface MinGasPriceProvider {

    long getMinGasPrice();

    MinGasPriceProviderType getType();

    Coin getMinGasPriceAsCoin();
}
