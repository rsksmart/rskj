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
package co.rsk.mine.gas.provider.web;

import co.rsk.config.mining.StableMinGasPriceSystemConfig;
import co.rsk.config.mining.WebStableMinGasSystemConfig;
import co.rsk.mine.gas.provider.MinGasPriceProvider;
import co.rsk.mine.gas.provider.MinGasPriceProviderType;
import co.rsk.net.http.HttpException;
import co.rsk.net.http.SimpleHttpClient;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class WebMinGasPriceProviderTest {

    @Test
    void returnsMappedPriceFromWebClient() throws HttpException {
        MinGasPriceProvider fallbackProvider = mock(MinGasPriceProvider.class);
        when(fallbackProvider.getType()).thenReturn(MinGasPriceProviderType.FIXED);

        SimpleHttpClient httpClient = mock(SimpleHttpClient.class);
        when(httpClient.doGet(anyString())).thenReturn("{\"bitcoin\":{\"usd\":10000}}");
        StableMinGasPriceSystemConfig config = createStableMinGasPriceSystemConfig();
        WebMinGasPriceProvider provider = new WebMinGasPriceProvider(config, fallbackProvider, httpClient);

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
        WebMinGasPriceProvider provider = spy(new WebMinGasPriceProvider(config, fallbackProvider, httpClient));

        provider.getMinGasPrice();
        provider.getMinGasPrice();

        verify(provider, times(2)).getMinGasPrice();
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
        WebMinGasPriceProvider provider = new WebMinGasPriceProvider(config, fallbackProvider, httpClient);

        Long result = provider.getMinGasPrice();
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
        WebMinGasPriceProvider provider = new WebMinGasPriceProvider(config, fallbackProvider, httpClient);

        Long result = provider.getMinGasPrice();
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
        WebMinGasPriceProvider provider = new WebMinGasPriceProvider(config, fallbackProvider, httpClient);

        Long result = provider.getMinGasPrice();

        verify(fallbackProvider, times(1)).getMinGasPrice();
        assertEquals(10L, result);
    }

    private StableMinGasPriceSystemConfig createStableMinGasPriceSystemConfig() {
        StableMinGasPriceSystemConfig config = mock(StableMinGasPriceSystemConfig.class);
        WebStableMinGasSystemConfig webConfig = createWebStableSystemConfig();
        when(config.getWebConfig()).thenReturn(webConfig);
        when(config.getMinStableGasPrice()).thenReturn(4265280000000L);
        when(config.getRefreshRate()).thenReturn(30);
        return config;
    }

    private WebStableMinGasSystemConfig createWebStableSystemConfig() {
        WebStableMinGasSystemConfig config = mock(WebStableMinGasSystemConfig.class);
        when(config.getRequestPath()).thenReturn("/bitcoin/usd");
        when(config.getUrl()).thenReturn("https://rsk.co/price?ids=bitcoin&vs_currencies=usd");
        when(config.getTimeout()).thenReturn(3000);
        return config;
    }

}