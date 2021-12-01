package co.rsk.core.bc;

import co.rsk.core.Coin;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.metrics.profilers.Metric;
import org.ethereum.core.*;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.trace.ProgramTraceProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.LongAccumulator;

public class TransactionConcurrentExecutor implements Callable<List<TransactionExecutionResult>> {
    private static final Logger logger = LoggerFactory.getLogger("transactionconcurrentexecutor");

    private final Repository track;
    private Block block;
    private LongAccumulator totalGasUsed;
    private boolean vmTrace;
    private int vmTraceOptions;
    private Set<DataWord> deletedAccounts;
    private boolean acceptInvalidTransactions;
    private boolean discardInvalidTxs;
    private ProgramTraceProcessor programTraceProcessor;
    private Coin totalPaidFees;
    private List<TransactionReceipt> receipts;
    private final Map<Integer, Transaction> txs;
    private final TransactionExecutorFactory transactionExecutorFactory;


    public TransactionConcurrentExecutor(Map<Integer, Transaction> txs,
                                         TransactionExecutorFactory transactionExecutorFactory,
                                         LongAccumulator totalGasUsed,
                                         Repository track,
                                         Block block,
                                         boolean vmTrace,
                                         int vmTraceOptions,
                                         boolean acceptInvalidTransactions,
                                         boolean discardInvalidTxs,
                                         ProgramTraceProcessor programTraceProcessor) {
        this.txs = txs;
        this.track = track;
        this.block = block;
        this.totalGasUsed = totalGasUsed;
        this.vmTrace = vmTrace;
        this.vmTraceOptions = vmTraceOptions;
        this.deletedAccounts = new HashSet<>();
        this.acceptInvalidTransactions = acceptInvalidTransactions;
        this.discardInvalidTxs = discardInvalidTxs;
        this.programTraceProcessor = programTraceProcessor;
        this.totalPaidFees = Coin.ZERO;
        this.transactionExecutorFactory = transactionExecutorFactory;
        receipts = new ArrayList<>();
    }

    @Override
    public List<TransactionExecutionResult> call() throws TransactionException {
        List<TransactionExecutionResult> results = new ArrayList<>();
        for (Map.Entry<Integer, Transaction> txMap :
                txs.entrySet()) {
            Transaction tx = txMap.getValue();
            Integer txIndex = txMap.getKey();
            TransactionExecutor txExecutor = transactionExecutorFactory.newInstance(
                    tx,
                    txIndex,
                    block.getCoinbase(),
                    track,
                    block,
                    totalGasUsed.get(),
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
            totalGasUsed.accumulate(gasUsed);
            Coin paidFees = txExecutor.getPaidFees();
            if (paidFees != null) {
                totalPaidFees = totalPaidFees.add(paidFees);
            }

            deletedAccounts.addAll(txExecutor.getResult().getDeleteAccounts());

            TransactionReceipt receipt = new TransactionReceipt();
            receipt.setGasUsed(gasUsed);
            receipt.setCumulativeGas(totalGasUsed.get());

            receipt.setTxStatus(txExecutor.getReceipt().isSuccessful());
            receipt.setTransaction(tx);
            receipt.setLogInfoList(txExecutor.getVMLogs());
            receipt.setStatus(txExecutor.getReceipt().getStatus());

            logger.trace("block: [{}] executed tx: [{}]", block.getNumber(), tx.getHash());

            logger.trace("tx[{}].receipt", txIndex);

            receipts.add(receipt);

            logger.trace("tx done");
            results.add(new TransactionExecutionResult(
                    tx,
                    deletedAccounts,
                    totalGasUsed.get(),
                    totalPaidFees,
                    receipt,
                    txExecutor.getResult()));

        }

        return results;
    }
}