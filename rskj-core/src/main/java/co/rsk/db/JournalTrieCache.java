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
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.vm.DataWord;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class JournalTrieCache implements MutableTrie {

    private MutableTrie trie;
    private MultiLevelCache cache;

    public JournalTrieCache(MutableTrie parentTrie) {
        trie = parentTrie;
        cache = new MultiLevelCache();
    }

    @Override
    public Keccak256 getHash() {
        return null;
    }

    @Nullable
    @Override
    public byte[] get(byte[] key) {
        return new byte[0];
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
    public void deleteRecursive(byte[] key) {

    }

    @Override
    public void save() {

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

    }

    @Override
    public Set<ByteArrayWrapper> collectKeys(int size) {
        Set<ByteArrayWrapper> parentSet = trie.collectKeys(size);
        cache.collectKeys(parentSet, size);
        return parentSet;
    }

    @Override
    public Trie getTrie() {
        return null;
    }

    @Override
    public Uint24 getValueLength(byte[] key) {
        return null;
    }

    @Override
    public Keccak256 getValueHash(byte[] key) {
        return null;
    }

    @Override
    public Iterator<DataWord> getStorageKeys(RskAddress addr) {
        return null;
    }

    private class MultiLevelCache {

        static final int FIRST_CACHE_LEVEL = 1;
        int currentLevel = FIRST_CACHE_LEVEL;
        private final Map<ByteArrayWrapper, Map<Integer, byte[]>> valuesPerLevelPerKey = new HashMap<>();
        private final Map<Integer, Set<ByteArrayWrapper>> deletedKeysPerLevel = new HashMap<>();
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
         * Merge the latest cache level with the previous one
         */
        public void commit() {


        }

        /**
         * Clear the cache data for the current cache level
         */
        public void clear() {
            if (isFirstLevel()) {
                valuesPerLevelPerKey.clear();
                deletedKeysPerLevel.clear();
                updatedKeysPerLevel.clear();
                initLevel(currentLevel);
            }
		    else {
                clear(currentLevel);
            }
        }

        private void initLevel(int cacheLevel) {
            this.deletedKeysPerLevel.put(cacheLevel, new HashSet<>());
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
                Map<Integer, byte[]> valuesPerLevel = valuesPerLevelPerKey.get(key);
                if (valuesPerLevel != null) {
                    valuesPerLevel.remove(cacheLevel);
                }
            });
            // clear the deletedKeys and updatedKeys for this cache level
            initLevel(cacheLevel);
        }

        private void put(ByteArrayWrapper key, byte[] value, int cacheLevel) {
            Map<Integer, byte[]> valuesPerLevel = this.valuesPerLevelPerKey.computeIfAbsent(key, k -> new HashMap<>());
            valuesPerLevel.put(cacheLevel, value);
            updatedKeysPerLevel.get(cacheLevel).add(key);
        }

        private byte[] get(ByteArrayWrapper key, int cacheLevel) {
            Map<Integer, byte[]> valuesPerLevel = valuesPerLevelPerKey.get(key);
            if (valuesPerLevel != null) {
                return valuesPerLevel.get(cacheLevel);
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

    }
}
