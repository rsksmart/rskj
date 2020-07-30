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

import org.ethereum.core.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by mario on 18/01/17.
 */
public class BlockParentCompositeRule implements BlockParentDependantValidationRule {

    private static final Logger logger = LoggerFactory.getLogger("blockvalidator");

    private List<BlockParentDependantValidationRule> rules;

    public BlockParentCompositeRule(BlockParentDependantValidationRule... rules) {
        this.rules = new ArrayList<>();
        if(rules != null) {
            for (BlockParentDependantValidationRule rule : rules) {
                if(rule != null) {
                    this.rules.add(rule);
                }
            }
        }
    }

    @Override
    public boolean isValid(Block block, Block parent) {
        final String shortHash = block.getShortHash();
        long number = block.getNumber();
        logger.debug("Validating block {} {}", shortHash, number);
        for(BlockParentDependantValidationRule rule : this.rules) {
            logger.debug("Validation rule {}", rule.getClass().getSimpleName());

            if(!rule.isValid(block, parent)) {
                logger.warn("Error Validating block {} {}", shortHash, number);
                return false;
            }
        }
        return true;
    }
}
