package co.rsk.config.mining;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WebStableMinGasSystemConfigTest {
    private WebStableMinGasSystemConfig config;

    @BeforeEach
    void setUp() {
        Config testConfig = ConfigFactory.parseString(
                "url=\"http://test.url\"\n" +
                        "requestPath=testPath\n" +
                        "timeout=1000\n" +
                        "apiKey=testApiKey"
        );
        config = new WebStableMinGasSystemConfig(testConfig);
    }

    @Test
    void testGetUrl() {
        assertEquals("http://test.url", config.getUrl());
    }

    @Test
    void testRequestPath() {
        assertEquals("testPath", config.getRequestPath());
    }

    @Test
    void testGetTimeout() {
        assertEquals(1000, config.getTimeout());
    }

    @Test
    void testGetApiKey() {
        assertEquals("testApiKey", config.getApiKey());
    }
}