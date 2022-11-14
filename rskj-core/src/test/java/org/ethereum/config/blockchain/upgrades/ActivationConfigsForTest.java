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

import com.google.common.collect.ImmutableSet;
import com.typesafe.config.ConfigFactory;
import org.ethereum.config.SystemProperties;

import java.util.EnumSet;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@SuppressWarnings({"squid:S2187"}) // used from another class
public class ActivationConfigsForTest {

    private static final ActivationConfig REGTEST = read("config/regtest");

    public static ActivationConfig genesis() {
        return only();
    }

    public static ActivationConfig orchid() {
        return only(
                ConsensusRule.RSKIP85,
                ConsensusRule.RSKIP87,
                ConsensusRule.RSKIP88,
                ConsensusRule.RSKIP89,
                ConsensusRule.RSKIP90,
                ConsensusRule.RSKIP91,
                ConsensusRule.RSKIP92,
                ConsensusRule.RSKIP97,
                ConsensusRule.RSKIP98
        );
    }

    public static ActivationConfig regtest() {
        return REGTEST;
    }

    public static ActivationConfig all() {
        return allBut();
    }

    public static ActivationConfig allBut(ConsensusRule... upgradesToDisable) {
        Map<ConsensusRule, Long> consensusRules = EnumSet.allOf(ConsensusRule.class).stream()
                .collect(Collectors.toMap(Function.identity(), ignored -> 0L));
        for (ConsensusRule consensusRule : upgradesToDisable) {
            consensusRules.put(consensusRule, -1L);
        }

        return new ActivationConfig(consensusRules, ImmutableSet.of(new ActivationConfig.MessageVersionForHeight(0, 1)));
    }

    public static ActivationConfig only(ConsensusRule... upgradesToEnable) {
        Map<ConsensusRule, Long> consensusRules = EnumSet.allOf(ConsensusRule.class).stream()
                .collect(Collectors.toMap(Function.identity(), ignored -> -1L));
        for (ConsensusRule consensusRule : upgradesToEnable) {
            consensusRules.put(consensusRule, 0L);
        }

        return new ActivationConfig(consensusRules, ImmutableSet.of(new ActivationConfig.MessageVersionForHeight(0, 1)));
    }

    public static ActivationConfig bridgeUnitTest() {
        Map<ConsensusRule, Long> allDisabled = EnumSet.allOf(ConsensusRule.class).stream()
                .collect(Collectors.toMap(Function.identity(), ignored -> -1L));
        allDisabled.put(ConsensusRule.ARE_BRIDGE_TXS_PAID, 10L);
        return new ActivationConfig(allDisabled, ImmutableSet.of(new ActivationConfig.MessageVersionForHeight(0, 1)));
    }

    private static ActivationConfig read(String resourceBasename) {
        return ActivationConfig.read(
                ConfigFactory.load(resourceBasename)
                        .getConfig(SystemProperties.PROPERTY_BLOCKCHAIN_CONFIG)
        );
    }
}
