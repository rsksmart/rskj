package org.ethereum.db;

import java.util.HashMap;
import java.util.Map;

public class FhStore {

    // todo(fedejinich) i should use byte[] for the external world, and bytearraywrapper for the internal state

    private static FhStore instance;
    private Map<ByteArrayWrapper, ByteArrayWrapper> cache = new HashMap<>();
    private Map<String, ByteArrayWrapper> encryptedParams = new HashMap<>();

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

    public ByteArrayWrapper getEncryptedData(byte[] hash) {
        return cache.get(new ByteArrayWrapper(hash)); // Updated to wrap hash into ByteArrayWrapper for correct retrieval
    }

    public ByteArrayWrapper getEncryptedParam(String name) {
        return this.encryptedParams.get(name);
    }

    public void putEncryptedParam(String name, byte[] hash) {
        this.encryptedParams.put(name, new ByteArrayWrapper(hash));
    }
}
