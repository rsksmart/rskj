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

/**
 * Implements a cache accessor to a MutableTrie, using a MultiLevelCache system.
 * IF the nested MutableTrie is a JournalTrieCache too, the same MultiLevelCache is shared across the accessors
 */
public class JournalTrieCache implements MutableTrie {

    private final TrieKeyMapper trieKeyMapper = new TrieKeyMapper();

    private MutableTrie trie;
    private ICache cache;

    /**
     * Builds a cache accessor to a MutableTrie, using a MultiLevelCache system.
     * IF the nested MutableTrie is a JournalTrieCache too, the same MultiLevelCache is shared across the accessors
     * @param parentTrie
     */
    public JournalTrieCache(MutableTrie parentTrie) {
        if (parentTrie instanceof JournalTrieCache) {
            // if parentTrie is a JournalTrieCache, then we add a MultiLevelCacheSnapshot to the same instance
            // of MultiLevelCache than the parent, with one caching level upper.
            trie = ((JournalTrieCache) parentTrie).getMutableTrie();
            cache = new MultiLevelCacheSnapshot(
                ((JournalTrieCache) parentTrie).getCache(),
                ((JournalTrieCache) parentTrie).getCacheLevel() + 1
            );
        } else {
            // if parentTrie is not a JournalTrieCache, we create a new MultiLevelCacheSnapshot looking at a
            // new MultiCacheLevel instance. Then, only one caching level for now.
            trie = parentTrie;

            cache = new MultiLevelCacheSnapshot();
        }
    }

    private MultiLevelCache getCache() {
        return ((MultiLevelCacheSnapshot) cache).getCache();
    }

    private MutableTrie getMutableTrie() {
        return trie;
    }

    private int getCacheLevel() {
        return ((MultiLevelCacheSnapshot) cache).currentLevel;
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
        cache.commitAll();
        // now everything has been merged in only one-level cache, commit the first level
        commitFirstLevel(((MultiLevelCacheSnapshot) cache).getFirstLevel());
        trie.save();
    }

