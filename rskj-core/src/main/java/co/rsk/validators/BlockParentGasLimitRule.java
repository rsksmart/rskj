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

package co.rsk.validators;

import co.rsk.core.bc.BlockExecutor;
import org.ethereum.core.Block;
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
public class BlockParentGasLimitRule implements BlockParentDependantValidationRule, BlockHeaderParentDependantValidationRule {

    private static final Logger logger = LoggerFactory.getLogger("blockvalidator");

    private int gasLimitBoundDivisor;

    public BlockParentGasLimitRule(int gasLimitBoundDivisor) {
        if (gasLimitBoundDivisor < 1) {
            throw new IllegalArgumentException("The gasLimitBoundDivisor argument must be strictly greater than 0");
        }

        this.gasLimitBoundDivisor = gasLimitBoundDivisor;
    }


    @Override
    public boolean isValid(BlockHeader header, Block parent) {
        if (header == null || parent == null) {
            logger.warn("BlockParentGasLimitRule - block or parent are null");
            return false;
        }

        BlockHeader parentHeader = parent.getHeader();
        BigInteger headerGasLimit = new BigInteger(1, header.getGasLimit());
        BigInteger parentGasLimit = new BigInteger(1, parentHeader.getGasLimit());

        if (headerGasLimit.compareTo(parentGasLimit.multiply(BigInteger.valueOf(gasLimitBoundDivisor - 1L)).divide(BigInteger.valueOf(gasLimitBoundDivisor))) < 0 ||
            headerGasLimit.compareTo(parentGasLimit.multiply(BigInteger.valueOf(gasLimitBoundDivisor + 1L)).divide(BigInteger.valueOf(gasLimitBoundDivisor))) > 0) {
            logger.warn(String.format("#%d: gas limit exceeds parentBlock.getGasLimit() (+-) GAS_LIMIT_BOUND_DIVISOR", header.getNumber()));
            return false;
        }
        return true;
    }

    @Override
    public boolean isValid(Block block, Block parent, BlockExecutor blockExecutor) {
        return isValid(block.getHeader(), parent);
    }
}
