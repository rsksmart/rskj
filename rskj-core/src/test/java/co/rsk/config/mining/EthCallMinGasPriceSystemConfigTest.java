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

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EthCallMinGasPriceSystemConfigTest {
    private EthCallMinGasPriceSystemConfig config;
    private String to = "0x77045E71a7A2c50903d88e564cD72fab11e82051";
    private String from = "0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826";
    private String data = "0x98d5fdca";

    @BeforeEach
    void setUp() {
        Config testConfig = ConfigFactory.parseString(
                "to=\"" + to + "\"\n" +
                "from=\"" + from + "\"\n" +
                "data=\"" + data + "\""
        );
        config = new EthCallMinGasPriceSystemConfig(testConfig);
    }

    @Test
    void testAddress() {
        assertEquals(to, config.getAddress());
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
