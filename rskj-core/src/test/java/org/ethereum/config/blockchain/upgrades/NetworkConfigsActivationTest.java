/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Consistency checks over every shipped network configuration (config/*.conf), using the production parser
 * so numeric shadowing and negative (disabled) heights are interpreted exactly as the node does.
 */
class NetworkConfigsActivationTest {

    /** Walks up from the CWD until it finds rskj-core's config resources (same idiom as JsonRpcDocCoverageTest). */
    static Path configDir() {
        for (Path dir = Paths.get("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path viaModule = dir.resolve("rskj-core/src/main/resources/config");
            if (Files.isRegularFile(viaModule.resolve("main.conf"))) {
                return viaModule;
            }
            Path direct = dir.resolve("src/main/resources/config");
            if (Files.isRegularFile(direct.resolve("main.conf"))) {
                return direct;
            }
        }
        throw new IllegalStateException("Could not find rskj-core/src/main/resources/config above " + Paths.get("").toAbsolutePath());
    }

    static List<String> networkNames() throws IOException {
        try (Stream<Path> files = Files.list(configDir())) {
            List<String> names = files
                .map(p -> p.getFileName().toString())
                .filter(n -> n.endsWith(".conf"))
                .map(n -> n.substring(0, n.length() - ".conf".length()))
                .sorted()
                .collect(Collectors.toList());
            assertFalse(names.isEmpty(), "no network configs found under " + configDir());
            return names;
        }
    }

    /** ConfigFactory.load(basename) = system overrides + config/<name>.conf + reference.conf, resolved. */
    static Config loadNetwork(String name) {
        return ConfigFactory.load("config/" + name);
    }

    /** Upgrade name -> configured height, in the order the config file declares them. */
    static Map<String, Long> activationHeights(Config networkConfig) {
        Config heights = networkConfig.getConfig("blockchain.config.hardforkActivationHeights");
        Map<String, Long> out = new LinkedHashMap<>();
        for (Map.Entry<String, ConfigValue> e : heights.entrySet()) {
            out.put(e.getKey(), heights.getLong(e.getKey()));
        }
        return out;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("networkNames")
    void activationConfigReadsWithoutError(String network) {
        Config cfg = loadNetwork(network);
        assertDoesNotThrow(() -> ActivationConfig.read(cfg.getConfig("blockchain.config")),
            "ActivationConfig.read failed for network " + network);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("networkNames")
    void everyNetworkUpgradeExceptGenesisIsConfigured(String network) {
        Map<String, Long> heights = activationHeights(loadNetwork(network));
        List<String> missing = new ArrayList<>();
        for (NetworkUpgrade upgrade : NetworkUpgrade.values()) {
            if (upgrade == NetworkUpgrade.GENESIS) {
                continue; // listed in expected.conf, configured in no network file
            }
            if (!heights.containsKey(upgrade.getName())) {
                missing.add(upgrade.getName());
            }
        }
        assertTrue(missing.isEmpty(), network + " is missing hardforkActivationHeights for " + missing);
    }

    /** Known historical inversions, as "<earlierEnum>><laterEnum>". Extend only with a reason in the commit message. */
    private static final Set<String> KNOWN_ENUM_ORDER_INVERSIONS = Set.of(
        "papyrus200>twoToThree",     // twoToThree shipped before papyrus200 on main/testnet/testnet2
        "afterBridgeSync>orchid");   // orchid is active from genesis on testnet/testnet2; afterBridgeSync is not

    @ParameterizedTest(name = "{0}")
    @MethodSource("networkNames")
    void enabledHeightsAreMonotonicInEnumOrder(String network) {
        Map<String, Long> heights = activationHeights(loadNetwork(network));
        List<String> inversions = new ArrayList<>();
        String prevName = null;
        long prevHeight = -1;
        for (NetworkUpgrade upgrade : NetworkUpgrade.values()) {
            Long h = heights.get(upgrade.getName());
            if (h == null || h < 0) {
                continue; // unconfigured (genesis) or disabled
            }
            if (prevName != null && h < prevHeight
                && !KNOWN_ENUM_ORDER_INVERSIONS.contains(prevName + ">" + upgrade.getName())) {
                inversions.add(network + ": " + prevName + "=" + prevHeight + " > " + upgrade.getName() + "=" + h);
            }
            prevName = upgrade.getName();
            prevHeight = h;
        }
        assertTrue(inversions.isEmpty(),
            "new enum-order inversion (add to KNOWN_ENUM_ORDER_INVERSIONS only with a documented reason): " + inversions);
    }
}
