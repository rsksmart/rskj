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
import org.ethereum.config.SystemProperties;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ConfigLoaderTest {

    private CliArgs<NodeCliOptions, NodeCliFlags> cliArgs;
    private ConfigLoader loader;

    @Before
    public void setUp() {
        cliArgs = (CliArgs<NodeCliOptions, NodeCliFlags>) mock(CliArgs.class);
        loader = new ConfigLoader(cliArgs);
    }

    @Test
    public void loadBaseMainnetConfigWithEmptyCliArgs() {
        Config config = loader.getConfig();

        assertThat(config.getString(SystemProperties.PROPERTY_BC_CONFIG_NAME), is("main"));
        assertThat(config.getBoolean(SystemProperties.PROPERTY_DB_RESET), is(false));
        assertThat(config.getString(SystemProperties.PROPERTY_RPC_CORS), is("*.rsk.co"));
        assertThat(config.getBoolean(SystemProperties.PROPERTY_RPC_ENABLED), is(true));
    }

    @Test
    public void regtestCliFlagOverridesNetworkBaseConfig() {
        when(cliArgs.getFlags())
                .thenReturn(Collections.singleton(NodeCliFlags.NETWORK_REGTEST));
        Config config = loader.getConfig();

        assertThat(config.getString(SystemProperties.PROPERTY_BC_CONFIG_NAME), is("regtest"));
    }

    @Test
    public void testnetCliFlagOverridesNetworkBaseConfig() {
        when(cliArgs.getFlags())
                .thenReturn(Collections.singleton(NodeCliFlags.NETWORK_TESTNET));
        Config config = loader.getConfig();

        assertThat(config.getString(SystemProperties.PROPERTY_BC_CONFIG_NAME), is("testnet"));
    }

    @Test
    public void dbResetCliFlagEnablesReset() {
        when(cliArgs.getFlags())
                .thenReturn(Collections.singleton(NodeCliFlags.DB_RESET));
        Config config = loader.getConfig();

        assertThat(config.getString(SystemProperties.PROPERTY_BC_CONFIG_NAME), is("main"));
        assertThat(config.getBoolean(SystemProperties.PROPERTY_DB_RESET), is(true));
    }

    @Test
    public void rpcCorsCliOptionEnablesCorsAndChangesHostname() {
        when(cliArgs.getOptions())
                .thenReturn(Collections.singletonMap(NodeCliOptions.RPC_CORS, "myhostname"));
        Config config = loader.getConfig();

        assertThat(config.getString(SystemProperties.PROPERTY_RPC_CORS), is("myhostname"));
        assertThat(config.getBoolean(SystemProperties.PROPERTY_RPC_ENABLED), is(true));
    }
}
