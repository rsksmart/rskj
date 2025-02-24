/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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
package co.rsk.config;

import co.rsk.cli.CliArgs;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class TestSystemPropertiesV2 {

    private final RskSystemProperties rskSystemProperties;

    public TestSystemPropertiesV2(String filename) {
        Config testConfig = ConfigFactory.parseResources(filename)
                .withFallback(ConfigFactory.parseResources("rskj.conf"));

        rskSystemProperties = new RskSystemProperties(getConfigLoader(testConfig));
    }

    public RskSystemProperties getRskSystemProperties() {
        return rskSystemProperties;
    }

    private static ConfigLoader getConfigLoader(Config config) {
        return new ConfigLoader(CliArgs.empty()) {
            @Override
            public Config getConfig() {
                return config;
            }
        };
    }

}
