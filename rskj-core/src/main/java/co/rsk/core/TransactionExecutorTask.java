package co.rsk.core;

import co.rsk.core.bc.BlockExecutor;
import co.rsk.core.bc.BlockResult;
import co.rsk.metrics.profilers.Profiler;
import co.rsk.metrics.profilers.ProfilerFactory;
import org.ethereum.core.*;
import org.ethereum.vm.DataWord;
import org.slf4j.Logger;

import javax.swing.text.html.Option;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * This class is in charge of executing one transaction, using an TransactionExecutor for this purpose.
 * It implements Callable interface so that it can be run in a separate thread (using an ExecutorService).
 * Because several transaction can then be executed in parallel, we gather all block data that is shared between
 * them in a dedicated structure BlockSharedData. This allow us to manage concurrent access more explicitly
 */
public class TransactionExecutorTask implements Callable<Optional<TransactionReceipt>> {

    private static final Profiler profiler = ProfilerFactory.getInstance();
    private final TransactionExecutorFactory transactionExecutorFactory;
    private boolean acceptInvalidTransactions;
    private boolean discardInvalidTxs;
    private Logger logger;
    private Transaction tx;
    private int txindex;
    private Block block;
    BlockExecutor.BlockSharedData blockSharedData;

    public TransactionExecutorTask(
            TransactionExecutorFactory transactionExecutorFactory,
            Transaction tx,
            int txindex,
            Block block,
            BlockExecutor.BlockSharedData blockSharedData,
            Logger logger,
            boolean acceptInvalidTransactions,
            boolean discardInvalidTxs) {
        this.transactionExecutorFactory = transactionExecutorFactory;
        this.tx = tx;
        this.txindex = txindex;
        this.block = block;
        this.blockSharedData = blockSharedData;
        this.logger = logger;
        this.acceptInvalidTransactions = acceptInvalidTransactions;
        this.discardInvalidTxs = discardInvalidTxs;
    }

    @Override
    public Optional<TransactionReceipt> call() throws Exception {
        boolean vmTrace = blockSharedData.getProgramTraceProcessor() != null;
        logger.trace("apply block: [{}] tx: [{}] ", block.getNumber(), blockSharedData.getReceipts().size());

        TransactionExecutor txExecutor = transactionExecutorFactory.newInstance(
                tx,
                txindex,
                block.getCoinbase(),
                blockSharedData.getRepository(),
                block,
                blockSharedData.getTotalGasUsed(),
                vmTrace,
                blockSharedData.getDeletedAccounts());

        boolean transactionExecuted = txExecutor.executeTransaction();
        if (!acceptInvalidTransactions && !transactionExecuted) {
            if (discardInvalidTxs) {
                logger.warn("block: [{}] discarded tx: [{}]", block.getNumber(), tx.getHash());
                return Optional.empty();
            } else {
                logger.warn("block: [{}] execution interrupted because of invalid tx: [{}]",
                        block.getNumber(), tx.getHash());
                throw new InterruptedException("Invalid tx " + tx.getHash().toString());
            }
        }

        blockSharedData.addExecutedTransaction(tx);

        if (vmTrace) {
            txExecutor.extractTrace(blockSharedData.getProgramTraceProcessor());
        }

        logger.trace("tx executed");

        // No need to commit the changes here. track.commit();

        logger.trace("track commit");

        long gasUsed = txExecutor.getGasUsed();
        blockSharedData.addGasUsed(gasUsed);
        Coin paidFees = txExecutor.getPaidFees();
        if (paidFees != null) {
            blockSharedData.addPaidFees(paidFees);
        }

        blockSharedData.addDeletedAccounts(txExecutor.getResult().getDeleteAccounts());

        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setGasUsed(gasUsed);
        receipt.setCumulativeGas(blockSharedData.getTotalGasUsed());

        receipt.setTxStatus(txExecutor.getReceipt().isSuccessful());
        receipt.setTransaction(tx);
        receipt.setLogInfoList(txExecutor.getVMLogs());
        receipt.setStatus(txExecutor.getReceipt().getStatus());

        logger.trace("block: [{}] executed tx: [{}]", block.getNumber(), tx.getHash());

        int i = blockSharedData.addReceipt(receipt);
        logger.trace("tx[{}].receipt", i);

        logger.trace("tx done");
        return Optional.of(receipt);

    }
}
