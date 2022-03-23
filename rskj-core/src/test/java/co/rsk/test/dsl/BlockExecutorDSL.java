package co.rsk.test.dsl;

import co.rsk.core.TransactionExecutorFactory;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.db.RepositoryLocator;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.TransactionExecutor;

import java.util.HashMap;
import java.util.Map;

public class BlockExecutorDSL extends BlockExecutor {

    // all the used transaction-executors during a block processing
    private final Map<String, TransactionExecutor> transactionExecutors = new HashMap<>();

    public BlockExecutorDSL(ActivationConfig activationConfig, RepositoryLocator repositoryLocator,
                            TransactionExecutorFactory transactionExecutorFactory) {
        super(activationConfig, repositoryLocator, transactionExecutorFactory);
    }

    /**
     * This method is used to keep track of each TransactionExecutor used in each transaction.
     * */
    @Override
    protected boolean executeTransaction(TransactionExecutor transactionExecutor) {
        boolean result = super.executeTransaction(transactionExecutor);

        String txHash = transactionExecutor.getReceipt()
                .getTransaction()
                .getHash()
                .toHexString();

        transactionExecutors.put(txHash, transactionExecutor);

        return result;
    }

    public Map<String, TransactionExecutor> getTransactionExecutors() {
        return transactionExecutors;
    }
}
