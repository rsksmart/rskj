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

import co.rsk.cli.CliArg;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueFactory;
import org.ethereum.config.SystemProperties;

/**
 * Flags that the node can receive via command line arguments.
 * E.g. --testnet
 */
public enum NodeCliFlags implements CliArg {
    DB_RESET("reset", SystemProperties.PROPERTY_DB_RESET, true),
    DB_IMPORT("import", SystemProperties.PROPERTY_DB_IMPORT, true),
    VERIFY_CONFIG("verify-config", SystemProperties.PROPERTY_BC_VERIFY, true),
    PRINT_SYSTEM_INFO("print-system-info", SystemProperties.PROPERTY_PRINT_SYSTEM_INFO, true),
    NETWORK_TESTNET("testnet", SystemProperties.PROPERTY_BC_CONFIG_NAME, "testnet"),
    NETWORK_REGTEST("regtest", SystemProperties.PROPERTY_BC_CONFIG_NAME, "regtest"),
    NETWORK_DEVNET("devnet", SystemProperties.PROPERTY_BC_CONFIG_NAME, "devnet"),
    NETWORK_MAINNET("main", SystemProperties.PROPERTY_BC_CONFIG_NAME, "main"),
    ;

    private final String flagName;
    private final String configPath;
    private final Object configValue;

    NodeCliFlags(String flagName, String configPath, Object configValue) {
        this.flagName = flagName;
        this.configPath = configPath;
        this.configValue = configValue;
    }

    @Override
    public String getName() {
        return flagName;
    }

    /**
     * @return a new, augmented config with settings for this flag.
     */
    public Config withConfig(Config config) {
        return config.withValue(configPath, ConfigValueFactory.fromAnyRef(configValue));
    }
}
