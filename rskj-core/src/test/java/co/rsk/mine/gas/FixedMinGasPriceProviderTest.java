package co.rsk.mine.gas;

import co.rsk.core.Coin;
import co.rsk.mine.gas.provider.FixedMinGasPriceProvider;
import co.rsk.mine.gas.provider.MinGasPriceProviderType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FixedMinGasPriceProviderTest {


    @Test
    void testConstructorAndGetMinGasPrice() {
        long minGasPrice = 100L;
        FixedMinGasPriceProvider provider = new FixedMinGasPriceProvider(minGasPrice);
        assertEquals(minGasPrice, provider.getMinGasPrice());
    }

    @Test
    void testGetType() {
        long minGasPrice = 100L;
        FixedMinGasPriceProvider provider = new FixedMinGasPriceProvider(minGasPrice);
        assertEquals(MinGasPriceProviderType.FIXED, provider.getType());
    }

    @Test
    void testGetMinGasPriceAsCoin() {
        long minGasPrice = 50;
        FixedMinGasPriceProvider provider = new FixedMinGasPriceProvider(minGasPrice);
        assertEquals(Coin.valueOf(minGasPrice), provider.getMinGasPriceAsCoin());
    }
}