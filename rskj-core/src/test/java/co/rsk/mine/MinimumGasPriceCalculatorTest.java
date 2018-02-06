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

import co.rsk.core.Coin;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by mario on 22/12/16.
 */
public class MinimumGasPriceCalculatorTest {

    @Test
    public void increaseMgp() {
        MinimumGasPriceCalculator mgpCalculator = new MinimumGasPriceCalculator();

        Coin prev = Coin.valueOf(1000L);
        Coin target = Coin.valueOf(2000L);

        Coin mgp = mgpCalculator.calculate(prev, target);

        Assert.assertEquals(Coin.valueOf(1010), mgp);
    }

    @Test
    public void decreaseMGP() {
        MinimumGasPriceCalculator mgpCalculator = new MinimumGasPriceCalculator();

        Coin prev = Coin.valueOf(1000L);
        Coin target = Coin.valueOf(900L);

        Coin mgp = mgpCalculator.calculate(prev, target);

        Assert.assertEquals(Coin.valueOf(990), mgp);
    }

    @Test
    public void mgpOnRage() {
        MinimumGasPriceCalculator mgpCalculator = new MinimumGasPriceCalculator();

        Coin prev = Coin.valueOf(1000L);
        Coin target = Coin.valueOf(995L);

        Coin mgp = mgpCalculator.calculate(prev, target);

        Assert.assertEquals(target, mgp);
    }

    @Test
    public void previousMgpEqualsTarget() {
        MinimumGasPriceCalculator mgpCalculator = new MinimumGasPriceCalculator();

        Coin prev = Coin.valueOf(1000L);
        Coin target = Coin.valueOf(1000L);

        Coin mgp = mgpCalculator.calculate(prev, target);

        Assert.assertEquals(target, mgp);
    }

    @Test
    public void previousValueIsZero() {
        MinimumGasPriceCalculator mgpCalculator = new MinimumGasPriceCalculator();

        Coin target = Coin.valueOf(1000L);

        Coin mgp = mgpCalculator.calculate(Coin.ZERO, target);

        Assert.assertEquals(Coin.valueOf(1L), mgp);
    }

    @Test
    public void previousValueIsSmallTargetIsZero() {
        MinimumGasPriceCalculator mgpCalculator = new MinimumGasPriceCalculator();

        Coin target = Coin.ZERO;

        Coin mgp = mgpCalculator.calculate(Coin.valueOf(1L), target);

        Assert.assertEquals(Coin.ZERO, mgp);
    }
}
