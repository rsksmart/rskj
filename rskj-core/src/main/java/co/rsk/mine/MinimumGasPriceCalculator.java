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
 * This is the implementation of RSKIP-09
 * Created by mario on 22/12/16.
 */
public class MinimumGasPriceCalculator {

    public BigInteger calculate(BigInteger previousMGP, BigInteger targetMGP) {
        BigInteger mgp;
        BlockGasPriceRange priceRange = new BlockGasPriceRange(previousMGP);
        if(priceRange.inRange(targetMGP)) {
            mgp = targetMGP;
        } else {
            mgp = (previousMGP.compareTo(targetMGP) < 0) ? priceRange.getUpperLimit() : priceRange.getLowerLimit();
        }
        return mgp;
    }
}
