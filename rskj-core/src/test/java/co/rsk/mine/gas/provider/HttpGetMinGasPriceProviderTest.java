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

import co.rsk.config.mining.HttpGetStableMinGasSystemConfig;
import co.rsk.config.mining.StableMinGasPriceSystemConfig;
import co.rsk.net.http.HttpException;
import co.rsk.net.http.SimpleHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpGetMinGasPriceProviderTest {

    private final MinGasPriceProvider fallbackProvider = mock(MinGasPriceProvider.class);
    private final SimpleHttpClient httpClient = mock(SimpleHttpClient.class);
    private StableMinGasPriceSystemConfig config;

    @BeforeEach
    void setUp() {
        reset(fallbackProvider, httpClient);
        config = createStableMinGasPriceSystemConfig();
    }

    @Test
    void returnsMappedPriceFromWebClient() throws HttpException {
        // given
        when(fallbackProvider.getType()).thenReturn(MinGasPriceProviderType.FIXED);
        when(httpClient.doGet(anyString())).thenReturn("{\"bitcoin\":{\"usd\":10000}}");

        // when
        HttpGetMinGasPriceProvider provider = new HttpGetMinGasPriceProvider(config, fallbackProvider, httpClient);

        // then
        Optional<Long> result = provider.getBtcExchangeRate();
        verify(fallbackProvider, times(0)).getMinGasPrice();
        assertTrue(result.isPresent());
        assertEquals(10000L, result.get());
    }

    @Test
    void whenRequestingTheValueTwiceCachedValueIsUsed() throws HttpException {
        // given
        when(fallbackProvider.getType()).thenReturn(MinGasPriceProviderType.FIXED);
        when(httpClient.doGet(anyString())).thenReturn("{\"bitcoin\":{\"usd\":10000}}");
        HttpGetMinGasPriceProvider provider = spy(new HttpGetMinGasPriceProvider(config, fallbackProvider, httpClient));

        // when
        // First call should fetch and cache the value
        provider.getMinGasPrice(true);
        // Second call should use the cached value
        provider.getMinGasPrice();

        // then
        verify(httpClient, times(1)).doGet(anyString());
        verify(provider, times(2)).getMinGasPrice(anyBoolean());
    }

    @Test
    void whenEmptyResponseReturnsFallbackProvider() throws HttpException {
        // given
        when(fallbackProvider.getMinGasPrice()).thenReturn(10L);
        when(fallbackProvider.getType()).thenReturn(MinGasPriceProviderType.FIXED);
        when(httpClient.doGet(anyString())).thenReturn("");

        // when
        HttpGetMinGasPriceProvider provider = new HttpGetMinGasPriceProvider(config, fallbackProvider, httpClient);
        Long result = provider.getMinGasPrice(true);

        // then
        verify(fallbackProvider, times(1)).getMinGasPrice();
        assertEquals(10L, result);
    }

    @Test
    void whenErrorReturnsFallbackProvider() throws HttpException {
        // given
        when(fallbackProvider.getMinGasPrice()).thenReturn(10L);
        when(fallbackProvider.getType()).thenReturn(MinGasPriceProviderType.FIXED);
        when(httpClient.doGet(anyString())).thenThrow(new HttpException("Error"));

        // when
        HttpGetMinGasPriceProvider provider = new HttpGetMinGasPriceProvider(config, fallbackProvider, httpClient);
        Long result = provider.getMinGasPrice(true);

        // then
        verify(fallbackProvider, times(1)).getMinGasPrice();
        assertEquals(10L, result);
    }

    @Test
    void whenPathIsWrongReturnsFallBack() throws HttpException {
        // given
        when(fallbackProvider.getMinGasPrice()).thenReturn(10L);
        when(fallbackProvider.getType()).thenReturn(MinGasPriceProviderType.FIXED);
        when(httpClient.doGet(anyString())).thenReturn("{\"btc\":{\"usd\":10000}}");

        // when
        HttpGetMinGasPriceProvider provider = new HttpGetMinGasPriceProvider(config, fallbackProvider, httpClient);
        Long result = provider.getMinGasPrice(true);

        // then
        verify(fallbackProvider, times(1)).getMinGasPrice();
        assertEquals(10L, result);
    }

    private StableMinGasPriceSystemConfig createStableMinGasPriceSystemConfig() {
        StableMinGasPriceSystemConfig config = mock(StableMinGasPriceSystemConfig.class);
        HttpGetStableMinGasSystemConfig webConfig = createWebStableSystemConfig();
        when(config.getHttpGetConfig()).thenReturn(webConfig);
        when(config.getMinStableGasPrice()).thenReturn(4265280000000L);
        when(config.getRefreshRate()).thenReturn(Duration.ofSeconds(30));
        when(config.getMinValidPrice()).thenReturn(1000L);
        when(config.getMaxValidPrice()).thenReturn(100000L);
        return config;
    }

    private HttpGetStableMinGasSystemConfig createWebStableSystemConfig() {
        HttpGetStableMinGasSystemConfig config = mock(HttpGetStableMinGasSystemConfig.class);
        when(config.getJsonPath()).thenReturn("/bitcoin/usd");
        when(config.getUrl()).thenReturn("https://rsk.co/price?ids=bitcoin&vs_currencies=usd");
        when(config.getTimeout()).thenReturn(Duration.ofMillis(3000));
        return config;
    }

}
