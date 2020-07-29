package co.rsk.db;

import co.rsk.trie.MutableTrie;
import org.ethereum.db.ByteArrayWrapper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SingleCacheImpl implements MutableTrieCache.ICache {

    // We use a single cache to mark both changed elements and removed elements.
    // null value means the element has been removed.
    private final Map<ByteArrayWrapper, Map<ByteArrayWrapper, byte[]>> cache;

    // this logs recursive delete operations to be performed at commit time
    private final Set<ByteArrayWrapper> deleteRecursiveLog;

    public SingleCacheImpl() {
        cache = new HashMap<>();
        deleteRecursiveLog = new HashSet<>();
    }

    @Override
    public void put(ByteArrayWrapper key, byte[] value) {
        ByteArrayWrapper accountWrapper = MutableTrieCache.getAccountWrapper(key);
        Map<ByteArrayWrapper, byte[]> accountMap = cache.computeIfAbsent(accountWrapper, k -> new HashMap<>());
        accountMap.put(key, value);

    }

    @Override
    public byte[] get(ByteArrayWrapper key) {
        ByteArrayWrapper accountWrapper = MutableTrieCache.getAccountWrapper(key);
        Map<ByteArrayWrapper, byte[]> accountItems = cache.get(accountWrapper);
        if (accountItems == null) {
            return null;
        }
        return accountItems.get(key);
    }

    @Override
    public void collectKeys(Set<ByteArrayWrapper> parentKeys, int keySize) {
        // all cached items to be transferred to parent
        cache.forEach((accountKey, account) ->
                account.forEach((realKey, value) -> {
                    if (keySize == Integer.MAX_VALUE || realKey.getData().length == keySize) {
                        if (this.get(realKey) == null) {
                            parentKeys.remove(realKey);
                        } else {
                            parentKeys.add(realKey);
                        }
                    }
                })
        );
    }

    @Override
    public void deleteAccount(ByteArrayWrapper key) {
        deleteRecursiveLog.add(key);
        cache.remove(key);
    }

    @Override
    public void commit(MutableTrie trie) {
        // in case something was deleted and then put again, we first have to delete all the previous data
        deleteRecursiveLog.forEach(item -> trie.deleteRecursive(item.getData()));
        cache.forEach((accountKey, accountData) -> {
            if (accountData != null) {
                // cached account
                accountData.forEach((realKey, value) -> trie.put(realKey, value));
            }
        });
        clear();
    }

    @Override
    public void commitAll(MutableTrie trie) {
        commit(trie);
    }

    @Override
    public void clear() {
        deleteRecursiveLog.clear();
        cache.clear();
    }

    @Override
    public Map<ByteArrayWrapper, byte[]> getAccountItems(ByteArrayWrapper key) {
        return cache.get(key);
    }

    @Override
    public boolean isAccountDeleted(ByteArrayWrapper key) {
        ByteArrayWrapper accountWrapper = MutableTrieCache.getAccountWrapper(key);
        return deleteRecursiveLog.contains(accountWrapper);
    }

    @Override
    public boolean isInCache(ByteArrayWrapper key) {
        ByteArrayWrapper accountWrapper = MutableTrieCache.getAccountWrapper(key);
        boolean isDeletedAccount = deleteRecursiveLog.contains(accountWrapper);
        Map<ByteArrayWrapper, byte[]> accountItems = cache.get(accountWrapper);
        return (isDeletedAccount || (accountItems != null && accountItems.containsKey(key)));
    }

    @Override
    public boolean isEmpty() {
        return cache.isEmpty() && deleteRecursiveLog.isEmpty();
    }

    @Override
    public void rollback() {
        clear();
    }
}
