/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package co.rsk.mine.gas.provider;

import co.rsk.core.Coin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

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
        stableMinGasPriceProvider = new TestStableMingGasPriceProvider(fallBackProvider, 100);
    }

    @Test
    void testGetMinGasPrice() {
        long result = stableMinGasPriceProvider.getMinGasPrice();
        assertEquals(6L, result);
        verify(fallBackProvider, times(0)).getMinGasPrice();
    }

    @Test
    void GetMinGasPriceUsesFallbackWhenReturnIsNull() {
        stableMinGasPriceProvider = new TestStableMingGasPriceProvider(fallBackProvider, 100) {
            @Override
            public Optional<Long> getBtcExchangeRate() {
                return Optional.empty();
            }
        };

        long result = stableMinGasPriceProvider.getMinGasPrice();

        assertEquals(10L, result);
        verify(fallBackProvider, times(1)).getMinGasPrice();
    }

    @Test
    void testGetMinGasPriceAsCoin() {
        Coin result = stableMinGasPriceProvider.getMinGasPriceAsCoin();
        assertEquals(Coin.valueOf(6L), result);
    }

    public static class TestStableMingGasPriceProvider extends StableMinGasPriceProvider {

        protected TestStableMingGasPriceProvider(MinGasPriceProvider fallBackProvider, long minStableGasPrice) {
            super(fallBackProvider, minStableGasPrice);
        }

        @Override
        public MinGasPriceProviderType getType() {
            return MinGasPriceProviderType.FIXED;
        }

        @Override
        public Optional<Long> getBtcExchangeRate() {
            return Optional.of(15L);
        }
    }
}
