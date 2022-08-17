package co.rsk.core;

import co.rsk.core.bc.IReadWrittenKeysTracker;
import co.rsk.crypto.Keccak256;
import org.ethereum.core.*;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.program.ProgramResult;
import org.ethereum.vm.trace.ProgramTraceProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAccumulator;

public class TransactionListExecutor implements Callable<Boolean> {

    private static final Logger logger = LoggerFactory.getLogger("transactionlistexecutor");

    private final TransactionExecutorFactory transactionExecutorFactory;
    private final List<Transaction> transactions;
    private final IReadWrittenKeysTracker readWrittenKeysTracker;
    private final Block block;
    private final Repository track;
    private final boolean vmTrace;
    private final int vmTraceOptions;
    private final Set<DataWord> deletedAccounts;
    private final boolean discardInvalidTxs;
    private final boolean acceptInvalidTransactions;
    private final Map<Integer, Transaction> executedTransactions;
    private final Map<Integer, TransactionReceipt> receipts;
    private final Map<Keccak256, ProgramResult> transactionResults;
    private final ProgramTraceProcessor programTraceProcessor;
    private final AtomicReference<Coin> accumulatedFees;
    private final LongAccumulator accumulatedGas;

    private int i;
    private final boolean registerProgramResults;

    public TransactionListExecutor(
            List<Transaction> transactions,
            IReadWrittenKeysTracker readWrittenKeysTracker,
            Block block,
            TransactionExecutorFactory transactionExecutorFactory,
            Repository track,
            boolean vmTrace,
            int vmTraceOptions,
            Set<DataWord> deletedAccounts,
            boolean discardInvalidTxs,
            boolean acceptInvalidTransactions,
            Map<Integer, TransactionReceipt> receipts,
            Map<Integer, Transaction> executedTransactions,
            Map<Keccak256, ProgramResult> transactionResults,
            boolean registerProgramResults,
            @Nullable ProgramTraceProcessor programTraceProcessor,
            AtomicReference<Coin> accumulatedFees,
            LongAccumulator accumulatedGas,
            int firstTxIndex) {
        this.readWrittenKeysTracker = readWrittenKeysTracker;
        this.block = block;
        this.transactionExecutorFactory = transactionExecutorFactory;
        this.track = track;
        this.vmTrace = vmTrace;
        this.vmTraceOptions = vmTraceOptions;
        this.transactions = new ArrayList<>(transactions);
        this.deletedAccounts = deletedAccounts;
        this.discardInvalidTxs = discardInvalidTxs;
        this.acceptInvalidTransactions = acceptInvalidTransactions;
        this.executedTransactions = executedTransactions;
        this.receipts = receipts;
        this.registerProgramResults = registerProgramResults;
        this.transactionResults = transactionResults;
        this.programTraceProcessor = programTraceProcessor;
        this.accumulatedFees = accumulatedFees;
        this.accumulatedGas = accumulatedGas;
        this.i = firstTxIndex;
    }

    @Override
    public Boolean call() {
        long totalGasUsed = 0;
        Coin totalPaidFees = Coin.ZERO;

        for (Transaction tx : transactions) {
            TransactionExecutor txExecutor = transactionExecutorFactory.newInstance(
                    tx,
                    i,
                    block.getCoinbase(),
                    track,
                    block,
                    totalGasUsed,
                    vmTrace,
                    vmTraceOptions,
                    deletedAccounts
            );
            boolean transactionExecuted = txExecutor.executeTransaction();

            if (readWrittenKeysTracker.hasCollided()) {
                return false;
            }

            if (!acceptInvalidTransactions && !transactionExecuted) {
                if (!discardInvalidTxs) {
                    logger.warn("block: [{}] execution interrupted because of invalid tx: [{}]",
                            block.getNumber(), tx.getHash()
                    );
                    return false;
                }

                logger.warn("block: [{}] discarded tx: [{}]", block.getNumber(), tx.getHash());
                continue;
            }

            executedTransactions.put(i, tx);

            if (this.registerProgramResults) {
                this.transactionResults.put(tx.getHash(), txExecutor.getResult());
            }

            if (vmTrace) {
                txExecutor.extractTrace(programTraceProcessor);
            }

            logger.trace("tx[{}] executed", i + 1);
            logger.trace("track commit");

            long txGasUsed = txExecutor.getGasUsed();
            totalGasUsed += txGasUsed;

            Coin txPaidFees = txExecutor.getPaidFees();
            if (txPaidFees != null) {
                totalPaidFees = totalPaidFees.add(txPaidFees);
            }

            deletedAccounts.addAll(txExecutor.getResult().getDeleteAccounts());

            TransactionReceipt receipt = new TransactionReceipt();
            receipt.setGasUsed(txGasUsed);
            receipt.setCumulativeGas(totalGasUsed);

            receipt.setTxStatus(txExecutor.getReceipt().isSuccessful());
            receipt.setTransaction(tx);
            receipt.setLogInfoList(txExecutor.getVMLogs());
            receipt.setStatus(txExecutor.getReceipt().getStatus());

            logger.trace("block: [{}] executed tx: [{}]", block.getNumber(), tx.getHash());

            logger.trace("tx[{}].receipt", i + 1);

            i++;

            receipts.put(i, receipt);

            logger.trace("tx[{}] done", i);
        }
        accumulatedGas.accumulate(totalGasUsed);
        accumulatedFees.getAndAccumulate(totalPaidFees, Coin::add);

        return true;
    }
}
