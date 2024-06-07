package co.rsk.mine.gas.provider;

import co.rsk.config.mining.StableMinGasPriceSystemConfig;
import co.rsk.mine.gas.provider.example.ExampleProvider;
import co.rsk.mine.gas.provider.web.WebMinGasPriceProvider;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinGasPriceProviderFactoryTest {


    @Test
    void createFixedMinGasPriceProvider() {
        Config testConfig = ConfigFactory.parseString(
                "enabled=true\n" +
                        "refreshRate=10\n" +
                        "minStableGasPrice=100\n" +
                        "method=FIXED"
        );
        StableMinGasPriceSystemConfig config = new StableMinGasPriceSystemConfig(testConfig);

        MinGasPriceProvider provider = MinGasPriceProviderFactory.create(100L, config);

        assertTrue(provider instanceof FixedMinGasPriceProvider);
        assertEquals(100, provider.getMinGasPrice());
        assertEquals(MinGasPriceProviderType.FIXED, provider.getType());
    }

    @Test
    void createWithNullConfig() {
        MinGasPriceProvider provider = MinGasPriceProviderFactory.create(100L, null);
        assertTrue(provider instanceof FixedMinGasPriceProvider);
        assertEquals(100, provider.getMinGasPrice());
        assertEquals(MinGasPriceProviderType.FIXED, provider.getType());
    }

    @Test
    void createWebProviderMethod() {
        Config testConfig = ConfigFactory.parseString(
                "enabled=true   \n" +
                        "refreshRate=10\n" +
                        "minStableGasPrice=100\n" +
                        "method=WEB\n" +
                        "web.url=url\n" +
                        "web.timeout=1000 \n" +
                        "web.apiKey=1234\n" +
                        "web.requestPath=price"
        );

        StableMinGasPriceSystemConfig disabledConfig = new StableMinGasPriceSystemConfig(testConfig);

        MinGasPriceProvider provider = MinGasPriceProviderFactory.create(100L, disabledConfig);

        assertTrue(provider instanceof WebMinGasPriceProvider);
        assertEquals(100, provider.getMinGasPrice());
        assertEquals(MinGasPriceProviderType.WEB, provider.getType());
    }

    @Test
    void createExampleProviderMethod() {
        Config testConfig = ConfigFactory.parseString(
                "enabled=true   \n" +
                        "refreshRate=10\n" +
                        "minStableGasPrice=100\n" +
                        "method=EXAMPLE_PROVIDER\n" +
                        "example.fixedPrice=250"
        );

        StableMinGasPriceSystemConfig disabledConfig = new StableMinGasPriceSystemConfig(testConfig);

        MinGasPriceProvider provider = MinGasPriceProviderFactory.create(100L, disabledConfig);

        assertTrue(provider instanceof ExampleProvider);
        assertEquals(250, provider.getMinGasPrice());
        assertEquals(MinGasPriceProviderType.EXAMPLE_PROVIDER, provider.getType());
    }

    @Test
    void createWithDisabledConfigReturnFixed() {
        Config testConfig = ConfigFactory.parseString(
                "enabled=false   \n" +
                        "refreshRate=10\n" +
                        "minStableGasPrice=100\n" +
                        "method=WEB\n" +
                        "web.url=url\n" +
                        "web.timeout=1000 \n" +
                        "web.apiKey=1234\n" +
                        "web.jsonPath=price"
        );

        StableMinGasPriceSystemConfig disabledConfig = new StableMinGasPriceSystemConfig(testConfig);

        MinGasPriceProvider provider = MinGasPriceProviderFactory.create(100L, disabledConfig);

        assertTrue(provider instanceof FixedMinGasPriceProvider);
        assertEquals(100, provider.getMinGasPrice());
        assertEquals(MinGasPriceProviderType.FIXED, provider.getType());
    }


}
