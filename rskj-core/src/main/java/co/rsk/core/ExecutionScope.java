package co.rsk.core;

import co.rsk.core.bc.BlockExecutor;
import co.rsk.trie.TrieStore;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.vm.trace.ProgramTraceProcessor;

/**
 * Defines a new scope of execution. Should only be created via the {@link ExecutionScopeFactory}
 */
public class ExecutionScope {

    private final BlockExecutor executor;
    private final TrieStore trieStore;

    public ExecutionScope(BlockExecutor executor, TrieStore trieStore) {
        this.executor = executor;
        this.trieStore = trieStore;
    }

    public void traceBlock(ProgramTraceProcessor programTraceProcessor, int vmTraceOptions, Block block, BlockHeader parent) {
        this.executor.traceBlock(
                programTraceProcessor,
                vmTraceOptions,
                block,
                parent,
                false,
                false);
    }

    public void flush() {
        trieStore.flush();
    }
}