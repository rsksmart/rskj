package co.rsk.core;

import co.rsk.config.RskSystemProperties;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.db.RepositoryLocator;
import co.rsk.db.StateRootHandler;
import co.rsk.trie.TrieStore;

/**
 * Generates a scope in which block execution is possible.
 * <p>
 * The idea is to develop this class and implement the functionality not only to external RPC calls but also to the main
 * blockchain execution.
 * <p>
 * This allows each scope to handle its own cache, commits and flushes.
 */
public class ExecutionScopeFactory {

    private final RskSystemProperties rskSystemProperties;
    private final StateRootHandler stateRootHandler;
    private final TrieStoreFactory trieStoreFactory;
    private final TransactionExecutorFactory transactionExecutorFactory;

    public ExecutionScopeFactory(
            RskSystemProperties rskSystemProperties,
            StateRootHandler stateRootHandler,
            TrieStoreFactory trieStoreFactory,
            TransactionExecutorFactory transactionExecutorFactory) {

        this.rskSystemProperties = rskSystemProperties;
        this.stateRootHandler = stateRootHandler;
        this.trieStoreFactory = trieStoreFactory;
        this.transactionExecutorFactory = transactionExecutorFactory;
    }

    /**
     * @return A new execution scope.
     */
    public ExecutionScope newScope() {
        TrieStore trieStore = trieStoreFactory.newInstance();
        RepositoryLocator locator = new RepositoryLocator(trieStore, stateRootHandler);

        return new ExecutionScope(
                new BlockExecutor(
                        rskSystemProperties.getActivationConfig(),
                        locator,
                        stateRootHandler,
                        transactionExecutorFactory),
                trieStore);
    }

    /**
     * Instantiates a new TrieStore each time a scope is required.
     */
    public interface TrieStoreFactory {
        TrieStore newInstance();
    }
}
