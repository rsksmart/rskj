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

/**
 * Parent class for {@link BlockHeader} validators which run depends on the header of another block
 *
 * @author Mikhail Kalinin
 * @since 02.09.2015
 */
public abstract class DependentBlockHeaderRule extends AbstractValidationRule {

    @Override
    public Class getEntityClass() {
        return BlockHeader.class;
    }

    /**
     * Runs header validation and returns its result
     *
     * @param header block's header
     * @param dependency header of the dependency block
     * @return true if validation passed, false otherwise
     */
    public abstract boolean validate(BlockHeader header, BlockHeader dependency);
}
