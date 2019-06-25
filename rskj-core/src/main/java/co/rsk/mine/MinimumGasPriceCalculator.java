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

/** This is the implementation of RSKIP-09 Created by mario on 22/12/16. */
public class MinimumGasPriceCalculator {

    private final Coin targetMGP;

    public MinimumGasPriceCalculator(Coin targetMGP) {
        this.targetMGP = targetMGP;
    }

    public Coin calculate(Coin previousMGP) {
        BlockGasPriceRange priceRange = new BlockGasPriceRange(previousMGP);
        if (priceRange.inRange(targetMGP)) {
            return targetMGP;
        }

        if (previousMGP.compareTo(targetMGP) < 0) {
            return priceRange.getUpperLimit();
        }

        return priceRange.getLowerLimit();
    }
}
