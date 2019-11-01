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
import co.rsk.trie.TrieKeySlice;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.TrieKeyMapper;
import org.ethereum.vm.DataWord;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

public class JournalTrieCache implements MutableTrie {

    private final TrieKeyMapper trieKeyMapper = new TrieKeyMapper();

    private MutableTrie trie;
    private MultiLevelCache cache;

    public JournalTrieCache(MutableTrie parentTrie) {
        trie = parentTrie;
        cache = new MultiLevelCache();
    }

    @Override
    public Keccak256 getHash() {
        // TODO implement this mmethod
        throw new NotImplementedException();
    }

    @Nullable
    @Override
    public byte[] get(byte[] key) {
        return internalGet(key, trie::get, Function.identity()).orElse(null);
    }

    @Override
    public void put(byte[] key, byte[] value) {
        put(new ByteArrayWrapper(key), value);
    }

    @Override
    public void put(String key, byte[] value) {
        byte[] keybytes = key.getBytes(StandardCharsets.UTF_8);
        put(keybytes, value);
    }

    @Override
    public void put(ByteArrayWrapper key, byte[] value) {
        // If value==null, do we have the choice to either store it
        // in cache with null or in deleteCache. Here we have the choice to
        // to add it to cache with null value or to deleteCache.
        cache.put(key, value);
    }

    @Override
    public void deleteRecursive(byte[] account) {
        // the key has to match exactly an account key
        // it won't work if it is used with an storage key or any other
        ByteArrayWrapper wrap = new ByteArrayWrapper(account);
        cache.deleteAccount(wrap);
    }

    @Override
    public void save() {
        // TODO implement this mmethod
        throw new NotImplementedException();
    }

    @Override
    public void commit() {
        if (cache.isFirstLevel()) {
            // only one cache level: apply changes directly in parent Trie
            // TODO : manage deleted keys
            cache.getUpdatedKeys().forEach(key -> {
                byte[] value = cache.get(key);
                trie.put(key, value);
            });
            // clear the complete cache
            cache.clear();
        } else {
            // merge last cache level with the previous one
            cache.commit();
        }
    }

    @Override
    public void rollback() {
        // TODO implement this mmethod
        throw new NotImplementedException();
    }

    @Override
    public Set<ByteArrayWrapper> collectKeys(int size) {
        Set<ByteArrayWrapper> parentSet = trie.collectKeys(size);
        cache.collectKeys(parentSet, size);
        return parentSet;
    }

    @Override
    public Trie getTrie() {
        // TODO implement this mmethod
        throw new NotImplementedException();
    }

    @Override
    public Uint24 getValueLength(byte[] key) {
        return internalGet(key, trie::getValueLength, cachedBytes -> new Uint24(cachedBytes.length)).orElse(Uint24.ZERO);
    }

    @Override
    public Keccak256 getValueHash(byte[] key) {
        return internalGet(key, trie::getValueHash, cachedBytes -> new Keccak256(Keccak256Helper.keccak256(cachedBytes))).orElse(Keccak256.ZERO_HASH);
    }

    @Override
    public Iterator<DataWord> getStorageKeys(RskAddress addr) {
        byte[] accountStoragePrefixKey = trieKeyMapper.getAccountStoragePrefixKey(addr);
        Map<ByteArrayWrapper, byte[]> accountItems = cache.getAccountItems(new ByteArrayWrapper(accountStoragePrefixKey));

        // TODO : manage deleted keys
        // TODO : Is account deleted and keys deleted equivalent ?
        //  boolean isDeletedAccount = cache.getDeletedKeys().contains(key);
        //  if (accountItems == null && isDeletedAccount) {
        //     return Collections.emptyIterator();
        //  }
        //   if (isDeletedAccount) {
        //      // lower level is deleted, return cached items
        //      return new StorageKeysIterator(Collections.emptyIterator(), accountItems, addr, trieKeyMapper);
        //  }

        Iterator<DataWord> storageKeys = trie.getStorageKeys(addr);
        if (accountItems == null) {
            // uncached account
            return storageKeys;
        }

        return new StorageKeysIterator(storageKeys, accountItems, addr, trieKeyMapper);
    }

