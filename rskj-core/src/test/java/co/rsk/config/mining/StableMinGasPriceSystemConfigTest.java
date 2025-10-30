/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package co.rsk.config.mining;

import co.rsk.mine.gas.provider.MinGasPriceProviderType;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class StableMinGasPriceSystemConfigTest {

    private StableMinGasPriceSystemConfig config;

    @BeforeEach
    void setUp() {
        Config testConfig = ConfigFactory.parseString(""" 
                enabled=true
                refreshRate=10 hours
                minStableGasPrice=100
                source={ method=FIXED }
                """);
        config = new StableMinGasPriceSystemConfig(testConfig);
    }

    @Test
    void testGetRefreshRate() {
        assertEquals(Duration.ofHours(10), config.getRefreshRate());
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
        Config testConfig = ConfigFactory.parseString("""
                enabled=true
                refreshRate=10
                minStableGasPrice=100
                source={ method=INVALID }
                """);
        assertThrows(ConfigException.BadValue.class, () -> new StableMinGasPriceSystemConfig(testConfig), "Expected to throw Config exception, but it didn't");
    }

    @Test
    void missingPropertyShouldThrowException() {
        Config testConfig = ConfigFactory.parseString("""
                enabled=true
                minStableGasPrice=100
                method=FIXED
                """);
        assertThrows(ConfigException.Missing.class, () -> new StableMinGasPriceSystemConfig(testConfig), "Expected to throw Config exception, but it didn't");
    }

    @Test
    void testRanges() {
        Config testConfig = ConfigFactory.parseString("""
                enabled=true
                minValidPrice=10
                maxValidPrice=1000
                source={ method=FIXED }
                """);
        config = new StableMinGasPriceSystemConfig(testConfig);
        assertEquals(10, config.getMinValidPrice());
        assertEquals(1000, config.getMaxValidPrice());
    }


}
