/*
 * This file is part of RskJ
 * Copyright (C) 2021 RSK Labs Ltd.
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
package org.ethereum.datasource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Objects;

public enum DbKind {
    LEVEL_DB("leveldb"), ROCKS_DB("rocksdb");

    private static final Logger logger = LoggerFactory.getLogger("general");

    private final String name;

    DbKind(@Nonnull String name) {
        this.name = name;
    }

    public static DbKind ofName(@Nonnull String name) {
        Objects.requireNonNull(name, "name cannot be null");
        return Arrays.stream(DbKind.values()).filter(dk -> dk.name.equals(name))
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException(String.format("%s: not found as DbKind, using leveldb as default", name)));
    }
}
