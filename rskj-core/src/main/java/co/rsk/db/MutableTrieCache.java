package co.rsk.db;

import co.rsk.crypto.Keccak256;
import co.rsk.trie.MutableTrie;
import co.rsk.trie.Trie;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by SerAdmin on 9/23/2018.
 */
public class MutableTrieCache implements MutableTrie {

    MutableTrie trie;
    // We use a single cache to mark both changed elements and removed elements.
    // null value means the element has been removed.
    HashMap<ByteArrayWrapper,byte[]> cache;

    Set<ByteArrayWrapper> deleteRecursiveCache;

    public MutableTrieCache(MutableTrie parentTrie) {
        trie = parentTrie;
        cache = new HashMap<>();
        deleteRecursiveCache = new HashSet<>();
    }

    public Trie getTrie() {
        assertNoCache();
        return trie.getTrie();
    }

    public MutableTrie getParentTrie() {
        return trie;
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
        // Note that after deleteRecursive without commits
        // get()s will still find the elements.
        ByteArrayWrapper wrap = new ByteArrayWrapper(key);


        if (cache.containsKey(wrap)) {
            // Contains key
            byte[] value = cache.get(wrap);

            if (value == null)
                return null; // erased

            return value;
        }
        else
            // Does not contains key, check parent
            return trie.get(key);


    }

    @Override
    public byte[] get(String key) {
        byte[] keybytes =key.getBytes(StandardCharsets.UTF_8);
        return get(keybytes);

    }

    @Override
    public void put(byte[] key, byte[] value) {
        // If value==null, do we have the choice to either store it
        // in cache with null or in deleteCache. Here we have the choice to
        // to add it to cache with null value or to deleteCache.
        cache.put(new ByteArrayWrapper(key),value);
        return;
    }

    // This method optimizes cache-to-cache transfers
    @Override
    public void put(ByteArrayWrapper key, byte[] value) {
        cache.put(key,value);
    }

    @Override
    public void put(String key, byte[] value) {
        byte[] keybytes =key.getBytes(StandardCharsets.UTF_8);
        put(keybytes,value);
    }

    @Override
    public void delete(byte[] key) {
        ByteArrayWrapper wrap = new ByteArrayWrapper(key);
        cache.put(wrap,null);
        return;
    }

    @Override
    public Set<ByteArrayWrapper> collectKeysFrom(byte[] key) {
        ByteArrayWrapper wrap = new ByteArrayWrapper(key);
        Set<ByteArrayWrapper> set = trie.collectKeysFrom(key);

        // This can be slow. When can it be slow ?
        // If the user creates a contract, then modifies lots of storage keys
        // then selfdestroys the contract.
        // Maybe if the semantic is that an element in deleteCache means all
        // childs must be deleted, then that would help make this code O(1)
        for (ByteArrayWrapper item : cache.keySet()) {
            if (ByteUtil.fastPrefix(key,item.getData())) {
                // Already know that it cointains the key
                byte[] value = cache.get(item);
                if (value==null)
                    set.remove(item);
                else
                    set.add(item);
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

        cache.put(wrap,null);

        for (ByteArrayWrapper s : set) {
            cache.put(s,null);
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

        // We could iterate the puts() and remove those that have the
        // deleteCache key prefix. However we don't care much about the puts,
        // because in commit() we're going to execute the deleteRecurse() operations
        // last. However we must make sure we don't remove the "key" item,
        // as deleteRecursive() requires this item to be found on the trie.
        deleteRecursiveCache.add(wrap);
        cache.remove(wrap);
        return;
    }

    @Override
    public void delete(String key) {
        byte[] keybytes =key.getBytes(StandardCharsets.UTF_8);
        delete(keybytes);
        return;
    }

    @Override
    public byte[] toMessage() {
        return trie.toMessage();
    }

    @Override
    public void commit() {
        // all cached items must be transferred to parent
        // some items will represend deletions (null values)
        for (ByteArrayWrapper item : cache.keySet()) {
            this.trie.put(item,cache.get(item));
        }

        // now remove all elements recursively
        for (ByteArrayWrapper item : deleteRecursiveCache) {
            this.trie.deleteRecursive(item.getData());
        }
        cache.clear();
        deleteRecursiveCache.clear();
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
    }

    @Override
    public int trieSize() {
        // This is tricky because cached items are not really in the trie until commited
        return 0;
    }


    @Override
    public Set<ByteArrayWrapper> collectKeys(int size) {
        Set<ByteArrayWrapper> parentSet = trie.collectKeys(size);

        // all cached items to be transferred to parent
        for (ByteArrayWrapper item : cache.keySet()) {
            if ((size==Integer.MAX_VALUE) || (item.getData().length==size)) {
                if (cache.get(item)==null)
                    parentSet.remove(item);
                else
                    parentSet.add(item);
            }
        }
        // Recursive deleted keys are not reflected until commit.
        return parentSet;
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
    public byte[] serialize() {
        return null;
    }

    @Override
    public boolean hasStore() {
        return trie.hasStore();
    }

    @Override
    public int getValueLength(byte[] key) {
        ByteArrayWrapper wrap = new ByteArrayWrapper(key);

        if (cache.containsKey(wrap)) {
            // Contains key
            byte[] value = cache.get(wrap);

            if (value == null)
                return 0; // erased

            return value.length;
        }
        else
        return trie.getValueLength(key);
    }

    @Override
    public byte[] getValueHash(byte[] key) {
        ByteArrayWrapper wrap = new ByteArrayWrapper(key);

        if (cache.containsKey(wrap)) {
            // Contains key
            byte[] value = cache.get(wrap);

            if (value == null)
                return null; // erased

            // Note that is is inefficient because the hash is not cached
            return Keccak256Helper.keccak256(value);
        }
        else
        return trie.getValueHash(key);
    }
}
