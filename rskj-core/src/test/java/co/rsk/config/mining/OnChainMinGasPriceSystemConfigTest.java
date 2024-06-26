package co.rsk.config.mining;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OnChainMinGasPriceSystemConfigTest {
    private OnChainMinGasPriceSystemConfig config;
    private String address = "0x77045E71a7A2c50903d88e564cD72fab11e82051";
    private String from = "0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826";
    private String data = "0x98d5fdca";

    @BeforeEach
    void setUp() {
        Config testConfig = ConfigFactory.parseString(
                "address=\"" + address + "\"\n" +
                "from=\"" + from + "\"\n" +
                "data=\"" + data + "\""
        );
        config = new OnChainMinGasPriceSystemConfig(testConfig);
    }

    @Test
    void testAddress() {
        assertEquals(address, config.getAddress());
    }

    @Test
    void testFrom() {
        assertEquals(from, config.getFrom());
    }

    @Test
    void testData() {
        assertEquals(data, config.getData());
    }
}
