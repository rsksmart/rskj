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

import co.rsk.config.mining.EthCallMinGasPriceSystemConfig;
import co.rsk.config.mining.StableMinGasPriceSystemConfig;
import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.util.HexUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EthCallMinGasPriceProviderTest {
    private EthModule ethModule_mock;
    private MinGasPriceProvider fallback_mock;
    private EthCallMinGasPriceSystemConfig ethCallMinGasPriceSystemConfig_mock;
    private EthCallMinGasPriceProvider ethCallMinGasPriceProvider;
    private StableMinGasPriceSystemConfig stableMinGasPriceSystemConfig;

    @BeforeEach
    public void beforeEach() {
        ethModule_mock = mock(EthModule.class);
        when(ethModule_mock.chainId()).thenReturn("0x21");

        fallback_mock = mock(MinGasPriceProvider.class);
        when(fallback_mock.getType()).thenReturn(MinGasPriceProviderType.FIXED);
        long fallback_minGasPrice_fake = 1234567890L;
        when(fallback_mock.getMinGasPrice()).thenReturn(fallback_minGasPrice_fake);

        ethCallMinGasPriceSystemConfig_mock = mock(EthCallMinGasPriceSystemConfig.class);
        String oracle_address = "0xbffBD993FF1d229B0FfE55668F2009d20d4F7C5f";
        when(ethCallMinGasPriceSystemConfig_mock.getAddress()).thenReturn(oracle_address);
        String from_address = "0xbffBD993FF1d229B0FfE55668F2009d20d4F7C5f";
        when(ethCallMinGasPriceSystemConfig_mock.getFrom()).thenReturn(from_address);
        String data = "0x";
        when(ethCallMinGasPriceSystemConfig_mock.getData()).thenReturn(data);
        
        stableMinGasPriceSystemConfig = mock(StableMinGasPriceSystemConfig.class);
        when(stableMinGasPriceSystemConfig.getEthCallConfig()).thenReturn(ethCallMinGasPriceSystemConfig_mock);
        when(stableMinGasPriceSystemConfig.getMinStableGasPrice()).thenReturn(fallback_minGasPrice_fake);


        ethCallMinGasPriceProvider = new EthCallMinGasPriceProvider(
                fallback_mock,
                stableMinGasPriceSystemConfig,
                () -> ethModule_mock
        );
    }

    @AfterEach
    public void afterEach() {
        ethModule_mock = null;
        fallback_mock = null;
    }


    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0x123", "0xabc"})
    void constructorSetsFieldsCorrectly(String data_input) {
        MinGasPriceProvider fallbackProvider = mock(MinGasPriceProvider.class);
        EthCallMinGasPriceSystemConfig config = stableMinGasPriceSystemConfig.getEthCallConfig();

        when(config.getAddress()).thenReturn("0xaddress");
        when(config.getFrom()).thenReturn("0xfrom");
        when(config.getData()).thenReturn(data_input);

        EthCallMinGasPriceProvider provider = new EthCallMinGasPriceProvider(fallbackProvider, stableMinGasPriceSystemConfig, () -> ethModule_mock);

        Assertions.assertEquals("0xaddress", provider.getToAddress());
    }

    @Test
    void constructorSetsFieldsToNullWhenConfigReturnsNull() {
        MinGasPriceProvider fallbackProvider = mock(MinGasPriceProvider.class);
        EthCallMinGasPriceSystemConfig config = stableMinGasPriceSystemConfig.getEthCallConfig();
        when(config.getAddress()).thenReturn(null);
        when(config.getFrom()).thenReturn(null);
        when(config.getData()).thenReturn(null);


        EthCallMinGasPriceProvider provider = new EthCallMinGasPriceProvider(fallbackProvider, stableMinGasPriceSystemConfig, () -> ethModule_mock);

        Assertions.assertNull(provider.getToAddress());
        Assertions.assertNull(provider.getFromAddress());
        Assertions.assertNull(provider.getData());
    }

    @Test
    void getStableMinGasPrice_callsEthModulesCallMethod() {
        String expectedPrice = "0x21";
        when(ethModule_mock.call(any(), any())).thenReturn(expectedPrice);

        assertTrue(ethCallMinGasPriceProvider.getBtcExchangeRate().isPresent());
        Assertions.assertEquals(
                HexUtils.jsonHexToLong(expectedPrice),
                ethCallMinGasPriceProvider.getBtcExchangeRate().get()
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "0x"})
    void getStableMinGasPrice_callsFallback_whenNoData(String data_input) {
        when(ethCallMinGasPriceSystemConfig_mock.getData()).thenReturn(data_input);

        assertFalse(ethCallMinGasPriceProvider.getBtcExchangeRate().isPresent());
    }


    @Test
    void getStableMinGasPrice_callsFallback_whenEthModuleIsNull() {
        assertFalse(ethCallMinGasPriceProvider.getBtcExchangeRate().isPresent());
    }

    @Test
    void getType_returnsOnChain() {
        Assertions.assertEquals(MinGasPriceProviderType.ETH_CALL, ethCallMinGasPriceProvider.getType());
    }

}
