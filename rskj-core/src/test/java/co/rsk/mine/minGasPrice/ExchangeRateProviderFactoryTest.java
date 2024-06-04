package co.rsk.mine.minGasPrice;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ExchangeRateProviderFactoryTest {

//    @Test
//    public void getProvidersFromSourceConfig() {
//        ConfigList minerStableGasPriceSources = config.minerStableGasPriceSources();
//
//        List<ExchangeRateProvider> providers = ExchangeRateProviderFactory.getProvidersFromSourceConfig(
//                minerStableGasPriceSources
//        );
//
//        assert !providers.isEmpty();
//
//
//    }

    @Test
    public void createProvider_throws_whenNoTypeSpecified() {
        ConfigObject configObject_mock = mock(ConfigObject.class);
        Config config_mock = mock(Config.class);
        when(configObject_mock.toConfig()).thenReturn(config_mock);
        when(config_mock.hasPath("type")).thenReturn(false);
        when(config_mock.getString("type")).thenReturn(null);

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> ExchangeRateProviderFactory.createProvider(configObject_mock),
                "A stableGasPrice miner source requires specifying the 'type' attribute in configuration"
        );
    }

    @Test
    public void createProvider_returnsNull_whenTypeNotKnown() {
        Config config_mock = mock(Config.class);
        when(config_mock.hasPath("type")).thenReturn(true);
        when(config_mock.getString("type")).thenReturn("Unknown Test Type");
        ConfigObject configObject_mock = mock(ConfigObject.class);
        when(configObject_mock.toConfig()).thenReturn(config_mock);

        Assertions.assertNull(ExchangeRateProviderFactory.createProvider(configObject_mock));
    }

    @Test
    public void createProvider_throws_whenMissingTypeSpecificConfigs() {
        Config config_mock = mock(Config.class);
        when(config_mock.hasPath("type")).thenReturn(true);
        when(config_mock.getString("type")).thenReturn(ExchangeRateProvider.XRSourceType.ETH_CALL.name());
        ConfigObject configObject_mock = mock(ConfigObject.class);
        when(configObject_mock.toConfig()).thenReturn(config_mock);

        Assertions.assertNull(ExchangeRateProviderFactory.createProvider(configObject_mock));
    }

    @Test
    public void createProvider_returnsProvider () {
        Config config_mock = mock(Config.class);
        when(config_mock.hasPath("type")).thenReturn(true);
        when(config_mock.getString("type")).thenReturn(ExchangeRateProvider.XRSourceType.ETH_CALL.name());
        when(config_mock.hasPath("contract")).thenReturn(true);
        when(config_mock.getString("contract")).thenReturn("FakeContract");
        when(config_mock.hasPath("method")).thenReturn(true);
        when(config_mock.getString("method")).thenReturn("FakeMethod");
        when(config_mock.hasPath("params")).thenReturn(true);
        when(config_mock.getStringList("params")).thenReturn(new ArrayList<String>());
        ConfigObject configObject_mock = mock(ConfigObject.class);
        when(configObject_mock.toConfig()).thenReturn(config_mock);

        Assertions.assertInstanceOf(ExchangeRateProvider.class, ExchangeRateProviderFactory.createProvider(configObject_mock));
    }

}
