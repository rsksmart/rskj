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

package org.ethereum.config;

import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;

import java.util.EnumSet;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class UnitTestBlockchainNetConfig extends RegtestBlockchainNetConfig {
    public UnitTestBlockchainNetConfig() {
        super(getActivationConfig());
    }

    private static ActivationConfig getActivationConfig() {
        Map<ConsensusRule, Long> allDisabled = EnumSet.allOf(ConsensusRule.class).stream()
                .collect(Collectors.toMap(Function.identity(), ignored -> -1L));
        allDisabled.put(ConsensusRule.ARE_BRIDGE_TXS_PAID, 10L);
        return new ActivationConfig(allDisabled);
    }
}
