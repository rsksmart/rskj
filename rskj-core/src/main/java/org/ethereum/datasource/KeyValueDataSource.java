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

import org.ethereum.db.ByteArrayWrapper;

import java.util.Map;
import java.util.Set;

/**
 * @author Roman Mandeleil
 * @since 18.01.2015
 */
public interface KeyValueDataSource extends DataSource {

    byte[] get(byte[] key);

    // Empty puts are allowed and interpreted as deletions
    // because empty arrays do not delete items.
    // null puts() are NOT allowed.
    byte[] put(byte[] key, byte[] value);

    void delete(byte[] key);

    Set<ByteArrayWrapper> keys();

    // Null datums are not allowed.
    // Empty datums are re-interpreted as deletions
    // this is because in levelDb empty arrays do not remove the item in databases
    // Note that updateBatch() does not imply the operation is atomic:
    // if somethings bracks, it's possible that some keys get written and some
    // others don't.
    void updateBatch(Map<ByteArrayWrapper, byte[]> rows);

    // This makes things go to disk. To enable caching.
    void flush();
}
