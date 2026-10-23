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

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import org.ethereum.config.SystemProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the shipped activation mapping of the account abstraction rules: RSKIP-543, 545, 546 and 692
 * activate with cardamom1000, and RSKIP-544 with vetiver900. It reads the real network files
 * (config/main, config/testnet, config/testnet2, config/regtest on top of reference.conf), not a test activation preset.
 */
class AccountAbstractionActivationMappingTest {

    private static final List<ConsensusRule> CARDAMOM_RULES = List.of(
            ConsensusRule.RSKIP543, ConsensusRule.RSKIP545, ConsensusRule.RSKIP546, ConsensusRule.RSKIP692);

    // Far above any real height, to tell "never active" apart from "active later"
    private static final long FAR_FUTURE = 1_000_000_000_000L;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"config/main", "config/testnet", "config/testnet2", "config/regtest"})
    void accountAbstractionRules_activateExactlyWithTheirHardforkOnEachNetwork(String network) {
        Config blockchainConfig = ConfigFactory.load(network).getConfig(SystemProperties.PROPERTY_BLOCKCHAIN_CONFIG);
        ActivationConfig activationConfig = ActivationConfig.read(blockchainConfig);

        long cardamom = blockchainConfig.getLong("hardforkActivationHeights.cardamom1000");
        long vetiver = blockchainConfig.getLong("hardforkActivationHeights.vetiver900");

        for (ConsensusRule rule : CARDAMOM_RULES) {
            assertActivatesExactlyAt(activationConfig, rule, cardamom, network);
        }
        assertActivatesExactlyAt(activationConfig, ConsensusRule.RSKIP544, vetiver, network);
    }

    @Test
    void accountAbstractionRules_followTheirHardforkWhenHeightsAreDistinct() {
        // Distinct heights make a rule moved to another hardfork visible even where both are -1
        Config blockchainConfig = ConfigFactory.load("config/regtest")
                .withValue("blockchain.config.hardforkActivationHeights.vetiver900", ConfigValueFactory.fromAnyRef(100))
                .withValue("blockchain.config.hardforkActivationHeights.cardamom1000", ConfigValueFactory.fromAnyRef(200))
                .getConfig(SystemProperties.PROPERTY_BLOCKCHAIN_CONFIG);
        ActivationConfig activationConfig = ActivationConfig.read(blockchainConfig);

        for (ConsensusRule rule : CARDAMOM_RULES) {
            assertActivatesExactlyAt(activationConfig, rule, 200, "distinct heights");
        }
        assertActivatesExactlyAt(activationConfig, ConsensusRule.RSKIP544, 100, "distinct heights");
    }

    /**
     * Deliberate tripwire: this fails on the commit that sets the cardamom1000 height for mainnet or testnet.
     * That commit must update this test, so turning the rules on is always a visible decision.
     */
    @Test
    void accountAbstractionRules_areOffOnMainnetAndTestnet() {
        for (String network : List.of("config/main", "config/testnet")) {
            ActivationConfig activationConfig = ActivationConfig.read(
                    ConfigFactory.load(network).getConfig(SystemProperties.PROPERTY_BLOCKCHAIN_CONFIG));
            for (ConsensusRule rule : CARDAMOM_RULES) {
                assertFalse(activationConfig.forBlock(FAR_FUTURE).isActive(rule), network + ": " + rule + " must be off");
            }
        }
    }

    private static void assertActivatesExactlyAt(ActivationConfig activationConfig, ConsensusRule rule, long height, String context) {
        if (height < 0) {
            assertFalse(activationConfig.forBlock(FAR_FUTURE).isActive(rule), context + ": " + rule + " must never activate");
            return;
        }
        assertTrue(activationConfig.forBlock(height).isActive(rule), context + ": " + rule + " must be active at " + height);
        if (height > 0) {
            assertFalse(activationConfig.forBlock(height - 1).isActive(rule), context + ": " + rule + " must be inactive at " + (height - 1));
        }
    }
}