    @Override
    public void commit() {
        if (cache.isFirstLevel()) {
            commitFirstLevel(cache);
        } else {
            // merge last cache level with the previous one
            cache.commit();
        }
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

    @Override
    public Trie getTrie() {
        assertNoCache();
        return trie.getTrie();
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
        ByteArrayWrapper accountWrapper = new ByteArrayWrapper(accountStoragePrefixKey);
        Map<ByteArrayWrapper, byte[]> accountItems = cache.getAccountItems(accountWrapper);
        boolean isDeletedAccount = cache.isAccountDeleted(accountWrapper);

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

    /**
     * apply a 'retriever method' for a given key, taking into account the cache if any, or gets from the nested mutable trie otherwise.
     * @param key : the key to get for
     * @param trieRetriever : a retriever method for a not cached key
     * @param cacheTransformer : a retriever method for a cached key
     * @param <T> : variable return type of the retriever methods
     * @return : returned value of the retriever method
     */
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
        return Optional.ofNullable( value == null ? null : cacheTransformer.apply(value));
    }

    /**
     * Committing the first level of the MultiLevelCache.
     * Means the nested mutable trie is effectively modified with the changed in cache
     * @param firstLevelCache
     */
    private void commitFirstLevel(ICache firstLevelCache) {
        // only one cache level: apply changes directly in parent Trie
        firstLevelCache.getDeletedAccounts().forEach(account -> trie.deleteRecursive(account.getData()));
        firstLevelCache.getUpdatedKeys().forEach(key -> {
            byte[] value = firstLevelCache.get(key);
            trie.put(key, value);
        });
        // clear the complete cache
        firstLevelCache.clear();
    }

    /**
     * Insure that there is no changes in cache anymore (ie all changes have been committed or rolled back)
     */
    private void assertNoCache() {
        if (!cache.isEmpty()) {
            throw new IllegalStateException();
        }
    }

    /**
     * A class to implement a multilevel cache system, so that the same
     *  object can record changes at different caching level
     */
    private static class MultiLevelCache {

        // The minimum cache level
        public static final int FIRST_CACHE_LEVEL = 1;
        // The current depth level of cache
        private int depth = FIRST_CACHE_LEVEL;
        // Map used to store cached value of key if changed at a given level
        private final Map<ByteArrayWrapper, Map<ByteArrayWrapper, Map<Integer, byte[]>>> valuesPerLevelPerKeyPerAccount
                = new HashMap<>();
        // Map used to store deletedAccount at each level
        private final Map<Integer, Set<ByteArrayWrapper>> deletedAccountsPerLevel = new HashMap<>();
        // Map used for more efficient way to check if a key has been cached at a given level
        private final Map<Integer, Set<ByteArrayWrapper>> updatedKeysPerLevel = new HashMap<>();
        // Map used for more efficient access to the latest cached value of a key
        private final Map<ByteArrayWrapper, Map<ByteArrayWrapper,byte[]>> latestValuesPerKeyPerAccount = new HashMap<>();
        // Set used for more efficient way to check if an account is deleted at the highest cache level
        private final Set<ByteArrayWrapper> latestDeletedAccounts = new HashSet<>();

        MultiLevelCache() {
            initLevel(depth);
        }

        /**
         * the current depth of the cache level
         * @return
         */
        int getDepth() {
            return depth;
        }

        /**
         * add a depth level to the current cache
         * @return
         */
        int getNextLevel() {
            depth++;
            initLevel(depth);
            return depth;
        }

        /**
         * set the new value for the given key in cache at the given level
         * @param key
         * @param value
         * @param cacheLevel
         */
        void put(ByteArrayWrapper key, byte[] value, int cacheLevel) {
            if (cacheLevel > depth) {
                // the current depth of the cache need to be increased
                this.depth = cacheLevel;
                initLevel(cacheLevel);
            }
            ByteArrayWrapper accountWrapper = getAccountWrapper(key);
            // if this account is in the deletedAccounts list, dont remove it, because this means that the lower
            // level is deleted
            Map<ByteArrayWrapper, Map<Integer, byte[]>> valuesPerLevelPerKey
                    = this.valuesPerLevelPerKeyPerAccount.computeIfAbsent(accountWrapper, k -> new HashMap<>());
            Map<Integer, byte[]> valuesPerLevel = valuesPerLevelPerKey.computeIfAbsent(key, k -> new HashMap<>());
            valuesPerLevel.put(cacheLevel, value);
            updatedKeysPerLevel.get(cacheLevel).add(key);
            recordLatestValue(key, value, cacheLevel);
        }

        /**
         * get the cached value of a key at a given cache level, if it has been cached at this level
         * @param key
         * @param cacheLevel
         * @return
         */
        byte[] get(ByteArrayWrapper key, int cacheLevel) {
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

        /**
         * get the cached value of a key at a given maximum level, if it has been cached at this level, or at the
         * highest level below maxLevel, if any
         * @param key
         * @param maxLevel
         * @return
         */
        byte[] getNewestValue(ByteArrayWrapper key, int maxLevel) {
            if (maxLevel >= getDepth()) {
                // if the key has never been changed or not been changed since the account is deleted, the returned value is null
                return getLatestValue(key);
            }
            ByteArrayWrapper accountWrapper = getAccountWrapper(key);
            Map<ByteArrayWrapper, Map<Integer, byte[]>> valuesPerLevelPerKey = valuesPerLevelPerKeyPerAccount.get(accountWrapper);
            for (int level = maxLevel; level >= FIRST_CACHE_LEVEL; level--) {
                // For this level,
                //  first: check if an updated key/value exist in cache
                if (valuesPerLevelPerKey != null) {
                    Map<Integer, byte[]> valuesPerLevel = valuesPerLevelPerKey.get(key);
                    if ((valuesPerLevel != null) && (valuesPerLevel.containsKey(level))) {
                        // the key exists so it has been updated at least once in cache
                        return valuesPerLevel.get(level);
                    }
                }
                // second: if no updated key/value, check if the account has been deleted
                if (isAccountDeleted(key, level)) {
                    return null;
                }
            }
            throw new UnsupportedOperationException(
                    "Key '" + new String(key.getData(), StandardCharsets.UTF_8) +
                            "' does not appear in cache.\n" +
                            " Method 'getNewestValue' shall always  be called only after checking that the key is in cache"
            );
        }

        void collectKeys(Set<ByteArrayWrapper> parentKeys, int keySize, int maxLevel) {
            // in order to manage the key deletion correctly, we need to walk over the different levels
            // starting from the oldest to the newest one
            for (int level = FIRST_CACHE_LEVEL; level <= maxLevel; level++) {
                collectKeysAtLevel(parentKeys, keySize, level);
            }
        }

        /**
         * get all the keys that have been cached at a given cache level
         * @param cacheLevel
         * @return
         */
        Set<ByteArrayWrapper> getUpdatedKeys(int cacheLevel) {
            return updatedKeysPerLevel.get(cacheLevel);
        }

        /**
         * get all the accounts that have been deleted at a given cache level
         * @param cacheLevel
         * @return
         */
        Set<ByteArrayWrapper> getDeletedAccounts(int cacheLevel) {
            return deletedAccountsPerLevel.get(cacheLevel);
        }

        /**
         * delete the given account at the given cache level
         * @param account
         * @param cacheLevel
         */
        void deleteAccount(ByteArrayWrapper account, int cacheLevel) {
            if (cacheLevel > depth) {
                this.depth = cacheLevel;
                initLevel(cacheLevel);
            }
            deletedAccountsPerLevel.get(cacheLevel).add(account);
            // clean cache for all updated key/value of this account for the same cache level
            Map<ByteArrayWrapper, Map<Integer, byte[]>> valuesPerLevelPerKey = valuesPerLevelPerKeyPerAccount.get(account);
            if (valuesPerLevelPerKey != null) {
                Set<ByteArrayWrapper> updatedKeys = updatedKeysPerLevel.get(cacheLevel);
                ArrayList<ByteArrayWrapper> emptyKeys = new ArrayList<>();
                valuesPerLevelPerKey.forEach((key, valuesPerLevel) -> {
                    updatedKeys.remove(key);
                    valuesPerLevel.remove(cacheLevel);
                    if (valuesPerLevel.size() == 0) {
                        emptyKeys.add(key);
                    }
                });
                emptyKeys.forEach(key -> valuesPerLevelPerKey.remove(key));
            }
            // we need to clean the latest values map from every change on the account keys, except those from cacheLevel to maxLevel
            recomputeLatestValues(account, cacheLevel);
        }

        /**
         * Merge cache from one level to another one
         * @param levelFrom : level from which cache shall be merged
         * @param levelTo : level to which cache shall be merged
         * @param shallClear : whether the merged level shall be cleared once merged
         */
        void commit(int levelFrom, int levelTo, boolean shallClear) {
            if (levelFrom <= levelTo) {
                throw new IllegalArgumentException(
                        "Can not merge cache. levelFrom shall be greater than levelTo. " +
                                "levelFrom=" + levelFrom + ", levelTo=" + levelTo);
            }
            for (int level = levelFrom; level > levelTo; level--) {
                Set<ByteArrayWrapper> updatedKeys = updatedKeysPerLevel.get(level);
                Set<ByteArrayWrapper> deletedAccounts = deletedAccountsPerLevel.get(level);
                int level2 = level; // required to be used in lambda expression
                deletedAccounts.forEach(account -> deleteAccount(account, level2 - 1));
                updatedKeys.forEach(key -> {
                    byte[] value = get(key, level2);
                    put(key, value, level2 - 1);
                });
                if (shallClear) {
                    clear(level);
                }
            }
            if (levelFrom >= getDepth()) {
                this.depth = levelTo;
            }
        }

        /**
         * clean the latest values map from every change on the given account keys, except those after the givan levelFrom
         * @param account
         * @param levelFrom
         */
        void recomputeLatestValues(ByteArrayWrapper account, int levelFrom) {
            // clear the map for the given account
            latestValuesPerKeyPerAccount.remove(account);
            // parse all levels in ascendant order, to refill the latest values map
            for (int level = levelFrom; level <= getDepth(); level++) {
                Set<ByteArrayWrapper> deletedAccounts = deletedAccountsPerLevel.get(level);
                Set<ByteArrayWrapper> updatedKeys = updatedKeysPerLevel.get(level);
                if (deletedAccounts.contains(account)) {
                    // clear again because account has been deleted at this level
                    latestValuesPerKeyPerAccount.remove(account);
                    latestDeletedAccounts.add(account);
                }
                // for each key updated at that level belonging to this account, record the value as latest
                for (ByteArrayWrapper updatedKey: updatedKeys) {
                    if (getAccountWrapper(updatedKey).equals(account)) {
                        recordLatestValue(updatedKey, get(updatedKey, level));
                    }
                }
            }
        }

        /**
         * recompute/update the latestValuesPerKeyPerAccount map and latestDeletedAccounts based on the
         * recorded value cached in other maps
         */
        void recomputeLatestValues() {
            latestValuesPerKeyPerAccount.clear();
            latestDeletedAccounts.clear();
            // parse all level in ascendant order, to refill the latest values map
            for (int level = FIRST_CACHE_LEVEL; level <= getDepth(); level++) {
                Set<ByteArrayWrapper> deletedAccounts = deletedAccountsPerLevel.get(level);
                Set<ByteArrayWrapper> updatedKeys = updatedKeysPerLevel.get(level);
                // for each account deleted at that level, clear all latest values recorded for this account
                for (ByteArrayWrapper deletedAccount: deletedAccounts) {
                    latestValuesPerKeyPerAccount.remove(deletedAccount);
                    latestDeletedAccounts.add(deletedAccount);
                }
                // for each key updated at that level, record the value as latest
                for (ByteArrayWrapper updatedKey: updatedKeys) {
                    recordLatestValue(updatedKey, get(updatedKey, level));
                }
            }
        }

        /**
         * Clear the cache data for the given cache level
         * @param cacheLevel
         */
        void clear(int cacheLevel) {
            if (cacheLevel == FIRST_CACHE_LEVEL) {
                // Clear all data
                valuesPerLevelPerKeyPerAccount.clear();
                deletedAccountsPerLevel.clear();
                updatedKeysPerLevel.clear();
                latestValuesPerKeyPerAccount.clear();
                latestDeletedAccounts.clear();
                initLevel(cacheLevel);
            }
		    else {
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
        }

        /**
         * Discard every changes at the given cache level
         * @param cacheLevel
         */
        void rollback(int cacheLevel) {
            clear(cacheLevel);
            if (cacheLevel == getDepth()) {
                // only if the rollbacked level is the highest one
                this.depth--;
            }
            recomputeLatestValues();
        }

        Map<ByteArrayWrapper, byte[]> getAccountItems(ByteArrayWrapper key, int cacheLevel) {
            Map<ByteArrayWrapper, byte[]> accountItems = new HashMap<>();
            ByteArrayWrapper accountWrapper = getAccountWrapper(key);
            Map<ByteArrayWrapper, Map<Integer, byte[]>> valuesPerLevelPerKey = valuesPerLevelPerKeyPerAccount.get(accountWrapper);
            if (valuesPerLevelPerKey != null) {
                valuesPerLevelPerKey.forEach((key2, valuesPerLevel) -> {
                    // Walk down through all level. If there is one level where account is deleted, stop it
                    for (int level = cacheLevel; level >= FIRST_CACHE_LEVEL; level--) {
                        if (getUpdatedKeys(level).contains(key2)) {
                            byte[] value = valuesPerLevel.get(level);
                            // even if value is null, we need to keep it in the list because it means the key has been deleted in cache
                            accountItems.put(key2, value);
                        }
                        if (isAccountDeleted(accountWrapper, level)) {
                            break;
                        }
                    }
                });
            }
            return accountItems;
        }

        /**
         * check if the given account has been deleted at the given cache level
         * @param key
         * @param cacheLevel
         * @return
         */
        boolean isAccountDeleted(ByteArrayWrapper key, int cacheLevel) {
            ByteArrayWrapper accountWrapper = getAccountWrapper(key);
            return getDeletedAccounts(cacheLevel).contains(accountWrapper);
        }

        /**
         * check if the given account has been deleted at the highest cache level and no key from this account
         * have been cached since
         * @param key
         * @return
         */
        boolean isAccountLatestDeleted(ByteArrayWrapper key) {
            ByteArrayWrapper accountWrapper = getAccountWrapper(key);
            return latestDeletedAccounts.contains(accountWrapper);
        }

        /**
         * cache the given value for the given key, only if the given cache level is the highest one,
         * or if there is no latest value already cached for this key or this is a value but cached at
         * a lower or equal level from the given one
         * @param key
         * @param value
         * @param cacheLevel
         */
        void recordLatestValue(ByteArrayWrapper key, byte[] value, int cacheLevel) {
            // If we are at the highest level, we must record in every case and we override the existing velu if any
            // Else :
            if (cacheLevel < getDepth()) {
                ByteArrayWrapper accountWrapper = getAccountWrapper(key);
                Map<ByteArrayWrapper, byte[]> latestValuesPerKey = latestValuesPerKeyPerAccount.get(accountWrapper);
                if ((latestValuesPerKey != null) && latestValuesPerKey.containsKey(key)) {
                    // If there is already a cached value for this key :
                    // we must override this existing value if and only if it comes from a cache at a lower or equal level
                    // In other words, we must override except when this is a higher level where this key has been cached or
                    // its account deleted
                    for (int level = cacheLevel + 1; level <= getDepth(); level++) {
                        Set<ByteArrayWrapper> updatedKeys = updatedKeysPerLevel.get(level);
                        if (updatedKeys.contains(key)) {
                            // do not override the latest value because a value has be cached at a higher level
                            return;
                        }
                        Set<ByteArrayWrapper> deletedAccounts = deletedAccountsPerLevel.get(level);
                        if (deletedAccounts.contains(accountWrapper)) {
                            // do not override the latest value because the account has been deleted at a higher level
                            return;
                        }
                    }
                }
            }
            recordLatestValue(key, value);
        }

        /**
         * cache the latest value for the given key
         * @param key
         * @param value
         * @param cacheLevel
         * @param updatedKeysPerLevel
         */
        void recordLatestValue(ByteArrayWrapper key, byte[] value) {
            ByteArrayWrapper accountWrapper = getAccountWrapper(key);
            Map<ByteArrayWrapper, byte[]> latestValuesPerKey = latestValuesPerKeyPerAccount.computeIfAbsent(
                    accountWrapper,
                    k -> new HashMap<>()
            );
            latestValuesPerKey.put(key, value);
            // remove account if present in latestDeletedAccounts, because we only keep in this set the accout that have
            // been deleted in cache and whose no key has been updated since
            latestDeletedAccounts.remove(accountWrapper);
        }

        /**
         * get the latest cached value for the given key
         * @param key
         * @return
         */
        byte[] getLatestValue(ByteArrayWrapper key) {
            ByteArrayWrapper accountWrapper = getAccountWrapper(key);
            Map<ByteArrayWrapper, byte[]> latestValuesPerKey = latestValuesPerKeyPerAccount.get(accountWrapper);
            if (latestValuesPerKey != null) {
                return latestValuesPerKey.get(key);
            }
            return null;
        }

        /**
         * Check if the given key has been changed in cache at the given level or a level below
         * @param key
         * @param cacheLevel
         * @return
         */
        boolean isInCache(ByteArrayWrapper key, int cacheLevel) {
            if (cacheLevel == getDepth()) {
                ByteArrayWrapper accountWrapper = getAccountWrapper(key);
                return isAccountLatestDeleted(key) || (
                        latestValuesPerKeyPerAccount.containsKey(accountWrapper) &&
                                latestValuesPerKeyPerAccount.get(accountWrapper).containsKey(key)
                );
            }
            for (int level = cacheLevel; level >= FIRST_CACHE_LEVEL; level --) {
                if (getUpdatedKeys(level).contains(key) || isAccountDeleted(key, level)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Check if there is no changed in cache anymore
         * @return
         */
        boolean isEmpty() {
            for (int level = FIRST_CACHE_LEVEL; level <= getDepth(); level++) {
                if (!getUpdatedKeys(level).isEmpty()) {
                    return false;
                }
                if (!getDeletedAccounts(level).isEmpty()) {
                    return false;
                }
                if (valuesPerLevelPerKeyPerAccount.size() > 0) {
                    return false;
                }
            }
            return true;
        }

        // This method returns a wrapper with the same content and size expected for a account key
        // when the key is from the same size than the original wrapper, it returns the same object
        private ByteArrayWrapper getAccountWrapper(ByteArrayWrapper originalWrapper) {
            byte[] key = originalWrapper.getData();
            int size = TrieKeyMapper.domainPrefix().length + TrieKeyMapper.ACCOUNT_KEY_SIZE + TrieKeyMapper.SECURE_KEY_SIZE;
            return key.length == size ? originalWrapper : new ByteArrayWrapper(Arrays.copyOf(key, size));
        }

        private void collectKeysAtLevel(Set<ByteArrayWrapper> parentKeys, int keySize, int cacheLevel) {
            getDeletedAccounts(cacheLevel).forEach(key -> parentKeys.remove(key));
            getUpdatedKeys(cacheLevel).forEach(key -> {
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

        /**
         * Initialize / clear internal maps at a given cache level
         * @param cacheLevel
         */
        private void initLevel(int cacheLevel) {
            this.deletedAccountsPerLevel.put(cacheLevel, new HashSet<>());
            this.updatedKeysPerLevel.put(cacheLevel, new HashSet<>());
        }
    }

    /**
     * An interface declaring methods to manage a cache
     */
    private interface ICache {
        /**
         * put in the current cache level, the given value at the given key
         * @param key
         * @param value
         */
        void put(ByteArrayWrapper key, byte[] value);

        /**
         * get the value in cache for the given key, only if it has been updated in this cache level.
         * To get the value in cache regardless of the level, use getNewestValue()
         * @param key
         * @return the value if the key has been updated in this cache level, otherwise null
         */
        byte[] get(ByteArrayWrapper key);

        /**
         * get the value in cache for the given key, from the last cache level when this key has been updated
         * @param key
         * @return the newest value if the key has been updated in cache,
         * or null if the key has been deleted in cache and this deletion is more recent than any updates
         * (we assume that the caller previously checked if the key is in cache before, using isInCache(),
         *  so that a 'null' returned value means a deleted key)
         */
        byte[] getNewestValue(ByteArrayWrapper key);

        /**
         * collect all the keys in cache (all levels included) with the specified size,
         * or all keys when keySize = Integer.MAX_VALUE
         * @param parentKeys : the set of collected keys
         * @param keySize : the size of the key to collect, or Integer.MAX_VALUE to collect all
         */
        void collectKeys(Set<ByteArrayWrapper> parentKeys, int keySize);

        /**
         * whether the cache is at its first level or deeper
         * @return true is at first level, false otherwise
         */
        boolean isFirstLevel();

        /**
         * Returns all the keys that have been updated in the current level of caching
         * Keys that have been deleted are not returned
         * @return
         */
        Set<ByteArrayWrapper> getUpdatedKeys();

        /**
         * Returns all the keys that have been deleted in the current level of caching
         * @return
         */
        Set<ByteArrayWrapper> getDeletedAccounts();

        /**
         *
         * @param key
         */
        void deleteAccount(ByteArrayWrapper key);

        /**
         * Merge the latest cache level with the previous one
         */
        void commit();

        /**
         * Merge every levels of cache into the first one.
         */
        void commitAll();

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

    /**
     * A class to look at a MultiLevelCache with a given caching level
     */
    private static class MultiLevelCacheSnapshot implements ICache {
        private int currentLevel;
        private MultiLevelCache cache;

        /**
         * builds a new MultiLevelCacheSnapshot looking at a new instance of MultiLevelCache (one-level caching for now)
         */
        MultiLevelCacheSnapshot() {
            this.cache = new MultiLevelCache();
            this.currentLevel = this.cache.getDepth();
        }

        /**
         * builds a new MultiLevelCacheSnapshot looking at an existing instance of MultiLevelCache, with the
         * specified caching level
         * @param cache : the nested MultiLevelCache to look at
         * @param cacheLevel : the cache level of the snapshot. It can be any level up to the nested cache depth plus one.
         */
        MultiLevelCacheSnapshot(MultiLevelCache cache, int cacheLevel) {
            if (cacheLevel > cache.getDepth() + 1) {
                throw new IllegalArgumentException("Unable to create MultiLevelCacheSnapshot with cacheLevel=" +
                        cacheLevel + " from a cache with lower depth");
            }
            // The same instance of MultiLevelCache is shared by several MultiLevelCacheSnapshot
            // Each MultiLevelCacheSnapshot look at the MultiLevelCache with its own caching level
            this.cache = cache;
            if (cacheLevel == cache.getDepth() + 1) {
                // Looking at the cache with an additional caching level
                this.currentLevel = cache.getNextLevel();
            } else {
                // or just looking at the cache with a given existing caching level.
                // This usecase happens when committing all levels at one time (see getFirstLevel())
                this.currentLevel = cacheLevel;
            }

        }

        /**
         * returns the nested MultiCacheLevel instance the Snapshot is look at
         * @return
         */
        public MultiLevelCache getCache() {
            return this.cache;
        }

        /**
         * whether the cache is at its first level or deeper
         * @return true is at first level, false otherwise
         */
        public boolean isFirstLevel() {
            return currentLevel == MultiLevelCache.FIRST_CACHE_LEVEL;
        }

        /**
         * put in the current cache level, the given value at the given key
         * @param key
         * @param value
         */
        public void put(ByteArrayWrapper key, byte[] value) {
            this.cache.put(key, value, this.currentLevel);
        }

        /**
         * get the value in cache for the given key, only if it has been updated in this cache level.
         * To get the value in cache regardless of the level, use getNewestValue()
         * @param key
         * @return the value if the key has been updated in this cache level, otherwise null
         */
        public byte[] get(ByteArrayWrapper key) {
            return this.cache.get(key, currentLevel);
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
            return this.cache.getNewestValue(key, currentLevel);
        }

        /**
         * collect all the keys in cache (all levels included) with the specified size,
         * or all keys when keySize = Integer.MAX_VALUE
         * @param parentKeys : the set of collected keys
         * @param keySize : the size of the key to collect, or Integer.MAX_VALUE to collect all
         */
        public void collectKeys(Set<ByteArrayWrapper> parentKeys, int keySize) {
            this.cache.collectKeys(parentKeys, keySize, currentLevel);
        }

        /**
         * Returns all the keys that have been updated in the current level of caching
         * Keys that have been deleted are not returned
         * @return
         */
        public Set<ByteArrayWrapper> getUpdatedKeys() {
            return this.cache.getUpdatedKeys(this.currentLevel);
        }

        /**
         * Returns all the keys that have been deleted in the current level of caching
         * @return
         */
        public Set<ByteArrayWrapper> getDeletedAccounts() {
            return this.cache.getDeletedAccounts(this.currentLevel);
        }

        /**
         * delete the specified account in the cache for the current cache level
         * @param key
         */
        public void deleteAccount(ByteArrayWrapper key) {
            this.cache.deleteAccount(key, currentLevel);
        }

        /**
         * Merge the latest cache level with the previous one
         */
        public void commit() {
            if (isFirstLevel()) {
                throw new IllegalStateException("commit() is not allowed for one level cache");
            }
            this.cache.commit(currentLevel, currentLevel-1, true);
        }

        /**
         * Merge every levels of cache into the first one.
         */
        public void commitAll() {
            if (currentLevel > MultiLevelCache.FIRST_CACHE_LEVEL) {
                // shallClear set to false to optimize, since everything will be clear() after.
                this.cache.commit(currentLevel, MultiLevelCache.FIRST_CACHE_LEVEL, false);
            }
        }

        /**
         * Clear the cache data for the current cache level
         */
        public void clear() {
            this.cache.clear(currentLevel);
        }

        /**
         * returns a map of the [key, value] updated in cache for the current cache level
         * @param key
         * @return
         */
        public Map<ByteArrayWrapper, byte[]> getAccountItems(ByteArrayWrapper key) {
            return this.cache.getAccountItems(key, currentLevel);
        }

        /**
         * Check if the specified account has been deleted in cache for the current cache level
         * @param key
         * @return
         */
        public boolean isAccountDeleted(ByteArrayWrapper key) {
            return this.cache.isAccountDeleted(key, currentLevel);
        }

        /**
         * Check if there is some changes in cache for the specified key for the current cache level
         * @param key
         * @return
         */
        public boolean isInCache(ByteArrayWrapper key) {
            return this.cache.isInCache(key, currentLevel);
        }

        /**
         * whether the cache contains some changes at any level or not
         * @return true when it contains no changes at all
         */
        public boolean isEmpty() {
            return this.cache.isEmpty();
        }

        @Override
        public void rollback() {
            this.cache.rollback(currentLevel);
        }

        /**
         * return a Snapshot of the first caching level of the nested MultiLevelCache
         * @return
         */
        public ICache getFirstLevel() {
            return new MultiLevelCacheSnapshot(cache, MultiLevelCache.FIRST_CACHE_LEVEL);
        }
    }
}
