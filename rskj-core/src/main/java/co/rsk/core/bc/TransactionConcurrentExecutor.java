package co.rsk.core.bc;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.metrics.profilers.Metric;
import co.rsk.metrics.profilers.Profiler;
import co.rsk.metrics.profilers.ProfilerFactory;
import org.ethereum.core.*;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.program.ProgramResult;
import org.ethereum.vm.trace.ProgramTraceProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RunnableFuture;

public class TransactionConcurrentExecutor implements Callable<TransactionExecutionResult> {
    private static final Logger logger = LoggerFactory.getLogger("transactionconcurrentexecutor");

    private final Repository track;
    private Block block;
    private long totalGasUsed;
    private boolean vmTrace;
    private int vmTraceOptions;
    private Set<DataWord> deletedAccounts;
    private boolean acceptInvalidTransactions;
    private boolean discardInvalidTxs;
    private Metric metric;
    private ProgramTraceProcessor programTraceProcessor;
    private Coin totalPaidFees;
    private int i;
    private List<TransactionReceipt> receipts;
    private final Transaction tx;
    private final TransactionExecutorFactory transactionExecutorFactory;
    private static final Profiler profiler = ProfilerFactory.getInstance();


    public TransactionConcurrentExecutor(Transaction tx,
                                         TransactionExecutorFactory transactionExecutorFactory,
                                         Repository track,
                                         Block block,
                                         boolean vmTrace,
                                         int vmTraceOptions,
                                         boolean acceptInvalidTransactions,
                                         boolean discardInvalidTxs,
                                         Metric metric,
                                         ProgramTraceProcessor programTraceProcessor,
                                         int index) {
        this.tx = tx;
        this.track = track;
        this.block = block;
        this.totalGasUsed = 0;
        this.vmTrace = vmTrace;
        this.vmTraceOptions = vmTraceOptions;
        this.deletedAccounts = new HashSet<>();
        this.acceptInvalidTransactions = acceptInvalidTransactions;
        this.discardInvalidTxs = discardInvalidTxs;
        this.metric = metric;
        this.programTraceProcessor = programTraceProcessor;
        this.totalPaidFees = Coin.ZERO;
        this.i = index;
        this.transactionExecutorFactory = transactionExecutorFactory;
    }

    @Override
    public TransactionExecutionResult call() throws TransactionException {
        TransactionExecutor txExecutor = transactionExecutorFactory.newInstance(
                tx,
                i,
                block.getCoinbase(),
                track,
                block,
                totalGasUsed,
                vmTrace,
                vmTraceOptions,
                deletedAccounts);
        boolean transactionExecuted = txExecutor.executeTransaction();

        if (!acceptInvalidTransactions && !transactionExecuted) {
            if (discardInvalidTxs) {
                logger.warn("block: [{}] discarded tx: [{}]", block.getNumber(), tx.getHash());
                return null;
            } else {
                logger.warn("block: [{}] execution interrupted because of invalid tx: [{}]",
                        block.getNumber(), tx.getHash());
                profiler.stop(metric);
                throw new TransactionException("Invalid transaction.");
            }
        }

        if (vmTrace) {
            txExecutor.extractTrace(programTraceProcessor);
        }

        logger.trace("tx executed");

        // No need to commit the changes here. track.commit();

        logger.trace("track commit");

        long gasUsed = txExecutor.getGasUsed();
        totalGasUsed += gasUsed;
        Coin paidFees = txExecutor.getPaidFees();
        if (paidFees != null) {
            totalPaidFees = totalPaidFees.add(paidFees);
        }

        deletedAccounts.addAll(txExecutor.getResult().getDeleteAccounts());

        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setGasUsed(gasUsed);
        receipt.setCumulativeGas(totalGasUsed);

        receipt.setTxStatus(txExecutor.getReceipt().isSuccessful());
        receipt.setTransaction(tx);
        receipt.setLogInfoList(txExecutor.getVMLogs());
        receipt.setStatus(txExecutor.getReceipt().getStatus());

        logger.trace("block: [{}] executed tx: [{}]", block.getNumber(), tx.getHash());

        logger.trace("tx[{}].receipt", i);

        i++;

        receipts.add(receipt);

        logger.trace("tx done");
        return new TransactionExecutionResult(
                tx,
                deletedAccounts,
                totalGasUsed,
                totalPaidFees,
                receipt,
                txExecutor.getResult());
    }
}