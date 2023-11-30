package co.rsk;

import java.util.List;

// this class is used as a JSON response
public class FhBenchmarks {
    private ExecutionBenchmark[] benchmarksAdd;
    private ExecutionBenchmark[] benchmarksTxExecution;
    private ExecutionBenchmark[] benchmarksTranscipher;

    public FhBenchmarks(List<ExecutionBenchmark> benchmarksAdd,
                        List<ExecutionBenchmark> benchmarksTxExecution,
                        List<ExecutionBenchmark> benchmarksTranscipher) {
        this.benchmarksAdd = toArray(benchmarksAdd);
        this.benchmarksTxExecution = toArray(benchmarksTxExecution);
        this.benchmarksTranscipher = toArray(benchmarksTranscipher);
    }

    private ExecutionBenchmark[] toArray(List<ExecutionBenchmark> benchmarkList) {
        int size = benchmarkList.size();
        ExecutionBenchmark[] benchmarks = new ExecutionBenchmark[size];

        for (int i = 0; i < size; i++) {
            benchmarks[i] = benchmarkList.get(i);
        }

        return benchmarks;
    }

    public ExecutionBenchmark[] getBenchmarksAdd() {
        return benchmarksAdd;
    }

    public void setBenchmarksAdd(ExecutionBenchmark[] benchmarksAdd) {
        this.benchmarksAdd = benchmarksAdd;
    }

    public ExecutionBenchmark[] getBenchmarksTxExecution() {
        return benchmarksTxExecution;
    }

    public void setBenchmarksTxExecution(ExecutionBenchmark[] benchmarksTxExecution) {
        this.benchmarksTxExecution = benchmarksTxExecution;
    }

    public ExecutionBenchmark[] getBenchmarksTranscipher() {
        return benchmarksTranscipher;
    }

    public void setBenchmarksTranscipher(ExecutionBenchmark[] benchmarksTranscipher) {
        this.benchmarksTranscipher = benchmarksTranscipher;
    }
}
