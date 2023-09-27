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
    RSKIP153("rskip153"), // BLAKE2 Compression Function Precompiled
    RSKIP156("rskip156"),
    RSKIP169("rskip169"),
    RSKIP170("rskip170"),
    RSKIP171("rskip171"),
    RSKIP174("rskip174"),
    RSKIP176("rskip176"),
    RSKIP179("rskip179"), // BTC-RSK timestamp linking
    RSKIP180("rskip180"), // Limit RSK merged mining merkle proof
    RSKIP181("rskip181"), // Peg-in rejection events
    RSKIP186("rskip186"), // Active Federation creation block height registration
    RSKIPUMM("rskipUMM"),
    RSKIP185("rskip185"), // Peg-out refund and events
    RSKIP191("rskip191"),
    RSKIP197("rskip197"), // Handle error in Precompile Contracts execution.
    RSKIP199("rskip199"),
    RSKIP200("rskip200"),
    RSKIP201("rskip201"),
    RSKIP218("rskip218"), // New rewards fee adddress
    RSKIP219("rskip219"),
    RSKIP220("rskip220"),
    RSKIP252("rskip252"), // Transaction Gas Price Cap
    RSKIP271("rskip271"), // Peg Out Batching
    RSKIP284("rskip284"),
    RSKIP290("rskip290"), // Testnet difficulty should drop to a higher difficulty
    RSKIP293("rskip293"), // Flyover improvements
    RSKIP294("rskip294"),
    RSKIP297("rskip297"), // Increase max timestamp difference between btc and rsk blocks for Testnet
    RSKIP326("rskip326"), // release_request_received event update to use base58 for btcDestinationAddress
    RSKIP353("rskip353"),
    RSKIP357("rskip357"),
    RSKIP374("rskip374"),
    RSKIP375("rskip375"),
    RSKIP376("rskip376"),
    RSKIP377("rskip377"),
    RSKIP383("rskip383"),
    RSKIP385("rskip385"),
    RSKIP398("rskip398"),
    RSKIP400("rskip400"), // From EIP-2028 calldata gas cost reduction
    ;

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
