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

import co.rsk.cli.RskCli;
import co.rsk.rpc.ModuleDescription;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import org.ethereum.net.rlpx.Node;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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

    @Test
    void defaultValues() {
        assertFalse(config.isMinerClientEnabled());
        assertFalse(config.isMinerServerEnabled());
        assertEquals(0, config.minerMinGasPrice());
        assertEquals(0, config.minerGasUnitInDollars(), 0.001);
        assertEquals(0, config.minerMinFeesNotifyInDollars(), 0.001);
        assertEquals(1.1, config.getMinGasPriceMultiplier());

        assertEquals(3, config.getSnapshotMaxSenderRequests());

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
        RskCli rskCli = new RskCli();
        rskCli.load(new String[]{});

        RskSystemProperties rskSystemProperties = new RskSystemProperties(
                new ConfigLoader(
                        rskCli.getCliArgs()
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
    void rskCliSnapNodes_ShouldSetSnapBootNodes() {
        RskCli rskCli = new RskCli();
        String[] snapNodesArgs = {
                "--snap-nodes=enode://b2a304b30b3ff90aabcb5e37fa3cc70511c9f5bf457d6d8bfb6f0905baf6d714b66a73fede2ea0671b3a4d1af2aed3379d7eb9340d775ae27800e0757dc1e502@3.94.45.146:50501",
                "--snap-nodes=enode://b2a304b30b3ff90bbbcb5e37fa3cc70511c9f5bf457d6d8bfb6f0905baf6d714b66a73fede2ea0671b3a4d1af2aed3379d7eb9340d775ae27800e0757dc10502@3.94.45.146:50501"
        };
        rskCli.load(snapNodesArgs);

        RskSystemProperties rskSystemProperties = new RskSystemProperties(
                new ConfigLoader(
                        rskCli.getCliArgs()
                )
        );

        Node expectedFirstSnapNode = new Node("enode://b2a304b30b3ff90aabcb5e37fa3cc70511c9f5bf457d6d8bfb6f0905baf6d714b66a73fede2ea0671b3a4d1af2aed3379d7eb9340d775ae27800e0757dc1e502@3.94.45.146:50501");
        Node expectedSecondSnapNode = new Node("enode://b2a304b30b3ff90bbbcb5e37fa3cc70511c9f5bf457d6d8bfb6f0905baf6d714b66a73fede2ea0671b3a4d1af2aed3379d7eb9340d775ae27800e0757dc10502@3.94.45.146:50501");

        Assertions.assertEquals(2, rskSystemProperties.getSnapBootNodes().size());
        Assertions.assertEquals(expectedFirstSnapNode.getHexId(), rskSystemProperties.getSnapBootNodes().get(0).getHexId());
        Assertions.assertEquals(expectedSecondSnapNode.getHexId(), rskSystemProperties.getSnapBootNodes().get(1).getHexId());
        Assertions.assertEquals(expectedFirstSnapNode.getId(), rskSystemProperties.getSnapBootNodes().get(0).getId());
        Assertions.assertEquals(expectedSecondSnapNode.getId(), rskSystemProperties.getSnapBootNodes().get(1).getId());
    }

    @Test
    void rskCliSnapNodes_ShouldReturnZeroSnapBootNodesForInvalidNodeFormat() {
        RskCli rskCli = new RskCli();
        String[] snapNodesArgs = {
                "--snap-nodes=http://www.google.es",
        };

        rskCli.load(snapNodesArgs);

        Assertions.assertThrows(RuntimeException.class, () -> {
            RskSystemProperties rskSystemProperties = new RskSystemProperties(
                    new ConfigLoader(rskCli.getCliArgs())
            );

            Assertions.assertEquals(0, rskSystemProperties.getSnapBootNodes().size());
        });
    }

    @Test
    void rskCliSyncMode_ShouldSetSyncMode() {
        RskCli rskCli = new RskCli();
        String[] snapNodesArgs = {"--sync-mode=snap"};
        rskCli.load(snapNodesArgs);

        RskSystemProperties rskSystemProperties = new RskSystemProperties(
                new ConfigLoader(
                        rskCli.getCliArgs()
                )
        );

        Assertions.assertTrue(rskSystemProperties.isClientSnapshotSyncEnabled());
    }

    @Test
    void rskCliSyncMode_ShouldSetDefaultSyncMode() {
        RskCli rskCli = new RskCli();
        String[] snapNodesArgs = {"--sync-mode=full"};
        rskCli.load(snapNodesArgs);

        RskSystemProperties rskSystemProperties = new RskSystemProperties(
                new ConfigLoader(
                        rskCli.getCliArgs()
                )
        );

        Assertions.assertFalse(rskSystemProperties.isClientSnapshotSyncEnabled());
    }

    @Test
    void testGetRpcModulesWithList() {
        TestSystemProperties testSystemProperties = new TestSystemProperties(rawConfig ->
                ConfigFactory.parseString("{" +
                        "rpc.modules = [\n" +
                        "  {\n" +
                        "    name: \"eth\", \n" +
                        "    version: \"1.0\",\n" +
                        "    enabled: \"true\",\n" +
                        "  },\n" +
                        "  {\n" +
                        "    name: \"web\", \n" +
                        "    version: \"2.0\",\n" +
                        "    enabled: false,\n" +
                        "    timeout: 1000,\n" +
                        "  }\n" +
                        "  {\n" +
                        "    name: \"net\", \n" +
                        "    version: \"3.0\",\n" +
                        "    enabled: true,\n" +
                        "    methods: {\n" +
                        "       enabled: [ \"evm_snapshot\", \"evm_revert\" ],\n" +
                        "       disabled: [ \"evm_reset\"]\n" +
                        "       timeout: { \"eth_getBlockByHash\" = 5000}\n" +
                        "    }\n" +
                        "  }\n" +
                        "]\n" +
                        " }").withFallback(rawConfig));
        List<ModuleDescription> rpcModules = testSystemProperties.getRpcModules();

        Map<String, ModuleDescription> moduleDescriptionMap = rpcModules.stream()
                .collect(Collectors.toMap(ModuleDescription::getName, Function.identity()));

        assertTrue(moduleDescriptionMap.containsKey("eth"));
        ModuleDescription ethModule = moduleDescriptionMap.get("eth");
        assertEquals("1.0", ethModule.getVersion());
        assertEquals(true, ethModule.isEnabled());
        ModuleDescription webModule = moduleDescriptionMap.get("web");
        assertEquals("2.0", webModule.getVersion());
        assertEquals(false, webModule.isEnabled());
        assertEquals(1000, webModule.getTimeout());
        ModuleDescription netModule = moduleDescriptionMap.get("net");
        assertEquals(2, netModule.getEnabledMethods().size());
        assertEquals(1, netModule.getDisabledMethods().size());
        assertEquals(5000, netModule.getMethodTimeout("eth_getBlockByHash"));
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
                        "    timeout: 9000,\n" +
                        "    methods: {\n" +
                        "       enabled: [ \"evm_snapshot\", \"evm_revert\" ],\n" +
                        "       disabled: [ \"evm_reset\"]\n" +
                        "       timeout: { \"eth_getBlockByHash\" = 30000}\n" +
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
        assertEquals(9000, netModule.getTimeout());
        assertEquals(30000, netModule.getMethodTimeout("eth_getBlockByHash"));
    }

    @Test
    void testGasPriceMultiplier() {
        assertEquals(1.05, config.gasPriceMultiplier());
    }

    @Test
    void testGasPriceMultiplierWithNull() {
        // Set miner.gasPriceMultiplier to null which yields the same result as not finding the path
        TestSystemProperties testSystemProperties = new TestSystemProperties(rawConfig ->
                ConfigFactory.parseString("{" +
                        "rpc.gasPriceMultiplier = null" +
                        " }").withFallback(rawConfig));

        assertEquals(1.1, testSystemProperties.gasPriceMultiplier());
    }

    @Test
    void testGasPriceMultiplierThrowsErrorForInvalidType() {
        TestSystemProperties testSystemProperties = new TestSystemProperties(rawConfig ->
                ConfigFactory.parseString("{" +
                        "rpc.gasPriceMultiplier = invalid" +
                        " }").withFallback(rawConfig));

        Assertions.assertThrows(ConfigException.WrongType.class, testSystemProperties::gasPriceMultiplier);
    }

    @Test
    void testGasPriceMultiplierThrowsErrorForNegativeValue() {
        TestSystemProperties testSystemProperties = new TestSystemProperties(rawConfig ->
                ConfigFactory.parseString("{" +
                        "rpc.gasPriceMultiplier = -1" +
                        " }").withFallback(rawConfig));

        Assertions.assertThrows(RskConfigurationException.class, testSystemProperties::gasPriceMultiplier);
    }

    @Test
    void checkPeerLastSessionProperty(){
        TestSystemProperties testSystemProperties = new TestSystemProperties(rawConfig ->
                ConfigFactory.parseString("{" +
                        "peer {\n" +
                        "  discovery{ usePeersFromLastSession = true\n}" +
                                "}" +
                        " }").withFallback(rawConfig));

        assertTrue(testSystemProperties.usePeersFromLastSession());

        testSystemProperties = new TestSystemProperties(rawConfig ->
                ConfigFactory.parseString("{" +
                        "peer {\n" +
                        "  discovery{ usePeersFromLastSession = false\n}" +
                        "}" +
                        " }").withFallback(rawConfig));
        assertFalse(testSystemProperties.usePeersFromLastSession());

        testSystemProperties = new TestSystemProperties();

        assertFalse(testSystemProperties.usePeersFromLastSession());
    }

    @Test
    void checkLastSessionPeersFilePathProperty(){
        TestSystemProperties testSystemProperties = new TestSystemProperties(rawConfig ->
                ConfigFactory.parseString("{" +
                        "database {\n" +
                        "  dir = \"/dbdir\"\n" +
                        "}" +
                        " }").withFallback(rawConfig));

        assertEquals("/dbdir/lastPeers.properties", testSystemProperties.getLastKnewPeersFilePath().toString());
    }

}
