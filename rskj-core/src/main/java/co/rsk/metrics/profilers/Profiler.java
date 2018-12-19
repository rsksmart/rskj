package co.rsk.metrics.profilers;

public interface Profiler {

    enum PROFILING_TYPE {
        BLOCK_EXECUTE, //0
        SIG_VALIDATION, //1
        DB_WRITE,//2
        VM_EXECUTE, //3
        DB_READ, //4
        GENESIS_GENERATION, //5
        BLOCK_CONNECTION, //6
        GENESIS_BLOCKSTORE_FLUSH, //7
        FINAL_BLOCKCHAIN_FLUSH, //8
        FILLING_EXECUTED_BLOCK, //9
        BLOCK_MINING, //10
        BLOCK_FINAL_STATE_VALIDATION,//11
        PRECOMPILED_CONTRACT_EXECUTE,//12
        LEVEL_DB_INIT,//13
        LEVEL_DB_CLOSE,//14
        LEVEL_DB_DESTROY,//15
        GET_ACCOUNT_KEY,//16
        BLOCK_REBRANCH,//17
        BLOCKSTORE_SAVE_BLOCK,//18
        TRX_RLP_PARSE,//19
        GET_REPOSITORY_SNAPSHOT, //20
        GET_ENCODED_TRX, //21
        TRX_GET_HASH, //22
        TRIE_GET_HASH, //23
        TRIE_FLUSH, //24
        TRIE_SAVE_PUT_IN_STORE, //25
        TRIE_SAVE_GET_HASH, //26
        TRIE_SAVE_GET_MESSAGE, //27
        TRIE_SAVE_GET_VALUE_HASH, //28
        TRIE_SAVE_GET_VALUE, //29
        BLOCK_VALIDATION, //30
        PRECOMPILED_CONTRACT_INIT, //31
        BUILD_TRIE_FROM_MSG, //32
        BLOCK_TXS_VAL //33
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
    void stop (Metric metric);


    /**
     * Indicates that a new block is about to be profiled
     * @param blockId id of the block about to be profiled
     * @param trxQty quantity of transactions included in the block
     */
    void newBlock(long blockId, int trxQty);

    ///**
    // * Starts the block-connection metric, which occurs once per block
    // */
    //Metric startBlockConnection();

}
