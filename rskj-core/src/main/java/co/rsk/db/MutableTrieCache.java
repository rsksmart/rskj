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

import co.rsk.core.RskAddress;
import co.rsk.core.types.ints.Uint24;
import co.rsk.crypto.Keccak256;
import co.rsk.trie.MutableTrie;
import co.rsk.trie.Trie;
import co.rsk.util.StorageKeysIteratorWithCache;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.TrieKeyMapper;
import org.ethereum.vm.DataWord;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

public class MutableTrieCache implements MutableTrie {

    private final TrieKeyMapper trieKeyMapper = new TrieKeyMapper();
    protected MutableTrie trie;
    protected ICache cache;

    public MutableTrieCache(MutableTrie parentTrie) {
        this(parentTrie, new SingleCacheImpl());
    }

    protected MutableTrieCache(MutableTrie parentTrie, ICache _cache) {
        trie = parentTrie;
        cache = _cache;
    }

    @Override
    public Trie getTrie() {
        assertNoCache();
        return trie.getTrie();
    }

    @Override
    public Keccak256 getHash() {
        assertNoCache();
        return trie.getHash();
    }

    @Nullable
    @Override
    public byte[] get(byte[] key) {
        return internalGet(key, trie::get, Function.identity()).orElse(null);
    }

    /**
     * apply a 'retriever method' for a given key, taking into account the cache if any, or gets from the nested mutable trie otherwise.
     *
     * @param key              : the key to get for
     * @param trieRetriever    : a retriever method for a not cached key
     * @param cacheTransformer : a retriever method for a cached key
     * @param <T>              : variable return type of the retriever methods
     * @return : returned value of the retriever method
     */
    protected <T> Optional<T> internalGet(
            byte[] key,
            Function<byte[], T> trieRetriever,
            Function<byte[], T> cacheTransformer) {
        ByteArrayWrapper wrapper = new ByteArrayWrapper(key);
        if (!cache.isInCache(wrapper)) {
            return Optional.ofNullable(trieRetriever.apply(key));
        }
        ByteArrayWrapper accountWrapper = new ByteArrayWrapper(key);
        byte[] value = cache.get(wrapper);
        if (cache.isAccountDeleted(wrapper) && (value == null)) {
            return Optional.empty();
        }
        return Optional.ofNullable(value == null ? null : cacheTransformer.apply(value));
    }

    @Override
    public Iterator<DataWord> getStorageKeys(RskAddress addr) {
        byte[] accountStoragePrefixKey = trieKeyMapper.getAccountStoragePrefixKey(addr);
        ByteArrayWrapper accountWrapper = MutableTrieCache.getAccountWrapper(new ByteArrayWrapper(accountStoragePrefixKey));

        boolean isDeletedAccount = cache.isAccountDeleted(accountWrapper);
        Map<ByteArrayWrapper, byte[]> accountItems = cache.getAccountItems(accountWrapper);
        if (accountItems == null && isDeletedAccount) {
            return Collections.emptyIterator();
        }

        if (isDeletedAccount) {
            // lower level is deleted, return cached items
            return new StorageKeysIteratorWithCache(Collections.emptyIterator(), accountItems, addr, trieKeyMapper);
        }

        Iterator<DataWord> storageKeys = trie.getStorageKeys(addr);
        if (accountItems == null) {
            // uncached account
            return storageKeys;
        }

        return new StorageKeysIteratorWithCache(storageKeys, accountItems, addr, trieKeyMapper);
    }

    // This method returns a wrapper with the same content and size expected for a account key
    // when the key is from the same size than the original wrapper, it returns the same object
    public static ByteArrayWrapper getAccountWrapper(ByteArrayWrapper originalWrapper) {
        byte[] key = originalWrapper.getData();
        int size = TrieKeyMapper.domainPrefix().length + TrieKeyMapper.ACCOUNT_KEY_SIZE + TrieKeyMapper.SECURE_KEY_SIZE;
        return key.length == size ? originalWrapper : new ByteArrayWrapper(Arrays.copyOf(key, size));
    }

    @Override
    public void put(byte[] key, byte[] value) {
        put(new ByteArrayWrapper(key), value);
    }

    @Override
    public void put(ByteArrayWrapper key, byte[] value) {
        // If value==null, do we have the choice to either store it
        // in cache with null or in deleteCache. Here we have the choice to
        // to add it to cache with null value or to deleteCache.
        cache.put(key, value);
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
    public void deleteRecursive(byte[] account) {
        // the key has to match exactly an account key
        // it won't work if it is used with an storage key or any other
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
        ByteArrayWrapper wrap = new ByteArrayWrapper(account);
        cache.deleteAccount(wrap);
    }

    @Override
    public void commit() {
        cache.commit(trie);
    }

    @Override
    public void save() {
        cache.commitAll(trie);
        trie.save();
    }

    @Override
    public void rollback() {
        cache.rollback();
    }

    @Override
    public Set<ByteArrayWrapper> collectKeys(int size) {
        Set<ByteArrayWrapper> parentSet = trie.collectKeys(size);
        cache.collectKeys(parentSet, size);
        return parentSet;
    }

    /**
     * Insure that there is no changes in cache anymore (ie all changes have been committed or rolled back)
     */
    protected void assertNoCache() {
        if (!cache.isEmpty()) {
            throw new IllegalStateException();
        }
    }

    @Override
    public Uint24 getValueLength(byte[] key) {
        return internalGet(key, trie::getValueLength, cachedBytes -> new Uint24(cachedBytes.length)).orElse(Uint24.ZERO);
    }

    @Override
    public Keccak256 getValueHash(byte[] key) {
        return internalGet(key, trie::getValueHash, cachedBytes -> new Keccak256(Keccak256Helper.keccak256(cachedBytes))).orElse(Keccak256.ZERO_HASH);
    }

    /**
     * An interface declaring methods to manage a cache
     */
    public interface ICache {
        /**
         * put in the current cache level, the given value at the given key
         *
         * @param key
         * @param value
         */
        void put(ByteArrayWrapper key, byte[] value);

        /**
         * get the value in cache for the given key, from the last cache level when this key has been updated
         *
         * @param key
         * @return the newest value if the key has been updated in cache,
         * or null if the key has been deleted in cache and this deletion is more recent than any updates
         * (we assume that the caller previously checked if the key is in cache before, using isInCache(),
         * so that a 'null' returned value means a deleted key)
         */
        byte[] get(ByteArrayWrapper key);

        /**
         * collect all the keys in cache (all levels included) with the specified size,
         * or all keys when keySize = Integer.MAX_VALUE
         *
         * @param parentKeys : the set of collected keys
         * @param keySize    : the size of the key to collect, or Integer.MAX_VALUE to collect all
         */
        void collectKeys(Set<ByteArrayWrapper> parentKeys, int keySize);

        /**
         * @param key
         */
        void deleteAccount(ByteArrayWrapper key);

        /**
         * Merge the latest cache level with the previous one
         */
        void commit(MutableTrie trie);

        /**
         * Merge every levels of cache into the first one.
         */
        void commitAll(MutableTrie trie);

        /**
         * Clear the cache data for the current cache level
         */
        void clear();

        Map<ByteArrayWrapper, byte[]> getAccountItems(ByteArrayWrapper key);

        boolean isAccountDeleted(ByteArrayWrapper key);

        boolean isInCache(ByteArrayWrapper key);

        boolean isEmpty();

        void rollback();
    }

}
