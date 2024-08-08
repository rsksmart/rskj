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
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;
import org.ethereum.config.SystemProperties;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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
    BASE_PATH("base-path", true) {
        @Override
        public Config withConfig(Config config, String configValue) {
            return config.withValue(SystemProperties.PROPERTY_BASE_PATH, ConfigValueFactory.fromAnyRef(configValue));
        }
    },
    SYNC_MODE("sync-mode", true) {
        @Override
        public Config withConfig(Config config, String configValue) {
            SyncMode mode;
            try {
                mode = SyncMode.valueOf(configValue.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid sync mode: " + configValue + ". The valid options are <full> or <snap>.");
            }

            if (mode == SyncMode.SNAP) {
                return config.withValue(RskSystemProperties.PROPERTY_SNAP_CLIENT_ENABLED, ConfigValueFactory.fromAnyRef(true));
            }
            return config;
        }
    },
    SNAP_NODES("snap-nodes", true) {
        @Override
        public Config withConfig(Config config, String configValue) {
            try {
                List<ConfigObject> snapConfigObjects = Arrays.stream(configValue.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(this::createConfigObjectFromSnapNode)
                        .collect(Collectors.toList());

                if(!snapConfigObjects.isEmpty()) {
                    ConfigValue snapConfigValue = ConfigValueFactory.fromIterable(snapConfigObjects);
                    return config.withValue(RskSystemProperties.PROPERTY_SNAP_NODES, snapConfigValue);
                }
                return config;
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("expecting URL in the format enode://PUBKEY@HOST:PORT", e);
            }
        }
        private ConfigObject createConfigObjectFromSnapNode(String snapNode) {
            try {
                return ConfigValueFactory.fromMap(Collections.singletonMap("url", ConfigValueFactory.fromAnyRef(snapNode)));
            } catch (Exception e) {
                throw new RuntimeException("Error processing SnapBoot Nodes configuration. Ensure the URL format is 'enode://PUBKEY@HOST:PORT'.");
            }
        }
    }
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
