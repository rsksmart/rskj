package co.rsk.db;

import co.rsk.trie.MutableTrie;
import org.ethereum.db.ByteArrayWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private MutableTrie trie;
    // Map used to store original values of a key if changed at a given level
    private final Map<Integer, Map<ByteArrayWrapper, Map<ByteArrayWrapper, byte[]>>> originalValuesPerKeyPerAccountPerLevel = new HashMap<>();
    // Map used to store last cached value of key (whatever if changed or not)
    private final Map<ByteArrayWrapper, Map<ByteArrayWrapper, byte[]>> valuesPerKeyPerAccount = new HashMap<>();
    // Map used to store the deepest (= highest) cache level where each key has been modified
    private final Map<ByteArrayWrapper, Map<ByteArrayWrapper, Integer>> deepestLevelPerKeyPerAccount = new HashMap<>();
    // Map used to store at which level(s) the accounts are deleted
    private final Map<ByteArrayWrapper, Set<Integer>> deletedAccounts = new HashMap<>();

    public MultiLevelCache(MutableTrie trie) {
        this.trie = trie;
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
        }
        ByteArrayWrapper accountWrapper = MutableTrieCache.getAccountWrapper(key);
        Map<ByteArrayWrapper, byte[]> originalValuesPerKey
                = findOriginalValuesPerKey(accountWrapper, cacheLevel, true);
        Map<ByteArrayWrapper, Integer> deepestLevelPerKey = this.deepestLevelPerKeyPerAccount.computeIfAbsent(accountWrapper, k -> new HashMap<>());
        byte[] originalValue = null;
        // operation done at the deepest cache level or check the key has not been already updated at a deeper level
        if ((cacheLevel == getDepth()) || (getCachedKeyDeepestLevel(key, deepestLevelPerKey) <= cacheLevel)) {
            // put value in latestValues map and return the originalValue for the key
            originalValue = this.putLatestCachedValue(accountWrapper, key, value, originalValuesPerKey);
            // set our level as the deepest level for this key
            if (deepestLevelPerKey != null) { // always true after computeIfAbsent but sonar complains ...
                deepestLevelPerKey.put(key, cacheLevel);
            }
        } else {
            // the key has been modified at some deeper level(s) -> find the original value stored looping over the deeper levels
            Map<ByteArrayWrapper, byte[]> originalValuesPerKeyForLevel
                    = findOriginalValuesPerKeyAtDeeperLevel(accountWrapper, key, cacheLevel, getCachedKeyDeepestLevel(key, deepestLevelPerKey));
            // get the originalValue stored at deeper level
            originalValue = originalValuesPerKeyForLevel.get(key);
            // change that original value to the new value
            originalValuesPerKeyForLevel.put(key, value);
        }
        // set the original value at our level
        originalValuesPerKey.put(key, originalValue);
    }


    /**
     * get the cached value of a key at a given maximum level, if it has been cached at this level, or at the
     * highest level below maxLevel, if any
     *
     * @param key
     * @param maxLevel
     * @return
     */
    public byte[] get(ByteArrayWrapper key, int maxLevel) {
        ByteArrayWrapper accountWrapper = MutableTrieCache.getAccountWrapper(key);
        if (maxLevel == getDepth()) {
            return this.getLatestCachedValue(accountWrapper, key, maxLevel);
        }
        // else we need to find whether the key has been updated at a deeper level
        Map<ByteArrayWrapper, Integer> deepestLevelPerKey = this.deepestLevelPerKeyPerAccount.computeIfAbsent(accountWrapper, k -> new HashMap<>());
        int deepestLevel = getCachedKeyDeepestLevel(key, deepestLevelPerKey);
        if (deepestLevel <= maxLevel) {
            // key has not been modified at a deeper level
            return this.getLatestCachedValue(accountWrapper, key, maxLevel);
        } else {
            // the key has been modified at some deeper level(s) -> find the original value stored looping over the deeper levels
            Map<ByteArrayWrapper, byte[]> originalValuesPerKeyForLevel
                    = findOriginalValuesPerKeyAtDeeperLevel(accountWrapper, key, maxLevel, deepestLevel);
            // return the original value found at the first deeper level
            return originalValuesPerKeyForLevel.get(key);
        }
    }

    /**
     * collect all the keys in cache (all levels included) with the specified size,
     *      or all keys when keySize = Integer.MAX_VALUE
     * @param parentKeys
     * @param keySize
     * @param maxLevel
     */
    public void collectKeys(Set<ByteArrayWrapper> parentKeys, int keySize, int maxLevel) {
        getDeletedAccounts(maxLevel).forEach(key -> parentKeys.remove(key));
        this.valuesPerKeyPerAccount.forEach((account, valuesPerKey) -> {
            if (valuesPerKey.size() > 0) {
                Map<ByteArrayWrapper, Integer> deepestLevelPerKey = this.deepestLevelPerKeyPerAccount.get(account);
                if (deepestLevelPerKey == null) {
                    throw new IllegalStateException(
                            String.format(
                                    "ERROR: Unable to find the deepest level Map for account %s",
                                    account.toString()));
                }
                valuesPerKey.forEach((key, value) -> this.collectKey(key, parentKeys, keySize, maxLevel));
            }
        });
    }

    /**
     * get all the accounts that have been deleted at a given cache level
     *
     * @param cacheLevel
     * @return
     */
    public Set<ByteArrayWrapper> getDeletedAccounts(int cacheLevel) {
        return getDeletedAccounts(0, cacheLevel);
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
        }
        // add the current level in the list of levels where this account is deleted
        ByteArrayWrapper accountWrapper = MutableTrieCache.getAccountWrapper(account);
        Set<Integer> deletedAccountsSet = deletedAccounts.computeIfAbsent(account, k -> new HashSet<>());
        deletedAccountsSet.add(cacheLevel);

        Map<ByteArrayWrapper, byte[]> valuesPerKey
                = this.valuesPerKeyPerAccount.computeIfAbsent(accountWrapper, k -> new HashMap<>());
        // for each key in global cached map, set value = null.
        valuesPerKey.forEach((key, value) -> this.put(key, null, cacheLevel));
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
        this.commitDeletedAccounts(levelFrom, levelTo);

        Map<ByteArrayWrapper, Map<ByteArrayWrapper, byte[]>> originalValuesPerKeyPerAccountTarget
                = this.originalValuesPerKeyPerAccountPerLevel.computeIfAbsent(levelTo, k -> new HashMap<>());
        for (int level = levelTo + 1; level <= levelFrom; level++) {
            // commit value of each key updated at each level from levelTo+1 to levelFrom,
            this.commitCachedValuesAtLevel(level, levelTo, originalValuesPerKeyPerAccountTarget);
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

        // for each account deleted at level FIRST_CACHE_LEVEL, remove the keys from the trie
        getDeletedAccounts(0, FIRST_CACHE_LEVEL).forEach(account -> trie.deleteRecursive(account.getData()));
        // only one cache level: apply changes directly in parent Trie
        if (getDepth() == FIRST_CACHE_LEVEL) {
            this.valuesPerKeyPerAccount.forEach(
                    (account, valuesPerKey) -> valuesPerKey.forEach(
                            (key, value) -> trie.put(key, value)
                    )
            );
        } else {
            this.valuesPerKeyPerAccount.forEach(
                    (account, valuesPerKey) -> valuesPerKey.forEach(
                            (key, value) -> {
                    int deepestLevel = getCachedKeyDeepestLevel(key, account);
                    if (deepestLevel <= FIRST_CACHE_LEVEL) {
                        trie.put(key, value);
                    } else {
                        // the key has been modified at some deeper level(s) -> find the original value stored looping over the deeper levels
                        Map<ByteArrayWrapper, byte[]> originalValuesPerKeyForLevel
                                = findOriginalValuesPerKeyAtDeeperLevel(account, key, FIRST_CACHE_LEVEL, deepestLevel);
                        trie.put(key, originalValuesPerKeyForLevel.get(key));
                    }
                }));
        }

        getDeletedAccounts(FIRST_CACHE_LEVEL).forEach(account -> trie.deleteRecursive(account.getData()));
        // clear the complete cache
        clear(FIRST_CACHE_LEVEL);
    }

    /**
     * Clear the cache data for the given cache level
     *
     * @param cacheLevel
     */
    public void clear(int cacheLevel) {
        if (cacheLevel == FIRST_CACHE_LEVEL) {
            // Clear all data
            originalValuesPerKeyPerAccountPerLevel.clear();
            deletedAccounts.clear();
            deepestLevelPerKeyPerAccount.clear();
            valuesPerKeyPerAccount.clear();
        } else {
            this.clearDeletedAccounts(cacheLevel);
            this.clearUpdatedValuesAtLevel(cacheLevel);
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
    }

    /**
     * returns a map of the [key, value] updated in cache for the given cache level
     * @param account
     * @param cacheLevel
     * @return
     */
    public Map<ByteArrayWrapper, byte[]> getAccountItems(ByteArrayWrapper account, int cacheLevel) {
        Map<ByteArrayWrapper, byte[]> accountItems = new HashMap<>();
        ByteArrayWrapper accountWrapper = MutableTrieCache.getAccountWrapper(account);

        Map<ByteArrayWrapper, byte[]> valuesPerKey = this.valuesPerKeyPerAccount.get(accountWrapper);
        if (valuesPerKey != null) {
            valuesPerKey.forEach((key, value) -> accountItems.put(key, this.get(key, cacheLevel)));
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
     * Check if the given key has been changed in cache at the given level or a level below
     *
     * @param key
     * @param cacheLevel
     * @return
     */
    public boolean isInCache(ByteArrayWrapper key, int cacheLevel) {
        ByteArrayWrapper accountWrapper = MutableTrieCache.getAccountWrapper(key);
        return this.findDeepestLevel(accountWrapper, key, cacheLevel) != -1;
    }

    /**
     * Check if there is no changed in cache anymore
     *
     * @return
     */
    public boolean isEmpty() {
        if (originalValuesPerKeyPerAccountPerLevel.size() > 0) {
            return false;
        }
        if (deletedAccounts.size() > 0) {
            return false;
        }
        return deepestLevelPerKeyPerAccount.size() <= 0;
    }

    /**
     * returns the deepestLevel for a key from the deepestLevel map, otherwise -1
     * @param accountWrapper
     * @param key
     * @return
     */
    private int getCachedKeyDeepestLevel(ByteArrayWrapper accountWrapper, ByteArrayWrapper key) {
        Map<ByteArrayWrapper, Integer> deepestLevelPerKey = this.deepestLevelPerKeyPerAccount.computeIfAbsent(accountWrapper, k -> new HashMap<>());
        return getCachedKeyDeepestLevel(key, deepestLevelPerKey);
    }

    /**
     * returns the deepestLevel for a key from the deepestLevel map, otherwise -1
     * @param key
     * @param deepestLevelPerKey
     * @return
     */
    private int getCachedKeyDeepestLevel(ByteArrayWrapper key, Map<ByteArrayWrapper, Integer> deepestLevelPerKey) {
        if ((deepestLevelPerKey != null) && deepestLevelPerKey.containsKey(key)) {
            return deepestLevelPerKey.get(key);
        } else {
            return -1;
        }
    }

    /**
     * retrieves the deepest Level where a key has been modified in cache, starting from the specified maxLevel (and going down until found)
     * @param account
     * @param key
     * @param maxLevel
     * @return
     */
    private int findDeepestLevel(ByteArrayWrapper account, ByteArrayWrapper key, int maxLevel) {
        for (int level = maxLevel; level >= FIRST_CACHE_LEVEL; level--) {
            Map<ByteArrayWrapper, Map<ByteArrayWrapper, byte[]>> originalValuesPerKeyPerAccountAtLevel
                    = this.originalValuesPerKeyPerAccountPerLevel.get(level);
            if (originalValuesPerKeyPerAccountAtLevel != null) {
                Map<ByteArrayWrapper, byte[]> originalValuesPerKeyAtLevel
                        = originalValuesPerKeyPerAccountAtLevel.get(account);
                if ((originalValuesPerKeyAtLevel != null) && originalValuesPerKeyAtLevel.containsKey(key)) {
                    return level;
                }
            }
        }
        return -1;
    }

    /**
     * returns the original value map for a key at a deeper level than the given cacheLevel
     * @param accountWrapper
     * @param key
     * @param cacheLevel
     * @param deepestLevel
     * @return
     */
    private Map<ByteArrayWrapper, byte[]> findOriginalValuesPerKeyAtDeeperLevel(
            ByteArrayWrapper accountWrapper,
            ByteArrayWrapper key,
            int cacheLevel,
            int deepestLevel) {
        for (int level = cacheLevel + 1; level <= deepestLevel; level++) {
            Map<ByteArrayWrapper, byte[]> originalValuesPerKeyForLevel
                    = findOriginalValuesPerKey(accountWrapper, level, false);
            if ((originalValuesPerKeyForLevel != null) && originalValuesPerKeyForLevel.containsKey(key)) {
                return originalValuesPerKeyForLevel;
            }
        }
        throw new IllegalStateException(
                String.format(
                        "ERROR: Unable to find the original values Map at deepest level %d from level %d for key %s",
                        deepestLevel, cacheLevel, key.toString()));
    }

    /**
     * returns the original value map for a key at a given cacheLevel
     * @param accountWrapper
     * @param cacheLevel
     * @param createIfMissing
     * @return
     */
    private Map<ByteArrayWrapper, byte[]> findOriginalValuesPerKey(
            ByteArrayWrapper accountWrapper,
            int cacheLevel,
            boolean createIfMissing) {
        Map<ByteArrayWrapper, Map<ByteArrayWrapper, byte[]>> originalValuesPerKeyPerAccount
                = this.originalValuesPerKeyPerAccountPerLevel.computeIfAbsent(cacheLevel, k -> new HashMap<>());
        if (createIfMissing) {
            return originalValuesPerKeyPerAccount.computeIfAbsent(accountWrapper, k -> new HashMap<>());
        } else {
            return originalValuesPerKeyPerAccount.get(accountWrapper);
        }
    }

    /**
     * Check if the account has been deleted in cache at the specified level or a level below
     * @param accountWrapper
     * @param maxLevel
     * @return
     */
    private boolean isDeletedAccount(ByteArrayWrapper accountWrapper, int maxLevel) {
        return isDeletedAccount(accountWrapper, maxLevel, 0);
    }

    /**
     * Check if the account has been deleted in cache at a level between the specified minLevel (excluded) and maxLevel (included)
     * @param accountWrapper
     * @param maxLevel
     * @param minLevel
     * @return
     */
    private boolean isDeletedAccount(ByteArrayWrapper accountWrapper, int maxLevel, int minLevel) {
        Set<Integer> deletedAccountsSet = this.deletedAccounts.get(accountWrapper);
        return isDeletedAccount(deletedAccountsSet, maxLevel, minLevel);
    }

    /**
     * Check if the account has been deleted in cache at a level between the specified minLevel (excluded) and maxLevel (included)
     * @param deletedAccountsSet
     * @param maxLevel
     * @param minLevel
     * @return
     */
    private boolean isDeletedAccount(Set<Integer> deletedAccountsSet, int maxLevel, int minLevel) {
        if (deletedAccountsSet == null) {
            return false;
        }
        for (int level : deletedAccountsSet) {
            if ((level > minLevel) && (level <= maxLevel)) {
                return true;
            }
        }
        return false;
    }

    /**
     * get all the accounts that have been deleted between the specified minLevel (excluded) and maxLevel (included)
     * @param minLevel
     * @param maxLevel
     * @return
     */
    private Set<ByteArrayWrapper> getDeletedAccounts(int minLevel, int maxLevel) {
        Set<ByteArrayWrapper> accounts = new HashSet<>();
        this.deletedAccounts.forEach((account, levels) -> {
            if (isDeletedAccount(levels, maxLevel, minLevel)) {
                accounts.add(account);
            }
        });
        return accounts;
    }

    /**
     * put value in latestValues map and return the originalValue for the key
     * @param accountWrapper
     * @param key
     * @param value
     * @param originalValuesPerKey
     * @return
     */
    private byte[] putLatestCachedValue(
            ByteArrayWrapper accountWrapper,
            ByteArrayWrapper key,
            byte[] value,
            Map<ByteArrayWrapper, byte[]> originalValuesPerKey
    ) {
        Map<ByteArrayWrapper, byte[]> valuesPerKey
                = this.valuesPerKeyPerAccount.computeIfAbsent(accountWrapper, k -> new HashMap<>());
        byte[] originalValue = null;
        // if the key has already been modified at this level, keep the same originalValue,
        // otherwise get from the global map if it exists, else from the trie
        if (originalValuesPerKey.containsKey(key)) {
            originalValue = originalValuesPerKey.get(key);
        } else if (valuesPerKey.containsKey(key)) {
            originalValue = valuesPerKey.get(key);
        } else {
            originalValue = this.trie.get(key.getData());
        }
        // store value into the global map valuesPerKeyPerAccount
        valuesPerKey.put(key, value);
        return originalValue;
    }


    /**
     * returns the cached value for the key from the latest value map
     * @param accountWrapper
     * @param key
     * @param maxLevel
     * @return
     */
    private byte[] getLatestCachedValue(ByteArrayWrapper accountWrapper, ByteArrayWrapper key, int maxLevel) {
        Map<ByteArrayWrapper, byte[]> valuesPerKey
                = this.valuesPerKeyPerAccount.computeIfAbsent(accountWrapper, k -> new HashMap<>());
        if (valuesPerKey.containsKey(key)) {
            // there is a cached value, then return it
            return valuesPerKey.get(key);
        } else {
            // If the account has been deleted at a lower level, store and return null
            if (isDeletedAccount(accountWrapper, maxLevel)) {
                valuesPerKey.put(key, null);
                return null;
            } else {
                // else store and return value from the trie
                byte[] value = this.trie.get(key.getData());
                valuesPerKey.put(key, value);
                return value;
            }
        }
    }

    /**
     * commit value of each key updated at a given level into cached values form level levelTo
     * @param cacheLevel
     * @param levelTo
     * @param originalValuesPerKeyPerAccountTarget
     */
    private void commitCachedValuesAtLevel(
            int cacheLevel,
            int levelTo,
            Map<ByteArrayWrapper, Map<ByteArrayWrapper, byte[]>> originalValuesPerKeyPerAccountTarget
    ) {
        // set the original value from that level in the target map only if it does not contains an original value already
        Map<ByteArrayWrapper, Map<ByteArrayWrapper, byte[]>> originalValuesPerKeyPerAccountForLevel
                = this.originalValuesPerKeyPerAccountPerLevel.get(cacheLevel);
        if (originalValuesPerKeyPerAccountForLevel != null) {
            originalValuesPerKeyPerAccountForLevel.forEach((account, originalValuesPerKeyForLevel) -> {
                Map<ByteArrayWrapper, byte[]> originalValuesPerKeyTarget
                        = originalValuesPerKeyPerAccountTarget.computeIfAbsent(account, k -> new HashMap<>());
                Map<ByteArrayWrapper, Integer> deepestLevelPerKey = deepestLevelPerKeyPerAccount.computeIfAbsent(account, k -> new HashMap<>());
                originalValuesPerKeyForLevel.forEach((key, value) -> {
                    if (!originalValuesPerKeyTarget.containsKey(key)) {
                        originalValuesPerKeyTarget.put(key, value);
                    }
                    // Update the deepestLevel of the key to levelTo, except if the key is also modified at a deeper level
                    int deepestLevel = getCachedKeyDeepestLevel(key, deepestLevelPerKey);
                    if (deepestLevel <= cacheLevel) {
                        deepestLevelPerKey.put(key, levelTo);
                    }
                });
            });
        }
        this.originalValuesPerKeyPerAccountPerLevel.remove(cacheLevel);
    }

    /**
     * commit as deleted at levelTo each account deleted between levelTo+1 and levelFrom
     * @param levelFrom
     * @param levelTo
     */
    private void commitDeletedAccounts(int levelFrom, int levelTo) {
        // for each account deleted between levelTo+1 and levelFrom, add them deleted at levelTo
        Set<ByteArrayWrapper> theDeletedAccounts = getDeletedAccounts(levelTo, levelFrom);
        theDeletedAccounts.forEach((account) -> {
            Set<Integer> newLevels = new HashSet<>(this.deletedAccounts.get(account));
            // remove all level between levelTo (excluded) and levelFrom (included)
            for (int level = levelTo + 1; level <= levelFrom; level++) {
                newLevels.remove(level);
            }
            // be sure levelTo is in the list
            newLevels.add(levelTo);
            // update the map
            this.deletedAccounts.put(account, newLevels);
        });
    }

    /**
     * clear/rollback the internal maps (originalValues and deepestLevel) at the given level
     * @param cacheLevel
     */
    private void clearUpdatedValuesAtLevel(int cacheLevel) {
        // get the originalValues stored at that level
        Map<ByteArrayWrapper, Map<ByteArrayWrapper, byte[]>> originalValuesPerKeyPerAccount = this.originalValuesPerKeyPerAccountPerLevel.get(cacheLevel);
        if (originalValuesPerKeyPerAccount != null) {
            originalValuesPerKeyPerAccount.forEach((account, originalValuesPerKey) -> {
                Map<ByteArrayWrapper, byte[]> valuesPerKey = this.valuesPerKeyPerAccount.get(account);
                Map<ByteArrayWrapper, Integer> deepestLevelPerKey = this.deepestLevelPerKeyPerAccount.get(account);
                originalValuesPerKey.forEach((key, originalValue) -> {
                    int deepestLevel = getCachedKeyDeepestLevel(key, deepestLevelPerKey);
                    if (deepestLevel <= cacheLevel) {
                        // replace actual value with the original one
                        valuesPerKey.put(key, originalValue);
                        // remove the actual level from the deepestLevel map
                        deepestLevelPerKey.remove(key);
                        // in case the key has been modified at a lower level, update the deepestLevel map
                        int newDeepestLevel = findDeepestLevel(account, key, cacheLevel - 1);
                        if (newDeepestLevel >= FIRST_CACHE_LEVEL) {
                            deepestLevelPerKey.put(key, newDeepestLevel);
                        }
                    } else {
                        // the key has been modified at some deeper level(s) -> find the original value stored looping over the deeper levels
                        Map<ByteArrayWrapper, byte[]> originalValuesPerKeyForLevel
                                = findOriginalValuesPerKeyAtDeeperLevel(account, key, cacheLevel, deepestLevel);
                        // change that original value to the new value
                        originalValuesPerKeyForLevel.put(key, originalValue);
                    }
                });
            });
            originalValuesPerKeyPerAccount.clear();
        }
    }

    /**
     * clear deleted account recorded at the given cache level
     * @param cacheLevel
     */
    private void clearDeletedAccounts(int cacheLevel) {
        // for each account deleted at cacheLevel, clear it
        Map<ByteArrayWrapper, Set<Integer>> cloneDeletedAccounts = new HashMap<>(this.deletedAccounts);
        cloneDeletedAccounts.forEach((account, levels) -> levels.remove(cacheLevel));
    }

    /**
     * collect all the keys in cache at the given cache level, with the specified size,
     *       or all keys when keySize = Integer.MAX_VALUE
     * @param key
     * @param parentKeys
     * @param keySize
     * @param maxLevel
     */
    private void collectKey(ByteArrayWrapper key, Set<ByteArrayWrapper> parentKeys, int keySize, int maxLevel) {
        if (keySize == Integer.MAX_VALUE || key.getData().length == keySize) {
            byte[] valueAtLevel = this.get(key, maxLevel);
            if (valueAtLevel != null) {
                parentKeys.add(key);
            } else {
                parentKeys.remove(key);
            }
        }
    }
}
