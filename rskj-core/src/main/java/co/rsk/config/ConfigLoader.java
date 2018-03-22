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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class ConfigLoader {

    private static final Logger logger = LoggerFactory.getLogger("config");

    private static final String YES = "yes";
    private static final String NO = "no";

    public static Config getConfigFromFiles() {
        File installerFile = new File("/etc/rsk/node.conf");
        Config installerConfig = installerFile.exists() ? ConfigFactory.parseFile(installerFile) : ConfigFactory.empty();
        logger.info(
                "Config ( {} ): default properties from installer '/etc/rsk/node.conf'",
                installerConfig.entrySet().isEmpty() ? NO : YES
        );

        String file = System.getProperty("rsk.conf.file");
        Config cmdLineConfigFile = file != null ? ConfigFactory.parseFile(new File(file)) : ConfigFactory.empty();
        logger.info(
                "Config ( {} ): user properties from -Drsk.conf.file file '{}'",
                cmdLineConfigFile.entrySet().isEmpty() ? NO : YES,
                file
        );

        Config userConfig = ConfigFactory.systemProperties()
                .withFallback(cmdLineConfigFile)
                .withFallback(installerConfig);
        Config networkBaseConfig = getNetworkBaseConfig(userConfig);
        return userConfig.withFallback(networkBaseConfig);
    }

    /**
     * @return the network-specific configuration based on the user config, or mainnet if no configuration is specified.
     */
    public static Config getNetworkBaseConfig(Config userConfig) {
        // these read reference.conf automatically, and overlay the network config on top
        if (userConfig.hasPath("blockchain.config.name")) {
            String network = userConfig.getString("blockchain.config.name");
            if ("testnet".equals(network)) {
                return ConfigFactory.load("config/testnet");
            } else if ("regtest".equals(network)) {
                return ConfigFactory.load("config/regtest");
            } else {
                logger.info("Invalid network '{}', using mainnet by default", network);
            }
        } else {
            logger.info("Network not set, using mainnet by default");
        }

        return ConfigFactory.load("config/main");
    }
}