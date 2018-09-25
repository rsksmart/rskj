package co.rsk.db;

import co.rsk.crypto.Keccak256;
import co.rsk.trie.MutableTrie;
import co.rsk.trie.Trie;
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
    HashMap<ByteArrayWrapper,byte[]> cache;
    Set<ByteArrayWrapper> deleteCache;

    public MutableTrieCache(MutableTrie parentTrie) {
        trie = parentTrie;
        cache = new HashMap<>();
        deleteCache = new HashSet<>();
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
        ByteArrayWrapper wrap = new ByteArrayWrapper(key);
        byte[] result = cache.get(wrap);
        if (result!=null)
            return result;
        if (deleteCache.contains(wrap))
            return null;

        return trie.get(key);

    }

    @Override
    public byte[] get(String key) {
        byte[] keybytes =key.getBytes(StandardCharsets.UTF_8);
        return get(keybytes);

    }

    @Override
    public void put(byte[] key, byte[] value) {
        cache.put(new ByteArrayWrapper(key),value);
        deleteCache.remove(key);
        return;
    }

    @Override
    public void put(String key, byte[] value) {
        byte[] keybytes =key.getBytes(StandardCharsets.UTF_8);
        put(keybytes,value);
    }

    @Override
    public void delete(byte[] key) {
        ByteArrayWrapper wrap = new ByteArrayWrapper(key);
        cache.remove(wrap);
        deleteCache.add(wrap );
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
            ByteUtil.fastPrefix(key,item.getData());
                set.add(item);
        }
        for (ByteArrayWrapper s : set) {
            if (deleteCache.contains(s)) {
                set.remove(s);
            }
        }

        return set;
    }

    @Override
    public void deleteRecursive(byte[] key) {
        ByteArrayWrapper wrap = new ByteArrayWrapper(key);
        // This is a bit ugly. All keys in cache with a a certain key prefix must
        // be deleted. The only way to do this right is to get all keys and store them
        // all here in deleteCache.
        Set<ByteArrayWrapper> set = trie.collectKeysFrom(key);

        cache.remove(wrap);
        deleteCache.add(wrap);

        for (ByteArrayWrapper s : set) {
            cache.remove(s);
            deleteCache.add(s);
        }

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
        for (ByteArrayWrapper item : cache.keySet()) {
            this.trie.put(item.getData(),cache.get(item));
        }

        // now remove
        for (ByteArrayWrapper item : deleteCache) {
            this.trie.delete(item.getData());
        }

        cache.clear();
        deleteCache.clear();
    }

    @Override
    public void save() {
        commit();
        trie.save();
    }

    @Override
    public void rollback() {
        cache.clear();
        deleteCache.clear();
    }


    @Override
    public int trieSize() {
        // This is tricky because cached items are not really in the trie until commited
        return 0;
    }


    @Override
    public Set<ByteArrayWrapper> collectKeys(int size) {
        Set<ByteArrayWrapper> parentSet = trie.collectKeys(size);
        // all cached items must be transferred to parent
        for (ByteArrayWrapper item : cache.keySet()) {
            if (item.getData().length==size)
                parentSet.add(item);
        }

        // now remove
        for (ByteArrayWrapper item : deleteCache) {
            if (item.getData().length==size)
                parentSet.remove(item);
        }
        return parentSet;
    }


    public void assertNoCache() {
        assert(cache.size()==0);
        assert(deleteCache.size()==0);
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

}
