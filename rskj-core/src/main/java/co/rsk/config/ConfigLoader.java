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
import org.ethereum.config.SystemProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;
import java.util.Objects;

/**
 * Loads configurations from different sources with the following precedence:
 * 1. Command line arguments
 * 2. Environment variables
 * 3. System properties
 * 4. User configuration file
 * 5. Installer configuration file
 * 6. Default settings per network in resources/[network].conf
 * 7. Default settings for all networks in resources/reference.conf
 */
public class ConfigLoader {

    private static final Logger logger = LoggerFactory.getLogger("config");

    private static final String MAINNET_RESOURCE_PATH = "config/main";
    private static final String TESTNET_RESOURCE_PATH = "config/testnet";
    private static final String REGTEST_RESOURCE_PATH = "config/regtest";
    private static final String DEVNET_RESOURCE_PATH = "config/devnet";
    private static final String YES = "yes";
    private static final String NO = "no";

    private final CliArgs<NodeCliOptions, NodeCliFlags> cliArgs;

    public ConfigLoader(CliArgs<NodeCliOptions, NodeCliFlags> cliArgs) {
        this.cliArgs = Objects.requireNonNull(cliArgs);
    }

    public Config getConfig() {
        Config userConfig = getConfigFromCliArgs()
                .withFallback(ConfigFactory.systemProperties())
                .withFallback(ConfigFactory.systemEnvironment())
                .withFallback(getUserCustomConfig())
                .withFallback(getInstallerConfig());
        Config networkBaseConfig = getNetworkDefaultConfig(userConfig);
        return userConfig.withFallback(networkBaseConfig);
    }

    private Config getConfigFromCliArgs() {
        Config config = ConfigFactory.empty();

        for (NodeCliFlags flag : cliArgs.getFlags()) {
            config = flag.withConfig(config);
        }

        for (Map.Entry<NodeCliOptions, String> entry : cliArgs.getOptions().entrySet()) {
            config = entry.getKey().withConfig(config, entry.getValue());
        }

        return config;
    }

    private Config getUserCustomConfig() {
        String file = System.getProperty("rsk.conf.file");
        Config cmdLineConfigFile = file != null ? ConfigFactory.parseFile(new File(file)) : ConfigFactory.empty();
        logger.info(
                "Config ( {} ): user properties from -Drsk.conf.file file '{}'",
                cmdLineConfigFile.entrySet().isEmpty() ? NO : YES,
                file
        );
        return cmdLineConfigFile;
    }

    private Config getInstallerConfig() {
        File installerFile = new File("/etc/rsk/node.conf");
        Config installerConfig = installerFile.exists() ? ConfigFactory.parseFile(installerFile) : ConfigFactory.empty();
        logger.info(
                "Config ( {} ): default properties from installer '/etc/rsk/node.conf'",
                installerConfig.entrySet().isEmpty() ? NO : YES
        );
        return installerConfig;
    }

    /**
     * @return the network-specific configuration based on the user config, or mainnet if no configuration is specified.
     */
    private Config getNetworkDefaultConfig(Config userConfig) {
        if (userConfig.hasPath(SystemProperties.PROPERTY_BC_CONFIG_NAME)) {
            String network = userConfig.getString(SystemProperties.PROPERTY_BC_CONFIG_NAME);
            if (NodeCliFlags.NETWORK_TESTNET.getName().equals(network)) {
                return ConfigFactory.load(TESTNET_RESOURCE_PATH);
            } else if (NodeCliFlags.NETWORK_REGTEST.getName().equals(network)) {
                return ConfigFactory.load(REGTEST_RESOURCE_PATH);
            } else if (NodeCliFlags.NETWORK_DEVNET.getName().equals(network)) {
                return ConfigFactory.load(DEVNET_RESOURCE_PATH);
            } else if (NodeCliFlags.NETWORK_MAINNET.getName().equals(network)) {
                return ConfigFactory.load(MAINNET_RESOURCE_PATH);
            } else {
                String exceptionMessage = String.format(
                        "%s is not a valid network name (%s property)",
                        network,
                        SystemProperties.PROPERTY_BC_CONFIG_NAME
                );
                logger.warn(exceptionMessage);
                throw new IllegalArgumentException(exceptionMessage);
            }
        }

        logger.info("Network not set, using mainnet by default");
        return ConfigFactory.load(MAINNET_RESOURCE_PATH);
    }
}
