package co.rsk.db;

import co.rsk.trie.MutableTrie;
import org.ethereum.db.ByteArrayWrapper;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * A class to implement a multilevel cache system, so that the same
 * object can record changes at different caching level
 */
public class MultiLevelCache {
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
    private final Map<ByteArrayWrapper, Map<ByteArrayWrapper, byte[]>> latestValuesPerKeyPerAccount = new HashMap<>();
    // Set used for more efficient way to check if an account is deleted at the highest cache level
    private final Set<ByteArrayWrapper> latestDeletedAccounts = new HashSet<>();

    public MultiLevelCache() {
        initLevel(depth);
    }

    /**
     * the current depth of the cache level
     *
     * @return
     */
    public int getDepth() {
        return depth;
    }

    /**
     * add a depth level to the current cache
     *
     * @return
     */
    public int getNextLevel() {
        depth++;
        initLevel(depth);
        return depth;
    }

    /**
     * set the new value for the given key in cache at the given level
     *
     * @param key
     * @param value
     * @param cacheLevel
     */
    public void put(ByteArrayWrapper key, byte[] value, int cacheLevel) {
        if (cacheLevel > depth) {
            // the current depth of the cache need to be increased
            this.depth = cacheLevel;
            initLevel(cacheLevel);
        }
        ByteArrayWrapper accountWrapper = MutableTrieCache.getAccountWrapper(key);
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
     *
     * @param key
     * @param cacheLevel
     * @return
     */
    public byte[] get(ByteArrayWrapper key, int cacheLevel) {
        ByteArrayWrapper accountWrapper = MutableTrieCache.getAccountWrapper(key);
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
     *
     * @param key
     * @param maxLevel
     * @return
     */
    public byte[] getNewestValue(ByteArrayWrapper key, int maxLevel) {
        if (maxLevel >= getDepth()) {
            // if the key has never been changed or not been changed since the account is deleted, the returned value is null
            return getLatestValue(key);
        }
        ByteArrayWrapper accountWrapper = MutableTrieCache.getAccountWrapper(key);
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

    public void collectKeys(Set<ByteArrayWrapper> parentKeys, int keySize, int maxLevel) {
        // in order to manage the key deletion correctly, we need to walk over the different levels
        // starting from the oldest to the newest one
        for (int level = FIRST_CACHE_LEVEL; level <= maxLevel; level++) {
            collectKeysAtLevel(parentKeys, keySize, level);
        }
    }

    /**
     * get all the keys that have been cached at a given cache level
     *
     * @param cacheLevel
     * @return
     */
    public Set<ByteArrayWrapper> getUpdatedKeys(int cacheLevel) {
        return updatedKeysPerLevel.get(cacheLevel);
    }

    /**
     * get all the accounts that have been deleted at a given cache level
     *
     * @param cacheLevel
     * @return
     */
    public Set<ByteArrayWrapper> getDeletedAccounts(int cacheLevel) {
        return deletedAccountsPerLevel.get(cacheLevel);
    }

    /**
     * delete the given account at the given cache level
     *
     * @param account
     * @param cacheLevel
     */
    public void deleteAccount(ByteArrayWrapper account, int cacheLevel) {
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
     *
     * @param levelFrom  : level from which cache shall be merged
     * @param levelTo    : level to which cache shall be merged
     * @param shallClear : whether the merged level shall be cleared once merged
     */
    public void commit(int levelFrom, int levelTo, boolean shallClear) {
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
     * Committing the first level of the MultiLevelCache.
     * Means the nested mutable trie is effectively modified with the changed in cache
     *
     * @param trie
     */
    public void commitFirstLevel(MutableTrie trie) {
        // only one cache level: apply changes directly in parent Trie
        getDeletedAccounts(FIRST_CACHE_LEVEL).forEach(account -> trie.deleteRecursive(account.getData()));
        getUpdatedKeys(FIRST_CACHE_LEVEL).forEach(key -> {
            byte[] value = get(key, FIRST_CACHE_LEVEL);
            trie.put(key, value);
        });
        // clear the complete cache
        clear(FIRST_CACHE_LEVEL);
    }


    /**
     * clean the latest values map from every change on the given account keys, except those after the givan levelFrom
     *
     * @param account
     * @param levelFrom
     */
    public void recomputeLatestValues(ByteArrayWrapper account, int levelFrom) {
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
            for (ByteArrayWrapper updatedKey : updatedKeys) {
                if (MutableTrieCache.getAccountWrapper(updatedKey).equals(account)) {
                    recordLatestValue(updatedKey, get(updatedKey, level));
                }
            }
        }
    }

    /**
     * recompute/update the latestValuesPerKeyPerAccount map and latestDeletedAccounts based on the
     * recorded value cached in other maps
     */
    public void recomputeLatestValues() {
        latestValuesPerKeyPerAccount.clear();
        latestDeletedAccounts.clear();
        // parse all level in ascendant order, to refill the latest values map
        for (int level = FIRST_CACHE_LEVEL; level <= getDepth(); level++) {
            Set<ByteArrayWrapper> deletedAccounts = deletedAccountsPerLevel.get(level);
            Set<ByteArrayWrapper> updatedKeys = updatedKeysPerLevel.get(level);
            // for each account deleted at that level, clear all latest values recorded for this account
            for (ByteArrayWrapper deletedAccount : deletedAccounts) {
                latestValuesPerKeyPerAccount.remove(deletedAccount);
                latestDeletedAccounts.add(deletedAccount);
            }
            // for each key updated at that level, record the value as latest
            for (ByteArrayWrapper updatedKey : updatedKeys) {
                recordLatestValue(updatedKey, get(updatedKey, level));
            }
        }
    }

    /**
     * Clear the cache data for the given cache level
     *
     * @param cacheLevel
     */
    public void clear(int cacheLevel) {
        if (cacheLevel == FIRST_CACHE_LEVEL) {
            // Clear all data
            valuesPerLevelPerKeyPerAccount.clear();
            deletedAccountsPerLevel.clear();
            updatedKeysPerLevel.clear();
            latestValuesPerKeyPerAccount.clear();
            latestDeletedAccounts.clear();
            initLevel(cacheLevel);
        } else {
            // clear all cache value for all updated keys for this cache level
            Set<ByteArrayWrapper> updatedKeys = updatedKeysPerLevel.get(cacheLevel);
            updatedKeys.forEach(key -> {
                ByteArrayWrapper accountWrapper = MutableTrieCache.getAccountWrapper(key);
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
     *
     * @param cacheLevel
     */
    public void rollback(int cacheLevel) {
        clear(cacheLevel);
        if (cacheLevel == getDepth()) {
            // only if the rollbacked level is the highest one
            this.depth--;
        }
        recomputeLatestValues();
    }

    public Map<ByteArrayWrapper, byte[]> getAccountItems(ByteArrayWrapper key, int cacheLevel) {
        Map<ByteArrayWrapper, byte[]> accountItems = new HashMap<>();
        ByteArrayWrapper accountWrapper = MutableTrieCache.getAccountWrapper(key);
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
     *
     * @param key
     * @param cacheLevel
     * @return
     */
    public boolean isAccountDeleted(ByteArrayWrapper key, int cacheLevel) {
        ByteArrayWrapper accountWrapper = MutableTrieCache.getAccountWrapper(key);
        return getDeletedAccounts(cacheLevel).contains(accountWrapper);
    }

    /**
     * check if the given account has been deleted at the highest cache level and no key from this account
     * have been cached since
     *
     * @param key
     * @return
     */
    private boolean isAccountLatestDeleted(ByteArrayWrapper key) {
        ByteArrayWrapper accountWrapper = MutableTrieCache.getAccountWrapper(key);
        return latestDeletedAccounts.contains(accountWrapper);
    }

    /**
     * cache the given value for the given key, only if the given cache level is the highest one,
     * or if there is no latest value already cached for this key or this is a value but cached at
     * a lower or equal level from the given one
     *
     * @param key
     * @param value
     * @param cacheLevel
     */
    private void recordLatestValue(ByteArrayWrapper key, byte[] value, int cacheLevel) {
        // If we are at the highest level, we must record in every case and we override the existing velu if any
        // Else :
        if (cacheLevel < getDepth()) {
            ByteArrayWrapper accountWrapper = MutableTrieCache.getAccountWrapper(key);
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
     *
     * @param key
     * @param value
     */
    private void recordLatestValue(ByteArrayWrapper key, byte[] value) {
        ByteArrayWrapper accountWrapper = MutableTrieCache.getAccountWrapper(key);
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
     *
     * @param key
     * @return
     */
    private byte[] getLatestValue(ByteArrayWrapper key) {
        ByteArrayWrapper accountWrapper = MutableTrieCache.getAccountWrapper(key);
        Map<ByteArrayWrapper, byte[]> latestValuesPerKey = latestValuesPerKeyPerAccount.get(accountWrapper);
        if (latestValuesPerKey != null) {
            return latestValuesPerKey.get(key);
        }
        return null;
    }

    /**
     * Check if the given key has been changed in cache at the given level or a level below
     *
     * @param key
     * @param cacheLevel
     * @return
     */
    public boolean isInCache(ByteArrayWrapper key, int cacheLevel) {
        if (cacheLevel == getDepth()) {
            ByteArrayWrapper accountWrapper = MutableTrieCache.getAccountWrapper(key);
            return isAccountLatestDeleted(key) || (
                    latestValuesPerKeyPerAccount.containsKey(accountWrapper) &&
                            latestValuesPerKeyPerAccount.get(accountWrapper).containsKey(key)
            );
        }
        for (int level = cacheLevel; level >= FIRST_CACHE_LEVEL; level--) {
            if (getUpdatedKeys(level).contains(key) || isAccountDeleted(key, level)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if there is no changed in cache anymore
     *
     * @return
     */
    public boolean isEmpty() {
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
     *
     * @param cacheLevel
     */
    private void initLevel(int cacheLevel) {
        this.deletedAccountsPerLevel.put(cacheLevel, new HashSet<>());
        this.updatedKeysPerLevel.put(cacheLevel, new HashSet<>());
    }

}
