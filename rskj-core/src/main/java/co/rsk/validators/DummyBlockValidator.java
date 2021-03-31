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

/**
 * Created by ajlopez on 4/20/2016.
 */
public class DummyBlockValidator implements BlockValidator {

    public static final BlockValidator VALID_RESULT_INSTANCE = new DummyBlockValidator(true);
    public static final BlockValidator INVALID_RESULT_INSTANCE = new DummyBlockValidator(false);

    private final boolean validationResult;

    public DummyBlockValidator(boolean validationResult) {
        this.validationResult = validationResult;
    }

    public DummyBlockValidator() {
        this(true);
    }

    @Override
    public boolean isValid(Block block) {
        return validationResult;
    }
}
