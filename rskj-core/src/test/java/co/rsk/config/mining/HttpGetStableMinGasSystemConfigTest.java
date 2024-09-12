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

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class HttpGetStableMinGasSystemConfigTest {
    private HttpGetStableMinGasSystemConfig config;

    @BeforeEach
    void setUp() {
        Config testConfig = ConfigFactory.parseString(
                "url=\"http://test.url\"\n" +
                        "jsonPath=testPath\n" +
                        "timeout=1000 milliseconds\n"
        );
        config = new HttpGetStableMinGasSystemConfig(testConfig);
    }

    @Test
    void testGetUrl() {
        assertEquals("http://test.url", config.getUrl());
    }

    @Test
    void testRequestPath() {
        assertEquals("testPath", config.getJsonPath());
    }

    @Test
    void testGetTimeout() {
        assertEquals(Duration.ofMillis(1000), config.getTimeout());
    }
}