    private <T> Optional<T> internalGet(
            byte[] key,
            Function<byte[], T> trieRetriever,
            Function<byte[], T> cacheTransformer) {
        ByteArrayWrapper wrapper = new ByteArrayWrapper(key);
        if (!cache.isInCache(wrapper)) {
            return Optional.ofNullable(trieRetriever.apply(key));
        }
        byte[] value = cache.getNewestValue(wrapper);
        // A null value means the key has been deleted
        return Optional.ofNullable(cacheTransformer.apply(value));
    }

    private static class StorageKeysIterator implements Iterator<DataWord> {
        private final Iterator<DataWord> keysIterator;
        private final Map<ByteArrayWrapper, byte[]> accountItems;
        private final RskAddress address;
        private final int storageKeyOffset = (
                TrieKeyMapper.domainPrefix().length +
                        TrieKeyMapper.SECURE_ACCOUNT_KEY_SIZE +
                        TrieKeyMapper.storagePrefix().length +
                        TrieKeyMapper.SECURE_KEY_SIZE)
                * Byte.SIZE;
        private final TrieKeyMapper trieKeyMapper;
        private DataWord currentStorageKey;
        private Iterator<Map.Entry<ByteArrayWrapper, byte[]>> accountIterator;

        StorageKeysIterator(
                Iterator<DataWord> keysIterator,
                Map<ByteArrayWrapper, byte[]> accountItems,
                RskAddress addr,
                TrieKeyMapper trieKeyMapper) {
            this.keysIterator = keysIterator;
            this.accountItems = new HashMap<>(accountItems);
            this.address = addr;
            this.trieKeyMapper = trieKeyMapper;
        }

        @Override
        public boolean hasNext() {
            if (currentStorageKey != null) {
                return true;
            }

            while (keysIterator.hasNext()) {
                DataWord item = keysIterator.next();
                ByteArrayWrapper fullKey = getCompleteKey(item);
                if (accountItems.containsKey(fullKey)) {
                    byte[] value = accountItems.remove(fullKey);
                    if (value == null){
                        continue;
                    }
                }
                currentStorageKey = item;
                return true;
            }

            if (accountIterator == null) {
                accountIterator = accountItems.entrySet().iterator();
            }

            while (accountIterator.hasNext()) {
                Map.Entry<ByteArrayWrapper, byte[]> entry = accountIterator.next();
                byte[] key = entry.getKey().getData();
                if (entry.getValue() != null && key.length * Byte.SIZE > storageKeyOffset) {
                    // cached account key
                    currentStorageKey = getPartialKey(key);
                    return true;
                }
            }

            return false;
        }

        private DataWord getPartialKey(byte[] key) {
            TrieKeySlice nodeKey = TrieKeySlice.fromKey(key);
            byte[] storageExpandedKeySuffix = nodeKey.slice(storageKeyOffset, nodeKey.length()).encode();
            return DataWord.valueOf(storageExpandedKeySuffix);
        }

        private ByteArrayWrapper getCompleteKey(DataWord subkey) {
            byte[] secureKeyPrefix = trieKeyMapper.getAccountStorageKey(address, subkey);
            return new ByteArrayWrapper(secureKeyPrefix);
        }

        @Override
        public DataWord next() {
            if (currentStorageKey == null && !hasNext()) {
                throw new NoSuchElementException();
            }

            DataWord next = currentStorageKey;
            currentStorageKey = null;
            return next;
        }
    }

    private class MultiLevelCache {

        static final int FIRST_CACHE_LEVEL = 1;
        int currentLevel = FIRST_CACHE_LEVEL;
        private final Map<ByteArrayWrapper, Map<ByteArrayWrapper, Map<Integer, byte[]>>> valuesPerLevelPerKeyPerAccount
                = new HashMap<>();
        private final Map<Integer, Set<ByteArrayWrapper>> deletedAccountsPerLevel = new HashMap<>();
        private final Map<Integer, Set<ByteArrayWrapper>> updatedKeysPerLevel = new HashMap<>();

