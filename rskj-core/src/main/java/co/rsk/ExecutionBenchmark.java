package co.rsk;

public class ExecutionBenchmark {

    private final String txHash;
    private final long value;

    public ExecutionBenchmark(String txHash, Long value) {
        this.txHash = txHash;
        this.value = value;
    }
}
