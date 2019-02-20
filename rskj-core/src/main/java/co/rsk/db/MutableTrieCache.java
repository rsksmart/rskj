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
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.TrieKeyMapper;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class MutableTrieCache implements MutableTrie {

    private MutableTrie trie;
    // We use a single cache to mark both changed elements and removed elements.
    // null value means the element has been removed.
    private final Map<ByteArrayWrapper, CacheItem> cache;

    private final List<LogItem> logOps;

    private final Map<ByteArrayWrapper, Integer> deleteRecursiveCache;

    private int currentOrder = 0;

    public MutableTrieCache(MutableTrie parentTrie) {
        trie = parentTrie;
        cache = new HashMap<>();
        logOps = new ArrayList<>();
        deleteRecursiveCache = new HashMap<>();
    }

    @Override
    public Trie getTrie() {
        assertNoCache();
        return trie.getTrie();
    }

    @Override
    public Keccak256 getHash() {
        return trie.getHash();
    }

    @Override
    public byte[] get(byte[] key) {
        ByteArrayWrapper wrap = new ByteArrayWrapper(key);
        CacheItem cacheItem = cache.get(wrap);
        int size = TrieKeyMapper.DOMAIN_PREFIX.length + TrieKeyMapper.ACCOUNT_KEY_SIZE + TrieKeyMapper.SECURE_KEY_SIZE;
        ByteArrayWrapper deleteWrap = key.length == size ? wrap : new ByteArrayWrapper(Arrays.copyOf(key, size));
        Integer order = deleteRecursiveCache.get(deleteWrap);

        if (cacheItem != null) {
            return order == null || order < cacheItem.order ? cacheItem.value : null;
        }

        return order != null ? null : trie.get(key);
    }

    @Override
    public void put(byte[] key, byte[] value) {
        put(new ByteArrayWrapper(key), value);
    }

    // This method optimizes cache-to-cache transfers
    @Override
    public void put(ByteArrayWrapper wrap, byte[] value) {
        // If value==null, do we have the choice to either store it
        // in cache with null or in deleteCache. Here we have the choice to
        // to add it to cache with null value or to deleteCache.
        cache.put(wrap, new CacheItem(value, ++currentOrder));
        logOps.add(new LogItem(wrap, LogOp.PUT));
    }

    @Override
    public void put(String key, byte[] value) {
        byte[] keybytes = key.getBytes(StandardCharsets.UTF_8);
        put(keybytes, value);
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
        deleteRecursiveCache.put(wrap, ++currentOrder);
        logOps.add(new LogItem(wrap, LogOp.DELETE));
    }

    @Override
    public void commit() {
        // all cached items must be transferred to parent
        // some items will represent deletions (null values)
        if (deleteRecursiveCache.isEmpty()) {
            cache.forEach((key, value) -> this.trie.put(key.getData(), value.value));
        } else {
            logOps.forEach(log -> {
                if (log.logOp == LogOp.PUT) {
                    this.trie.put(log.key.getData(), cache.get(log.key).value);
                } else {
                    this.trie.deleteRecursive(log.key.getData());
                }
            });
        }

        deleteRecursiveCache.clear();
        cache.clear();
        logOps.clear();
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
        logOps.clear();
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
    public Uint24 getValueLength(byte[] key) {
        byte[] value = this.get(key);
        return value != null ? new Uint24(value.length) : Uint24.ZERO;
    }

    @Override
    public byte[] getValueHash(byte[] key) {
        byte[] value = this.get(key);
        return value != null ? Keccak256Helper.keccak256(value) : null;
    }

    private static class CacheItem {
        public final byte[] value;
        public final int order;

        public CacheItem(byte[] value, int order) {
            this.value = value;
            this.order = order;
        }
    }

    private static class LogItem {
        public final ByteArrayWrapper key;
        public final LogOp logOp;

        public LogItem(ByteArrayWrapper key, LogOp logOp) {
            this.key = key;
            this.logOp = logOp;
        }
    }

    private enum LogOp {
        PUT, DELETE
    }
}