        public MultiLevelCache() {
            this.initLevel(this.currentLevel);
        }

        /**
         * put in the current cache level, the given value at the given key
         * @param key
         * @param value
         */
        public void put(ByteArrayWrapper key, byte[] value) {
            this.put(key, value, this.currentLevel);
        }

        /**
         * get the value in cache for the given key, only if it has been updated in this cache level.
         * To get the value in cache regardless of the level, use getNewestValue()
         * @param key
         * @return the value if the key has been updated in this cache level, otherwise null
         */
        public byte[] get(ByteArrayWrapper key) {
            return get(key, currentLevel);
        }

        /**
         * get the value in cache for the given key, from the last cache level when this key has been updated
         * @param key
         * @return the newest value if the key has been updated in cache,
         * or null if the key has been deleted in cache and this deletion is more recent than any updates
         * (we assume that the caller previously checked if the key is in cache before, using isInCache(),
         *  so that a 'null' returned value means a deleted key)
         */
        // We assume this methode
        public byte[] getNewestValue(ByteArrayWrapper key) {
            ByteArrayWrapper accountWrapper = getAccountWrapper(key);
            Map<ByteArrayWrapper, Map<Integer, byte[]>> valuesPerLevelPerKey = valuesPerLevelPerKeyPerAccount.get(accountWrapper);
            for (int level = currentLevel; level >= FIRST_CACHE_LEVEL; level--) {
                if (isAccountDeleted(key, level)) {
                    return null;
                }
                if (valuesPerLevelPerKey != null) {
                    Map<Integer, byte[]> valuesPerLevel = valuesPerLevelPerKey.get(key);
                    if (valuesPerLevel != null) {
                        // the key exists so it has been updated at least once in cache
                        if (valuesPerLevel.containsKey(level)) {
                            return valuesPerLevel.get(level);
                        }
                    }
                }
            }
            throw new UnsupportedOperationException(
                    "Key '" + new String(key.getData(), StandardCharsets.UTF_8) +
                            "' does not appear in cache.\n" +
                            " Method 'getNewestValue' shall always  be called only after checking that the key is in cache"
            );
        }

        /**
         * collect all the keys in cache (all levels included) with the specified size,
         * or all keys when keySize = Integer.MAX_VALUE
         * @param parentKeys : the set of collected keys
         * @param keySize : the size of the key to collect, or Integer.MAX_VALUE to collect all
         */
        public void collectKeys(Set<ByteArrayWrapper> parentKeys, int keySize) {
            // in order to manage the key deletion correctly, we need to walk over the different levels
            // starting from the oldest to the newest one
            for (int level = FIRST_CACHE_LEVEL; level <= currentLevel; level++) {
                collectKeys(parentKeys, keySize, FIRST_CACHE_LEVEL);
            }
        }

        /**
         * whether the cache is at its first level or deeper
         * @return true is at first level, false otherwise
         */
        public boolean isFirstLevel() {
            return currentLevel == FIRST_CACHE_LEVEL;
        }

        /**
         * Returns all the keys that have been updated in the current level of caching
         * Keys that have been deleted are not returned
         * @return
         */
        public Set<ByteArrayWrapper> getUpdatedKeys() {
            return getUpdatedKeys(this.currentLevel);
        }

        /**
         * Returns all the keys that have been deleted in the current level of caching
         * @return
         */
        public Set<ByteArrayWrapper> getDeletedAccounts() {
            return getDeletedAccounts(this.currentLevel);
        }

        /**
         *
         * @param key
         */
        public void deleteAccount(ByteArrayWrapper key) {
            deleteAccount(key, currentLevel);
        }

        /**
         * Merge the latest cache level with the previous one
         */
        public void commit() {
            if (isFirstLevel()) throw new IllegalStateException("commit() is not allowed for one level cache"); // shall never be called if not first level
            commit(currentLevel, currentLevel-1, true);
        }

