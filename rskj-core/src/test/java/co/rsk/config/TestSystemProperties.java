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

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class TestSystemProperties extends RskSystemProperties {
    private static final ConfigLoader TEST_LOADER = new ConfigLoader() {
        /**
         * Cache configurations that don't change so we don't read files multiple times.
         */
        private final Config TEST_CONFIG = ConfigFactory.parseResources("test-rskj.conf")
                .withFallback(ConfigFactory.parseResources("rskj.conf"))
                .withFallback(ConfigFactory.load("config/regtest"));

        @Override
        public Config getConfigFromFiles() {
            return TEST_CONFIG;
        }
    };

    public TestSystemProperties() {
        super(TEST_LOADER);
    }
}
