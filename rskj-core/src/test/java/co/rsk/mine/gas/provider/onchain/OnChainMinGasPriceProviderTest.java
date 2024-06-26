package co.rsk.mine.gas.provider.onchain;

import co.rsk.config.mining.OnChainMinGasPriceSystemConfig;
import co.rsk.config.mining.StableMinGasPriceSystemConfig;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OnChainMinGasPriceProviderTest {
    private EthModule ethModule_mock;
    private MinGasPriceProvider fallback_mock;
    private OnChainMinGasPriceSystemConfig onChainMinGasPriceSystemConfig_mock;
    private OnChainMinGasPriceProvider onChainMinGasPriceProvider;
    private StableMinGasPriceSystemConfig stableMinGasPriceSystemConfig;

    @BeforeEach
    public void beforeEach() {
        ethModule_mock = mock(EthModule.class);
        when(ethModule_mock.chainId()).thenReturn("0x21");

        fallback_mock = mock(MinGasPriceProvider.class);
        when(fallback_mock.getType()).thenReturn(MinGasPriceProviderType.FIXED);
        long fallback_minGasPrice_fake = 1234567890L;
        when(fallback_mock.getMinGasPrice()).thenReturn(fallback_minGasPrice_fake);

        onChainMinGasPriceSystemConfig_mock = mock(OnChainMinGasPriceSystemConfig.class);
        String oracle_address = "0xbffBD993FF1d229B0FfE55668F2009d20d4F7C5f";
        when(onChainMinGasPriceSystemConfig_mock.getAddress()).thenReturn(oracle_address);
        String from_address = "0xbffBD993FF1d229B0FfE55668F2009d20d4F7C5f";
        when(onChainMinGasPriceSystemConfig_mock.getFrom()).thenReturn(from_address);
        String data = "0x";
        when(onChainMinGasPriceSystemConfig_mock.getData()).thenReturn(data);
        
        stableMinGasPriceSystemConfig = mock(StableMinGasPriceSystemConfig.class);
        when(stableMinGasPriceSystemConfig.getOnChainConfig()).thenReturn(onChainMinGasPriceSystemConfig_mock);
        when(stableMinGasPriceSystemConfig.getMinStableGasPrice()).thenReturn(fallback_minGasPrice_fake);


        onChainMinGasPriceProvider = new OnChainMinGasPriceProvider(
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
        OnChainMinGasPriceSystemConfig config = stableMinGasPriceSystemConfig.getOnChainConfig();

        when(config.getAddress()).thenReturn("0xaddress");
        when(config.getFrom()).thenReturn("0xfrom");
        when(config.getData()).thenReturn(data_input);

        OnChainMinGasPriceProvider provider = new OnChainMinGasPriceProvider(fallbackProvider, stableMinGasPriceSystemConfig, () -> ethModule_mock);

        Assertions.assertEquals("0xaddress", provider.getToAddress());
    }

    @Test
    void constructorSetsFieldsToNullWhenConfigReturnsNull() {
        MinGasPriceProvider fallbackProvider = mock(MinGasPriceProvider.class);
        OnChainMinGasPriceSystemConfig config = stableMinGasPriceSystemConfig.getOnChainConfig();
        when(config.getAddress()).thenReturn(null);
        when(config.getFrom()).thenReturn(null);
        when(config.getData()).thenReturn(null);


        OnChainMinGasPriceProvider provider = new OnChainMinGasPriceProvider(fallbackProvider, stableMinGasPriceSystemConfig, () -> ethModule_mock);

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
        when(onChainMinGasPriceSystemConfig_mock.getData()).thenReturn(data_input);

        assertFalse(onChainMinGasPriceProvider.getBtcExchangeRate().isPresent());
    }


    @Test
    void getStableMinGasPrice_callsFallback_whenEthModuleIsNull() {
        assertFalse(onChainMinGasPriceProvider.getBtcExchangeRate().isPresent());
    }

    @Test
    void getType_returnsOnChain() {
        Assertions.assertEquals(MinGasPriceProviderType.ON_CHAIN, onChainMinGasPriceProvider.getType());
    }

}
