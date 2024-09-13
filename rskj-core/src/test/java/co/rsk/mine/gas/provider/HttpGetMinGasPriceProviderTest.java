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
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class HttpGetMinGasPriceProviderTest {

    @Test
    void returnsMappedPriceFromWebClient() throws HttpException {
        MinGasPriceProvider fallbackProvider = mock(MinGasPriceProvider.class);
        when(fallbackProvider.getType()).thenReturn(MinGasPriceProviderType.FIXED);

        SimpleHttpClient httpClient = mock(SimpleHttpClient.class);
        when(httpClient.doGet(anyString())).thenReturn("{\"bitcoin\":{\"usd\":10000}}");
        StableMinGasPriceSystemConfig config = createStableMinGasPriceSystemConfig();
        HttpGetMinGasPriceProvider provider = new HttpGetMinGasPriceProvider(config, fallbackProvider, httpClient);

        Optional<Long> result = provider.getBtcExchangeRate();
        verify(fallbackProvider, times(0)).getMinGasPrice();
        assertTrue(result.isPresent());
        assertEquals(10000L, result.get());
    }

    @Test
    void whenRequestingTheValueTwiceCachedValueIsUsed() throws HttpException {
        MinGasPriceProvider fallbackProvider = mock(MinGasPriceProvider.class);
        when(fallbackProvider.getType()).thenReturn(MinGasPriceProviderType.FIXED);

        SimpleHttpClient httpClient = mock(SimpleHttpClient.class);
        when(httpClient.doGet(anyString())).thenReturn("{\"bitcoin\":{\"usd\":10000}}");
        StableMinGasPriceSystemConfig config = createStableMinGasPriceSystemConfig();
        HttpGetMinGasPriceProvider provider = spy(new HttpGetMinGasPriceProvider(config, fallbackProvider, httpClient));

        provider.getMinGasPrice();
        provider.getMinGasPrice(true);

        verify(provider, times(2)).getMinGasPrice(anyBoolean());
        verify(httpClient, times(1)).doGet(anyString());
    }

    @Test
    void whenEmptyResponseReturnsFallbackProvider() throws HttpException {
        MinGasPriceProvider fallbackProvider = mock(MinGasPriceProvider.class);
        when(fallbackProvider.getMinGasPrice()).thenReturn(10L);
        when(fallbackProvider.getType()).thenReturn(MinGasPriceProviderType.FIXED);

        SimpleHttpClient httpClient = mock(SimpleHttpClient.class);
        when(httpClient.doGet(anyString())).thenReturn("");
        StableMinGasPriceSystemConfig config = createStableMinGasPriceSystemConfig();
        HttpGetMinGasPriceProvider provider = new HttpGetMinGasPriceProvider(config, fallbackProvider, httpClient);

        Long result = provider.getMinGasPrice(true);
        verify(fallbackProvider, times(1)).getMinGasPrice();
        assertEquals(10L, result);
    }

    @Test
    void whenErrorReturnsFallbackProvider() throws HttpException {
        MinGasPriceProvider fallbackProvider = mock(MinGasPriceProvider.class);
        when(fallbackProvider.getMinGasPrice()).thenReturn(10L);
        when(fallbackProvider.getType()).thenReturn(MinGasPriceProviderType.FIXED);

        SimpleHttpClient httpClient = mock(SimpleHttpClient.class);
        when(httpClient.doGet(anyString())).thenThrow(new HttpException("Error"));
        StableMinGasPriceSystemConfig config = createStableMinGasPriceSystemConfig();
        HttpGetMinGasPriceProvider provider = new HttpGetMinGasPriceProvider(config, fallbackProvider, httpClient);

        Long result = provider.getMinGasPrice(true);
        verify(fallbackProvider, times(1)).getMinGasPrice();
        assertEquals(10L, result);
    }

    @Test
    void whenPathIsWrongReturnsFallBack() throws HttpException {
        MinGasPriceProvider fallbackProvider = mock(MinGasPriceProvider.class);
        when(fallbackProvider.getMinGasPrice()).thenReturn(10L);
        when(fallbackProvider.getType()).thenReturn(MinGasPriceProviderType.FIXED);

        SimpleHttpClient httpClient = mock(SimpleHttpClient.class);
        when(httpClient.doGet(anyString())).thenReturn("{\"btc\":{\"usd\":10000}}");
        StableMinGasPriceSystemConfig config = createStableMinGasPriceSystemConfig();
        HttpGetMinGasPriceProvider provider = new HttpGetMinGasPriceProvider(config, fallbackProvider, httpClient);

        Long result = provider.getMinGasPrice(true);

        verify(fallbackProvider, times(1)).getMinGasPrice();
        assertEquals(10L, result);
    }

    private StableMinGasPriceSystemConfig createStableMinGasPriceSystemConfig() {
        StableMinGasPriceSystemConfig config = mock(StableMinGasPriceSystemConfig.class);
        HttpGetStableMinGasSystemConfig webConfig = createWebStableSystemConfig();
        when(config.getHttpGetConfig()).thenReturn(webConfig);
        when(config.getMinStableGasPrice()).thenReturn(4265280000000L);
        when(config.getRefreshRate()).thenReturn(Duration.ofSeconds(30));
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