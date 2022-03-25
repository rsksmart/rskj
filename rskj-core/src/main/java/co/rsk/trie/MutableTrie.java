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

package co.rsk.trie;

import co.rsk.core.RskAddress;
import co.rsk.core.types.ints.Uint24;
import co.rsk.crypto.Keccak256;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.vm.DataWord;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;

/**
 * Every operation of a MutableTrie mutates the parent trie top node and therefore its stateRoot.
 */
public interface MutableTrie {
    Keccak256 getHash();

    @Nullable
    byte[] get(byte[] key);

    void put(byte[] key, byte[] value);

    @VisibleForTesting
    void put(String key, byte[] value);

    // This method optimizes cache-to-cache transfers
    void put(ByteArrayWrapper key, byte[] value);

    // the key has to match exactly an account key
    // it won't work if it is used with an storage key or any other
    void deleteRecursive(byte[] key);

    void save();

    void commit();

    void rollback();

    /**
     * Puts a new rent timestamp for a given key (RSKIP240).
     *
     * @param key a trie key
     * @param rentTimestamp a rent timestamp (milliseconds)
     * */
    void putRentTimestamp(byte[] key, long rentTimestamp);

    @VisibleForTesting
    Set<ByteArrayWrapper> collectKeys(int size);

    Trie getTrie();

    /**
     * This is for optimizing EXTCODESIZE. It returns the size of the value
     * without the need to retrieve the value itself. Implementors can fallback to
     * getting the value and then returning its size.
     *
     * @param key a trie key
     * @return value length (ZERO if key is not present)
     * */
    Uint24 getValueLength(byte[] key);

    /**
     * This is for optimizing EXTCODEHASH. It returns the hash of the value
     * without the need to retrieve the value itself.
     *
     * @param key
     * @return value hash (empty if key is not present)
     * */
    Optional<Keccak256> getValueHash(byte[] key);

    // the key has to match exactly an account key
    // it won't work if it is used with an storage key or any other
    Iterator<DataWord> getStorageKeys(RskAddress addr);

    /**
     * Gets rent timestamp for a given key.
     * If the key is not found or not timestamped returns an empty value.
     *
     * @param key a trie key
     * @return an optional of rent timestamp (milliseconds)
     * */
    Optional<Long> getRentTimestamp(byte[] key);

    @VisibleForTesting
    MutableTrie find(byte[] key);
}
