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
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeightedPercentileCalcTest {

    @Test
    void testCalculateWeightedPercentile() {
        WeightedPercentileCalc weightedPercentileCalc = new WeightedPercentileCalc();

        // Sample gas entries with smaller numbers
        WeightedPercentileGasPriceCalculator.GasEntry entry1 = new WeightedPercentileGasPriceCalculator.GasEntry(new Coin(BigInteger.valueOf(100)), 1);
        WeightedPercentileGasPriceCalculator.GasEntry entry2 = new WeightedPercentileGasPriceCalculator.GasEntry(new Coin(BigInteger.valueOf(200)), 3);
        WeightedPercentileGasPriceCalculator.GasEntry entry3 = new WeightedPercentileGasPriceCalculator.GasEntry(new Coin(BigInteger.valueOf(300)), 1);
        WeightedPercentileGasPriceCalculator.GasEntry entry4 = new WeightedPercentileGasPriceCalculator.GasEntry(new Coin(BigInteger.valueOf(500)), 10);
        WeightedPercentileGasPriceCalculator.GasEntry entry5 = new WeightedPercentileGasPriceCalculator.GasEntry(new Coin(BigInteger.valueOf(400)), 1);
        WeightedPercentileGasPriceCalculator.GasEntry entry6 = new WeightedPercentileGasPriceCalculator.GasEntry(new Coin(BigInteger.valueOf(700)), 2);
        WeightedPercentileGasPriceCalculator.GasEntry entry7 = new WeightedPercentileGasPriceCalculator.GasEntry(new Coin(BigInteger.valueOf(600)), 4);
        WeightedPercentileGasPriceCalculator.GasEntry entry8 = new WeightedPercentileGasPriceCalculator.GasEntry(new Coin(BigInteger.valueOf(800)), 1);


        List<WeightedPercentileGasPriceCalculator.GasEntry> gasEntries = Arrays.asList(entry1, entry2, entry3, entry4, entry5, entry6, entry7,entry8);


        Coin result0 = weightedPercentileCalc.calculateWeightedPercentile(0, gasEntries);
        assertEquals(new Coin(BigInteger.valueOf(100)), result0, "0th percentile should be 100");

        Coin result10 = weightedPercentileCalc.calculateWeightedPercentile(1, gasEntries);
        assertEquals(new Coin(BigInteger.valueOf(100)), result10, "1th percentile should be 100");

        Coin result20 = weightedPercentileCalc.calculateWeightedPercentile(20.2f, gasEntries);
        assertEquals(new Coin(BigInteger.valueOf(300)), result20, "20th percentile should be 300");

        Coin result40 = weightedPercentileCalc.calculateWeightedPercentile(40, gasEntries);
        assertEquals(new Coin(BigInteger.valueOf(500)), result40, "40th percentile should be 500");

        Coin result50 = weightedPercentileCalc.calculateWeightedPercentile(50, gasEntries);
        assertEquals(new Coin(BigInteger.valueOf(500)), result50, "50th percentile should be 500");

        Coin result75 = weightedPercentileCalc.calculateWeightedPercentile(75, gasEntries);
        assertEquals(new Coin(BigInteger.valueOf(600)), result75, "75th percentile should be 600");

        Coin result90 = weightedPercentileCalc.calculateWeightedPercentile(90, gasEntries);
        assertEquals(new Coin(BigInteger.valueOf(700)), result90, "90th percentile should be 600");

        Coin result100 = weightedPercentileCalc.calculateWeightedPercentile(100, gasEntries);
        assertEquals(new Coin(BigInteger.valueOf(800)), result100, "100th percentile should be 800");


    }
}