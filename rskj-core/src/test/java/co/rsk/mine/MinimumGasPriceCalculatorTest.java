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

import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;

/**
 * Created by mario on 22/12/16.
 */
public class MinimumGasPriceCalculatorTest {

    @Test
    public void increaseMgp() {
        MinimumGasPriceCalculator mgpCalculator = new MinimumGasPriceCalculator();

        BigInteger prev = BigInteger.valueOf(1000L);
        BigInteger target = BigInteger.valueOf(2000L);

        BigInteger mgp = mgpCalculator.calculate(prev, target);

        Assert.assertTrue(BigInteger.valueOf(1010).compareTo(mgp) == 0);
    }

    @Test
    public void decreaseMGP() {
        MinimumGasPriceCalculator mgpCalculator = new MinimumGasPriceCalculator();

        BigInteger prev = BigInteger.valueOf(1000L);
        BigInteger target = BigInteger.valueOf(900L);

        BigInteger mgp = mgpCalculator.calculate(prev, target);

        Assert.assertTrue(BigInteger.valueOf(990).compareTo(mgp) == 0);
    }

    @Test
    public void mgpOnRage() {
        MinimumGasPriceCalculator mgpCalculator = new MinimumGasPriceCalculator();

        BigInteger prev = BigInteger.valueOf(1000L);
        BigInteger target = BigInteger.valueOf(995L);

        BigInteger mgp = mgpCalculator.calculate(prev, target);

        Assert.assertTrue(target.compareTo(mgp) == 0);
    }

    @Test
    public void previousMgpEqualsTarget() {
        MinimumGasPriceCalculator mgpCalculator = new MinimumGasPriceCalculator();

        BigInteger prev = BigInteger.valueOf(1000L);
        BigInteger target = BigInteger.valueOf(1000L);

        BigInteger mgp = mgpCalculator.calculate(prev, target);

        Assert.assertTrue(target.compareTo(mgp) == 0);
    }

    @Test
    public void previousValueIsZero() {
        MinimumGasPriceCalculator mgpCalculator = new MinimumGasPriceCalculator();

        BigInteger target = BigInteger.valueOf(1000L);

        BigInteger mgp = mgpCalculator.calculate(BigInteger.ZERO, target);

        Assert.assertTrue(BigInteger.ONE.compareTo(mgp) == 0);
    }

    @Test
    public void previousValueIsSmallTargetIsZero() {
        MinimumGasPriceCalculator mgpCalculator = new MinimumGasPriceCalculator();

        BigInteger target = BigInteger.valueOf(0L);

        BigInteger mgp = mgpCalculator.calculate(BigInteger.ONE, target);

        Assert.assertTrue(BigInteger.ZERO.compareTo(mgp) == 0);
    }
}
