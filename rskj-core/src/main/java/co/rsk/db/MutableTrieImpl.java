package co.rsk.db;

import co.rsk.crypto.Keccak256;
import co.rsk.trie.MutableTrie;
import co.rsk.trie.Trie;
import org.ethereum.db.ByteArrayWrapper;

import java.util.Set;

/**
 * Created by SerAdmin on 9/24/2018.
 */
public class MutableTrieImpl implements MutableTrie {

    Keccak256 hash;
    Trie trie;

    public MutableTrieImpl(Trie atrie) {
        trie = atrie;
        hash = atrie.getHash();
    }

    public Trie getTrie() {
        return trie;
    }

    @Override
    public Keccak256 getHash() {
        hash = trie.getHash();
        return hash;
    }

    @Override
    public boolean isSecure() {
     return trie.isSecure();
    }

    @Override
    public byte[] get(byte[] key) {
        return trie.get(key);
    }

    @Override
    public byte[] get(String key) {
        return trie.get(key);
    }

    @Override
    public void put(byte[] key, byte[] value) {
        trie = trie.put(key,value);

    }

    @Override
    public void put(ByteArrayWrapper key, byte[] value) {
        trie = trie.put(key,value);
    }

    @Override
    public void put(String key, byte[] value) {
        trie = trie.put(key,value);
    }

    @Override
    public void delete(byte[] key) {
        trie = trie.delete(key);
    }

    @Override
    public int getValueLength(byte[] key) {
        Trie atrie = trie.find(key);
        if (atrie==null) return 0;
        return atrie.getValueLength();
    }

    @Override
    public byte[] getValueHash(byte[] key) {
        Trie atrie = trie.find(key);
        if (atrie==null) return null;
        return atrie.getValueHash();
    }

    @Override
    public void deleteRecursive(byte[] key) { trie = trie.deleteRecursive(key); }

    @Override
    public void delete(String key) {
        trie = trie.delete(key);
    }

    @Override
    public byte[] toMessage() {
        return trie.toMessage();
    }

    @Override
    public void save() {
        trie.save();
    }

    @Override
    public void commit() {

    }

    @Override
    public void rollback() {

    }

    @Override
    public boolean isCache() {
        return false;
    }

    @Override
    public int trieSize() {
        return trie.trieSize();
    }

    @Override
    public Set<ByteArrayWrapper> collectKeys(int size) {
        return trie.collectKeys(size);
    }

    @Override
    public Set<ByteArrayWrapper> collectKeysFrom(byte[] key) { return trie.collectKeysFrom(key); }

    @Override
    public MutableTrie getSnapshotTo(Keccak256 hash) {
        // Since getSnapshotTo() does not modify the current trie (this.trie)
        // then there is no need to save nodes.
        return new MutableTrieImpl(trie.getSnapshotTo(hash));
    }

    @Override
    public void setSnapshotTo(Keccak256 hash) {
        // This changes the trie root node. Any other tree that has not been saved to
        // disk will be automatically deleted.
        this.trie = trie.getSnapshotTo(hash);
    }

    @Override
    public byte[] serialize() {
        return trie.serialize();
    }

    @Override
    public boolean hasStore() {
        return trie.hasStore();
    }
}
