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

import co.rsk.core.types.Uint24;
import co.rsk.crypto.Keccak256;
import org.ethereum.db.ByteArrayWrapper;

import java.util.Set;

/**
 * Every operation of a MutableTrie mutates the parent trie top node and therefore its stateRoot.
 */
public interface MutableTrie {
    Keccak256 getHash();

    byte[] get(byte[] key);

    void put(byte[] key, byte[] value);

    void put(String key, byte[] value);

    // This method optimizes cache-to-cache transfers
    void put(ByteArrayWrapper key, byte[] value);

    /////////////////////////////////////////////////////////////////////////////////
    // The semantic of deleteRecursive() is special, and not the same of delete()
    // When it is applied to a mutable trie which has a cache (such as MutableTrieCache)
    // then this is DELETE ON COMMIT, which means that changes are not applied until
    // commit() is called, and changes are applied as the last step of commit.
    // In a normal MutableTrie or Trie, changes are applied immediately.
    /////////////////////////////////////////////////////////////////////////////////
    void deleteRecursive(byte[] key);

    void save();

    void flush();

    void commit();

    void rollback();

    Set<ByteArrayWrapper> collectKeys(int size);

    MutableTrie getSnapshotTo(Keccak256 hash);

    boolean hasStore();

    Trie getTrie();

    // This is for optimizing EXTCODESIZE. It returns the size of the value
    // without the need to retrieve the value itself. Implementors can fallback to
    // getting the value and then returning its size.
    Uint24 getValueLength(byte[] key);

    // This is for optimizing EXTCODEHASH. It returns the hash digest of the value
    // without the need to retrieve the value itself. Implementors can fallback to
    // getting the value and then computing the hash.
    byte[] getValueHash(byte[] key);
}
