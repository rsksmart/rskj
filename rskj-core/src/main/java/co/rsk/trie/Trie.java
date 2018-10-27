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

package co.rsk.trie;

import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import org.ethereum.db.ByteArrayWrapper;

import java.util.Set;

/**
 * Created by ajlopez on 29/03/2017.
 */
public interface Trie {
    Keccak256 getHash();

    byte[] get(byte[] key);

    //FutureFeature: ExpandedKey expandKey(byte[] key);

    //FutureFeature: byte[] get(ExpandedKey key);

    byte[] get(String key);

    Trie put(byte[] key, byte[] value);

    //FutureFeature: Trie put(ExpandedKey key, byte[] value);

    Trie put(String key, byte[] value);

    // This method optimizes cache-to-cache transfers
    Trie put(ByteArrayWrapper key, byte[] value);

    //FutureFeature: Trie delete(ExpandedKey key);

    Trie delete(byte[] key);

    Trie delete(String key);

    // This is O(1). The node with exact key "key" MUST exists.
    Trie deleteRecursive(byte[] key);

    byte[] toMessage();

    void save();

    // sends all data to disk. This applies to the store and all keys saved into the
    // store, not only to this node.
    void flush();

    //void commit();
    //void rollback();

    boolean isSecure();


    void copyTo(TrieStore target);

    int trieSize();

    Trie cloneTrie();

    // This method can only return keys whose size is multiple of 8 bits
    // Special value Integer.MAX_VALUE means collect them all.
    Set<ByteArrayWrapper> collectKeys(int byteSize);

    Set<ByteArrayWrapper> collectKeysFrom(byte[] key);

    Trie cloneTrie(byte[] newValue);

    void removeNode(int position);

    void removeValue();

    void setHash(int n, Keccak256 hash);

    Trie getSnapshotTo(Keccak256 hash);

    byte[] serialize();

    boolean hasStore();

    boolean hasLongValue();

    byte[] getValueHash();

    int getValueLength();

    byte[] getValue();

    // find allows to explore a subtree
    Trie find(byte[] key);

    // key can only be pointing to a real node, it cannot be a partial prefix betweeen
    // two nodes. In that case it will return false.
    boolean hasDataWithPrefix(byte[] key);

}
