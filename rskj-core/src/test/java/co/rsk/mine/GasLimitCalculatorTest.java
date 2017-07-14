/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.mine;

import org.ethereum.config.Constants;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;

/**
 * Created by Ruben Altman on 5/23/2016.
 */
public class GasLimitCalculatorTest {

    private Constants constants = new Constants();

    @Test
    public void NextBlockGasLimitIsDecreasedByAFactor() {
        GasLimitCalculator calc = new GasLimitCalculator();
        BigInteger minGasLimit = BigInteger.valueOf(constants.getMinGasLimit());
        BigInteger parentGasLimit = minGasLimit.add(BigInteger.valueOf(21000));
        BigInteger targetGasLimit = BigInteger.valueOf(constants.getTargetGasLimit());

        BigInteger newGasLimit = calc.calculateBlockGasLimit(parentGasLimit, BigInteger.ZERO, minGasLimit, targetGasLimit);

        BigInteger factor = parentGasLimit.divide(BigInteger.valueOf(constants.getGasLimitBoundDivisor()));
        Assert.assertTrue(newGasLimit.compareTo(parentGasLimit) < 0);
        Assert.assertTrue(newGasLimit.compareTo(parentGasLimit.subtract(factor)) == 0);
    }

    @Test
    public void NextBlockGasLimitIsNotDecreasedLowerThanMinGasLimit() {
        GasLimitCalculator calc = new GasLimitCalculator();
        BigInteger minGasLimit = BigInteger.valueOf(constants.getMinGasLimit());
        BigInteger targetGasLimit = BigInteger.valueOf(constants.getTargetGasLimit());
        BigInteger newGasLimit = calc.calculateBlockGasLimit(minGasLimit, BigInteger.ZERO, minGasLimit, targetGasLimit);

        Assert.assertTrue(newGasLimit.compareTo(minGasLimit) == 0);
    }

    @Test
    public void NextBlockGasLimitIsIncreasedBasedOnGasUsed() {
        GasLimitCalculator calc = new GasLimitCalculator();
        BigInteger gasUsed = BigInteger.valueOf(10000);
        BigInteger targetGasLimit = BigInteger.valueOf(constants.getTargetGasLimit());

        // I used zero as parent gas limit so that there is no decay
        BigInteger newGasLimit = calc.calculateBlockGasLimit(BigInteger.ZERO, gasUsed, BigInteger.ZERO, targetGasLimit);

        BigInteger expected = BigInteger.valueOf(14); // parentGasUsed * 3 / 2) / 1024
        Assert.assertTrue(newGasLimit.compareTo(expected) == 0);
   }

    @Test
    public void NextBlockGasLimitIsNotIncreasedMoreThanTargetGasLimit() {
        GasLimitCalculator calc = new GasLimitCalculator();
        BigInteger targetGasLimit = BigInteger.valueOf(constants.getTargetGasLimit());
        BigInteger gasUsed = targetGasLimit;

        BigInteger newGasLimit = calc.calculateBlockGasLimit(targetGasLimit, gasUsed, BigInteger.ZERO, targetGasLimit);

        Assert.assertTrue(newGasLimit.compareTo(targetGasLimit) == 0);
    }

}