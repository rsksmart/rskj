/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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
import co.rsk.rpc.ModuleDescription;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * Created by ajlopez on 3/16/2016.
 */
class RskSystemPropertiesTest {

    private final TestSystemProperties config = new TestSystemProperties();
    @Mock
    private CliArgs<NodeCliOptions, NodeCliFlags> cliArgs;

    @Test
    void defaultValues() {
        assertFalse(config.isMinerClientEnabled());
        assertFalse(config.isMinerServerEnabled());
        assertEquals(0, config.minerMinGasPrice());
        assertEquals(0, config.minerGasUnitInDollars(), 0.001);
        assertEquals(0, config.minerMinFeesNotifyInDollars(), 0.001);

        assertFalse(config.getIsHeartBeatEnabled());
    }

    @Test
    void hasMessagesConfiguredInTestConfig() {
        assertTrue(config.hasMessageRecorderEnabled());

        List<String> commands = config.getMessageRecorderCommands();
        assertNotNull(commands);
        assertEquals(2, commands.size());
        assertTrue(commands.contains("TRANSACTIONS"));
        assertTrue(commands.contains("RSK_MESSAGE:BLOCK_MESSAGE"));
    }

    @Test
    void shouldUseExpectedBloomConfigKeys() {
        ArgumentCaptor<String> configKeyCaptorForHasPath = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> configKeyCaptorForGetBoolean = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> configKeyCaptorForGetInt = ArgumentCaptor.forClass(String.class);

        Config config = mock(Config.class);
        doReturn(ConfigFactory.empty().root()).when(config).root();
        doReturn(true).when(config).hasPath(configKeyCaptorForHasPath.capture());
        doReturn(true).when(config).getBoolean(configKeyCaptorForGetBoolean.capture());
        doReturn("leveldb").when(config).getString("keyvalue.datasource");

        ConfigLoader loader = mock(ConfigLoader.class);
        doReturn(config).when(loader).getConfig();

        RskSystemProperties sysProperties = new RskSystemProperties(loader);

        Config expectedConfig = ConfigLoader.getExpectedConfig(ConfigFactory.empty(), ConfigFactory.empty());

        boolean bloomServiceEnabled = sysProperties.bloomServiceEnabled();
        assertTrue(bloomServiceEnabled);
        assertTrue(expectedConfig.hasPath(configKeyCaptorForHasPath.getValue()));
        assertTrue(expectedConfig.hasPath(configKeyCaptorForGetBoolean.getValue()));

        doReturn(11).when(config).getInt(configKeyCaptorForGetInt.capture());

        int bloomNumberOfBlocks = sysProperties.bloomNumberOfBlocks();
        assertEquals(11, bloomNumberOfBlocks);
        assertTrue(expectedConfig.hasPath(configKeyCaptorForHasPath.getValue()));
        assertTrue(expectedConfig.hasPath(configKeyCaptorForGetInt.getValue()));

        doReturn(12).when(config).getInt(configKeyCaptorForGetInt.capture());

        int bloomNumberOfConfirmations = sysProperties.bloomNumberOfConfirmations();
        assertEquals(12, bloomNumberOfConfirmations);
        assertTrue(expectedConfig.hasPath(configKeyCaptorForHasPath.getValue()));
        assertTrue(expectedConfig.hasPath(configKeyCaptorForGetInt.getValue()));
    }

    @Test
    void testRpcModules() {
        RskSystemProperties rskSystemProperties = new RskSystemProperties(
                new ConfigLoader(
                        new CliArgs.Parser<>(
                                NodeCliOptions.class,
                                NodeCliFlags.class
                        ).parse(new String[]{})
                )
        );

        List<String> disabledModuleNames = Stream.of( "sco", "debug", "trace").collect(Collectors.toList());

        Map<String, List<ModuleDescription>> moduleNameEnabledMap = rskSystemProperties.getRpcModules()
                .stream()
                .collect(Collectors.groupingBy(ModuleDescription::getName));

        assertTrue(disabledModuleNames.stream().noneMatch(k -> moduleNameEnabledMap.get(k).get(0).isEnabled()));

        List<String> enabledModuleNames = moduleNameEnabledMap.keySet()
                .stream().filter(k -> disabledModuleNames.stream().noneMatch(k::equals))
                .collect(Collectors.toList());

        assertTrue(enabledModuleNames.stream().allMatch(k -> moduleNameEnabledMap.get(k).get(0).isEnabled()));
    }

    @Test
    void testGetRpcModulesWithObject() {
        TestSystemProperties testSystemProperties = new TestSystemProperties(rawConfig ->
                ConfigFactory.parseString("{" +
                        "rpc.modules = {\n" +
                        "  eth {\n" +
                        "    version: \"1.5\",\n" +
                        "    enabled: \"true\",\n" +
                        "  },\n" +
                        "  web {\n" +
                        "    version: \"2.0\",\n" +
                        "    enabled: false,\n" +
                        "  }\n" +
                        "  net {\n" +
                        "    version: \"3.0\",\n" +
                        "    enabled: true,\n" +
                        "    methods: {\n" +
                        "       enabled: [ \"evm_snapshot\", \"evm_revert\" ],\n" +
                        "       disabled: [ \"evm_reset\"]\n" +
                        "    }\n" +
                        "  }\n" +
                        "}\n" +
                        " }").withFallback(rawConfig));

        List<ModuleDescription> rpcModules = testSystemProperties.getRpcModules();

        Map<String, ModuleDescription> moduleDescriptionMap = rpcModules.stream()
                .collect(Collectors.toMap(ModuleDescription::getName, Function.identity()));

        assertTrue(moduleDescriptionMap.containsKey("eth"));
        ModuleDescription ethModule = moduleDescriptionMap.get("eth");
        assertEquals("1.5", ethModule.getVersion());
        assertEquals(true, ethModule.isEnabled());
        ModuleDescription webModule = moduleDescriptionMap.get("web");
        assertEquals("2.0", webModule.getVersion());
        assertEquals(false, webModule.isEnabled());
        ModuleDescription netModule = moduleDescriptionMap.get("net");
        assertEquals(2, netModule.getEnabledMethods().size());
        assertEquals(1, netModule.getDisabledMethods().size());
    }
}
