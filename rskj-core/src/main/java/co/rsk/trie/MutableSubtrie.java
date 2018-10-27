package co.rsk.trie;

import co.rsk.crypto.Keccak256;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.RepositoryTrack;

import java.util.Arrays;
import java.util.Set;

/**
 * Created by SerAdmin on 9/25/2018.
 */
public class MutableSubtrie implements MutableTrie {
    MutableTrie mutableTrie;
    byte[] prefix;

    public MutableSubtrie(MutableTrie mt,byte[] aprefix) {
        mutableTrie = mt;
        prefix = aprefix;

    }

    public Keccak256 getHash() {
        return null;
    }

    public static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    public byte[] computeKey(byte[] key) {
        return concat(prefix,RepositoryTrack.GetStorageTailKey(key,mutableTrie.isSecure()));
    }

    public byte[] get(byte[] key){
        return mutableTrie.get(computeKey(key));
    }

    public byte[] get(String key) {
        throw new UnsupportedOperationException();
    }

    public void put(byte[] key, byte[] value){
        mutableTrie.put(computeKey(key),value);
    }

    public void put(String key, byte[] value) {
        throw new UnsupportedOperationException();
    }

    public void put(ByteArrayWrapper key, byte[] value) {
        mutableTrie.put(computeKey(key.getData()),value);
    }

    public void deleteRecursive(byte[] key) {
        mutableTrie.deleteRecursive(computeKey(key));
    }

    public void delete(byte[] key) {
        mutableTrie.delete(computeKey(key));

    }

    public void delete(String key) {
        throw new UnsupportedOperationException();
    }

    public byte[] toMessage() {
        throw new UnsupportedOperationException();
    }

    public void save() {
        mutableTrie.save();
    }

    public void flush() {
        mutableTrie.flush();
    }

    public void commit() {
        mutableTrie.commit();

    }

    public void rollback() {
        mutableTrie.rollback();
    }

    public boolean isCache() {
        return mutableTrie.isCache();
    }

    public boolean isSecure() {
        return mutableTrie.isSecure();
    }

    public int trieSize() {
        return mutableTrie.trieSize();
    }

    public Set<ByteArrayWrapper> collectKeys(int size) {
        throw new UnsupportedOperationException();
    }

    public Set<ByteArrayWrapper> collectKeysFrom(byte[] key) {
        throw new UnsupportedOperationException();
    }

    public MutableTrie getSnapshotTo(Keccak256 hash) {
        throw new UnsupportedOperationException();
    }

    public void setSnapshotTo(Keccak256 hash) {
        throw new UnsupportedOperationException();
    }

    public byte[] serialize() {
        throw new UnsupportedOperationException();
    }

    public boolean hasStore() {
        return mutableTrie.hasStore();
    }

    public Trie getTrie() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getValueLength(byte[] key) {
        return mutableTrie.getValueLength(computeKey(key));
    }

    @Override
    public byte[] getValueHash(byte[] key) {
        return mutableTrie.getValueHash(computeKey(key));
    }
}
