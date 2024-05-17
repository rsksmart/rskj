package co.rsk.config.mining;

import co.rsk.mine.gas.provider.MinGasPriceProviderType;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StableMinGasPriceSystemConfigTest {

    private StableMinGasPriceSystemConfig config;

    @BeforeEach
    void setUp() {
        Config testConfig = ConfigFactory.parseString(
                "enabled=true\n" +
                        "refreshRate=10\n" +
                        "minStableGasPrice=100\n" +
                        "method=FIXED"
        );
        config = new StableMinGasPriceSystemConfig(testConfig);
    }

    @Test
    void testIsValid() {
        assertTrue(config.isValid());
    }

    @Test
    void testGetRefreshRate() {
        assertEquals(10, config.getRefreshRate());
    }

    @Test
    void testGetMinStableGasPrice() {
        assertEquals(100, config.getMinStableGasPrice());
    }

    @Test
    void testIsEnabled() {
        assertTrue(config.isEnabled());
    }

    @Test
    void testGetMethod() {
        assertEquals(MinGasPriceProviderType.FIXED, config.getMethod());
    }

    @Test
    void wrongMethodShouldThrowException() {
        Config testConfig = ConfigFactory.parseString(
                "enabled=true\n" +
                        "refreshRate=10\n" +
                        "minStableGasPrice=100\n" +
                        "method=INVALID"
        );
        assertThrows(
                ConfigException.BadValue.class,
                () -> new StableMinGasPriceSystemConfig(testConfig),
                "Expected to throw Config exception, but it didn't"
        );

    }

    @Test
    void missingPropertyShouldThrowException() {
        Config testConfig = ConfigFactory.parseString(
                "enabled=true\n" +
                        "minStableGasPrice=100\n" +
                        "method=FIXED"
        );
        assertThrows(
                ConfigException.Missing.class,
                () -> new StableMinGasPriceSystemConfig(testConfig),
                "Expected to throw Config exception, but it didn't"
        );
    }


}