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
package co.rsk.mine.gas.provider;

import co.rsk.config.mining.StableMinGasPriceSystemConfig;
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

        MinGasPriceProvider provider = MinGasPriceProviderFactory.create(100L, config, () -> null);

        assertTrue(provider instanceof FixedMinGasPriceProvider);
        assertEquals(100, provider.getMinGasPrice());
        assertEquals(MinGasPriceProviderType.FIXED, provider.getType());
    }

    @Test
    void createWithNullConfig() {
        MinGasPriceProvider provider = MinGasPriceProviderFactory.create(100L, null, () -> null);
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
                        "web.requestPath=/price"
        );

        StableMinGasPriceSystemConfig disabledConfig = new StableMinGasPriceSystemConfig(testConfig);

        MinGasPriceProvider provider = MinGasPriceProviderFactory.create(100L, disabledConfig, () -> null);

        assertTrue(provider instanceof WebMinGasPriceProvider);
        assertEquals(100, provider.getMinGasPrice());
        assertEquals(MinGasPriceProviderType.WEB, provider.getType());
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

        MinGasPriceProvider provider = MinGasPriceProviderFactory.create(100L, disabledConfig, () -> null);

        assertTrue(provider instanceof FixedMinGasPriceProvider);
        assertEquals(100, provider.getMinGasPrice());
        assertEquals(MinGasPriceProviderType.FIXED, provider.getType());
    }


}
