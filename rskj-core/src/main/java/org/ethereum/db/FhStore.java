package org.ethereum.db;

import java.util.HashMap;
import java.util.Map;

public class FhStore {

    private static FhStore instance;
    private Map<ByteArrayWrapper, ByteArrayWrapper> cache = new HashMap<>();

    private FhStore() {}

    public static FhStore getInstance() {
        if (instance == null) {
            instance = new FhStore();
        }
        return instance;
    }

    public void clear() {
        cache.clear();
    }

    public void put(byte[] hash, byte[] value) {
        if(hash.length != 32) {
            throw new RuntimeException("length must be 32");
        }
        cache.put(new ByteArrayWrapper(hash), new ByteArrayWrapper(value));
    }

    public ByteArrayWrapper get(byte[] hash) {
        return cache.get(new ByteArrayWrapper(hash)); // Updated to wrap hash into ByteArrayWrapper for correct retrieval
    }
}
