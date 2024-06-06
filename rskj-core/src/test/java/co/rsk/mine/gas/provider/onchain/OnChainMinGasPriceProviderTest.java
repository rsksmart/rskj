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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OnChainMinGasPriceProviderTest {
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
                onChainMinGasPriceSystemConfig_mock,
                () -> ethModule_mock
        );
    }

    @AfterEach
    public void afterEach() {
        ethModule_mock = null;
        fallback_mock = null;
    }

    @Test
    public void getStableMinGasPrice_callsEthModulesCallMethod() {
        String expectedPrice = "0x21";
        when(ethModule_mock.call(any(), any())).thenReturn(expectedPrice);

        Assertions.assertEquals(
                HexUtils.jsonHexToLong(expectedPrice),
                onChainMinGasPriceProvider.getStableMinGasPrice()
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "0x"})
    public void getStableMinGasPrice_callsFallback_whenNoData(String data_input) {
        when(onChainMinGasPriceSystemConfig_mock.data()).thenReturn(data_input);

        Assertions.assertEquals(
                fallback_minGasPrice_fake,
                onChainMinGasPriceProvider.getStableMinGasPrice(),
                "For " + data_input + ": "
        );
    }

}