        /**
         * Merge every levels of cache into the first one.
         */
        public void commitAll() {
            if (currentLevel > FIRST_CACHE_LEVEL) {
                // shallClear set to false to optimize, since everything will be clear() after.
                commit(currentLevel, FIRST_CACHE_LEVEL, false);
            }
        }

        /**
         * Clear the cache data for the current cache level
         */
        public void clear() {
            if (isFirstLevel()) {
                valuesPerLevelPerKeyPerAccount.clear();
                deletedAccountsPerLevel.clear();
                updatedKeysPerLevel.clear();
                initLevel(currentLevel);
            }
		    else {
                clear(currentLevel);
            }
        }

        public Map<ByteArrayWrapper, byte[]> getAccountItems(ByteArrayWrapper key) {
            Map<ByteArrayWrapper, byte[]> accountItems = new HashMap<>();
            ByteArrayWrapper accountWrapper = getAccountWrapper(key);
            Map<ByteArrayWrapper, Map<Integer, byte[]>> valuesPerLevelPerKey = valuesPerLevelPerKeyPerAccount.get(accountWrapper);
            if (valuesPerLevelPerKey != null) {
                valuesPerLevelPerKey.forEach((key2, valuesPerLevel) -> {
                    // TODO : not sure here if we need to walk over all levels or only get from current one
                    byte[] value = valuesPerLevel.get(currentLevel);
                    // even if value is null, we need to keep it in the list because it means the key has been deleted in cache
                    accountItems.put(key2, value);
                });
            }
            return accountItems;
        }

        public boolean isAccountDeleted(ByteArrayWrapper key) {
            return isAccountDeleted(key, currentLevel);
        }

        public boolean isInCache(ByteArrayWrapper key) {
            for (int level = currentLevel; level >= FIRST_CACHE_LEVEL; level --) {
                if (getUpdatedKeys(level).contains(key) || isAccountDeleted(key, level)) {
                    return true;
                }
            }
            return false;
        }

        // This method returns a wrapper with the same content and size expected for a account key
        // when the key is from the same size than the original wrapper, it returns the same object
        private ByteArrayWrapper getAccountWrapper(ByteArrayWrapper originalWrapper) {
            byte[] key = originalWrapper.getData();
            int size = TrieKeyMapper.domainPrefix().length + TrieKeyMapper.ACCOUNT_KEY_SIZE + TrieKeyMapper.SECURE_KEY_SIZE;
            return key.length == size ? originalWrapper : new ByteArrayWrapper(Arrays.copyOf(key, size));
        }

        private void initLevel(int cacheLevel) {
            this.deletedAccountsPerLevel.put(cacheLevel, new HashSet<>());
            this.updatedKeysPerLevel.put(cacheLevel, new HashSet<>());
        }

        /**
         * Merge cache from one level to another one
         * @param levelFrom : level from which cache shall be merged
         * @param levelTo : level to which cache shall be merged
         * @param shallClear : whether the merged level shall be cleared once merged
         */
        private void commit(int levelFrom, int levelTo, boolean shallClear) {
            if (levelFrom <= levelTo) {
                throw new IllegalArgumentException(
                        "Can not merge cache. levelFrom shall be greater than levelTo." +
                                "levelFrom=" + levelFrom + ", levelTo=" + levelTo);
            }
            for (int level = levelFrom; level > levelTo; level--) {
                // TODO manage deleted keys
                Set<ByteArrayWrapper> updatedKeys = updatedKeysPerLevel.get(level);
                int level2 = level; // required to be used in lambda expression
                updatedKeys.forEach(key -> {
                    byte[] value = get(key, level2);
                    // Optimization: put directly in the targeted level, instead of merging at each level
                    put(key, value, levelTo);
                });
                if (shallClear) {
                    clear(level);
                }
            }
        }

