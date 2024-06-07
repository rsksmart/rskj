package co.rsk.mine.gas.provider;

import co.rsk.core.Coin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class StableMinGasPriceProviderTest {
    private MinGasPriceProvider fallBackProvider;
    private StableMinGasPriceProvider stableMinGasPriceProvider;

    @BeforeEach
    void setUp() {
        fallBackProvider = Mockito.mock(MinGasPriceProvider.class);
        when(fallBackProvider.getMinGasPrice()).thenReturn(10L);
        when(fallBackProvider.getType()).thenReturn(MinGasPriceProviderType.FIXED);
        stableMinGasPriceProvider = new TestStableMingGasPriceProvider(fallBackProvider);
    }

    @Test
    void testGetMinGasPrice() {
        long result = stableMinGasPriceProvider.getMinGasPrice();
        assertEquals(1L, result);
        verify(fallBackProvider, times(0)).getMinGasPrice();
    }

    @Test
    void GetMinGasPriceUsesFallbackWhenReturnIsNull() {
        stableMinGasPriceProvider = new TestStableMingGasPriceProvider(fallBackProvider) {
            @Override
            public Long getStableMinGasPrice() {
                return null;
            }
        };

        long result = stableMinGasPriceProvider.getMinGasPrice();

        assertEquals(10L, result);
        verify(fallBackProvider, times(1)).getMinGasPrice();
    }

    @Test
    void testGetMinGasPriceAsCoin() {
        Coin result = stableMinGasPriceProvider.getMinGasPriceAsCoin();
        assertEquals(Coin.valueOf(1L), result);
    }

    public static class TestStableMingGasPriceProvider extends StableMinGasPriceProvider {

        protected TestStableMingGasPriceProvider(MinGasPriceProvider fallBackProvider) {
            super(fallBackProvider);
        }

        @Override
        public MinGasPriceProviderType getType() {
            return MinGasPriceProviderType.FIXED;
        }

        @Override
        public Long getStableMinGasPrice() {
            return 1L;
        }
    }
}
