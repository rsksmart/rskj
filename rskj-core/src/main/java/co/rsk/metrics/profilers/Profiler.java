package co.rsk.metrics.profilers;

public interface Profiler {

    enum PROFILING_TYPE {
        BLOCK_EXECUTE,
        SIG_VALIDATION,
        DATA_FLUSH,
        VM_EXECUTE,
        DISK_READ,
        GENESIS_GENERATION,
        BLOCK_CONNECTION,
        GENESIS_BLOCKSTORE_FLUSH,
        FINAL_BLOCKCHAIN_FLUSH,
        FILLING_EXECUTED_BLOCK,
        BLOCK_MINING,
        BLOCK_FINAL_STATE_VALIDATION,
        PRECOMPILED_CONTRACT_EXECUTE,
        LEVEL_DB_INIT,
        LEVEL_DB_CLOSE
    }

    int start (PROFILING_TYPE type);
    void stop (int id);
    void newBlock(long blockId, int trxQty);

    }
