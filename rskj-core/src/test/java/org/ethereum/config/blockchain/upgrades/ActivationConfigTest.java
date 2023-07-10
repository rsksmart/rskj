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

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.is;

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
            "    tbd600: 0",
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
            "    rskip326: fingerroot500",
            "    rskip353: hop401",
            "    rskip357: hop401",
            "    rskip374: fingerroot500",
            "    rskip375: fingerroot500",
            "    rskip376: tbd600",
            "    rskip377: fingerroot500",
            "    rskip383: fingerroot500",
            "    rskip385: fingerroot500",
            "    rskipyyy: tbd600",
            "}"
    ));

    @Test
    void readBaseConfig() {
        ActivationConfig config = ActivationConfig.read(BASE_CONFIG);

        for (ConsensusRule value : ConsensusRule.values()) {
            MatcherAssert.assertThat(config.isActive(value, 42), is(true));
        }
    }

    @Test
    void readWithTwoUpgradesInOrchid060() {
        ActivationConfig config = ActivationConfig.read(BASE_CONFIG
                .withValue("hardforkActivationHeights.orchid060", ConfigValueFactory.fromAnyRef(200))
                .withValue("consensusRules.rskip98", ConfigValueFactory.fromAnyRef("orchid060"))
        );

        for (ConsensusRule value : ConsensusRule.values()) {
            if (value == ConsensusRule.RSKIP98 || value == ConsensusRule.RSKIP103) {
                MatcherAssert.assertThat(config.isActive(value, 100), is(false));
            } else {
                MatcherAssert.assertThat(config.isActive(value, 100), is(true));
            }
        }
    }

    @Test
    void readWithOneHardcodedActivationNumber() {
        ActivationConfig config = ActivationConfig.read(BASE_CONFIG
                .withValue("consensusRules.rskip85", ConfigValueFactory.fromAnyRef(200))
        );

        for (ConsensusRule value : ConsensusRule.values()) {
            if (value == ConsensusRule.RSKIP85) {
                MatcherAssert.assertThat(config.isActive(value, 100), is(false));
            } else {
                MatcherAssert.assertThat(config.isActive(value, 100), is(true));
            }
        }
    }

    @Test
    void failsReadingWithMissingNetworkUpgrade() {
        Config config = BASE_CONFIG.withoutPath("consensusRules.rskip85");
        Assertions.assertThrows(IllegalArgumentException.class, () -> ActivationConfig.read(config));
    }

    @Test
    void failsReadingWithMissingHardFork() {
        Config config = BASE_CONFIG.withoutPath("hardforkActivationHeights.orchid");
        Assertions.assertThrows(IllegalArgumentException.class, () -> ActivationConfig.read(config));
    }

    @Test
    void failsReadingWithUnknownForkConfiguration() {
        Config config = BASE_CONFIG.withValue("hardforkActivationHeights.orkid", ConfigValueFactory.fromAnyRef(200));
        Assertions.assertThrows(IllegalArgumentException.class, () -> ActivationConfig.read(config));
    }

    @Test
    void failsReadingWithUnknownUpgradeConfiguration() {
        Config config = BASE_CONFIG.withValue("consensusRules.rskip420", ConfigValueFactory.fromAnyRef("orchid"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> ActivationConfig.read(config));
    }
}
