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

package co.rsk.validators;

import co.rsk.core.bc.BlockExecutor;
import co.rsk.panic.PanicProcessor;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;

/**
 * Created by martin.medina on 07/02/17.
 */
public class ValidGasUsedRule implements BlockValidationRule, BlockHeaderValidationRule {

    private static final Logger logger = LoggerFactory.getLogger("blockvalidator");
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    @Override
    public boolean isValid(Block block, BlockExecutor blockExecutor) {
        return isValid(block.getHeader());
    }

    @Override
    public boolean isValid(BlockHeader header) {
        long gasUsed = header.getGasUsed();
        long gasLimit = new BigInteger(1, header.getGasLimit()).longValue();

        if(gasUsed < 0 || gasUsed > gasLimit) {
            logger.warn("Block gas used is less than 0 or more than the gas limit of the block");
            panicProcessor.panic("invalidGasValue", "Block gas used is less than 0 or more than the gas limit of the block");
            return false;
        }

        return true;
    }
}
