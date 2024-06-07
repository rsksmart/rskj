package co.rsk.mine.gas.provider.onchain;

import co.rsk.config.mining.OnChainMinGasPriceSystemConfig;
import co.rsk.mine.gas.provider.MinGasPriceProvider;
import co.rsk.mine.gas.provider.MinGasPriceProviderType;
import co.rsk.util.HexUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import co.rsk.rpc.modules.eth.EthModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OnChainMinGasPriceProviderTest {
    private final String oracle_address = "0xbffBD993FF1d229B0FfE55668F2009d20d4F7C5f";
    private final String from_address = "0xbffBD993FF1d229B0FfE55668F2009d20d4F7C5f";
    private final String data = "0x";
    private final long fallback_minGasPrice_fake = 1234567890L;

    private EthModule ethModule_mock;
    private MinGasPriceProvider fallback_mock;
    private OnChainMinGasPriceSystemConfig onChainMinGasPriceSystemConfig_mock;

    private OnChainMinGasPriceProvider onChainMinGasPriceProvider;

    @BeforeEach
    public void beforeEach() {
        ethModule_mock = mock(EthModule.class);
        when(ethModule_mock.chainId()).thenReturn("0x21");

        fallback_mock = mock(MinGasPriceProvider.class);
        when(fallback_mock.getType()).thenReturn(MinGasPriceProviderType.FIXED);
        when(fallback_mock.getMinGasPrice()).thenReturn(fallback_minGasPrice_fake);

        onChainMinGasPriceSystemConfig_mock = mock(OnChainMinGasPriceSystemConfig.class);
        when(onChainMinGasPriceSystemConfig_mock.address()).thenReturn(oracle_address);
        when(onChainMinGasPriceSystemConfig_mock.from()).thenReturn(from_address);
        when(onChainMinGasPriceSystemConfig_mock.data()).thenReturn(data);


        onChainMinGasPriceProvider = new OnChainMinGasPriceProvider(
                fallback_mock,
                fallback_minGasPrice_fake,
                onChainMinGasPriceSystemConfig_mock,
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
        OnChainMinGasPriceSystemConfig config = mock(OnChainMinGasPriceSystemConfig.class);

        when(config.address()).thenReturn("0xaddress");
        when(config.from()).thenReturn("0xfrom");
        when(config.data()).thenReturn(data_input);

        OnChainMinGasPriceProvider provider = new OnChainMinGasPriceProvider(fallbackProvider, fallback_minGasPrice_fake, config, () -> ethModule_mock);

        Assertions.assertEquals("0xaddress", provider.getToAddress());
    }

    @Test
    void constructorSetsFieldsToNullWhenConfigReturnsNull() {
        MinGasPriceProvider fallbackProvider = mock(MinGasPriceProvider.class);
        OnChainMinGasPriceSystemConfig config = mock(OnChainMinGasPriceSystemConfig.class);

        when(config.address()).thenReturn(null);
        when(config.from()).thenReturn(null);
        when(config.data()).thenReturn(null);

        OnChainMinGasPriceProvider provider = new OnChainMinGasPriceProvider(fallbackProvider, fallback_minGasPrice_fake, config, () -> ethModule_mock);

        Assertions.assertNull(provider.getToAddress());
        Assertions.assertNull(provider.getFromAddress());
        Assertions.assertNull(provider.getData());
    }

    @Test
    void getStableMinGasPrice_callsEthModulesCallMethod() {
        String expectedPrice = "0x21";
        when(ethModule_mock.call(any(), any())).thenReturn(expectedPrice);

        assertTrue(onChainMinGasPriceProvider.getBtcExchangeRate().isPresent());
        Assertions.assertEquals(
                HexUtils.jsonHexToLong(expectedPrice),
                onChainMinGasPriceProvider.getBtcExchangeRate().get()
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "0x"})
    void getStableMinGasPrice_callsFallback_whenNoData(String data_input) {
        when(onChainMinGasPriceSystemConfig_mock.data()).thenReturn(data_input);

        assertTrue(onChainMinGasPriceProvider.getBtcExchangeRate().isPresent());
        Assertions.assertEquals(
                fallback_minGasPrice_fake,
                onChainMinGasPriceProvider.getBtcExchangeRate().get(),
                "For " + data_input + ": "
        );
    }


    @Test
    void getStableMinGasPrice_callsFallback_whenEthModuleIsNull() {
        Assertions.assertEquals(
                fallback_minGasPrice_fake,
                onChainMinGasPriceProvider.getBtcExchangeRate().get()
        );
    }

    @Test
    void getType_returnsOnChain() {
        Assertions.assertEquals(MinGasPriceProviderType.ON_CHAIN, onChainMinGasPriceProvider.getType());
    }

}
