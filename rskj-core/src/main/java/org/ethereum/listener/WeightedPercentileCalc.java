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
package org.ethereum.listener;

import co.rsk.core.Coin;

import java.util.Collections;
import java.util.List;

class WeightedPercentileCalc {

    Coin calculateWeightedPercentile(float percentile, List<WeightedPercentileGasPriceCalculator.GasEntry> gasEntries) {
        if (gasEntries == null || gasEntries.isEmpty()) {
            return null;
        }

        Collections.sort(gasEntries);

        double totalWeight = gasEntries.stream().mapToLong(WeightedPercentileGasPriceCalculator.GasEntry::getGasUsed).sum();

        double targetWeight = percentile / 100 * totalWeight;


        double cumulativeWeight = 0;
        for (WeightedPercentileGasPriceCalculator.GasEntry pair : gasEntries) {
            cumulativeWeight += pair.getGasUsed();
            if (cumulativeWeight >= targetWeight) {
                return pair.getGasPrice();
            }
        }

        return null;
    }
}
