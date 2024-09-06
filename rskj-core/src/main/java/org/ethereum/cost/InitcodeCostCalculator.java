/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package org.ethereum.cost;

import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;

import static org.ethereum.vm.GasCost.INITCODE_WORD_COST;

public class InitcodeCostCalculator implements CostCalculator {
    private static InitcodeCostCalculator INSTANCE;

    private InitcodeCostCalculator() {}

    public static InitcodeCostCalculator getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new InitcodeCostCalculator();
        }
        return INSTANCE;
    }

    @Override
    public long calculateCost(long dataLength, ActivationConfig.ForBlock activations) {
        if ( dataLength > 0 && activations.isActive(ConsensusRule.RSKIP438) ) {
            return  INITCODE_WORD_COST  *  ( (long) Math.ceil( ( (double) dataLength ) / 32 ) );
        }
        return 0;
    }
}
