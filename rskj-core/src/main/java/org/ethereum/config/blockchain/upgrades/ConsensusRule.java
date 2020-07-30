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

public enum ConsensusRule {
    ARE_BRIDGE_TXS_PAID("areBridgeTxsPaid"),
    RSKIP85("rskip85"),
    RSKIP87("rskip87"),
    RSKIP88("rskip88"),
    RSKIP89("rskip89"),
    RSKIP90("rskip90"),
    RSKIP91("rskip91"),
    RSKIP92("rskip92"),
    RSKIP97("rskip97"),
    RSKIP98("rskip98"),
    RSKIP103("rskip103"),
    RSKIP106("rskip106"),
    RSKIP110("rskip110"),
    RSKIP119("rskip119"),
    RSKIP120("rskip120"),
    RSKIP122("rskip122"),
    RSKIP123("rskip123"),
    RSKIP124("rskip124"),
    RSKIP125("rskip125"),
    RSKIP126("rskip126"),
    RSKIP132("rskip132"),
    RSKIP134("rskip134"),
    RSKIP136("rskip136"),
    RSKIP137("rskip137"),
    RSKIP140("rskip140"),
    RSKIP143("rskip143"),
    RSKIP146("rskip146"),
    RSKIP150("rskip150"),
    RSKIP151("rskip151"),
    RSKIP152("rskip152"),
    RSKIP156("rskip156"),
    RSKIPUMM("rskipUMM");

    private String configKey;

    ConsensusRule(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigKey() {
        return configKey;
    }

    public static ConsensusRule fromConfigKey(String configKey) {
        for (ConsensusRule consensusRule : ConsensusRule.values()) {
            if (consensusRule.configKey.equals(configKey)) {
                return consensusRule;
            }
        }

        throw new IllegalArgumentException(String.format("Unknown consensus rule %s", configKey));
    }
}
