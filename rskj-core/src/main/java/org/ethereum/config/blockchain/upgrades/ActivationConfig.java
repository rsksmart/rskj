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
import com.typesafe.config.ConfigException.WrongType;
import com.typesafe.config.ConfigValue;

import java.util.*;
import java.util.stream.Collectors;

public class ActivationConfig {
    private static final String PROPERTY_ACTIVATION_HEIGHTS = "hardforkActivationHeights";
    private static final String PROPERTY_CONSENSUS_RULES = "consensusRules";

    private final Map<ConsensusRule, Long> activationHeights;

    public ActivationConfig(Map<ConsensusRule, Long> activationHeights) {
        if (activationHeights.size() != ConsensusRule.values().length) {
            List<ConsensusRule> missing = new ArrayList<>(Arrays.asList(ConsensusRule.values()));
            missing.removeAll(activationHeights.keySet());
            throw new IllegalArgumentException(String.format(
                "The configuration must contain all consensus rule values but is missing [%s]",
                missing.stream().map(ConsensusRule::getConfigKey).collect(Collectors.joining(", "))
            ));
        }

        this.activationHeights = activationHeights;
    }

    public byte getHeaderVersion(long blockNumber) {
        if (this.isActive(ConsensusRule.RSKIP351, blockNumber)) {
            return 0x1;
        }

        return 0x0;
    }

    public boolean isActive(ConsensusRule consensusRule, long blockNumber) {
        long activationHeight = activationHeights.get(consensusRule);
        return 0 <= activationHeight && activationHeight <= blockNumber;
    }

    private boolean isActivating(ConsensusRule consensusRule, long blockNumber) {
        long activationHeight = activationHeights.get(consensusRule);
        return activationHeight == blockNumber;
    }

    public ForBlock forBlock(long blockNumber) {
        return new ForBlock(blockNumber);
    }

    public static ActivationConfig read(Config config) {
        Map<NetworkUpgrade, Long> networkUpgrades = new EnumMap<>(NetworkUpgrade.class);
        Config networkUpgradesConfig = config.getConfig(PROPERTY_ACTIVATION_HEIGHTS);
        for (Map.Entry<String, ConfigValue> e : networkUpgradesConfig.entrySet()) {
            NetworkUpgrade networkUpgrade = NetworkUpgrade.named(e.getKey());
            long activationHeight = networkUpgradesConfig.getLong(networkUpgrade.getName());
            networkUpgrades.put(networkUpgrade, activationHeight);
        }

        Map<ConsensusRule, Long> activationHeights = new EnumMap<>(ConsensusRule.class);
        Config consensusRulesConfig = config.getConfig(PROPERTY_CONSENSUS_RULES);
        for (Map.Entry<String, ConfigValue> e : consensusRulesConfig.entrySet()) {
            ConsensusRule consensusRule = ConsensusRule.fromConfigKey(e.getKey());
            long activationHeight = parseActivationHeight(networkUpgrades, consensusRulesConfig, consensusRule);
            activationHeights.put(consensusRule, activationHeight);
        }

        return new ActivationConfig(activationHeights);
    }

    private static long parseActivationHeight(
            Map<NetworkUpgrade, Long> networkUpgrades,
            Config consensusRulesConfig,
            ConsensusRule consensusRule) {
        try {
            return consensusRulesConfig.getLong(consensusRule.getConfigKey());
        } catch (WrongType ex) {
            NetworkUpgrade networkUpgrade = NetworkUpgrade.named(consensusRulesConfig.getString(consensusRule.getConfigKey()));
            if (!networkUpgrades.containsKey(networkUpgrade)) {
                throw new IllegalArgumentException(
                        String.format("Unknown activation height for network upgrade %s", networkUpgrade.getName())
                );
            }

            return networkUpgrades.get(networkUpgrade);
        }
    }

    public class ForBlock {
        private final long blockNumber;

        private ForBlock(long blockNumber) {
            this.blockNumber = blockNumber;
        }

        public boolean isActive(ConsensusRule consensusRule) {
            return ActivationConfig.this.isActive(consensusRule, blockNumber);
        }

        public boolean isActivating(ConsensusRule consensusRule) {
            return ActivationConfig.this.isActivating(consensusRule, blockNumber);
        }
    }
}
