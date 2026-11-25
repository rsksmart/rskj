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
package co.rsk.core.bc.transactionexecutor;

import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.config.blockchain.upgrades.NetworkUpgrade;

import java.util.EnumMap;
import java.util.Map;

/**
 * Builds activation configs that start from a base config and switch one consensus rule on or off.
 */
final class GenesisActivations {

    private GenesisActivations() {
    }

    /**
     * Returns a config where every rule active at genesis in {@code defaults} activates at block 0 and every other
     * rule is never active, except {@code rule}, which activates at block 0 when {@code active} and never otherwise.
     */
    static ActivationConfig withRule(ActivationConfig defaults, ConsensusRule rule, boolean active) {
        Map<ConsensusRule, Long> heights = new EnumMap<>(ConsensusRule.class);
        for (ConsensusRule each : ConsensusRule.values()) {
            heights.put(each, defaults.isActive(each, 0L) ? 0L : -1L);
        }
        heights.put(rule, active ? 0L : -1L);
        return new ActivationConfig(heights, new EnumMap<>(NetworkUpgrade.class));
    }
}
