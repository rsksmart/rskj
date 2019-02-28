package co.rsk.db;

import co.rsk.crypto.Keccak256;
import co.rsk.trie.MutableTrie;
import co.rsk.trie.Trie;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Created by SerAdmin on 9/23/2018.
 */
public class MutableTrieCache implements MutableTrie {

    public static final int ACCOUNT_KEY_SIZE = 33;
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

    public Trie getTrie() {
        assertNoCache();
        return trie.getTrie();
    }

    public boolean isCache() {
        return true;
    }

    public boolean isSecure() {
        return trie.isSecure();
    }

    @Override
    public Keccak256 getHash() {
        return trie.getHash();
    }

    @Override
    public byte[] get(byte[] key) {
        ByteArrayWrapper wrap = new ByteArrayWrapper(key);
        CacheItem cacheItem = cache.get(wrap);
        ByteArrayWrapper deleteWrap = key.length == ACCOUNT_KEY_SIZE ? wrap :
                new ByteArrayWrapper(Arrays.copyOf(key, ACCOUNT_KEY_SIZE));
        Integer order = deleteRecursiveCache.get(deleteWrap);

        if (cacheItem != null) {
            return order == null || order < cacheItem.order ? cacheItem.value : null;
        }

        return order != null ? null : trie.get(key);
    }

    @Override
    public byte[] get(String key) {
        byte[] keybytes = key.getBytes(StandardCharsets.UTF_8);
        return get(keybytes);
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

    @Override
    public void delete(byte[] key) {
        // note that this is cached with the put operations
        put(key, null);
    }

    @Override
    public Set<ByteArrayWrapper> collectKeys(int size) {
        Set<ByteArrayWrapper> parentSet = trie.collectKeys(size);

        // all cached items to be transferred to parent
        for (ByteArrayWrapper item : cache.keySet()) {
            if ((size == Integer.MAX_VALUE) || (item.getData().length == size)) {
                if (this.get(item.getData()) == null) {
                    parentSet.remove(item);
                } else {
                    parentSet.add(item);
                }
            }
        }
        return parentSet;
    }

    @Override
    public Set<ByteArrayWrapper> collectKeysFrom(byte[] key) {
        Set<ByteArrayWrapper> set = trie.collectKeysFrom(key);
        // This can be slow. When can it be slow ?
        // If the user creates a contract, then modifies lots of storage keys
        // then selfdestroys the contract.
        // Maybe if the semantic is that an element in deleteCache means all
        // childs must be deleted, then that would help make this code O(1)
        for (ByteArrayWrapper item : cache.keySet()) {
            if (ByteUtil.fastPrefix(key, item.getData())) {
                // Already know that it cointains the key
                if (this.get(key) == null) {
                    set.remove(item);
                } else {
                    set.add(item);
                }
            }
        }

        return set;
    }

    ////////////////////////////////////////////////////////////////////////////////
    // This was an attempt to create a deleteRecursive method which is immediate
    // that resulted on a slow (not O(1)) method.
    ///////////////////////////////////////////////////////////////////////////////
    public void deleteRecursive_immediate(byte[] key) {
        // This is a bit ugly. All keys in cache with a a certain key prefix must
        // be deleted. The only way to do this right is to get all keys and store them
        // all here in deleteCache.
        // This was improved with a new cache deleteRecursiveCache.
        //
        // If there are items in deleteRecursiveCache, and a new put(key,val) that matches
        // one of the prefixes in deleteRecursiveCache is received, then the
        // data in that deleteRecursiveCache is expanded with collectKeysFrom() and
        // the prefix is removed from deleteRecursiveCache.
        //
        // Cons: Each Put() after an item is added to deleteRecursiveCache must check
        // each every element in deleteRecursiveCache unless we restrict deleteRecursive
        // to prefixes of certain length (the address part) which is 21 por insecure trie,
        // and 33 for secure trie.
        ByteArrayWrapper wrap = new ByteArrayWrapper(key);
        Set<ByteArrayWrapper> set = trie.collectKeysFrom(key);

        cache.put(wrap, null);

        for (ByteArrayWrapper s : set) {
            cache.put(s, null);
        }

        return;
    }

    @Override
    ////////////////////////////////////////////////////////////////////////////////////
    // The semantic of implementations is special, and not the same of the MutableTrie
    // It is DELETE ON COMMIT, which means that changes are not applies until commit()
    // is called, and changes are applied last.
    ////////////////////////////////////////////////////////////////////////////////////
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
    public void delete(String key) {
        byte[] keybytes = key.getBytes(StandardCharsets.UTF_8);
        delete(keybytes);
    }

    @Override
    public byte[] toMessage() {
        return trie.toMessage();
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
    public void rollback() {
        cache.clear();
        deleteRecursiveCache.clear();
        logOps.clear();
    }

    @Override
    public int trieSize() {
        // This is tricky because cached items are not really in the trie until commited
        return 0;
    }

    public void assertNoCache() {
        if ((cache.size() != 0)) {
            throw new IllegalStateException();
        }
        if ((deleteRecursiveCache.size() != 0)) {
            throw new IllegalStateException();
        }
    }

    @Override
    public MutableTrie getSnapshotTo(Keccak256 hash) {
        assertNoCache();
        return new MutableTrieCache(trie.getSnapshotTo(hash));
    }

    @Override
    public void setSnapshotTo(Keccak256 hash) {
        assertNoCache();
        this.trie = trie.getSnapshotTo(hash);
    }

    @Override
    public boolean hasStore() {
        return trie.hasStore();
    }

    @Override
    public int getValueLength(byte[] key) {
        byte[] value = this.get(key);
        return value != null ? value.length : 0;
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
