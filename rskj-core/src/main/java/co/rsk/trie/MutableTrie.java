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
import java.util.Iterator;
import java.util.Set;
import javax.annotation.Nullable;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.vm.DataWord;

/**
 * Every operation of a MutableTrie mutates the parent trie top node and therefore its stateRoot.
 */
public interface MutableTrie {
    Keccak256 getHash();

    @Nullable
    byte[] get(byte[] key);

    void put(byte[] key, byte[] value);

    void put(String key, byte[] value);

    // This method optimizes cache-to-cache transfers
    void put(ByteArrayWrapper key, byte[] value);

    // the key has to match exactly an account key
    // it won't work if it is used with an storage key or any other
    void deleteRecursive(byte[] key);

    void save();

    void flush();

    void commit();

    void rollback();

    // TODO(mc) this method is only used from tests
    Set<ByteArrayWrapper> collectKeys(int size);

    MutableTrie getSnapshotTo(Keccak256 hash);

    boolean hasStore();

    Trie getTrie();

    // This is for optimizing EXTCODESIZE. It returns the size of the value
    // without the need to retrieve the value itself. Implementors can fallback to
    // getting the value and then returning its size.
    Uint24 getValueLength(byte[] key);

    // the key has to match exactly an account key
    // it won't work if it is used with an storage key or any other
    Iterator<DataWord> getStorageKeys(RskAddress addr);
}
