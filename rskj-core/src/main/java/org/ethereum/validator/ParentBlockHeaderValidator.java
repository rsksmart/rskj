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

import java.util.ArrayList;
import java.util.List;

/**
 * Composite {@link BlockHeader} validator
 * aggregating list of simple validation rules depending on parent's block header
 *
 * @author Mikhail Kalinin
 * @since 02.09.2015
 */
public class ParentBlockHeaderValidator extends DependentBlockHeaderRule {

    private List<DependentBlockHeaderRule> rules;

    public ParentBlockHeaderValidator(List<DependentBlockHeaderRule> rules) {
        this.rules = new ArrayList<>();
        if(rules != null) {
            this.rules.addAll(rules);
        }
    }

    @Override
    public boolean validate(BlockHeader header, BlockHeader parent) {
        if(parent == null) {
            return false;
        }
        for (DependentBlockHeaderRule rule : rules) {
            if (!rule.validate(header, parent)) {
                return false;
            }
        }
        return true;
    }
}
