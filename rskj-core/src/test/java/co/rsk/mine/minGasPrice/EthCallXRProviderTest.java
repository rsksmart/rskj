package co.rsk.mine.minGasPrice;

import co.rsk.core.RskAddress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import co.rsk.rpc.modules.eth.EthModule;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

public class EthCallXRProviderTest {
    private EthModule ethModuleMock;

    private String oracle_address = RskAddress.nullAddress().toHexString();
    private String oracle_method = "getPrice()(uint256)";
    private String[] oracle_params = new String[] { "USD-RBTC" };



    @BeforeEach
    public void beforeEach() {
        ethModuleMock = mock(EthModule.class);
//        when(ethModuleMock.call(oracle_method, )).thenReturn(null);
    }

    @AfterEach
    public void afterEach() {
        ethModuleMock = null;
    }

    @Test
    public void getPrice() {
        EthCallXRProvider ethCallProvider = new EthCallXRProvider(oracle_address, oracle_method, Arrays.asList(oracle_params), null, null);
        assert ethCallProvider.getPrice(new MinGasPriceProvider.ProviderContext(
                null
        )) == 0;
    }

}
