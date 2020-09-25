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
        // BLOCK_CONNECTION - BLOCK_EXECUTE = Time consumed fetching the block and, after block execution, saving the data
        // that means some DB_READ and DB_WRITE will be included here (and contained in the DB_READ and DB_WRITE categories again)
        BLOCK_CONNECTION,
        BLOCK_EXECUTE,
        PRECOMPILED_CONTRACT_INIT,
        PRECOMPILED_CONTRACT_EXECUTE,
        VM_EXECUTE,
        BLOCK_VALIDATION, //Note some validators call TRIE_GET_HASH
        BLOCK_TXS_VALIDATION, //Note that it internally calls KEY_RECOV_FROM_SIG
        BLOCK_FINAL_STATE_VALIDATION,
        KEY_RECOV_FROM_SIG,
        DB_READ,
        DB_WRITE,
        FILLING_EXECUTED_BLOCK,
        LEVEL_DB_INIT,
        LEVEL_DB_CLOSE,
        LEVEL_DB_DESTROY,
        TRIE_GET_VALUE_FROM_KEY,
        BEFORE_BLOCK_EXEC,
        AFTER_BLOCK_EXEC,
        BUILD_TRIE_FROM_MSG,
        TRIE_TO_MESSAGE, //Currently inactive, to measure, add the hooks in Trie::toMessage() and Trie::toMessageOrchid()
        TRIE_CONVERTER_GET_ACCOUNT_ROOT,
        BLOCKCHAIN_FLUSH
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
