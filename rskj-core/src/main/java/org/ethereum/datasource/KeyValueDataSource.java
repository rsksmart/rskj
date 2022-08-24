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
import javax.annotation.Nullable;
import java.util.*;

public interface KeyValueDataSource extends DataSource {
    String DB_KIND_PROPERTIES_FILE = "dbKind.properties";
    String KEYVALUE_DATASOURCE_PROP_NAME = "keyvalue.datasource";
    String TRIE_DATASOURCE_PROP_NAME = "trie.datasource";

    @Nullable
    byte[] get(byte[] key);

    /**
     * null puts() are NOT allowed.
     *
     * @return the same value it received
     */
    byte[] put(byte[] key, byte[] value);

    void delete(byte[] key);

    Set<ByteArrayWrapper> keys();

    /**
     * Note that updateBatch() does not imply the operation is atomic:
     * if somethings breaks, it's possible that some keys get written and some
     * others don't.
     * IMPORTANT: keysToRemove override entriesToUpdate
     *
     * @param entriesToUpdate
     * @param keysToRemove
     */
    void updateBatch(Map<ByteArrayWrapper, byte[]> entriesToUpdate, Set<ByteArrayWrapper> keysToRemove);

    /**
     * This makes things go to disk. To enable caching.
     */
    void flush();

    /*
    * This method is used for logging, debugging and monitoring.
    * Implementor can retrieve any relevant information regarding the state
    * of the data source, such as number of gets, puts, etc.
    * If no imformation is available, it can return simply null.
    */
    default List<String> getStats() {
        return null;
    }

    /* This is a forced save of the cache to disk
     * Only used in testing performance to create a cache file for a certain height.
     */
    default void saveCache() { }
}
