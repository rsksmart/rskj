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

import co.rsk.validators.BlockParentDependantValidationRule;
import co.rsk.validators.BlockValidationRule;
import co.rsk.validators.BlockValidator;
import org.ethereum.core.Block;
import org.ethereum.db.BlockStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BlockValidator has methods to validate block content before its execution
 *
 * Created by ajlopez on 29/07/2016.
 */
public class BlockValidatorImpl implements BlockValidator {

    private static final Logger logger = LoggerFactory.getLogger("blocksyncservice");

    private BlockStore blockStore;

    private BlockParentDependantValidationRule blockParentValidator;

    private BlockValidationRule blockValidator;
    private BlockExecutor blockExecutor;

    public BlockValidatorImpl(BlockStore blockStore, BlockParentDependantValidationRule blockParentValidator, BlockValidationRule blockValidator, BlockExecutor blockExecutor) {
        this.blockStore = blockStore;
        this.blockParentValidator = blockParentValidator;
        this.blockValidator = blockValidator;
        this.blockExecutor = blockExecutor;
    }

    /**
     * Validate a block.
     * The validation includes
     * - Validate the header data relative to parent block
     * - Validate the transaction root hash to transaction list
     * - Validate uncles
     * - Validate transactions
     *
     * @param block        Block to validate
     * @return true if the block is valid, false if the block is invalid
     */
    @Override
    public boolean isValid(Block block) {
        if (block.isGenesis()) {
            logger.warn("Block is Genesis");
            return false;
        }

        Block parent = getParent(block);

        if(!this.blockParentValidator.isValid(block, parent, blockExecutor)) {
            return false;
        }

        if(!this.blockValidator.isValid(block, blockExecutor)) {
            return false;
        }

        return true;
    }

    private Block getParent(Block block) {
        if (this.blockStore == null) {
            return null;
        }

        return blockStore.getBlockByHash(block.getParentHash().getBytes());
    }
}

