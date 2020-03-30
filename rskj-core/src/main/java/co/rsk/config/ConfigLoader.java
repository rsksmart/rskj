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
import com.typesafe.config.*;
import org.ethereum.config.SystemProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Class that encapsulates config loading strategy.
 */
public class ConfigLoader {

    private static final Logger logger = LoggerFactory.getLogger("config");

    private static final String MAINNET_RESOURCE_PATH = "config/main";
    private static final String TESTNET_RESOURCE_PATH = "config/testnet";
    private static final String REGTEST_RESOURCE_PATH = "config/regtest";
    private static final String DEVNET_RESOURCE_PATH = "config/devnet";
    private static final String EXPECTED_RESOURCE_PATH = "expected";
    private static final String YES = "yes";
    private static final String NO = "no";

    private final CliArgs<NodeCliOptions, NodeCliFlags> cliArgs;

    public ConfigLoader(CliArgs<NodeCliOptions, NodeCliFlags> cliArgs) {
        this.cliArgs = Objects.requireNonNull(cliArgs);
    }

    /**
     * Loads configurations from different sources with the following precedence:
     * 1. Command line arguments
     * 2. Environment variables
     * 3. System properties
     * 4. User configuration file
     * 5. Installer configuration file
     * 6. Default settings per network in resources/[network].conf
     * 7. Default settings for all networks in resources/reference.conf
     *
     * <p>
     * If the <b><blockchain.config.verify/b> setting is {@code true} (either set in a .conf file or via <b>--verify-config</b> command line flag),
     * then the loaded config wll be tested against the setting names defined in the expected.conf config file.
     *
     * Note:
     *  1. The <b><blockchain.config.verify/b> setting is {@code false} by default.
     *  2. Config verification process of matching actual and expected configs is recursive and takes into account appropriate setting names
     *      and values which are collections of other settings (LIST's and OBJECT's).
     *
     * @throws RskConfigurationException on configuration errors
     *
     * @see ConfigProblems
     */
    public Config getConfig() {
        Config cliConfig = getConfigFromCliArgs();
        Config systemPropsConfig = ConfigFactory.systemProperties();
        Config systemEnvConfig = ConfigFactory.systemEnvironment();
        Config userCustomConfig = getUserCustomConfig();
        Config installerConfig = getInstallerConfig();

        Config userConfig = ConfigFactory.empty()
                .withFallback(cliConfig)
                .withFallback(systemPropsConfig)
                .withFallback(systemEnvConfig)
                .withFallback(userCustomConfig)
                .withFallback(installerConfig);
        Config networkBaseConfig = getNetworkDefaultConfig(userConfig);
        Config unifiedConfig = userConfig.withFallback(networkBaseConfig);

        if (unifiedConfig.getBoolean(SystemProperties.PROPERTY_BC_VERIFY)) {
            Config expectedConfig = ConfigFactory.parseResourcesAnySyntax(EXPECTED_RESOURCE_PATH)
                    .withFallback(systemPropsConfig)
                    .withFallback(systemEnvConfig);

            ArrayList<String> problems = new ArrayList<>();
            verify("", expectedConfig.root(), unifiedConfig.root(), problems);
            if (!problems.isEmpty()) {
                throw new RskConfigurationException("Verification of node configs has failed. The following problems were found: "
                        + String.join("; ", problems));
            }
        }

        return unifiedConfig;
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

    private static void verify(String keyPath, @Nullable ConfigValue expectedValue, ConfigValue actualValue, List<String> problems) {
        Objects.requireNonNull(keyPath);
        Objects.requireNonNull(actualValue);
        ConfigValueType actualValueType = Objects.requireNonNull(actualValue.valueType());

        if (expectedValue == null) {
            problems.add(ConfigProblems.unexpectedKeyProblem(keyPath, actualValue));
            return;
        }

        ConfigValueType expectedValueType = Objects.requireNonNull(expectedValue.valueType());

        if (!isCollectionType(expectedValueType) && !isCollectionType(actualValueType)) {
            return; // We don't verify non-collection types
        }

        if (expectedValueType != actualValueType) {
            problems.add(ConfigProblems.typeMismatchProblem(keyPath, expectedValue, actualValue));
            return;
        }

        switch (actualValueType) {
            case OBJECT:
                ConfigObject actualObject = (ConfigObject) actualValue;
                ConfigObject expectedObject = (ConfigObject) expectedValue;
                String prefix = keyPath.isEmpty() ? "" : keyPath + ".";
                for (Map.Entry<String, ConfigValue> actualEntry : actualObject.entrySet()) {
                    if (expectedObject.isEmpty()) {
                        // if expected object is empty, then the actual object should contain only scalar items
                        if (isCollectionType(actualEntry.getValue().valueType())) {
                            problems.add(ConfigProblems.expectedScalarValueProblem(prefix + actualEntry.getKey(), actualEntry.getValue()));
                        }
                    } else {
                        ConfigValue expectedEntryValue = expectedObject.get(actualEntry.getKey());
                        verify(prefix + actualEntry.getKey(), expectedEntryValue, actualEntry.getValue(), problems);
                    }
                }
                break;
            case LIST:
                ConfigList actualList = (ConfigList) actualValue;
                ConfigList expectedList = (ConfigList) expectedValue;
                if (expectedList.size() > 1) {
                    throw new RuntimeException("An array in expected.conf should either be empty or contain one template item.");
                }

                int index = 0;
                for (ConfigValue actualItem : actualList) {
                    if (expectedList.isEmpty()) {
                        // if expected list is empty, then the actual list should contain only scalar items
                        if (isCollectionType(actualItem.valueType())) {
                            problems.add(ConfigProblems.expectedScalarValueProblem(keyPath + "[" + index + "]", actualItem));
                        }
                    } else {
                        // Assuming that all items in the list should have the same configuration structure.
                        verify(keyPath + "[" + index + "]", expectedList.get(0), actualItem, problems);
                    }
                    index++;
                }
                break;
            default:
                break;
        }
    }

    /**
     * Checks whether the value type is a collection of other values.
     *
     * @return {@code true} if the value type is either {@link ConfigValueType#OBJECT} or {@link ConfigValueType#LIST}.
     */
    public static boolean isCollectionType(ConfigValueType valueType) {
        return valueType == ConfigValueType.OBJECT || valueType == ConfigValueType.LIST;
    }


}
