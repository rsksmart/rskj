/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package org.ethereum.validator;

import org.ethereum.core.BlockHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;

/**
 * Checks if {@link BlockHeader#gasLimit} matches gas limit bounds. <br>
 *
 * This check is NOT run in Frontier
 *
 * @author Mikhail Kalinin
 * @since 02.09.2015
 */
public class ParentGasLimitRule extends DependentBlockHeaderRule {

    private static final Logger logger = LoggerFactory.getLogger(ParentGasLimitRule.class);

    private BigInteger gasLimitBoundDivisor;

    public ParentGasLimitRule(int gasLimitBoundDivisor) {
        this.gasLimitBoundDivisor = BigInteger.valueOf(gasLimitBoundDivisor);
    }


    @Override
    public boolean validate(BlockHeader header, BlockHeader parent) {

        BigInteger headerGasLimit = new BigInteger(1, header.getGasLimit());
        BigInteger parentGasLimit = new BigInteger(1, parent.getGasLimit());
        BigInteger deltaLimit = parentGasLimit.divide(gasLimitBoundDivisor);

        if (headerGasLimit.compareTo(parentGasLimit.subtract(deltaLimit)) < 0 ||
                headerGasLimit.compareTo(parentGasLimit.add(deltaLimit)) > 0) {
            logger.error(String.format("#%d: gas limit exceeds parentBlock.getGasLimit() (+-) GAS_LIMIT_BOUND_DIVISOR", header.getNumber()));
            return false;
        }
        return true;
    }
}
