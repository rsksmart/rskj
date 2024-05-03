package co.rsk.mine.gas;

import co.rsk.core.Coin;

public interface MinGasPriceProvider {

    long getMinGasPrice();

    GasPriceProviderType getType();

    Coin getMinGasPriceAsCoin();
}
