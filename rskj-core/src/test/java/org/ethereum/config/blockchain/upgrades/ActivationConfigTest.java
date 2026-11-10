/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package org.ethereum.config.blockchain.upgrades;

import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP103;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP351;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP535;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP85;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP98;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.values;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigResolveOptions;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class ActivationConfigTest {
    private static final Config BASE_CONFIG = ConfigFactory.parseString(String.join("\n",
        "hardforkActivationHeights: {",
        "    genesis: 0",
        "    bahamas: 0",
        "    afterBridgeSync: 0,",
        "    orchid: 0,",
        "    orchid060: 0,",
        "    wasabi100: 0",
        "    papyrus200: 0",
        "    twoToThree: 0",
        "    iris300: 0",
        "    hop400: 0",
        "    hop401: 0",
        "    fingerroot500: 0",
        "    arrowhead600: 0",
        "    arrowhead631: 0",
        "    lovell700: 0",
        "    reed800: 0",
        "    reed810: 0",
        "    vetiver900: 0",
        "    cardamom1000: 0",
        "},",
        "consensusRules: {",
        "    areBridgeTxsPaid: afterBridgeSync,",
        "    rskip85: orchid,",
        "    rskip87: orchid,",
        "    rskip88: orchid,",
        "    rskip89: orchid,",
        "    rskip90: orchid,",
        "    rskip91: orchid,",
        "    rskip92: orchid,",
        "    rskip97: orchid,",
        "    rskip98: orchid,",
        "    rskip103: orchid060,",
        "    rskip106: wasabi100,",
        "    rskip110: wasabi100,",
        "    rskip119: wasabi100,",
        "    rskip120: wasabi100,",
        "    rskip122: wasabi100,",
        "    rskip123: wasabi100,",
        "    rskip124: wasabi100,",
        "    rskip125: wasabi100",
        "    rskip126: wasabi100",
        "    rskip132: wasabi100",
        "    rskip134: papyrus200",
        "    rskip136: bahamas",
        "    rskip137: papyrus200",
        "    rskip140: papyrus200,",
        "    rskip143: papyrus200",
        "    rskip144: reed810",
        "    rskip146: papyrus200",
        "    rskip150: twoToThree",
        "    rskip151: papyrus200",
        "    rskip152: papyrus200",
        "    rskip156: papyrus200",
        "    rskipUMM: papyrus200",
        "    rskip153: iris300",
        "    rskip169: iris300",
        "    rskip170: iris300",
        "    rskip171: iris300",
        "    rskip174: iris300",
        "    rskip176: iris300",
        "    rskip179: iris300",
        "    rskip180: iris300",
        "    rskip181: iris300",
        "    rskip185: iris300",
        "    rskip186: iris300",
        "    rskip191: iris300",
        "    rskip197: iris300",
        "    rskip199: iris300",
        "    rskip200: iris300",
        "    rskip201: iris300",
        "    rskip203: arrowhead600",
        "    rskip218: iris300",
        "    rskip219: iris300",
        "    rskip220: iris300",
        "    rskip252: fingerroot500",
        "    rskip271: hop400",
        "    rskip284: hop400",
        "    rskip290: hop400",
        "    rskip293: hop400",
        "    rskip294: hop400",
        "    rskip297: hop400",
        "    rskip305: reed800",
        "    rskip326: fingerroot500",
        "    rskip351: reed810",
        "    rskip353: hop401",
        "    rskip357: hop401",
        "    rskip374: fingerroot500",
        "    rskip375: fingerroot500",
        "    rskip376: arrowhead600",
        "    rskip377: fingerroot500",
        "    rskip378: cardamom1000",
        "    rskip379: arrowhead600",
        "    rskip383: fingerroot500",
        "    rskip385: fingerroot500",
        "    rskip398: arrowhead600",
        "    rskip400: arrowhead600",
        "    rskip412: arrowhead600",
        "    rskip415: arrowhead600",
        "    rskip417: arrowhead600",
        "    rskip419: lovell700",
        "    rskip427: lovell700",
        "    rskip428: lovell700",
        "    rskip434: arrowhead631",
        "    rskip438: lovell700",
        "    rskip445: lovell700",
        "    rskip446: lovell700",
        "    rskip453: lovell700",
        "    rskip454: lovell700",
        "    rskip455: cardamom1000",
        "    rskip459: lovell700",
        "    rskip460: lovell700",
        "    rskip502: reed810",
        "    rskip516: reed800",
        "    rskip529: reed810",
        "    rskip535: vetiver900",
        "    rskip536: reed810",
        "    rskip540: vetiver900",
        "    rskip543: cardamom1000",
        "    rskip544: vetiver900",
        "    rskip545: cardamom1000",
        "    rskip546: cardamom1000",
        "    rskip551: vetiver900",
        "    rskip552: vetiver900",
        "    rskip559: cardamom1000",
        "    rskip692: cardamom1000",
        "    rskip701: cardamom1000",
        "}"
    ));

    @Test
    void readBaseConfig() {
        ActivationConfig config = ActivationConfig.read(BASE_CONFIG);

        for (ConsensusRule value : values()) {
            MatcherAssert.assertThat(config.isActive(value, 42), is(true));
        }
    }

    @Test
    void readWithTwoUpgradesInOrchid060() {
        ActivationConfig config = ActivationConfig.read(BASE_CONFIG
            .withValue("hardforkActivationHeights.orchid060", ConfigValueFactory.fromAnyRef(200))
            .withValue("consensusRules.rskip98", ConfigValueFactory.fromAnyRef("orchid060"))
        );

        for (ConsensusRule value : values()) {
            if (value == RSKIP98 || value == RSKIP103) {
                MatcherAssert.assertThat(config.isActive(value, 100), is(false));
            } else {
                MatcherAssert.assertThat(config.isActive(value, 100), is(true));
            }
        }
    }

    @Test
    void readWithOneHardcodedActivationNumber() {
        ActivationConfig config = ActivationConfig.read(
            BASE_CONFIG.withValue("consensusRules.rskip85", ConfigValueFactory.fromAnyRef(200))
        );

        for (ConsensusRule value : values()) {
            if (value == RSKIP85) {
                MatcherAssert.assertThat(config.isActive(value, 100), is(false));
            } else {
                MatcherAssert.assertThat(config.isActive(value, 100), is(true));
            }
        }
    }

    @Test
    void failsReadingWithMissingNetworkUpgrade() {
        Config config = BASE_CONFIG.withoutPath("consensusRules.rskip85");
        assertThrows(IllegalArgumentException.class, () -> ActivationConfig.read(config));
    }

    @Test
    void failsReadingWithMissingHardFork() {
        Config config = BASE_CONFIG.withoutPath("hardforkActivationHeights.orchid");
        assertThrows(IllegalArgumentException.class, () -> ActivationConfig.read(config));
    }

    @Test
    void failsReadingWithUnknownForkConfiguration() {
        Config config = BASE_CONFIG.withValue("hardforkActivationHeights.orkid", ConfigValueFactory.fromAnyRef(200));
        assertThrows(IllegalArgumentException.class, () -> ActivationConfig.read(config));
    }

    @Test
    void failsReadingWithUnknownUpgradeConfiguration() {
        Config config = BASE_CONFIG.withValue("consensusRules.rskip420", ConfigValueFactory.fromAnyRef("orchid"));
        assertThrows(IllegalArgumentException.class, () -> ActivationConfig.read(config));
    }

    @Test
    void headerVersion0() {
        ActivationConfig config = ActivationConfigsForTest.allBut(RSKIP351, RSKIP535);
        assertEquals((byte) 0x0, config.getHeaderVersion(10));
    }

    @Test
    void headerVersion1() {
        ActivationConfig config = ActivationConfigsForTest.allBut(RSKIP535);
        assertEquals((byte) 0x1, config.getHeaderVersion(10));
    }

    @Test
    void headerVersion2() {
        ActivationConfig config = ActivationConfigsForTest.all();
        assertEquals((byte) 0x2, config.getHeaderVersion(10));
    }

    @Test
    void isActive_allRulesActive_returnsTrue() {
        ActivationConfig config = ActivationConfig.read(BASE_CONFIG);
        assertTrue(config.areActive(42L, RSKIP85, RSKIP98, RSKIP103));
    }

    @Test
    void isActive_oneRuleInactive_returnsFalse() {
        ActivationConfig config = ActivationConfigsForTest.allBut(RSKIP351);
        assertFalse(config.areActive(10L, RSKIP85, RSKIP351));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("activationDependencies")
    void acceptsRequiredRuleActivatingBefore(Dependency dependency) {
        ActivationConfig activationConfig = ActivationConfig.read(dependency.config(200, 100));

        assertTrue(activationConfig.isActive(ConsensusRule.fromConfigKey(dependency.dependentKey()), 200));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("activationDependencies")
    void acceptsRequiredRuleActivatingAtTheSameHeight(Dependency dependency) {
        ActivationConfig activationConfig = ActivationConfig.read(dependency.config(200, 200));

        assertTrue(activationConfig.isActive(ConsensusRule.fromConfigKey(dependency.dependentKey()), 200));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("activationDependencies")
    void rejectsRequiredRuleActivatingAfter(Dependency dependency) {
        Config config = dependency.config(100, 200);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> ActivationConfig.read(config));

        assertEquals(dependency.dependentKey() + " (height 100) requires " + dependency.requiredKey() + " active at or before 100, but it activates at 200",
                exception.getMessage());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("activationDependencies")
    void rejectsDisabledRequiredRule(Dependency dependency) {
        Config config = dependency.config(100, -1);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> ActivationConfig.read(config));

        assertEquals(dependency.dependentKey() + " (height 100) requires " + dependency.requiredKey() + " active at or before 100, but it is disabled",
                exception.getMessage());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("activationDependencies")
    void dependentActiveFromGenesisRejectsDisabledRequiredRule(Dependency dependency) {
        Config config = dependency.config(0, -1);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> ActivationConfig.read(config));

        assertEquals(dependency.dependentKey() + " (height 0) requires " + dependency.requiredKey() + " active at or before 0, but it is disabled",
                exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"rskip545", "rskip692"})
    void failsReadingWithMissingRuleOfActivationDependency(String missingKey) {
        Config config = BASE_CONFIG.withoutPath("consensusRules." + missingKey);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> ActivationConfig.read(config));

        assertEquals("The configuration must contain all consensus rule values but is missing [" + missingKey + "]",
                exception.getMessage());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("activationDependencies")
    void disabledDependentImposesNoOrderingOnRequiredRule(Dependency dependency) {
        Config disabledRequired = dependency.config(-1, -1);
        Config lateRequired = dependency.config(-1, 200);

        assertFalse(ActivationConfig.read(disabledRequired).isActive(ConsensusRule.fromConfigKey(dependency.dependentKey()), 1000));
        assertFalse(ActivationConfig.read(lateRequired).isActive(ConsensusRule.fromConfigKey(dependency.dependentKey()), 1000));
    }

    @Test
    void reportsTheFirstViolatedRuleInConsensusRuleOrder() {
        // RSKIP545 and RSKIP546 both activate before RSKIP543, which they both require
        Config config = BASE_CONFIG
                .withValue("consensusRules.rskip545", ConfigValueFactory.fromAnyRef(100))
                .withValue("consensusRules.rskip546", ConfigValueFactory.fromAnyRef(100))
                .withValue("consensusRules.rskip543", ConfigValueFactory.fromAnyRef(200));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> ActivationConfig.read(config));

        assertEquals("rskip545 (height 100) requires rskip543 active at or before 100, but it activates at 200",
                exception.getMessage());
    }

    @Test
    void testedDependenciesMatchTheProductionTable() {
        // a pair added to the production table but not to activationDependencies() would otherwise go untested
        Map<ConsensusRule, Set<ConsensusRule>> productionDependencies = ActivationConfig.ACTIVATION_DEPENDENCIES
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));

        assertEquals(expectedDependencies(), productionDependencies);
    }

    /**
     * The pairs of {@link #activationDependencies()}, grouped by dependent rule.
     */
    private static Map<ConsensusRule, Set<ConsensusRule>> expectedDependencies() {
        return activationDependencies().collect(Collectors.groupingBy(
                dependency -> ConsensusRule.fromConfigKey(dependency.dependentKey()),
                Collectors.mapping(dependency -> ConsensusRule.fromConfigKey(dependency.requiredKey()),
                        Collectors.toSet())));
    }

    private static Stream<Dependency> activationDependencies() {
        return Stream.of(
                // RSKIP546 also requires RSKIP543, so it activates with RSKIP545
                new Dependency("rskip545", "rskip543", List.of("rskip546"), List.of()),
                new Dependency("rskip545", "rskip544", List.of(), List.of()),
                new Dependency("rskip545", "rskip546", List.of(), List.of()),
                new Dependency("rskip545", "rskip692", List.of(), List.of()),
                new Dependency("rskip545", "rskip701", List.of(), List.of()),
                // RSKIP545 requires RSKIP546, so it is disabled to leave RSKIP546 as the only dependent rule
                new Dependency("rskip546", "rskip543", List.of(), List.of("rskip545"))
        );
    }

    /**
     * A dependent rule, a rule it requires, and the other rules a case must move so that every dependency outside
     * the pair stays satisfied: those in {@code activateWithDependent} take the dependent rule's height, those in
     * {@code disabled} are switched off.
     */
    private record Dependency(String dependentKey, String requiredKey,
                              List<String> activateWithDependent, List<String> disabled) {

        Config config(long dependentHeight, long requiredHeight) {
            Config config = BASE_CONFIG
                    .withValue("consensusRules." + dependentKey, ConfigValueFactory.fromAnyRef(dependentHeight))
                    .withValue("consensusRules." + requiredKey, ConfigValueFactory.fromAnyRef(requiredHeight));
            for (String key : activateWithDependent) {
                config = config.withValue("consensusRules." + key, ConfigValueFactory.fromAnyRef(dependentHeight));
            }
            for (String key : disabled) {
                config = config.withValue("consensusRules." + key, ConfigValueFactory.fromAnyRef(-1));
            }
            return config;
        }

        @Override
        public String toString() {
            return dependentKey + " requires " + requiredKey;
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"main", "testnet", "testnet2", "regtest"})
    void requiredRulesActivateNoLaterThanTheirDependents_shippedNetworkConfigs(String network) {
        Config blockchainConfig = shippedBlockchainConfig(network);

        for (Map.Entry<ConsensusRule, Set<ConsensusRule>> dependency : expectedDependencies().entrySet()) {
            assertRequiredRulesActivateNoLaterThan(network, blockchainConfig, dependency.getKey(),
                    dependency.getValue());
        }
    }

    /**
     * The "no later than" check above holds trivially where cardamom1000 = -1 (main, testnet, testnet2), because
     * none of the rules it checks is active at any height. RSKIP701 and RSKIP545 must name the same upgrade, so they move together whatever
     * height that upgrade is given later.
     */
    @ParameterizedTest
    @ValueSource(strings = {"reference", "main", "testnet", "testnet2", "regtest"})
    void rskip701AndRskip545NameTheSameUpgrade_shippedConfigs(String network) {
        Config blockchainConfig = "reference".equals(network)
                ? referenceBlockchainConfig()
                : shippedBlockchainConfig(network);

        String rskip545 = blockchainConfig.getString("consensusRules.rskip545");
        String rskip701 = blockchainConfig.getString("consensusRules.rskip701");

        assertEquals(rskip545, rskip701, network + ": RSKIP701 and RSKIP545 must activate with the same upgrade");
        // a height instead of an upgrade name would pin the rule to that height and let the upgrade move alone
        assertDoesNotThrow(() -> NetworkUpgrade.named(rskip701),
                network + ": RSKIP701 must name an upgrade, not a height");
    }

    private static Config referenceBlockchainConfig() {
        return ConfigFactory.parseResourcesAnySyntax("reference.conf")
                .resolve(ConfigResolveOptions.defaults().setAllowUnresolved(true))
                .getConfig("blockchain.config");
    }

    private static Config shippedBlockchainConfig(String network) {
        // same layering as ConfigLoader: the network file overrides the defaults in reference.conf; paths that
        // depend on the environment are left unresolved, the activation heights do not use them
        return ConfigFactory.parseResourcesAnySyntax("config/" + network)
                .withFallback(ConfigFactory.parseResourcesAnySyntax("reference.conf"))
                .resolve(ConfigResolveOptions.defaults().setAllowUnresolved(true))
                .getConfig("blockchain.config");
    }

    /**
     * A dependent rule must not activate before the rules it requires, so each of them may activate at the same
     * height as the dependent rule or earlier. Activation is monotonic, so it is enough to check every height at
     * which any rule could switch on: wherever the dependent rule is active, the required rules must be active too.
     */
    private static void assertRequiredRulesActivateNoLaterThan(
            String network, Config blockchainConfig, ConsensusRule dependent, Set<ConsensusRule> requiredRules) {
        ActivationConfig activationConfig = ActivationConfig.read(blockchainConfig);

        Set<Long> candidateHeights = new TreeSet<>();
        for (String section : new String[]{"hardforkActivationHeights", "consensusRules"}) {
            for (Map.Entry<String, ConfigValue> entry : blockchainConfig.getConfig(section).entrySet()) {
                if (entry.getValue().unwrapped() instanceof Number) {
                    candidateHeights.add(((Number) entry.getValue().unwrapped()).longValue());
                }
            }
        }
        candidateHeights.removeIf(height -> height < 0);

        for (long height : candidateHeights) {
            if (activationConfig.isActive(dependent, height)) {
                for (ConsensusRule required : requiredRules) {
                    assertTrue(activationConfig.isActive(required, height),
                            String.format("%s: %s must not activate before %s, but %s is active and %s is not at height %d",
                                    network, dependent, required, dependent, required, height));
                }
            }
        }
    }

}
