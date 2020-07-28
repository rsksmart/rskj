package co.rsk.db;

import co.rsk.trie.MutableTrie;
import org.ethereum.db.ByteArrayWrapper;

import java.util.Map;
import java.util.Set;

/**
 * A class to look at a MultiLevelCache with a given caching level
 */
public class MultiLevelCacheSnapshot implements MutableTrieCache.ICache {
    private int currentLevel;
    private MultiLevelCache cache;

    /**
     * builds a new MultiLevelCacheSnapshot looking at a new instance of MultiLevelCache (one-level caching for now)
     */
    public MultiLevelCacheSnapshot(MutableTrie trie) {
        this.cache = new MultiLevelCache(trie);
        this.currentLevel = this.cache.getDepth();
    }

    /**
     * builds a new MultiLevelCacheSnapshot looking at an existing instance of MultiLevelCache, with the
     * specified caching level
     *
     * @param cache      : the nested MultiLevelCache to look at
     * @param cacheLevel : the cache level of the snapshot. It can be any level up to the nested cache depth plus one.
     */
    public MultiLevelCacheSnapshot(MultiLevelCache cache, int cacheLevel) {
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
     *
     * @return
     */
    public MultiLevelCache getCache() {
        return this.cache;
    }

    /**
     * whether the cache is at its first level or deeper
     *
     * @return true is at first level, false otherwise
     */
    public boolean isFirstLevel() {
        return currentLevel == MultiLevelCache.FIRST_CACHE_LEVEL;
    }

    /**
     * returns the current level of the cache the Snapshot is looking at
     *
     * @return
     */
    public int getCurrentLevel() {
        return currentLevel;
    }

    /**
     * return a Snapshot of the first caching level of  MultiLevelCache
     *
     * @return
     */
    public MutableTrieCache.ICache getFirstLevel() {
        return new MultiLevelCacheSnapshot(cache, MultiLevelCache.FIRST_CACHE_LEVEL);
    }

    /**
     * put in the current cache level, the given value at the given key
     *
     * @param key
     * @param value
     */
    @Override
    public void put(ByteArrayWrapper key, byte[] value) {
        this.cache.put(key, value, this.currentLevel);
    }

    /**
     * get the value in cache for the given key, only if it has been updated in this cache level.
     * To get the value in cache regardless of the level, use getNewestValue()
     *
     * @param key
     * @return the value if the key has been updated in this cache level, otherwise null
     */
    @Override
    public byte[] get(ByteArrayWrapper key) {
        return this.cache.get(key, currentLevel);
    }

    /**
     * get the value in cache for the given key, from the last cache level when this key has been updated
     *
     * @param key
     * @return the newest value if the key has been updated in cache,
     * or null if the key has been deleted in cache and this deletion is more recent than any updates
     * (we assume that the caller previously checked if the key is in cache before, using isInCache(),
     * so that a 'null' returned value means a deleted key)
     */
    @Override
    public byte[] getNewestValue(ByteArrayWrapper key) {
        return this.cache.getNewestValue(key, currentLevel);
    }

    /**
     * collect all the keys in cache (all levels included) with the specified size,
     * or all keys when keySize = Integer.MAX_VALUE
     *
     * @param parentKeys : the set of collected keys
     * @param keySize    : the size of the key to collect, or Integer.MAX_VALUE to collect all
     */
    @Override
    public void collectKeys(Set<ByteArrayWrapper> parentKeys, int keySize) {
        this.cache.collectKeys(parentKeys, keySize, currentLevel);
    }

    /**
     * delete the specified account in the cache for the current cache level
     *
     * @param key
     */
    @Override
    public void deleteAccount(ByteArrayWrapper key) {
        this.cache.deleteAccount(key, currentLevel);
    }

    /**
     * If not a firstLevel cache, merge the latest cache level with the previous one
     * Otherwise, commit effectiveley in the nested mutable trie
     */
    public void commit(MutableTrie trie) {
        if (isFirstLevel()) {
            this.cache.commitFirstLevel(trie);
        } else {
            this.cache.commit(currentLevel, currentLevel - 1, true);
        }
    }

    /**
     * Merge every levels of cache into the first one.
     */
    public void commitAll(MutableTrie trie) {
        if (currentLevel > MultiLevelCache.FIRST_CACHE_LEVEL) {
            // shallClear set to false to optimize, since everything will be clear() after.
            this.cache.commit(currentLevel, MultiLevelCache.FIRST_CACHE_LEVEL, false);
        }
        this.cache.commitFirstLevel(trie);
    }

    /**
     * Clear the cache data for the current cache level
     */
    public void clear() {
        this.cache.clear(currentLevel);
    }

    /**
     * returns a map of the [key, value] updated in cache for the current cache level
     *
     * @param key
     * @return
     */
    public Map<ByteArrayWrapper, byte[]> getAccountItems(ByteArrayWrapper key) {
        return this.cache.getAccountItems(key, currentLevel);
    }

    /**
     * Check if the specified account has been deleted in cache for the current cache level
     *
     * @param key
     * @return
     */
    @Override
    public boolean isAccountDeleted(ByteArrayWrapper key) {
        return this.cache.isAccountDeleted(key, currentLevel);
    }

    /**
     * Check if there is some changes in cache for the specified key for the current cache level
     *
     * @param key
     * @return
     */
    @Override
    public boolean isInCache(ByteArrayWrapper key) {
        return this.cache.isInCache(key, currentLevel);
    }

    /**
     * whether the cache contains some changes at any level or not
     *
     * @return true when it contains no changes at all
     */
    @Override
    public boolean isEmpty() {
        return this.cache.isEmpty();
    }

    @Override
    public void rollback() {
        this.cache.rollback(currentLevel);
    }
}
