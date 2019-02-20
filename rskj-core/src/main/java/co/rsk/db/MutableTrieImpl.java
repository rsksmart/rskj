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

package co.rsk.db;

import co.rsk.core.types.Uint24;
import co.rsk.crypto.Keccak256;
import co.rsk.trie.MutableTrie;
import co.rsk.trie.Trie;
import org.ethereum.db.ByteArrayWrapper;

import java.util.Set;

public class MutableTrieImpl implements MutableTrie {

    private Trie trie;

    public MutableTrieImpl(Trie atrie) {
        trie = atrie;
    }

    @Override
    public Trie getTrie() {
        return trie;
    }

    @Override
    public Keccak256 getHash() {
        return trie.getHash();
    }

    @Override
    public byte[] get(byte[] key) {
        return trie.get(key);
    }

    @Override
    public void put(byte[] key, byte[] value) {
        trie = trie.put(key, value);
    }

    @Override
    public void put(ByteArrayWrapper key, byte[] value) {
        trie = trie.put(key, value);
    }

    @Override
    public void put(String key, byte[] value) {
        trie = trie.put(key, value);
    }

    @Override
    public Uint24 getValueLength(byte[] key) {
        Trie atrie = trie.find(key);
        if (atrie == null) {
            return Uint24.ZERO;
        }

        return atrie.getValueLength();
    }

    @Override
    public byte[] getValueHash(byte[] key) {
        Trie atrie = trie.find(key);
        if (atrie == null) {
            return null;
        }

        return atrie.getValueHash();
    }

    @Override
    public void deleteRecursive(byte[] key) {
        trie = trie.deleteRecursive(key);
    }

    @Override
    public void save() {
        trie.save();
    }

    @Override
    public void flush() {
        trie.flush();
    }

    @Override
    public void commit() {

    }

    @Override
    public void rollback() {

    }

    @Override
    public Set<ByteArrayWrapper> collectKeys(int size) {
        return trie.collectKeys(size);
    }

    @Override
    public MutableTrie getSnapshotTo(Keccak256 hash) {
        // Since getSnapshotTo() does not modify the current trie (this.trie)
        // then there is no need to save nodes.
        return new MutableTrieImpl(trie.getSnapshotTo(hash));
    }

    @Override
    public boolean hasStore() {
        return trie.hasStore();
    }
}
