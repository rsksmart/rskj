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

package org.ethereum.datasource;

import java.util.Map;
import java.util.Set;

/**
 * @author Roman Mandeleil
 * @since 18.01.2015
 */
public interface KeyValueDataSource extends DataSource {

    byte[] get(byte[] key);

    byte[] put(byte[] key, byte[] value);

    void delete(byte[] key);

    Set<byte[]> keys();

    void updateBatch(Map<byte[], byte[]> rows);

    default void copyFrom(KeyValueDataSource ds) {
        for (byte[] key : ds.keys()) {
            this.put(key, ds.get(key));
        }
    }
}
