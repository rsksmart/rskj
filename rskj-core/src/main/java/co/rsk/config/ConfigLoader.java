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

import javax.annotation.Nonnull;
import java.io.File;
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

    private final ConfigFactoryWrapper configFactory;

    public ConfigLoader(CliArgs<NodeCliOptions, NodeCliFlags> cliArgs, ConfigFactoryWrapper configFactory) {
        this.cliArgs = Objects.requireNonNull(cliArgs);
        this.configFactory = configFactory;
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
     * During the verification process the unified configuration is being tested against the setting names
     * defined in the expected.conf config file. The process is silent by default which means that in a case of any problems
     * with the config settings only error logs will be generated and the node will continue its running.
     *
     * <p>
     * If the <b><blockchain.config.verify/b> setting is {@code true} (either set in a .conf file or via <b>--verify-config</b> command line flag),
     * then in a case of any problems an exception will be thrown.
     *
     * Note:
     *  1. The <b><blockchain.config.verify/b> setting is {@code false} by default.
     *  2. Config verification process of matching actual and expected config settings is a recursive process and takes into
     *  account appropriate setting names. Scalar values are not tested for matching, e.g. if we have settingKey="some value"
     *  in the expected.conf file and settingKey=100 in a user config file, then it will pass the verification process.
     *
     * @throws RskConfigurationException on configuration errors
     */
    public Config getConfig() {
        Config cliConfig = getConfigFromCliArgs();
        Config systemPropsConfig = configFactory.systemProperties();
        Config systemEnvConfig = configFactory.systemEnvironment();
        Config userCustomConfig = getUserCustomConfig();
        Config installerConfig = getInstallerConfig();

        Config userConfig = configFactory.empty()
                .withFallback(cliConfig)
                .withFallback(systemPropsConfig)
                .withFallback(systemEnvConfig)
                .withFallback(userCustomConfig)
                .withFallback(installerConfig);
        Config networkBaseConfig = getNetworkDefaultConfig(userConfig);
        Config unifiedConfig = userConfig.withFallback(networkBaseConfig);

        Config expectedConfig = getExpectedConfig(systemPropsConfig, systemEnvConfig);
        boolean valid = isActualObjectValid("", expectedConfig.root(), unifiedConfig.root());

        if (unifiedConfig.getBoolean(SystemProperties.PROPERTY_BC_VERIFY) && !valid) {
            throw new RskConfigurationException("Verification of node config settings has failed. See the previous error logs for details.");
        }

        return unifiedConfig;
    }

    protected Config getExpectedConfig(Config systemPropsConfig, Config systemEnvConfig) {
        return configFactory.parseResourcesAnySyntax(EXPECTED_RESOURCE_PATH)
                .withFallback(systemPropsConfig)
                .withFallback(systemEnvConfig);
    }

    private Config getConfigFromCliArgs() {
        Config config = configFactory.empty();

        for (NodeCliFlags flag : cliArgs.getFlags()) {
            config = flag.withConfig(config);
        }

        for (Map.Entry<NodeCliOptions, String> entry : cliArgs.getOptions().entrySet()) {
            config = entry.getKey().withConfig(config, entry.getValue());
        }

        if (!cliArgs.getParamValueMap().isEmpty()) {
            for (String param: cliArgs.getParamValueMap().keySet()) {
                ConfigValue configValue = ConfigValueFactory.fromAnyRef(cliArgs.getParamValueMap().get(param));
                config = config.withValue(param, configValue);
            }
        }

        return config;
    }

    private Config getUserCustomConfig() {
        String file = System.getProperty("rsk.conf.file");
        Config cmdLineConfigFile = file != null ? configFactory.parseFile(new File(file)) : configFactory.empty();
        logger.info(
                "Config ( {} ): user properties from -Drsk.conf.file file '{}'",
                cmdLineConfigFile.entrySet().isEmpty() ? NO : YES,
                file
        );
        return cmdLineConfigFile;
    }

    private Config getInstallerConfig() {
        File installerFile = new File("/etc/rsk/node.conf");
        Config installerConfig = installerFile.exists() ? configFactory.parseFile(installerFile) : configFactory.empty();
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
                return configFactory.load(TESTNET_RESOURCE_PATH);
            } else if (NodeCliFlags.NETWORK_REGTEST.getName().equals(network)) {
                return configFactory.load(REGTEST_RESOURCE_PATH);
            } else if (NodeCliFlags.NETWORK_DEVNET.getName().equals(network)) {
                return configFactory.load(DEVNET_RESOURCE_PATH);
            } else if (NodeCliFlags.NETWORK_MAINNET.getName().equals(network)) {
                return configFactory.load(MAINNET_RESOURCE_PATH);
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
        return configFactory.load(MAINNET_RESOURCE_PATH);
    }

    private static boolean isActualObjectValid(@Nonnull String keyPath, @Nonnull ConfigObject expectedObject, @Nonnull ConfigObject actualObject) {
        boolean valid = true;
        String prefix = keyPath.isEmpty() ? "" : keyPath + ".";
        for (Map.Entry<String, ConfigValue> actualEntry : actualObject.entrySet()) {
            String actualEntryKey = actualEntry.getKey();
            ConfigValue actualEntryValue = actualEntry.getValue();
            if (expectedObject.isEmpty()) {
                // if expected object is empty, then the actual object should contain only scalar items
                if (isCollectionType(actualEntryValue.valueType())) {
                    String entryKeyPath = prefix + actualEntryKey;
                    logger.error("Expected scalar config value for key path `{}`. Actual value is {}. See expected.conf for the expected settings",
                            entryKeyPath, actualEntryValue);
                    valid = false;
                }
            } else {
                ConfigValue expectedEntryValue = expectedObject.get(actualEntryKey);
                String entryKeyPath = prefix + actualEntryKey;
                if (expectedEntryValue == null) {
                    logger.error("Unexpected config value {} for key path `{}`. See expected.conf for the expected settings", actualEntryValue, entryKeyPath);
                    valid = false;
                } else {
                    valid &= isActualValueValid(entryKeyPath, expectedEntryValue, actualEntryValue);
                }
            }
        }
        return valid;
    }

    private static boolean isActualListValid(@Nonnull String keyPath, @Nonnull ConfigList expectedList, @Nonnull ConfigList actualList) {
        if (expectedList.size() > 1) {
            throw new RuntimeException("An array in expected.conf should either be empty or contain one template item.");
        }

        boolean valid = true;
        int index = 0;
        for (ConfigValue actualItem : actualList) {
            if (expectedList.isEmpty()) {
                // if expected list is empty, then the actual list should contain only scalar items
                if (isCollectionType(actualItem.valueType())) {
                    String itemKeyPath = keyPath + "[" + index + "]";
                    logger.error("Expected scalar config value for key path `{}`. Actual value is {}. See expected.conf for the expected settings",
                            itemKeyPath, actualItem);
                    valid = false;
                }
            } else {
                // Assuming that all items in the list should have the same configuration structure.
                String itemKeyPath = keyPath + "[" + index + "]";
                ConfigValue expectedItem = expectedList.get(0);
                valid &= isActualValueValid(itemKeyPath, expectedItem, actualItem);
            }
            index++;
        }
        return valid;
    }

    private static boolean isActualValueValid(@Nonnull String keyPath, @Nonnull ConfigValue expectedValue, @Nonnull ConfigValue actualValue) {
        ConfigValueType actualValueType = Objects.requireNonNull(actualValue.valueType());
        ConfigValueType expectedValueType = Objects.requireNonNull(expectedValue.valueType());

        if (!isCollectionType(expectedValueType) && !isCollectionType(actualValueType)) {
            return true; // We don't verify non-collection types
        }

        if (expectedValueType != actualValueType) {
            logger.error("Config value type mismatch. `{}` has type {}, but should have {}. See expected.conf for the expected settings",
                    keyPath, actualValueType, expectedValueType);
            return false;
        }

        switch (actualValueType) {
            case OBJECT:
                ConfigObject actualObject = (ConfigObject) actualValue;
                ConfigObject expectedObject = (ConfigObject) expectedValue;
                return isActualObjectValid(keyPath, expectedObject, actualObject);
            case LIST:
                ConfigList actualList = (ConfigList) actualValue;
                ConfigList expectedList = (ConfigList) expectedValue;
                return isActualListValid(keyPath, expectedList, actualList);
            default:
                return true;
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
