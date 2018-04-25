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

import java.math.BigInteger;

/**
 * Created by mario on 26/12/16.
 */
public class BlockGasPriceRange {

    private static final BigInteger VARIATION_PERCENTAGE_RANGE = BigInteger.ONE;

    private final Coin upperLimit;
    private final Coin lowerLimit;

    public BlockGasPriceRange(Coin center) {
        Coin mgpDelta = center.multiply(VARIATION_PERCENTAGE_RANGE).divide(BigInteger.valueOf(100));
        mgpDelta = mgpDelta.equals(Coin.ZERO) ? Coin.valueOf(1L) : mgpDelta;
        this.upperLimit = center.add(mgpDelta);
        this.lowerLimit = center.subtract(mgpDelta);
    }

    public boolean inRange(Coin mgp) {
        return mgp.compareTo(upperLimit) <= 0 && mgp.compareTo(lowerLimit) >= 0;
    }

    public Coin getUpperLimit() {
        return upperLimit;
    }

    public Coin getLowerLimit() {
        return lowerLimit;
    }
}
