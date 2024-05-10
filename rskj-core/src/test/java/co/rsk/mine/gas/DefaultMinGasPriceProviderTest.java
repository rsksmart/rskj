package co.rsk.mine.gas;

import co.rsk.core.Coin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DefaultMinGasPriceProviderTest {


    @Test
    void testConstructorAndGetMinGasPrice() {
        long minGasPrice = 100L;
        DefaultMinGasPriceProvider provider = new DefaultMinGasPriceProvider(minGasPrice);
        assertEquals(minGasPrice, provider.getMinGasPrice());
    }

    @Test
    void testGetType() {
        long minGasPrice = 100L;
        DefaultMinGasPriceProvider provider = new DefaultMinGasPriceProvider(minGasPrice);
        assertEquals(MinGasPriceProviderType.DEFAULT, provider.getType());
    }

    @Test
    void testGetMinGasPriceAsCoin() {
        long minGasPrice = 50;
        DefaultMinGasPriceProvider provider = new DefaultMinGasPriceProvider(minGasPrice);
        assertEquals(Coin.valueOf(minGasPrice), provider.getMinGasPriceAsCoin());
    }
}