        private void clear(int cacheLevel) {
            // clear all cache value for all updated keys for this cache level
            Set<ByteArrayWrapper> updatedKeys = updatedKeysPerLevel.get(cacheLevel);
            updatedKeys.forEach(key -> {
                ByteArrayWrapper accountWrapper = getAccountWrapper(key);
                Map<ByteArrayWrapper, Map<Integer, byte[]>> valuesPerLevelPerKey
                        = this.valuesPerLevelPerKeyPerAccount.computeIfAbsent(accountWrapper, k -> new HashMap<>());
                if (valuesPerLevelPerKey != null) {
                    Map<Integer, byte[]> valuesPerLevel = valuesPerLevelPerKey.get(key);
                    if (valuesPerLevel != null) {
                        valuesPerLevel.remove(cacheLevel);
                    }
                }
            });
            // clear the deletedKeys and updatedKeys for this cache level
            initLevel(cacheLevel);
        }

        private void put(ByteArrayWrapper key, byte[] value, int cacheLevel) {
            ByteArrayWrapper accountWrapper = getAccountWrapper(key);
            // if this account is in the deletedAccounts list, remove it
            deletedAccountsPerLevel.get(cacheLevel).remove(accountWrapper);
            Map<ByteArrayWrapper, Map<Integer, byte[]>> valuesPerLevelPerKey
                    = this.valuesPerLevelPerKeyPerAccount.computeIfAbsent(accountWrapper, k -> new HashMap<>());
            Map<Integer, byte[]> valuesPerLevel = valuesPerLevelPerKey.computeIfAbsent(key, k -> new HashMap<>());
            valuesPerLevel.put(cacheLevel, value);
            updatedKeysPerLevel.get(cacheLevel).add(key);
        }

        private byte[] get(ByteArrayWrapper key, int cacheLevel) {
            ByteArrayWrapper accountWrapper = getAccountWrapper(key);
            Map<ByteArrayWrapper, Map<Integer, byte[]>> valuesPerLevelPerKey = valuesPerLevelPerKeyPerAccount.get(accountWrapper);
            if (valuesPerLevelPerKey != null) {
                Map<Integer, byte[]> valuesPerLevel = valuesPerLevelPerKey.get(key);
                if (valuesPerLevel != null) {
                    return valuesPerLevel.get(cacheLevel);
                }
            }
            // TODO Prefer Optional<T> over null
            return null;
        }

        private void collectKeys(Set<ByteArrayWrapper> parentKeys, int keySize, int cacheLevel) {
            // TODO manage delete correctly
            //            for key in getDeletedKeys(cacheLevel)
            //            if parentKeys.containsKey(key)
            //            parentKeys.remove(key)
            getUpdatedKeys(cacheLevel).forEach((key) -> {
                if (keySize == Integer.MAX_VALUE || key.getData().length == keySize) {
                    byte[] value = get(key, cacheLevel);
                    if (value == null) { // it means delete the key
                        parentKeys.remove(key);
                    } else {
                        parentKeys.add(key);
                    }
                }
            });
        }

        private Set<ByteArrayWrapper> getUpdatedKeys(int cacheLevel) {
            return updatedKeysPerLevel.get(cacheLevel);
        }

        private Set<ByteArrayWrapper> getDeletedAccounts(int cacheLevel) {
            return deletedAccountsPerLevel.get(cacheLevel);
        }

        private void deleteAccount(ByteArrayWrapper account, int cacheLevel) {
            deletedAccountsPerLevel.get(cacheLevel).add(account);
            // clean cache for all keys of this account
            Map<ByteArrayWrapper, Map<Integer, byte[]>> valuesPerLevelPerKey = valuesPerLevelPerKeyPerAccount.remove(account);
            if (valuesPerLevelPerKey != null) {
                Set<ByteArrayWrapper> updatedKeys = updatedKeysPerLevel.get(cacheLevel);
                valuesPerLevelPerKey.forEach((key, value) -> updatedKeys.remove(key));
            }
        }

        private boolean isAccountDeleted(ByteArrayWrapper key, int cacheLevel) {
            ByteArrayWrapper accountWrapper = getAccountWrapper(key);
            return getDeletedAccounts(cacheLevel).contains(accountWrapper);
        }

    }
}
