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
import co.rsk.mine.gas.provider.MinGasPriceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the implementation of RSKIP-09
 * Created by mario on 22/12/16.
 */
public class MinimumGasPriceCalculator {

    private static final Logger logger = LoggerFactory.getLogger(MinimumGasPriceCalculator.class);

    private final MinGasPriceProvider minGasPriceProvider;

    public MinimumGasPriceCalculator(MinGasPriceProvider minGasPriceProvider) {
        this.minGasPriceProvider = minGasPriceProvider;
    }

    public Coin calculate(Coin previousMGP) {
        BlockGasPriceRange priceRange = new BlockGasPriceRange(previousMGP);
        Coin targetMGP = minGasPriceProvider.getMinGasPriceAsCoin();
        if (priceRange.inRange(targetMGP)) {
            logger.debug("Previous MGP: {}. Target MGP: {} is in range: {}. Returning target MGP", previousMGP, targetMGP, priceRange);
            return targetMGP;
        }

        if (previousMGP.compareTo(targetMGP) < 0) {
            logger.debug("Previous MGP: {}. Target MGP: {} is not in range: {}. Returning upper boundary: {}", previousMGP, targetMGP, priceRange, priceRange.getUpperLimit());
            return priceRange.getUpperLimit();
        }

        logger.debug("Previous MGP: {}. Target MGP: {} is not in range: {}. Returning lower boundary: {}", previousMGP, targetMGP, priceRange, priceRange.getLowerLimit());
        return priceRange.getLowerLimit();
    }
}
