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

package co.rsk.core.bc;

import co.rsk.validators.BlockHeaderValidationRule;
import co.rsk.validators.BlockValidator;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

/**
 * Validates a block header if it is good enough to be propagated.
 *
 * The validation includes:
 * - validation of the header data of the block
 */
public class BlockHeaderValidatorImpl implements BlockValidator {

    private static final Logger logger = LoggerFactory.getLogger("blocksyncservice");

    private final BlockHeaderValidationRule blockHeaderValidator;

    public BlockHeaderValidatorImpl(@Nonnull BlockHeaderValidationRule blockHeaderValidator) {
        this.blockHeaderValidator = blockHeaderValidator;
    }

    /**
     * Checks if a block header is valid.
     *
     * @param block Block to validate
     * @return true if the block is valid, otherwise - false.
     */
    @Override
    public boolean isValid(@Nonnull Block block) {
        if (block.isGenesis()) {
            logger.error("Block is Genesis");
            return false;
        }

        BlockHeader header = block.getHeader();
        return blockHeaderValidator.isValid(header);
    }
}
