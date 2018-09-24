package co.rsk.db;

import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.trie.MutableTrie;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import org.ethereum.db.ByteArrayWrapper;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by SerAdmin on 9/23/2018.
 */
public class TrieCache implements MutableTrie {

    MutableTrie trie;
    HashMap<ByteArrayWrapper,byte[]> cache;
    Set<ByteArrayWrapper> deleteCache;

    public TrieCache(MutableTrie parentTrie) {
        trie = parentTrie;
        cache = new HashMap<>();
        deleteCache = new HashSet<>();
    }

    public MutableTrie getParentTrie() {
        return trie;
    }

    public boolean isCache() {
        return true;
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
        return new TrieCache(trie.getSnapshotTo(hash));
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
