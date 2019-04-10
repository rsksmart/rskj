package co.rsk.metrics.profilers;


/**
 * Interface every profiler has to implement. The profiler is responsible of the profiling logic.
 * Different profilers may take completely different measurements or use different approaches
 */
public interface Profiler {

    /**
     *List of possible measurement categories (or types).
     * Depending on what is actually being profiled, new categories can be added or
     * categories not needed can be removed
     */
    enum PROFILING_TYPE {
        GENESIS_GENERATION,
        BLOCK_REBRANCH,
        BLOCK_CONNECTION,
        BLOCK_EXECUTE,
        PRECOMPILED_CONTRACT_INIT,
        PRECOMPILED_CONTRACT_EXECUTE,
        VM_EXECUTE,
        BLOCK_VALIDATION,
        BLOCK_TXS_VALIDATION,
        BLOCK_FINAL_STATE_VALIDATION,
        KEY_RECOV_FROM_SIG,
        DB_READ,
        DB_WRITE,
        FINAL_BLOCKCHAIN_FLUSH,
        FILLING_EXECUTED_BLOCK,
        LEVEL_DB_INIT,
        LEVEL_DB_CLOSE,
        LEVEL_DB_DESTROY,
        GET_REPOSITORY_SNAPSHOT,
        GET_ENCODED_TRX,
        TRX_GET_HASH,
        TRIE_TO_MESSAGE,
        TRIE_GET_VALUE_FROM_KEY,
        BUILD_TRIE_FROM_MSG,
    }


    /**
     * Starts a metric of a specific type
     * @param type task category that needs to be profiled
     * @return new Metric instance
     */
    Metric start(PROFILING_TYPE type);

    /**
     * Stops a metric finalizing all the properties being profiled
     * @param metric Metric instance that needs to be finalized
     */
    void stop(Metric metric);
}
