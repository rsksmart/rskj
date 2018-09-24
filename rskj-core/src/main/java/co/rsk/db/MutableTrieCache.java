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

import co.rsk.crypto.Keccak256;
import co.rsk.trie.MutableTrie;
import co.rsk.trie.Trie;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.ByteArrayWrapper;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class MutableTrieCache implements MutableTrie {

    private MutableTrie trie;
    // We use a single cache to mark both changed elements and removed elements.
    // null value means the element has been removed.
    private final HashMap<ByteArrayWrapper, byte[]> cache;

    private final Set<ByteArrayWrapper> deleteRecursiveCache;

    public MutableTrieCache(MutableTrie parentTrie) {
        trie = parentTrie;
        cache = new HashMap<>();
        deleteRecursiveCache = new HashSet<>();
    }

    @Override
    public Trie getTrie() {
        assertNoCache();
        return trie.getTrie();
    }

    @Override
    public boolean isSecure() {
        return trie.isSecure();
    }

    @Override
    public Keccak256 getHash() {
        return trie.getHash();
    }

    @Override
    public byte[] get(byte[] key) {
        // Note that after deleteRecursive without commits
        // get()s will still find the elements.
        ByteArrayWrapper wrap = new ByteArrayWrapper(key);

        // Does not contain key, check parent
        if (!cache.containsKey(wrap)) {
            return trie.get(key);
        }

        byte[] value = cache.get(wrap);

        if (value == null) {
            return null; // erased
        }

        return value;
    }

    @Override
    public void put(String key, byte[] value) {
        put(key.getBytes(StandardCharsets.UTF_8), value);
    }

    @Override
    public void put(byte[] key, byte[] value) {
        // If value==null, do we have the choice to either store it
        // in cache with null or in deleteCache. Here we have the choice to
        // to add it to cache with null value or to deleteCache.
        put(new ByteArrayWrapper(key), value);
    }

    @Override
    public void put(ByteArrayWrapper key, byte[] value) {
        cache.put(key, value);
    }

    ////////////////////////////////////////////////////////////////////////////////////
    // The semantic of implementations is special, and not the same of the MutableTrie
    // It is DELETE ON COMMIT, which means that changes are not applies until commit()
    // is called, and changes are applied last.
    ////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void deleteRecursive(byte[] key) {
        ByteArrayWrapper wrap = new ByteArrayWrapper(key);

        // Can there be wrongly unhandled interactions interactions between put() and deleteRecurse()
        // In theory, yes. In practice, never.
        // Suppose that a contract X calls a contract S.
        // Contract S calls itself with CALL.
        // Contract S suicides with SUICIDE opcode.
        // This causes a return to prev contract.
        // But the SUICIDE DOES NOT cause the storage keys to be removed YET.
        // Now parent contract S is still running, and it then can create a new storage cell
        // with SSTORE. This will be stored in the cache as a put(). The cache later receives a
        // deleteRecursive, BUT NEVER IN THE OTHER order.
        // See TransactionExecutor.finalization(), when it iterates the list with getDeleteAccounts().forEach()

        // We could iterate the puts() and remove those that have the
        // deleteCache key prefix. However we don't care much about the puts,
        // because in commit() we're going to execute the deleteRecurse() operations
        // last. However we must make sure we don't remove the "key" item,
        // as deleteRecursive() requires this item to be found on the trie.
        deleteRecursiveCache.add(wrap);
        cache.remove(wrap);
    }

    @Override
    public void commit() {
        // all cached items must be transferred to parent
        // some items will represend deletions (null values)
        for (ByteArrayWrapper item : cache.keySet()) {
            this.trie.put(item, cache.get(item));
        }

        // now remove all elements recursively
        for (ByteArrayWrapper item : deleteRecursiveCache) {
            this.trie.deleteRecursive(item.getData());
        }

        cache.clear();
        deleteRecursiveCache.clear();
    }

    @Override
    public void save() {
        commit();
        trie.save();
    }

    @Override
    public void flush() {
        trie.flush();
    }

    @Override
    public void rollback() {
        cache.clear();
        deleteRecursiveCache.clear();
    }

    @Override
    public Set<ByteArrayWrapper> collectKeys(int size) {
        Set<ByteArrayWrapper> parentSet = trie.collectKeys(size);

        // all cached items to be transferred to parent
        for (ByteArrayWrapper item : cache.keySet()) {
            if (size == Integer.MAX_VALUE || item.getData().length == size) {
                if (this.get(item.getData()) == null) {
                    parentSet.remove(item);
                } else {
                    parentSet.add(item);
                }
            }
        }

        return parentSet;
    }

    private void assertNoCache() {
        if (cache.size() != 0) {
            throw new IllegalStateException();
        }

        if (deleteRecursiveCache.size() != 0) {
            throw new IllegalStateException();
        }
    }

    @Override
    public MutableTrie getSnapshotTo(Keccak256 hash) {
        assertNoCache();
        return new MutableTrieCache(trie.getSnapshotTo(hash));
    }

    @Override
    public boolean hasStore() {
        return trie.hasStore();
    }

    @Override
    public int getValueLength(byte[] key) {
        ByteArrayWrapper wrap = new ByteArrayWrapper(key);

        if (!cache.containsKey(wrap)) {
            return trie.getValueLength(key);
        }

        // Contains key
        byte[] value = cache.get(wrap);

        if (value == null) {
            return 0; // erased
        }

        return value.length;
    }

    @Override
    public byte[] getValueHash(byte[] key) {
        ByteArrayWrapper wrap = new ByteArrayWrapper(key);

        if (!cache.containsKey(wrap)) {
            return trie.getValueHash(key);
        }

        // Contains key
        byte[] value = cache.get(wrap);

        if (value == null) {
            return null; // erased
        }

        // Note that is is inefficient because the hash is not cached
        return Keccak256Helper.keccak256(value);
    }
}
