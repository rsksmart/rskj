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

import java.math.BigInteger;

/**
 * Created by mario on 26/12/16.
 */
public class BlockGasPriceRange {

    private static final BigInteger VARIATION_PERCENTAGE_RANGE = BigInteger.ONE;

    private final BigInteger upperLimit;
    private final BigInteger lowerLimit;

    public BlockGasPriceRange(BigInteger center) {
        BigInteger mgpDelta = center.multiply(VARIATION_PERCENTAGE_RANGE).divide(BigInteger.valueOf(100));
        mgpDelta = (BigInteger.ZERO.compareTo(mgpDelta) == 0) ? BigInteger.ONE : mgpDelta;
        this.upperLimit = center.add(mgpDelta);
        this.lowerLimit = center.subtract(mgpDelta);
    }

    public boolean inRange(BigInteger mgp) {
        return mgp.compareTo(upperLimit) <= 0 &&  mgp.compareTo(lowerLimit) >= 0;
    }

    public BigInteger getUpperLimit() {
        return upperLimit;
    }

    public BigInteger getLowerLimit() {
        return lowerLimit;
    }
}
