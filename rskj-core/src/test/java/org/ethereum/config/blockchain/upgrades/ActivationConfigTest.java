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
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class ActivationConfigTest {
    private static final Config BASE_CONFIG = ConfigFactory.parseString(String.join("\n",
            "hardforkActivationHeights: {",
            "    genesis: 0",
            "    bahamas: 0",
            "    afterBridgeSync: 0,",
            "    orchid: 0,",
            "    orchid060: 0,",
            "    wasabi100: 0",
            "    wasabiPlusOne: 0",
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
            "    rskip110: wasabi100,",
            "    rskip119: wasabi100,",
            "    rskip106: wasabi100,",
            "    rskip120: wasabi100,",
            "    rskip122: wasabi100,",
            "    rskip123: wasabi100,",
            "    rskip124: wasabi100,",
            "    rskip125: wasabi100",
            "    rskip126: wasabi100",
            "    rskip132: wasabi100",
            "    rskip136: bahamas",
            "    rskip140: wasabiPlusOne",
            "    rskip144: wasabiPlusOne",
            "    rskip152: wasabiPlusOne",
            "}"
    ));

    @Test
    public void readBaseConfig() {
        ActivationConfig config = ActivationConfig.read(BASE_CONFIG);

        for (ConsensusRule value : ConsensusRule.values()) {
            assertThat(config.isActive(value, 42), is(true));
        }
    }

    @Test
    public void readWithTwoUpgradesInOrchid060() {
        ActivationConfig config = ActivationConfig.read(BASE_CONFIG
                .withValue("hardforkActivationHeights.orchid060", ConfigValueFactory.fromAnyRef(200))
                .withValue("consensusRules.rskip98", ConfigValueFactory.fromAnyRef("orchid060"))
        );

        for (ConsensusRule value : ConsensusRule.values()) {
            if (value == ConsensusRule.RSKIP98 || value == ConsensusRule.RSKIP103) {
                assertThat(config.isActive(value, 100), is(false));
                assertThat(config.isActive(value, 300), is(true));
            } else {
                assertThat(config.isActive(value, 100), is(true));
                assertThat(config.isActive(value, 300), is(true));
            }
        }
    }

    @Test
    public void readWithOneHardcodedActivationNumber() {
        ActivationConfig config = ActivationConfig.read(BASE_CONFIG
                .withValue("consensusRules.rskip85", ConfigValueFactory.fromAnyRef(200))
        );

        for (ConsensusRule value : ConsensusRule.values()) {
            if (value == ConsensusRule.RSKIP85) {
                assertThat(config.isActive(value, 100), is(false));
                assertThat(config.isActive(value, 300), is(true));
            } else {
                assertThat(config.isActive(value, 100), is(true));
                assertThat(config.isActive(value, 300), is(true));
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void failsReadingWithMissingNetworkUpgrade() {
        ActivationConfig.read(BASE_CONFIG
                .withoutPath("consensusRules.rskip85")
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void failsReadingWithMissingHardFork() {
        ActivationConfig.read(BASE_CONFIG
                .withoutPath("hardforkActivationHeights.orchid")
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void failsReadingWithUnknownForkConfiguration() {
        ActivationConfig.read(BASE_CONFIG
                .withValue("hardforkActivationHeights.orkid", ConfigValueFactory.fromAnyRef(200))
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void failsReadingWithUnknownUpgradeConfiguration() {
        ActivationConfig.read(BASE_CONFIG
                .withValue("consensusRules.rskip420", ConfigValueFactory.fromAnyRef("orchid"))
        );
    }
}