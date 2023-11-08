package org.ethereum.db;

public class NoiseBudgetBenchmark {

    private final int noiseBudget;

    public NoiseBudgetBenchmark(int noiseBudget) {
        this.noiseBudget = noiseBudget;
    }

    public long getNoiseBudget() {
        return noiseBudget;
    }
}
