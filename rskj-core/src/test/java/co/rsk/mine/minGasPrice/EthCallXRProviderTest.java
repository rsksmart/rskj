package co.rsk.mine.minGasPrice;

import co.rsk.util.HexUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import co.rsk.rpc.modules.eth.EthModule;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EthCallXRProviderTest {
    private EthModule ethModuleMock;

    private final String from_address = "0xbffBD993FF1d229B0FfE55668F2009d20d4F7C5f";
    private final String oracle_address = "0xbffBD993FF1d229B0FfE55668F2009d20d4F7C5f";
    private final String data = "0x";


    @BeforeEach
    public void beforeEach() {

        ethModuleMock = mock(EthModule.class);
        when(ethModuleMock.chainId()).thenReturn("0x21");

    }

    @AfterEach
    public void afterEach() {
        ethModuleMock = null;
    }

    @Test
    public void getPrice_failedCallReturnsZero() {
        EthCallXRProvider ethCallProvider = new EthCallXRProvider(
                from_address,
                oracle_address,
                data
        );

        when(ethModuleMock.call(any(), any())).thenReturn(null);

        assert ethCallProvider.getPrice(
                new MinGasPriceProvider.ProviderContext(
                        ethModuleMock
        )) == 0;
    }

    @Test
    public void getPrice_successCallReturnsPrice() {
        String expectedPrice = "0x21";
        EthCallXRProvider ethCallXRProvider = new EthCallXRProvider(
                from_address,
                oracle_address,
                data
        );
        when(ethModuleMock.call(any(), any())).thenReturn(expectedPrice);

        assert ethCallXRProvider.getPrice(
                new MinGasPriceProvider.ProviderContext(
                        ethModuleMock
                )
        ) == HexUtils.jsonHexToLong(expectedPrice);
    }

}
