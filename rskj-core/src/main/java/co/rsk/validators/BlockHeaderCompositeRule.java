/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

import org.ethereum.core.BlockHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlockHeaderCompositeRule implements BlockHeaderValidationRule {

    private static final Logger logger = LoggerFactory.getLogger("blockvalidator");

    private BlockHeaderValidationRule[] rules;

    public BlockHeaderCompositeRule(BlockHeaderValidationRule... rules) {
        this.rules = rules;
    }

    @Override
    public boolean isValid(BlockHeader header) {
        String shortHash = header.getPrintableHash();
        long number = header.getNumber();
        logger.debug("Validating header {} {}", shortHash, number);
        for (BlockHeaderValidationRule rule : this.rules) {
            logger.debug("Validation rule {}", rule.getClass().getSimpleName());

            if (!rule.isValid(header)) {
                logger.warn("Error Validating {} for header {} {}", rule.getClass(), shortHash, number);
                return false;
            }
        }

        return true;
    }
}
