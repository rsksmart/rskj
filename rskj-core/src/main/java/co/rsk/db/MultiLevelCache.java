package co.rsk.db;

import co.rsk.trie.MutableTrie;
import org.ethereum.db.ByteArrayWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * A class to implement a multilevel cache system, so that the same
 * object can record changes at different caching level
 */
public class MultiLevelCache {
    private static final Logger logger = LoggerFactory.getLogger("general");

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

    public MultiLevelCache(MutableTrie trie) {
        this.trie = trie;
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

        Map<ByteArrayWrapper, byte[]> valuesPerKey
                = this.valuesPerKeyPerAccount.computeIfAbsent(accountWrapper, k -> new HashMap<>());
        Map<ByteArrayWrapper, Map<ByteArrayWrapper, byte[]>> originalValuesPerKeyPerAccount
                = this.originalValuesPerKeyPerAccountPerLevel.computeIfAbsent(cacheLevel, k -> new HashMap<>());
        Map<ByteArrayWrapper, byte[]> originalValuesPerKey
                = originalValuesPerKeyPerAccount.computeIfAbsent(accountWrapper, k -> new HashMap<>());
        if (!originalValuesPerKey.containsKey(key)) {
            byte[] oldValue = (valuesPerKey.containsKey(key) ? valuesPerKey.get(key) : this.trie.get(key.getData()));
            // if this is the first update of the value at this level, we shall store the original value for this key at this level
            // if key has been modified at a deeper level, the oldValue is NOT valuesPerKey.get(key), it will be updated later, see below
            originalValuesPerKey.put(key, oldValue);
        }

        Map<ByteArrayWrapper, Integer> deepestLevelPerKey = this.deepestLevelPerKeyPerAccount.computeIfAbsent(accountWrapper, k -> new HashMap<>());
        if (!deepestLevelPerKey.containsKey(key) || deepestLevelPerKey.get(key) <= cacheLevel) {
            // if the key has not been cached at a deepest level than cacheLevel, store value into the global map valuesPerKeyPerAccount
            valuesPerKey.put(key, value);
            deepestLevelPerKey.put(key, cacheLevel);
        } else {
            logger.error("put a value for account {} key {} at intermediate level {}", accountWrapper.toString(), key.toString(), cacheLevel);
            // if we put the value at a lower level, trace it
            // has this key been modified at an upper level ???
            for (int level = cacheLevel + 1; level <= getDepth(); level++) {
                Map<ByteArrayWrapper, Map<ByteArrayWrapper, byte[]>> originalValuesPerKeyPerAccountForLevel
                        = this.originalValuesPerKeyPerAccountPerLevel.get(level);
                if (originalValuesPerKeyPerAccountForLevel != null) {
                    Map<ByteArrayWrapper, byte[]> originalValuesPerKeyForLevel = originalValuesPerKeyPerAccountForLevel.get(accountWrapper);
                    if (originalValuesPerKeyForLevel != null) {
                        if (originalValuesPerKeyForLevel.containsKey(key)) {
                            logger.error("already modified at level {}", level);
                            byte[] oldValue = originalValuesPerKeyForLevel.get(key);
                            // update original value
                            originalValuesPerKeyForLevel.put(key, value);
                            originalValuesPerKey.put(key, oldValue);
                            break;
                        }
                    }
                }
            }

        }
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

    private boolean isDeletedAccount(ByteArrayWrapper accountWrapper, Integer maxLevel) {
        // return true if the account has been deleted at a level < maxLevel
        Set<Integer> deletedAccountsSet = this.deletedAccounts.get(accountWrapper);
        if (deletedAccountsSet == null) {
            return false;
        }
        for (Integer level : deletedAccountsSet) {
            if (level <= maxLevel) {
                return true;
            }
        }
        return false;
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
        ByteArrayWrapper accountWrapper = MutableTrieCache.getAccountWrapper(key);
        Map<ByteArrayWrapper, Integer> deepestLevelPerKey = this.deepestLevelPerKeyPerAccount.computeIfAbsent(accountWrapper, k -> new HashMap<>());
        if (!deepestLevelPerKey.containsKey(key) || deepestLevelPerKey.get(key) <= maxLevel) {
            // la clé n'a pas été modifiée à un niveau > au niveau demandé
            Map<ByteArrayWrapper, byte[]> valuesPerKey
                    = this.valuesPerKeyPerAccount.computeIfAbsent(accountWrapper, k -> new HashMap<>());
            if (valuesPerKey.containsKey(key)) {
                return valuesPerKey.get(key);
            } else {
                // If the account has been deleted at a lower level, store and return null
                if (isDeletedAccount(accountWrapper, maxLevel)) {
                    valuesPerKey.put(key, null);
                    return null;
                } else {
                    // else store and return trie[key]
                    byte[] value = this.trie.get(key.getData());
                    valuesPerKey.put(key, value);
                    return value;
                }
            }
        }
        // We want to get the value at a cache level and there has been an update (or more) at a deepest level
        // In that particular case, we need to iterate on deeper levels until we find the original value for the key
        for (int level = maxLevel + 1; level <= deepestLevelPerKey.get(key); level++) {
            Map<ByteArrayWrapper, Map<ByteArrayWrapper, byte[]>> originalValuesPerKeyPerAccountForLevel
                    = this.originalValuesPerKeyPerAccountPerLevel.get(level);
            if (originalValuesPerKeyPerAccountForLevel != null) {
                Map<ByteArrayWrapper, byte[]> originalValuesPerKeyForLevel = originalValuesPerKeyPerAccountForLevel.get(accountWrapper);
                if (originalValuesPerKeyForLevel != null) {
                    if (originalValuesPerKeyForLevel.containsKey(key)) {
                        // This key has been modified at this level
                        // return the original value
                        return originalValuesPerKeyForLevel.get(key);
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

    public void collectKeys(Set<ByteArrayWrapper> parentKeys, int keySize, int maxLevel) {
        collectKeysAtLevel(parentKeys, keySize, maxLevel);
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
        Set<ByteArrayWrapper> accounts = new HashSet<>();
        this.deletedAccounts.forEach((account, levels) -> {
            // if the account has been deleted at level = cacheLevel or below, then add it into the returned list
            for (int level: levels) {
                if (level <= cacheLevel) {
                    accounts.add(account);
                    break;
                }
            }
        });
        return accounts;
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
        ByteArrayWrapper accountWrapper = MutableTrieCache.getAccountWrapper(account);

        this.deletedAccountsPerLevel.get(cacheLevel).add(account);

        Set<Integer> deletedAccountsSet = deletedAccounts.computeIfAbsent(account, k -> new HashSet<>());
        deletedAccountsSet.add(cacheLevel);

        Map<ByteArrayWrapper, byte[]> valuesPerKey
                = this.valuesPerKeyPerAccount.computeIfAbsent(accountWrapper, k -> new HashMap<>());
        Map<ByteArrayWrapper, Integer> deepestLevelPerKey = this.deepestLevelPerKeyPerAccount.computeIfAbsent(accountWrapper, k -> new HashMap<>());
        Map<ByteArrayWrapper, Map<ByteArrayWrapper, byte[]>> originalValuesPerKeyPerAccount
                = this.originalValuesPerKeyPerAccountPerLevel.computeIfAbsent(cacheLevel, k -> new HashMap<>());
        Map<ByteArrayWrapper, byte[]> originalValuesPerKey
                = originalValuesPerKeyPerAccount.computeIfAbsent(accountWrapper, k -> new HashMap<>());
        // for each key in global cached map
        // if the key has not been updated at deeper level than cacheLevel, set value = null.
        valuesPerKey.forEach((key, value) -> {
            if (!deepestLevelPerKey.containsKey(key) || (deepestLevelPerKey.get(key) <= cacheLevel)) {
                byte[] oldValue = originalValuesPerKey.containsKey(key) ? originalValuesPerKey.get(key) : (valuesPerKey.containsKey(key) ? valuesPerKey.get(key) : this.trie.get(key.getData()));
                valuesPerKey.put(key, null);
                deepestLevelPerKey.put(key, cacheLevel);
                originalValuesPerKey.put(key, oldValue);
            } else {
                // the key has been updated at deeper level, so we need to loop over the deeper level until we update the original value
                for (int level = cacheLevel + 1; level <= getDepth(); level++) {
                    Map<ByteArrayWrapper, Map<ByteArrayWrapper, byte[]>> originalValuesPerKeyPerAccountForLevel
                            = this.originalValuesPerKeyPerAccountPerLevel.get(level);
                    if (originalValuesPerKeyPerAccountForLevel != null) {
                        Map<ByteArrayWrapper, byte[]> originalValuesPerKeyForLevel = originalValuesPerKeyPerAccountForLevel.get(accountWrapper);
                        if (originalValuesPerKeyForLevel != null) {
                            if (originalValuesPerKeyForLevel.containsKey(key)) {
                                byte[] oldValue = originalValuesPerKeyForLevel.get(key);
                                // update original value
                                originalValuesPerKeyForLevel.put(key, null);
                                originalValuesPerKey.put(key, oldValue);
                                break;
                            }
                        }
                    }
                }
            }
        });

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
        // for each account deleted between levelTo+1 and levelFrom, add them deleted at levelTo
        Map<ByteArrayWrapper, Set<Integer>> cloneDeletedAccounts = new HashMap<>(this.deletedAccounts);
        cloneDeletedAccounts.forEach((account, levels) -> {
            Set<Integer> newLevels = new HashSet<>(levels);
            boolean found = false;
            for (int level: levels) {
                if ((level > levelTo) && (level <= levelFrom)) {
                    newLevels.remove(level);
                    found = true;
                }
            }
            if (found) {
                newLevels.add(levelTo);
                this.deletedAccounts.put(account, newLevels);
            }
        });

        Map<ByteArrayWrapper, Map<ByteArrayWrapper, byte[]>> originalValuesPerKeyPerAccountTarget
                = this.originalValuesPerKeyPerAccountPerLevel.computeIfAbsent(levelTo, k -> new HashMap<>());
        for (int level = levelFrom; level > levelTo; level--) {
            // for each key updated at that level
            Map<ByteArrayWrapper, Map<ByteArrayWrapper, byte[]>> originalValuesPerKeyPerAccountForLevel
                    = this.originalValuesPerKeyPerAccountPerLevel.get(level);
            if (originalValuesPerKeyPerAccountForLevel != null) {
                originalValuesPerKeyPerAccountForLevel.forEach((account, originalValuesPerKeyForLevel) -> {
                    Map<ByteArrayWrapper, byte[]> originalValuesPerKeyTarget
                            = originalValuesPerKeyPerAccountTarget.computeIfAbsent(account, k -> new HashMap<>());
                    Map<ByteArrayWrapper, Integer> deepestLevelPerKey = deepestLevelPerKeyPerAccount.computeIfAbsent(account, k -> new HashMap<>());
                    originalValuesPerKeyForLevel.forEach((key, value) -> {
                        if (!originalValuesPerKeyTarget.containsKey(key)) {
                            originalValuesPerKeyTarget.put(key, value);
                        }
                        deepestLevelPerKey.put(key, levelTo);
                    });
                });
            }
            this.originalValuesPerKeyPerAccountPerLevel.remove(level);
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
        if (getDepth() == FIRST_CACHE_LEVEL) {
            this.valuesPerKeyPerAccount.forEach((account, valuesPerKey) -> {
                valuesPerKey.forEach((key, value) -> {
                    trie.put(key, value);
                });
            });
        } else {
            logger.error("WARNING committing first level of a cache with deeper levels not committed  before");
            this.valuesPerKeyPerAccount.forEach((account, valuesPerKey) -> {
                Map<ByteArrayWrapper, Integer> deepestLevelPerKey = this.deepestLevelPerKeyPerAccount.get(account);
                valuesPerKey.forEach((key, value) -> {
                    if (!deepestLevelPerKey.containsKey(key)) {
                        logger.error("ERROR not expected key not found in deepestLevel");
                        return;
                    }
                    if (deepestLevelPerKey.get(key) <= FIRST_CACHE_LEVEL) {
                        trie.put(key, value);
                    } else {
                        logger.error("WARNING committing first level of a cache with deeper levels not committed  before");
                        boolean found = false;
                        for(int level = FIRST_CACHE_LEVEL + 1; level <= deepestLevelPerKey.get(key); level++) {
                            Map<ByteArrayWrapper, Map<ByteArrayWrapper, byte[]>> originalValuesPerKeyPerAccount
                                    = this.originalValuesPerKeyPerAccountPerLevel.get(level);
                            if (originalValuesPerKeyPerAccount != null) {
                                Map<ByteArrayWrapper, byte[]> originalValuesPerKey = originalValuesPerKeyPerAccount.get(account);
                                if (originalValuesPerKey != null) {
                                    trie.put(key, originalValuesPerKey.get(key));
                                    found = true;
                                    break;
                                }
                            }
                        }
                        if (!found) {
                            logger.error("ERROR not expected key not found in original maps at deeper levels");
                        }
                    }
                });
            });
        }

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
            originalValuesPerKeyPerAccountPerLevel.clear();
            deletedAccounts.clear();
            deepestLevelPerKeyPerAccount.clear();
            valuesPerKeyPerAccount.clear();
            initLevel(cacheLevel);
        } else {
            // for each account deleted at cacheLevel, clear it
            Map<ByteArrayWrapper, Set<Integer>> cloneDeletedAccounts = new HashMap<>(this.deletedAccounts);
            cloneDeletedAccounts.forEach((account, levels) -> {
                if (levels.contains(cacheLevel)) {
                    levels.remove(cacheLevel);
                }
            });
            Map<ByteArrayWrapper, Map<ByteArrayWrapper, byte[]>> originalValuesPerKeyPerAccount = this.originalValuesPerKeyPerAccountPerLevel.get(cacheLevel);
            if (originalValuesPerKeyPerAccount != null) {
                originalValuesPerKeyPerAccount.forEach((account, originalValuesPerKey) -> {
                    Map<ByteArrayWrapper, byte[]> valuesPerKey = this.valuesPerKeyPerAccount.get(account);
                    Map<ByteArrayWrapper, Integer> deepestLevelPerKey = this.deepestLevelPerKeyPerAccount.get(account);
                    if (deepestLevelPerKey == null) {
                        logger.error("Error unexpected not found deepest level map for account");
                        return;
                    }
                    originalValuesPerKey.forEach((key, originalValue) -> {
                        if (!deepestLevelPerKey.containsKey(key)) {
                            logger.error("Error unexpected not found deepest level for key");
                            return;
                        }
                        if (deepestLevelPerKey.get(key) <= cacheLevel) {
                            valuesPerKey.put(key, originalValue);
                            boolean found = false;
                            for (int level = cacheLevel; level >= FIRST_CACHE_LEVEL; level--) {
                                Map<ByteArrayWrapper, Map<ByteArrayWrapper, byte[]>> originalValuesPerKeyPerAccountAtLevel
                                        = this.originalValuesPerKeyPerAccountPerLevel.get(level);
                                if (originalValuesPerKeyPerAccountAtLevel != null) {
                                    Map<ByteArrayWrapper, byte[]> originalValuesPerKeyAtLevel
                                            = originalValuesPerKeyPerAccountAtLevel.get(account);
                                    if (originalValuesPerKeyAtLevel != null) {
                                        if (originalValuesPerKeyAtLevel.containsKey(key)) {
                                            deepestLevelPerKey.put(key, level);
                                            found = true;
                                            break;
                                        }
                                    }
                                }
                            }
                            if (!found) {
                                logger.error("ERROR unexpected not found original value at lower cache levels");
                                return;
                            }
                        } else {
                            logger.error("Warning key has been modified at a deeper level");
                            boolean found = false;
                            for (int level = cacheLevel + 1; level <= deepestLevelPerKey.get(key); level++) {
                                Map<ByteArrayWrapper, Map<ByteArrayWrapper, byte[]>> originalValuesPerKeyPerAccountAtLevel
                                        = this.originalValuesPerKeyPerAccountPerLevel.get(level);
                                if (originalValuesPerKeyPerAccountAtLevel != null) {
                                    Map<ByteArrayWrapper, byte[]> originalValuesPerKeyAtLevel
                                            = originalValuesPerKeyPerAccountAtLevel.get(account);
                                    if (originalValuesPerKeyAtLevel != null) {
                                        if (originalValuesPerKeyAtLevel.containsKey(key)) {
                                            originalValuesPerKeyAtLevel.put(key, originalValue);
                                            found = true;
                                            break;
                                        }
                                    }
                                }
                            }
                            if (!found) {
                                logger.error("ERROR unexpected not found original value at deeper levels");
                                return;
                            }
                        }
                    });
                });
            }

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

    public Map<ByteArrayWrapper, byte[]> getAccountItems(ByteArrayWrapper account, int cacheLevel) {
        Map<ByteArrayWrapper, byte[]> accountItems = new HashMap<>();
        ByteArrayWrapper accountWrapper = MutableTrieCache.getAccountWrapper(account);

        if (cacheLevel >= getDepth()) {
            Map<ByteArrayWrapper, byte[]> valuesPerKey = this.valuesPerKeyPerAccount.get(accountWrapper);
            if (valuesPerKey != null) {
                valuesPerKey.forEach((key2, value) -> {
                    // if the value is null, add it to accountItems so that it will clear the value if key is also in the trie
                    accountItems.put(key2, value);
                });
            }
        } else {
            Map<ByteArrayWrapper, byte[]> valuesPerKey = this.valuesPerKeyPerAccount.get(accountWrapper);
            Map<ByteArrayWrapper, Integer> deepestLevelPerKey = this.deepestLevelPerKeyPerAccount.get(accountWrapper);
            if (deepestLevelPerKey == null) {
                logger.error("ERROR unexpected not found deepest level map for account");
                return null;
            }
            if (valuesPerKey != null) {
                valuesPerKey.forEach((key2, value) -> {
                    if (!deepestLevelPerKey.containsKey(key2)) {
                        logger.error("ERROR unexpected not found deepest level for key");
                    } else {
                        if (deepestLevelPerKey.get(key2) <= cacheLevel) {
                            // if the value is null, add it to accountItems so that it will clear the value if key is also in the trie
                            accountItems.put(key2, value);
                        } else {
                            logger.error("WARNING get key but modified at a deeper level");
                            boolean found = false;
                            for (int level = cacheLevel + 1; level <= deepestLevelPerKey.get(key2); level++) {
                                Map<ByteArrayWrapper, Map<ByteArrayWrapper, byte[]>> originalValuesPerKeyPerAccount = this.originalValuesPerKeyPerAccountPerLevel.get(level);
                                if (originalValuesPerKeyPerAccount != null) {
                                    Map<ByteArrayWrapper, byte[]> originalValuesPerKey = originalValuesPerKeyPerAccount.get(accountWrapper);
                                    if (originalValuesPerKey != null) {
                                        if (originalValuesPerKey.containsKey(key2)) {
                                            // if the value is null, add it to accountItems so that it will clear the value if key is also in the trie
                                            accountItems.put(key2, originalValuesPerKey.get(key2));
                                            found = true;
                                            break;
                                        }
                                    }
                                }
                            }
                            if (!found) {
                                logger.error("ERROR unexpected not found original value for key in deeper levels");
                                return;
                            }
                        }
                    }
                });
            }
        }
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
        ByteArrayWrapper accountWrapper = MutableTrieCache.getAccountWrapper(key);
        Map<ByteArrayWrapper, Integer> deepestLevelPerKey = this.deepestLevelPerKeyPerAccount.get(accountWrapper);
        return ((deepestLevelPerKey != null) && deepestLevelPerKey.containsKey(key));
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
        if (deepestLevelPerKeyPerAccount.size() > 0) {
            return false;
        }
        return true;
    }

    private void collectKeysAtLevel(Set<ByteArrayWrapper> parentKeys, int keySize, int cacheLevel) {
        getDeletedAccounts(cacheLevel).forEach(key -> parentKeys.remove(key));
        this.valuesPerKeyPerAccount.forEach((account, valuesPerKey) -> {
            Map<ByteArrayWrapper, Integer> deepestLevelPerKey = this.deepestLevelPerKeyPerAccount.get(account);
            if (deepestLevelPerKey == null) {
                logger.error("ERROR unexpected not found deepest level maps for account");
                return;
            }
            valuesPerKey.forEach((key, value) -> {
                if (!deepestLevelPerKey.containsKey(key)) {
                    logger.error("ERROR unexpected not found deepest level for key");
                    return;
                }
                if (deepestLevelPerKey.get(key) <= cacheLevel) {
                    if (value != null) {
                        parentKeys.add(key);
                    } else {
                        parentKeys.remove(key);
                    }
                } else {
                    boolean found = false;
                    for (int level = cacheLevel + 1; level <= deepestLevelPerKey.get(key); level++) {
                        Map<ByteArrayWrapper, Map<ByteArrayWrapper, byte[]>> originalValuesPerKeyPerAccount = this.originalValuesPerKeyPerAccountPerLevel.get(level);
                        if (originalValuesPerKeyPerAccount != null) {
                            Map<ByteArrayWrapper, byte[]> originalValuesPerKey = originalValuesPerKeyPerAccount.get(account);
                            if (originalValuesPerKey != null) {
                                if (originalValuesPerKey.containsKey(key)) {
                                    if (originalValuesPerKey.get(key) != null) {
                                        parentKeys.add(key);
                                    } else {
                                        parentKeys.remove(key);
                                    }
                                    found = true;
                                }
                            }
                        }
                    }
                    if (!found) {
                        logger.error("ERROR unexpected not found original value at deeper level for key");
                        return;
                    }
                }
            });
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
