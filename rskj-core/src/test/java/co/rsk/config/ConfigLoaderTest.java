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
import com.typesafe.config.impl.ConfigImpl;
import org.apache.commons.lang3.tuple.Pair;
import org.ethereum.config.SystemProperties;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ConfigLoaderTest {

    private static final ConfigValue NULL_VALUE = ConfigValueFactory.fromAnyRef(null);
    private static final ConfigValue TRUE_VALUE = ConfigValueFactory.fromAnyRef(true);
    private static final ConfigValue ZERO_VALUE = ConfigValueFactory.fromAnyRef(0);
    private static final ConfigValue STRING_VALUE = ConfigValueFactory.fromAnyRef("<string>");
    private static final ConfigValue EMPTY_OBJECT_VALUE = ConfigValueFactory.fromMap(Collections.emptyMap());
    private static final ConfigValue EMPTY_LIST_VALUE = ConfigValueFactory.fromIterable(Collections.emptyList());
    private static final Config EMPTY_CONFIG = ConfigImpl.emptyConfig(null);

    @Mock
    private CliArgs<NodeCliOptions, NodeCliFlags> cliArgs;

    @Mock
    private ConfigFactoryWrapper configFactory;

    private ConfigLoader loaderReal;

    private ConfigLoader loaderMocked;

    @Before
    public void setUp() {
        loaderReal = new ConfigLoader(cliArgs, ConfigFactoryWrapper.getInstance());
        loaderMocked = new ConfigLoader(cliArgs, configFactory);
    }

    @Test
    public void loadBaseMainnetConfigWithEmptyCliArgs() {
        Config config = loaderReal.getConfig();

        assertThat(config.getString(SystemProperties.PROPERTY_BC_CONFIG_NAME), is("main"));
        assertThat(config.getBoolean(SystemProperties.PROPERTY_DB_RESET), is(false));
        assertThat(config.getString(SystemProperties.PROPERTY_RPC_CORS), is("localhost"));
        assertThat(config.getBoolean(SystemProperties.PROPERTY_RPC_HTTP_ENABLED), is(true));
    }

    @Test
    public void regtestCliFlagOverridesNetworkBaseConfig() {
        when(cliArgs.getFlags())
                .thenReturn(Collections.singleton(NodeCliFlags.NETWORK_REGTEST));
        Config config = loaderReal.getConfig();

        assertThat(config.getString(SystemProperties.PROPERTY_BC_CONFIG_NAME), is("regtest"));
        assertThat(config.getBoolean(SystemProperties.PROPERTY_RPC_HTTP_ENABLED), is(true));
    }

    @Test
    public void testnetCliFlagOverridesNetworkBaseConfig() {
        when(cliArgs.getFlags())
                .thenReturn(Collections.singleton(NodeCliFlags.NETWORK_TESTNET));
        Config config = loaderReal.getConfig();

        assertThat(config.getString(SystemProperties.PROPERTY_BC_CONFIG_NAME), is("testnet"));
    }

    @Test
    public void dbResetCliFlagEnablesReset() {
        when(cliArgs.getFlags())
                .thenReturn(Collections.singleton(NodeCliFlags.DB_RESET));
        Config config = loaderReal.getConfig();

        assertThat(config.getString(SystemProperties.PROPERTY_BC_CONFIG_NAME), is("main"));
        assertThat(config.getBoolean(SystemProperties.PROPERTY_DB_RESET), is(true));
    }

    @Test
    public void rpcCorsCliOptionEnablesCorsAndChangesHostname() {
        when(cliArgs.getOptions())
                .thenReturn(Collections.singletonMap(NodeCliOptions.RPC_CORS, "myhostname"));
        Config config = loaderReal.getConfig();

        assertThat(config.getString(SystemProperties.PROPERTY_RPC_CORS), is("myhostname"));
        assertThat(config.getBoolean(SystemProperties.PROPERTY_RPC_HTTP_ENABLED), is(true));
    }

    @Test
    public void verifyConfigSettingIsOffByDefault() {
        Config config = loaderReal.getConfig();

        assertThat(config.getBoolean(SystemProperties.PROPERTY_BC_VERIFY), is(false));
    }

    @Test
    public void setVerifyConfigSetting() {
        when(cliArgs.getFlags()).thenReturn(Collections.singleton(NodeCliFlags.VERIFY_CONFIG));
        Config config = loaderReal.getConfig();

        assertThat(config.getBoolean(SystemProperties.PROPERTY_BC_VERIFY), is(true));
    }

    @Test
    public void printSystemInfoSettingIsOffByDefault() {
        Config config = loaderReal.getConfig();

        assertThat(config.getBoolean(SystemProperties.PROPERTY_PRINT_SYSTEM_INFO), is(false));
    }

    @Test
    public void setPrintSystemInfoSetting() {
        when(cliArgs.getFlags()).thenReturn(Collections.singleton(NodeCliFlags.PRINT_SYSTEM_INFO));
        Config config = loaderReal.getConfig();

        assertThat(config.getBoolean(SystemProperties.PROPERTY_PRINT_SYSTEM_INFO), is(true));
    }

    @Test(expected = RskConfigurationException.class)
    public void detectUnexpectedKeyProblem() {
        Config defaultConfig = EMPTY_CONFIG
                .withValue("blockchain.config.verify", TRUE_VALUE)
                .withValue("unexpectedKey", NULL_VALUE);
        Config expectedConfig = EMPTY_CONFIG
                .withValue("blockchain.config.verify", TRUE_VALUE)
                .withValue("expectedKey", NULL_VALUE);

        mockConfigFactory(defaultConfig, expectedConfig);

        loaderMocked.getConfig();
    }

    @Test(expected = RskConfigurationException.class)
    public void detectExpectedScalarValueProblemInObject() {
        Config defaultConfig = EMPTY_CONFIG
                .withValue("blockchain.config.verify", TRUE_VALUE)
                .withValue("expectedKey.nestedKey", EMPTY_OBJECT_VALUE);
        Config expectedConfig = EMPTY_CONFIG
                .withValue("blockchain.config.verify", TRUE_VALUE)
                .withValue("expectedKey", EMPTY_OBJECT_VALUE);

        mockConfigFactory(defaultConfig, expectedConfig);

        loaderMocked.getConfig();
    }

    @Parameterized.Parameters
    @Test(expected = RskConfigurationException.class)
    public void detectExpectedScalarValueProblemInList() {
        Config defaultConfig = EMPTY_CONFIG
                .withValue("blockchain.config.verify", TRUE_VALUE)
                .withValue("expectedKey", ConfigValueFactory.fromIterable(Collections.singletonList(EMPTY_LIST_VALUE)));
        Config expectedConfig = EMPTY_CONFIG
                .withValue("blockchain.config.verify", TRUE_VALUE)
                .withValue("expectedKey", EMPTY_LIST_VALUE);

        mockConfigFactory(defaultConfig, expectedConfig);

        loaderMocked.getConfig();
    }

    @Test
    public void detectTypeMismatchProblem() {
        ConfigValue[] values = { NULL_VALUE, TRUE_VALUE, ZERO_VALUE, STRING_VALUE, EMPTY_OBJECT_VALUE, EMPTY_LIST_VALUE };
        Predicate<ConfigValueType> isCollectionType = ConfigLoader::isCollectionType;

        BiConsumer<ConfigValue, ConfigValue> checkTypeMismatchProblem = (ConfigValue expectedValue, ConfigValue actualValue) -> {
            Config defaultConfig = EMPTY_CONFIG
                    .withValue("blockchain.config.verify", TRUE_VALUE)
                    .withValue("expectedKey", actualValue);
            Config expectedConfig = EMPTY_CONFIG
                    .withValue("blockchain.config.verify", TRUE_VALUE)
                    .withValue("expectedKey", expectedValue);

            mockConfigFactory(defaultConfig, expectedConfig);

            try {
                loaderMocked.getConfig();

                fail("Type mismatch problem is not detected");
            } catch (RskConfigurationException e) { /* ignore */ }
        };

        // stream of test data
        Stream.of(values)
                .flatMap(expectedValue -> Stream.of(values)
                        .filter(actualValue -> expectedValue.valueType() != actualValue.valueType()
                                && (isCollectionType.test(expectedValue.valueType()) || isCollectionType.test(actualValue.valueType())))
                        .map(actualValue -> Pair.of(expectedValue, actualValue)))
                .forEach(pair -> checkTypeMismatchProblem.accept(pair.getLeft(), pair.getRight()));
    }

    @Test
    public void cliParamValueMapOverrideBaseConfig() {
        when(cliArgs.getParamValueMap())
                .thenReturn(Collections.singletonMap("database.dir", "/home/rsk/data"));
        Config config = loaderReal.getConfig();

        assertThat(config.getString("database.dir"), is("/home/rsk/data"));
    }

    private void mockConfigFactory(Config defaultConfig, Config expectedConfig) {
        when(configFactory.empty()).thenReturn(EMPTY_CONFIG);
        when(configFactory.systemProperties()).thenReturn(EMPTY_CONFIG);
        when(configFactory.systemEnvironment()).thenReturn(EMPTY_CONFIG);
        when(configFactory.load(anyString())).thenReturn(defaultConfig);
        when(configFactory.parseResourcesAnySyntax(anyString())).thenReturn(expectedConfig);
    }
}
