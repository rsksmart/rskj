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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.ethereum.TestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Consistency checks over every shipped network configuration (config/*.conf), using the production parser
 * so numeric shadowing and negative (disabled) heights are interpreted exactly as the node does.
 */
class NetworkConfigsActivationTest {

    static Path configDir() {
        return TestUtils.moduleRoot().resolve("src/main/resources/config");
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

    /**
     * Upgrade name -> configured height, in NetworkUpgrade order. The golden then reads in fork order, and a
     * rescheduled upgrade changes in place: {@code entrySet()} order is neither the file order nor stable, since it
     * shifts when a value changes.
     */
    static Map<String, Long> activationHeights(Config networkConfig) {
        Config heights = networkConfig.getConfig("blockchain.config.hardforkActivationHeights");
        Map<String, Long> out = new LinkedHashMap<>();
        heights.root().keySet().stream()
            .sorted(Comparator.comparing(NetworkUpgrade::named))   // an unknown name throws, as in ActivationConfig.read
            .forEach(name -> out.put(name, heights.getLong(name)));
        return out;
    }

    /**
     * Consensus rule config key -> effective activation height, as ActivationConfig.read resolves it (numeric
     * entries and upgrade-name entries alike). -1 means never active. ActivationConfig exposes no per-rule getter,
     * so the height is recovered with a boundary search over isActive. Enum order keeps the golden byte-stable.
     */
    static Map<String, Long> consensusRuleHeights(Config networkConfig) {
        ActivationConfig activations = ActivationConfig.read(networkConfig.getConfig("blockchain.config"));
        Map<String, Long> out = new LinkedHashMap<>();
        for (ConsensusRule rule : ConsensusRule.values()) {
            long height = -1;
            if (activations.isActive(rule, Long.MAX_VALUE)) {
                long lo = 0;
                long hi = Long.MAX_VALUE;
                while (lo < hi) {   // smallest block at which the rule is active
                    long mid = lo + (hi - lo) / 2;
                    if (activations.isActive(rule, mid)) {
                        hi = mid;
                    } else {
                        lo = mid + 1;
                    }
                }
                height = lo;
            }
            out.put(rule.getConfigKey(), height);
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

    // twoToThree shipped before papyrus200 on main and testnet
    private static final String PAPYRUS200_TWO_TO_THREE = "papyrus200>twoToThree";

    /**
     * Known historical inversions per network, as "<earlierEnum>><laterEnum>". Per network so an inversion that is
     * fine on one network is still caught on another. Extend only with a reason in the commit message.
     */
    private static final Map<String, Set<String>> KNOWN_ENUM_ORDER_INVERSIONS = Map.of(
        "main", Set.of(PAPYRUS200_TWO_TO_THREE),
        // orchid, orchid060 and wasabi100 are active from genesis on testnet; afterBridgeSync is not
        "testnet", Set.of(PAPYRUS200_TWO_TO_THREE,
            "afterBridgeSync>orchid", "afterBridgeSync>orchid060", "afterBridgeSync>wasabi100"));

    @ParameterizedTest(name = "{0}")
    @MethodSource("networkNames")
    void enabledHeightsAreMonotonicInEnumOrder(String network) {
        Map<String, Long> heights = activationHeights(loadNetwork(network));
        Set<String> known = KNOWN_ENUM_ORDER_INVERSIONS.getOrDefault(network, Set.of());
        List<String> inversions = new ArrayList<>();
        // Compare against the highest height so far, not just the previous upgrade: after an allowlisted inversion
        // the previous height is the lower one, and an upgrade landing between the two would otherwise slip through.
        String maxName = null;
        long maxHeight = -1;
        for (NetworkUpgrade upgrade : NetworkUpgrade.values()) {
            Long h = heights.get(upgrade.getName());
            if (h == null || h < 0) {
                continue; // unconfigured (genesis) or disabled
            }
            if (maxName != null && h < maxHeight
                && !known.contains(maxName + ">" + upgrade.getName())) {
                inversions.add(network + ": " + maxName + "=" + maxHeight + " > " + upgrade.getName() + "=" + h);
            }
            if (h >= maxHeight) {
                maxName = upgrade.getName();
                maxHeight = h;
            }
        }
        assertTrue(inversions.isEmpty(),
            "new enum-order inversion (add to KNOWN_ENUM_ORDER_INVERSIONS only with a documented reason): " + inversions);
    }

    private static final Path GOLDEN = TestUtils.moduleRoot()
        .resolve("src/test/resources/config/activation-heights-golden.json");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Tip height per network at the time the golden was last blessed. Heights at or below asOf have been mined
     * and must never change. asOf < 0 means the network's history is not pinned: regtest heights are dev knobs,
     * and testnet2 is a new network (its own networkId and genesis) whose history is not pinned yet.
     * Bump main/testnet at release time, then run ./gradlew :rskj-core:updateActivationGolden.
     */
    private static final Map<String, Long> AS_OF = Map.of(
        "main", 9_318_000L,
        "testnet", 8_175_000L,
        "testnet2", -1L,
        "regtest", -1L);

    @Test
    void passedActivationHeightsMatchGolden() throws IOException {
        assertTrue(Files.exists(GOLDEN), "golden missing; run ./gradlew :rskj-core:updateActivationGolden to create " + GOLDEN);
        JsonNode golden = MAPPER.readTree(GOLDEN.toFile());
        List<String> violations = new ArrayList<>();
        for (String network : networkNames()) {
            JsonNode asOfNode = golden.path("asOf").path(network);
            assertFalse(asOfNode.isMissingNode(), "golden asOf missing for " + network);
            long asOf = asOfNode.asLong();
            assertEquals(AS_OF.getOrDefault(network, -1L).longValue(), asOf,
                network + ": golden asOf is stale; run ./gradlew :rskj-core:updateActivationGolden");
            Config cfg = loadNetwork(network);
            compareSection(goldenSection(golden, "heights", network), network + ".heights.", asOf,
                activationHeights(cfg), violations, violations);
            // Per-rule heights only matter where history is pinned; a pinned network without them fails in goldenSection.
            if (asOf >= 0) {
                compareSection(goldenSection(golden, "consensusRules", network), network + ".consensusRules.", asOf,
                    consensusRuleHeights(cfg), violations, violations);
            }
        }
        assertTrue(violations.isEmpty(), "Passed activation heights changed: " + violations);
    }

    private static JsonNode goldenSection(JsonNode golden, String section, String network) {
        JsonNode goldenHeights = golden.path(section).path(network);
        assertFalse(goldenHeights.isMissingNode(),
            "golden " + section + " missing for " + network + "; run ./gradlew :rskj-core:updateActivationGolden");
        return goldenHeights;
    }

    /**
     * Compares one golden section with the current heights. A difference that touches mined history (a height at or
     * below asOf, before or after the change) goes to {@code mined}; any other difference that a regeneration fixes
     * goes to {@code stale}. The test fails on both; regenerateGolden refuses only {@code mined}.
     */
    private static void compareSection(JsonNode goldenHeights, String label, long asOf, Map<String, Long> current,
                                       List<String> mined, List<String> stale) {
        for (Iterator<Map.Entry<String, JsonNode>> it = goldenHeights.fields(); it.hasNext();) {
            Map.Entry<String, JsonNode> e = it.next();
            long was = e.getValue().asLong();
            Long now = current.get(e.getKey());
            if (now == null) {
                (isMined(was, asOf) ? mined : stale).add(label + e.getKey() + " removed (was " + was + ")");
                continue;
            }
            // A change is a violation if the height had already passed, or is being moved retroactively
            // onto already-mined history. Scheduled/disabled changes above asOf are legitimate and show up
            // in the config diff itself; regenerate the golden after making one.
            if (was != now && (isMined(was, asOf) || isMined(now, asOf))) {
                mined.add(label + e.getKey() + " changed " + was + " -> " + now + " (asOf=" + asOf + ")");
            }
        }
        // A key the config has but the golden lacks is unpinned: it would never be compared again. A new key that is
        // already active at or below asOf is a retroactive activation, not a missing golden entry.
        for (Map.Entry<String, Long> e : current.entrySet()) {
            if (goldenHeights.has(e.getKey())) {
                continue;
            }
            if (isMined(e.getValue(), asOf)) {
                mined.add(label + e.getKey() + " is new and active at " + e.getValue()
                    + ", on already-mined history (asOf=" + asOf + ")");
            } else {
                stale.add(label + e.getKey() + " not in golden (now " + e.getValue()
                    + "); run ./gradlew :rskj-core:updateActivationGolden");
            }
        }
    }

    /** A height at or below asOf is on mined history. Nothing is when asOf < 0: that network is not pinned. */
    private static boolean isMined(long height, long asOf) {
        return height >= 0 && height <= asOf;
    }

    /**
     * Not a check: rewrites the golden. Runs only from ./gradlew :rskj-core:updateActivationGolden. It refuses to
     * write over a change to mined history, so rerunning the task cannot bless what passedActivationHeightsMatchGolden
     * reports. A deliberate change of that kind needs a hand edit of the golden and a reason in the PR.
     */
    @Test
    @EnabledIfSystemProperty(named = "activation.golden.update", matches = "true")
    void regenerateGolden() throws IOException {
        if (Files.exists(GOLDEN)) {
            List<String> mined = minedHistoryChanges(MAPPER.readTree(GOLDEN.toFile()));
            if (!mined.isEmpty()) {
                // Printed as well: the task's short exception format drops the assertion message.
                System.out.println("Golden not written: these changes touch mined history. Fix the config; if a change"
                    + " is deliberate, edit the golden by hand and give the reason in the PR.");
                mined.forEach(change -> System.out.println("  " + change));
            }
            assertTrue(mined.isEmpty(), "Golden not written: these changes touch mined history: " + mined);
        }
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode asOf = root.putObject("asOf");
        ObjectNode heights = root.putObject("heights");
        ObjectNode rules = root.putObject("consensusRules");
        for (String network : networkNames()) {
            long networkAsOf = AS_OF.getOrDefault(network, -1L);
            asOf.put(network, networkAsOf);
            Config cfg = loadNetwork(network);
            ObjectNode h = heights.putObject(network);
            activationHeights(cfg).forEach(h::put);
            if (networkAsOf >= 0) {
                ObjectNode r = rules.putObject(network);
                consensusRuleHeights(cfg).forEach(r::put);
            }
        }
        Files.createDirectories(GOLDEN.getParent());
        // Explicit LF indenter + trailing newline: the golden is a committed text file, and the platform default
        // line separator would make it churn between contributors.
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter()
            .withObjectIndenter(new DefaultIndenter("  ", "\n"))
            .withArrayIndenter(new DefaultIndenter("  ", "\n"));
        Files.writeString(GOLDEN, MAPPER.writer(pp).writeValueAsString(root) + "\n");
        System.out.println("Golden written to " + GOLDEN);   // visible: updateActivationGolden shows standard streams
    }

    /**
     * Changes to mined history on each network that the golden pins. The bound is the higher of the golden's asOf
     * and AS_OF, so a height moved above the old asOf but at or below a bumped AS_OF counts too. The bound is AS_OF,
     * not the real tip: blocks mined above AS_OF are not protected until the next bump.
     */
    private static List<String> minedHistoryChanges(JsonNode golden) throws IOException {
        List<String> mined = new ArrayList<>();
        List<String> stale = new ArrayList<>();   // the regeneration itself fixes these
        for (String network : networkNames()) {
            long goldenAsOf = golden.path("asOf").path(network).asLong(-1);
            if (goldenAsOf < 0) {
                continue;   // the golden does not pin this network yet
            }
            long asOf = Math.max(goldenAsOf, AS_OF.getOrDefault(network, -1L));
            Config cfg = loadNetwork(network);
            compareSection(golden.path("heights").path(network), network + ".heights.", asOf,
                activationHeights(cfg), mined, stale);
            compareSection(golden.path("consensusRules").path(network), network + ".consensusRules.", asOf,
                consensusRuleHeights(cfg), mined, stale);
        }
        return mined;
    }
}
