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
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * A rule which gathers an arbitrary amount of rules for validation of blocks.
 * This a lower-level validator which is intended to be used in specific cases and in tests,
 * but usage of higher-level validators like SyncBlockValidationRule or BlockValidationRule
 * should be preferred.
 */
@VisibleForTesting
public class BlockCompositeRule implements BlockValidationRule {

    private static final Logger logger = LoggerFactory.getLogger("blockvalidator");

    private List<BlockValidationRule> rules;

    public BlockCompositeRule(BlockValidationRule... rules) {
        this.rules = new ArrayList<>();
        if(rules != null) {
            for (BlockValidationRule rule : rules) {
                if(rule != null) {
                    this.rules.add(rule);
                }
            }
        }
    }
    @Override
    public boolean isValid(Block block, BlockExecutor blockExecutor) {
        String shortHash = block.getPrintableHash();
        long number = block.getNumber();
        logger.debug("Validating block {} {}", shortHash, number);
        for(BlockValidationRule rule : this.rules) {
            logger.debug("Validation rule {}", rule.getClass().getSimpleName());

            if(!rule.isValid(block, blockExecutor)) {
                logger.warn("Error Validating block {} {}", shortHash, number);
                return false;
            }
        }
        return true;
    }
}
