/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
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

package org.ethereum.core;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.function.Supplier;

public class SuperBlockResolver {

    public static final SuperBlockResolver TRUE = new SuperBlockResolver(true);
    public static final SuperBlockResolver FALSE = new SuperBlockResolver(false);

    private Boolean value;
    private Supplier<Boolean> supplier;

    private SuperBlockResolver(boolean value) {
        this.value = value;
    }

    public SuperBlockResolver(@Nonnull Supplier<Boolean> supplier) {
        this.supplier = Objects.requireNonNull(supplier);
    }

    public boolean resolve() {
        if (value == null) {
            value = Objects.requireNonNull(supplier.get());
            supplier = null;
        }
        return value;
    }

    public static SuperBlockResolver of(boolean value) {
        return value ? TRUE : FALSE;
    }
}
