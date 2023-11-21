package org.ethereum.db;

import co.rsk.db.MutableTrieCache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FhContext {

    // todo(fedejinich) i should use byte[] for the external world, and bytearraywrapper for the internal state

    private static FhContext instance;
    private final Map<ByteArrayWrapper, ByteArrayWrapper> cache = new HashMap<>();
    private final Map<String, ByteArrayWrapper> encryptedParams = new HashMap<>();
    private final List<NoiseBudgetBenchmark> noiseBudgetBenchmark = new ArrayList<>();
    private final boolean enableBenchmark = true;
    private final Map<String, Long> transcipherBenchmarks = new HashMap<>();

    private FhContext() {}

    public static FhContext getInstance() {
        if (instance == null) {
            instance = new FhContext();
        }
        return instance;
    }

    public void clear() {
        this.cache.clear();
        this.encryptedParams.clear();
    }

    public ByteArrayWrapper getEncryptedData(byte[] hash) {
        return cache.get(new ByteArrayWrapper(hash)); // Updated to wrap hash into ByteArrayWrapper for correct retrieval
    }

    public void putEncryptedData(byte[] hash, byte[] value) {
        if(hash.length != 32) {
            throw new RuntimeException("length must be 32");
        }
        cache.put(new ByteArrayWrapper(hash), new ByteArrayWrapper(value));
    }

    public ByteArrayWrapper getEncryptedParam(String name) {
        return this.encryptedParams.get(name);
    }

    public void putEncryptedParam(String name, byte[] hash) {
        this.encryptedParams.put(name, new ByteArrayWrapper(hash));
    }

    // this enables/disables benchmarking on this PoC
    public boolean enableBenchmark() {
        return this.enableBenchmark;
    }

    public void addNoiseBudgetBenchmark(int noiseBudget) {
        this.noiseBudgetBenchmark.add(new NoiseBudgetBenchmark(noiseBudget));
    }

    public void addTranscipherBenchmark(long start, long end, String id) {
        this.transcipherBenchmarks.put(id, start - end);
    }
}
