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

import co.rsk.config.TestSystemProperties;
import org.ethereum.config.Constants;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockHeader;
import org.ethereum.validator.ParentGasLimitRule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.ethereum.validator.ParentGasLimitRuleTest.getHeader;

/**
 * Created by Ruben Altman on 5/23/2016.
 */
class GasLimitCalculatorTest {

    private final TestSystemProperties config = new TestSystemProperties();
    private final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());
    private Constants constants = Constants.regtest();
    private ParentGasLimitRule rule = new ParentGasLimitRule(1024);

    @Test
    void NextBlockGasLimitIsDecreasedByAFactor() {
        GasLimitCalculator calc = new GasLimitCalculator(config.getNetworkConstants());
        BigInteger minGasLimit = BigInteger.valueOf(constants.getMinGasLimit());
        BigInteger parentGasLimit = minGasLimit.add(BigInteger.valueOf(21000));
        BigInteger targetGasLimit = BigInteger.valueOf(config.getTargetGasLimit());

        BigInteger newGasLimit = calc.calculateBlockGasLimit(parentGasLimit, BigInteger.ZERO, minGasLimit, targetGasLimit, false);

        BigInteger factor = parentGasLimit.divide(BigInteger.valueOf(constants.getGasLimitBoundDivisor()));
        Assertions.assertTrue(newGasLimit.compareTo(parentGasLimit) < 0);
        Assertions.assertEquals(0, newGasLimit.compareTo(parentGasLimit.subtract(factor)));
    }

    @Test
    void NextBlockGasLimitIsNotDecreasedLowerThanMinGasLimit() {
        GasLimitCalculator calc = new GasLimitCalculator(config.getNetworkConstants());
        BigInteger minGasLimit = BigInteger.valueOf(constants.getMinGasLimit());
        BigInteger targetGasLimit = BigInteger.valueOf(config.getTargetGasLimit());
        BigInteger newGasLimit = calc.calculateBlockGasLimit(minGasLimit, BigInteger.ZERO, minGasLimit, targetGasLimit, false);

        Assertions.assertEquals(0, newGasLimit.compareTo(minGasLimit));
    }

    @Test
    void NextBlockGasLimitIsIncreasedBasedOnGasUsed() {
        GasLimitCalculator calc = new GasLimitCalculator(config.getNetworkConstants());
        BigInteger parentGas = BigInteger.valueOf(3500000);
        BigInteger gasUsed = BigInteger.valueOf(3000000);
        BigInteger targetGasLimit = BigInteger.valueOf(config.getTargetGasLimit());

        BigInteger newGasLimit = calc.calculateBlockGasLimit(parentGas, gasUsed, BigInteger.ZERO, targetGasLimit, false);

        // These are strategic values, don't know why some people like this
        BigInteger expected = BigInteger.valueOf(3500977);
        Assertions.assertEquals(0, newGasLimit.compareTo(expected));
    }

    @Test
    void NextBlockGasLimitIsIncreasedBasedOnFullGasUsed() {
        GasLimitCalculator calc = new GasLimitCalculator(config.getNetworkConstants());
        BigInteger parentGas = BigInteger.valueOf(3500000);
        BigInteger gasUsed = BigInteger.valueOf(3500000);
        BigInteger targetGasLimit = BigInteger.valueOf(config.getTargetGasLimit());

        BigInteger newGasLimit = calc.calculateBlockGasLimit(parentGas, gasUsed, BigInteger.ZERO, targetGasLimit, false);

        // These are strategic values, don't know why some people like this
        BigInteger expected = BigInteger.valueOf(3501709);
        Assertions.assertEquals(0, newGasLimit.compareTo(expected));
    }
    @Test
    void NextBlockGasLimitIsNotIncreasedMoreThanTargetGasLimit() {
        GasLimitCalculator calc = new GasLimitCalculator(config.getNetworkConstants());
        BigInteger targetGasLimit = BigInteger.valueOf(config.getTargetGasLimit());
        BigInteger gasUsed = targetGasLimit;

        BigInteger newGasLimit = calc.calculateBlockGasLimit(targetGasLimit, gasUsed, BigInteger.ZERO, targetGasLimit, false);

        Assertions.assertEquals(0, newGasLimit.compareTo(targetGasLimit));
    }

    @Test
    void NextBlockGasLimitRemainsTheSame() {
        GasLimitCalculator calc = new GasLimitCalculator(config.getNetworkConstants());
        BigInteger minGasLimit = BigInteger.valueOf(config.getTargetGasLimit());
        BigInteger targetGasLimit = BigInteger.valueOf(config.getTargetGasLimit());
        BigInteger newGasLimit = calc.calculateBlockGasLimit(targetGasLimit, BigInteger.ZERO, minGasLimit, targetGasLimit, true);
        Assertions.assertEquals(0, newGasLimit.compareTo(targetGasLimit));
        Assertions.assertTrue(validByConsensus(newGasLimit, targetGasLimit));
    }

    @Test
    void NextBlockGasLimitIsIncreasedByMaximumValue() {
        GasLimitCalculator calc = new GasLimitCalculator(config.getNetworkConstants());
        BigInteger minGasLimit = BigInteger.valueOf(constants.getMinGasLimit());
        BigInteger targetGasLimit = BigInteger.valueOf(config.getTargetGasLimit());
        BigInteger newGasLimit = calc.calculateBlockGasLimit(minGasLimit, BigInteger.ZERO, minGasLimit, targetGasLimit, true);
        BigInteger newGasLimit2 = calc.calculateBlockGasLimit(minGasLimit, minGasLimit, minGasLimit, targetGasLimit, true);
        Assertions.assertEquals(0, newGasLimit.compareTo(newGasLimit2));
        Assertions.assertEquals(0, newGasLimit.compareTo(minGasLimit.add(minGasLimit.divide(BigInteger.valueOf(1024)))));
        Assertions.assertTrue(validByConsensus(newGasLimit, minGasLimit));
        Assertions.assertTrue(validByConsensus(newGasLimit2, minGasLimit));
        Assertions.assertFalse(validByConsensus(newGasLimit.add(BigInteger.ONE), minGasLimit));
    }

    @Test
    void NextBlockGasLimitIsIncreasedToTarget() {
        GasLimitCalculator calc = new GasLimitCalculator(config.getNetworkConstants());
        BigInteger minGasLimit = BigInteger.valueOf(constants.getMinGasLimit());
        BigInteger targetGasLimit = minGasLimit.add(BigInteger.ONE);
        BigInteger newGasLimit = calc.calculateBlockGasLimit(minGasLimit, BigInteger.ZERO, minGasLimit, targetGasLimit, true);
        BigInteger newGasLimit2 = calc.calculateBlockGasLimit(minGasLimit, minGasLimit, minGasLimit, targetGasLimit, true);
        Assertions.assertEquals(0, newGasLimit.compareTo(targetGasLimit));
        Assertions.assertEquals(0, newGasLimit.compareTo(newGasLimit2));
        Assertions.assertTrue(validByConsensus(newGasLimit, minGasLimit));
        Assertions.assertTrue(validByConsensus(newGasLimit2, minGasLimit));
    }

    @Test
    void NextBlockGasLimitIsDecreasedToTarget() {
        GasLimitCalculator calc = new GasLimitCalculator(config.getNetworkConstants());
        BigInteger minGasLimit = BigInteger.valueOf(constants.getMinGasLimit());
        BigInteger targetGasLimit = minGasLimit.add(BigInteger.ONE);
        BigInteger usedGas = targetGasLimit.add(BigInteger.ONE);
        BigInteger newGasLimit = calc.calculateBlockGasLimit(usedGas, usedGas, minGasLimit, targetGasLimit, true);
        BigInteger newGasLimit2 = calc.calculateBlockGasLimit(usedGas, BigInteger.ZERO, minGasLimit, targetGasLimit, true);
        Assertions.assertEquals(0, newGasLimit.compareTo(targetGasLimit));
        Assertions.assertEquals(0, newGasLimit.compareTo(newGasLimit2));
        Assertions.assertTrue(validByConsensus(newGasLimit, usedGas));
        Assertions.assertTrue(validByConsensus(newGasLimit2, usedGas));
    }

    @Test
    void NextBlockGasLimitIsDecreasedToMinimum() {
        GasLimitCalculator calc = new GasLimitCalculator(config.getNetworkConstants());
        BigInteger minGasLimit = BigInteger.valueOf(constants.getMinGasLimit());
        BigInteger targetGasLimit = minGasLimit.subtract(BigInteger.ONE);
        BigInteger usedGas = minGasLimit.add(BigInteger.ONE);
        BigInteger newGasLimit = calc.calculateBlockGasLimit(usedGas, usedGas, minGasLimit, targetGasLimit, true);
        BigInteger newGasLimit2 = calc.calculateBlockGasLimit(usedGas, BigInteger.ZERO, minGasLimit, targetGasLimit, true);
        Assertions.assertEquals(0, newGasLimit.compareTo(minGasLimit));
        Assertions.assertEquals(0, newGasLimit.compareTo(newGasLimit2));
        Assertions.assertTrue(validByConsensus(newGasLimit, usedGas));
        Assertions.assertTrue(validByConsensus(newGasLimit2, usedGas));
    }
    @Test
    void NextBlockGasLimitIsDecreasedByMaximumValue() {
        GasLimitCalculator calc = new GasLimitCalculator(config.getNetworkConstants());
        BigInteger minGasLimit = BigInteger.valueOf(constants.getMinGasLimit());
        BigInteger targetGasLimit = BigInteger.valueOf(config.getTargetGasLimit());
        BigInteger usedGas = targetGasLimit.multiply(BigInteger.valueOf(2));
        BigInteger newGasLimit = calc.calculateBlockGasLimit(usedGas, BigInteger.ZERO, minGasLimit, targetGasLimit, true);
        BigInteger newGasLimit2 = calc.calculateBlockGasLimit(usedGas, usedGas, minGasLimit, targetGasLimit, true);
        Assertions.assertEquals(0, newGasLimit.compareTo(newGasLimit2));
        Assertions.assertEquals(0, newGasLimit.compareTo(usedGas.subtract(usedGas.divide(BigInteger.valueOf(1024)))));
        Assertions.assertTrue(validByConsensus(newGasLimit, usedGas));
        Assertions.assertTrue(validByConsensus(newGasLimit2, usedGas));
        Assertions.assertFalse(validByConsensus(newGasLimit.subtract(BigInteger.ONE), usedGas));
    }

    private boolean validByConsensus(BigInteger newGas, BigInteger parentGas) {
        BlockHeader header = getHeader(blockFactory, newGas.intValue());
        BlockHeader parent = getHeader(blockFactory, parentGas.intValue());
        return rule.validate(header, parent);
    }
}
