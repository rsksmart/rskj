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

import co.rsk.cli.OptionalizableCliArg;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueFactory;
import org.ethereum.config.SystemProperties;

/**
 * Options that the node can receive via command line arguments.
 * E.g. -datadir /path/to/datadir
 */
public enum NodeCliOptions implements OptionalizableCliArg {
    RPC_CORS("rpccors", true) {
        @Override
        public Config withConfig(Config config, String configValue) {
            return config
                    .withValue(SystemProperties.PROPERTY_RPC_HTTP_ENABLED, ConfigValueFactory.fromAnyRef(true))
                    .withValue(SystemProperties.PROPERTY_RPC_CORS, ConfigValueFactory.fromAnyRef(configValue));
        }
    },
    ;

    private final String optionName;
    private final boolean optional;

    NodeCliOptions(String name, boolean optional) {
        this.optionName = name;
        this.optional = optional;
    }

    @Override
    public boolean isOptional() {
        return optional;
    }

    @Override
    public String getName() {
        return optionName;
    }

    /**
     * @return a new, augmented config with settings for this flag.
     */
    abstract public Config withConfig(Config config, String configValue);
}
