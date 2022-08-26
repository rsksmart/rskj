package org.ethereum.datasource;

import org.bouncycastle.util.Arrays;
import org.ethereum.db.ByteArrayWrapper;

import javax.annotation.Nullable;
import java.util.*;

public class PrefixedKeyValueDataSource implements KeyValueDataSource {
    byte[] prefix;
    KeyValueDataSource base;

    public PrefixedKeyValueDataSource(byte[] prefix,KeyValueDataSource base) {
        this.prefix = prefix;
        this.base = base;
    }
    @Override
    public String getName() {
        return "Prefixed-"+base.getName();
    }

    @Override
    public void init() {
        base.init();
    }

    @Override
    public boolean isAlive() {
        return base.isAlive();
    }

    @Override
    public void close() {
        base.close();
    }

    public byte[] getKey(byte[] key) {
        return Arrays.concatenate(prefix,key);
    }

    @Nullable
    @Override
    public byte[] get(byte[] key) {
        return base.get(getKey(key));
    }

    @Override
    public byte[] put(byte[] key, byte[] value) {
        return base.put(getKey(key),value);
    }

    @Override
    public void delete(byte[] key) {
        base.delete(getKey(key));
    }

    @Override
    public Set<ByteArrayWrapper> keys() {
        return appendPrefixKey(base.keys());

    }

    public Set<ByteArrayWrapper> appendPrefixKey(Set<ByteArrayWrapper> baseKeys) {
        HashSet<ByteArrayWrapper> result = new HashSet<>();
        for (ByteArrayWrapper key : baseKeys) {
            result.add(new ByteArrayWrapper(getKey(key.getData())));
        }
        return result;
    }

    public Map<ByteArrayWrapper, byte[]> appendPrefixKey(Map<ByteArrayWrapper, byte[]> entries) {
        HashMap<ByteArrayWrapper, byte[]> newEntriesToUpdate = new HashMap<>();
        for (Map.Entry<ByteArrayWrapper, byte[]> entry : entries.entrySet()) {
            newEntriesToUpdate.put(new ByteArrayWrapper(getKey(entry.getKey().getData())), entry.getValue());
        }
        return newEntriesToUpdate;
    }

    @Override
    public void updateBatch(Map<ByteArrayWrapper, byte[]> entriesToUpdate, Set<ByteArrayWrapper> keysToRemove) {
        Map<ByteArrayWrapper, byte[]> newEntriesToUpdate = appendPrefixKey(entriesToUpdate);
        Set<ByteArrayWrapper> newKeysToRemove = appendPrefixKey(keysToRemove);
        base.updateBatch(newEntriesToUpdate,newKeysToRemove);
    }

    @Override
    public void flush() {
        base.flush();
    }

    @Override
    public boolean exists() {
        return base.exists();
    }

    @Override
    public List<String> getStats() {
        return base.getStats();
    }
}
