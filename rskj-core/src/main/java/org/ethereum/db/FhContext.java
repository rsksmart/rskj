package org.ethereum.db;

import co.rsk.ExecutionBenchmark;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FhContext {

    private static FhContext instance;
    private final Map<ByteArrayWrapper, ByteArrayWrapper> cache = new HashMap<>();
    private final Map<String, ByteArrayWrapper> encryptedParams = new HashMap<>();

    // benchmarks
    private final boolean enableBenchmark = true;
    private final List<NoiseBudgetBenchmark> noiseBudgetBenchmark = new ArrayList<>();
    private final List<ExecutionBenchmark> transcipheringBenchmarks = new ArrayList<>();
    private final List<ExecutionBenchmark> addOperationBenchmarks = new ArrayList<>();
    private final List<ExecutionBenchmark> txExecutionBenchmarks = new ArrayList<>();

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

    public boolean enableBenchmarks() {
        return this.enableBenchmark;
    }

    public void addBenchmarkNoiseBudget(int noiseBudget) {
        this.noiseBudgetBenchmark.add(new NoiseBudgetBenchmark(noiseBudget));
    }

    public void addBenchmarkTranscipher(long start, long end, String id) {
        this.transcipheringBenchmarks.add(new ExecutionBenchmark(id,  end - start));
    }

    public void addBenchmarkAdd(long start, long end, String id) {
        this.addOperationBenchmarks.add(new ExecutionBenchmark(id, end - start));
    }

    public void addBenchmarkTxExecution(String id, long start, long end) {
        this.txExecutionBenchmarks.add(new ExecutionBenchmark(id, end - start));
    }

    public List<ExecutionBenchmark> getBenchmarksAdd() {
        return this.addOperationBenchmarks;
    }

    public List<ExecutionBenchmark> getBenchmarksTranscipher() {
        return this.transcipheringBenchmarks;
    }

    public List<ExecutionBenchmark> getBenchmarksTxExecution() {
        return this.txExecutionBenchmarks;
    }
}
