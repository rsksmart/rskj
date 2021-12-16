package co.rsk.core.bc;

import co.rsk.core.TransactionExecutorFactory;
import co.rsk.crypto.Keccak256;
import co.rsk.metrics.profilers.Metric;
import co.rsk.metrics.profilers.Profiler;
import co.rsk.metrics.profilers.ProfilerFactory;
import org.ethereum.core.*;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.program.ProgramResult;
import org.ethereum.vm.trace.ProgramTraceProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.LongAccumulator;

public class TransactionConcurrentExecutor implements Callable<Void> {
    private static final Logger logger = LoggerFactory.getLogger("transactionconcurrentexecutor");
    private static final Profiler profiler = ProfilerFactory.getInstance();

    private final Repository track;
    private final int thread;
    private final Queue<Transaction> executedTransactions;
    private final Map<Keccak256, ProgramResult> transactionResults;
    private final boolean registerProgramResults;
    private Block block;
    private LongAccumulator totalGasUsed;
    private boolean vmTrace;
    private int vmTraceOptions;
    private Set<DataWord> deletedAccounts;
    private boolean acceptInvalidTransactions;
    private boolean discardInvalidTxs;
    private ProgramTraceProcessor programTraceProcessor;
    private LongAccumulator totalPaidFees;
    private Queue<TransactionReceipt> receipts;
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
                                         ProgramTraceProcessor programTraceProcessor,
                                         int thread,
                                         Queue<TransactionReceipt> receipts,
                                         Queue<Transaction> executedTransactions,
                                         Set<DataWord> deletedAccounts,
                                         LongAccumulator totalPaidFees,
                                         boolean registerProgramResults,
                                         Map<Keccak256, ProgramResult> transactionResults) {
        this.txs = txs;
        this.track = track;
        this.block = block;
        this.totalGasUsed = totalGasUsed;
        this.vmTrace = vmTrace;
        this.vmTraceOptions = vmTraceOptions;
        this.acceptInvalidTransactions = acceptInvalidTransactions;
        this.discardInvalidTxs = discardInvalidTxs;
        this.programTraceProcessor = programTraceProcessor;
        this.thread = thread;
        this.transactionExecutorFactory = transactionExecutorFactory;
        this.receipts = receipts;
        this.executedTransactions = executedTransactions;
        this.deletedAccounts = deletedAccounts;
        this.totalPaidFees = totalPaidFees;
        this.registerProgramResults = registerProgramResults;
        this.transactionResults = transactionResults;
    }

    @Override
    public Void call() throws TransactionException {
        Metric parallelMetric = profiler.start(thread == 1 ? Profiler.PROFILING_TYPE.BLOCK_EXECUTE_PARALLEL_T1: Profiler.PROFILING_TYPE.BLOCK_EXECUTE_PARALLEL_T2);

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
                    continue;
                } else {
                    logger.warn("block: [{}] execution interrupted because of invalid tx: [{}]",
                            block.getNumber(), tx.getHash());
                    profiler.stop(parallelMetric);
                    throw new TransactionException("Block execution interrupted.");
                }
            }

            executedTransactions.add(tx);

            if (registerProgramResults) {
                this.transactionResults.put(tx.getHash(), txExecutor.getResult());
            }

            if (vmTrace) {
                txExecutor.extractTrace(programTraceProcessor);
            }

            logger.trace("tx executed");

            logger.trace("track commit");

            long gasUsed = txExecutor.getGasUsed();
            totalGasUsed.accumulate(gasUsed);
            BigInteger paidFees = txExecutor.getPaidFees().asBigInteger();
            if (paidFees != null) {
                totalPaidFees.accumulate(paidFees.longValue());
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

            logger.trace("tx[{}].receipt", txIndex + 1);

            receipts.add(receipt);

            logger.trace("tx done");
        }
        profiler.stop(parallelMetric);
        return null;
    }
}