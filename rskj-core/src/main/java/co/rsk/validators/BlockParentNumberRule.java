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

/**
 * Checks if {@link BlockHeader#number} == {@link BlockHeader#number} + 1 of parent's block
 *
 * @author Mikhail Kalinin
 * @since 02.09.2015
 */
public class BlockParentNumberRule implements BlockParentDependantValidationRule, BlockHeaderParentDependantValidationRule {

    private static final Logger logger = LoggerFactory.getLogger("blockvalidator");

    @Override
    public boolean isValid(BlockHeader header, Block parent) {
        if (header == null || parent == null) {
            logger.warn("BlockParentNumberRule - block or parent are null");
            return false;
        }
        BlockHeader parentHeader = parent.getHeader();
        if (header.getNumber() != (parentHeader.getNumber() + 1)) {
            logger.warn("#{}: block number is not parentBlock number + 1", header.getNumber());
            return false;
        }
        return true;
    }

    @Override
    public boolean isValid(Block block, Block parent, BlockExecutor blockExecutor) {
        return isValid(block.getHeader(), parent);
    }
}
