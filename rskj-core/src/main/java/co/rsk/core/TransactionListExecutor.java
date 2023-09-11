package co.rsk.core;

import co.rsk.crypto.Keccak256;
import org.ethereum.core.*;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.ProgramResult;
import org.ethereum.vm.trace.ProgramTraceProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.Callable;

public class TransactionListExecutor implements Callable<Boolean> {

    private static final Logger logger = LoggerFactory.getLogger("transactionlistexecutor");

    private final TransactionExecutorFactory transactionExecutorFactory;
    private final List<Transaction> transactions;
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
    private long sublistGasLimit;
    private final boolean remascEnabled;
    private long totalGas;
    private int i;
    private final boolean registerProgramResults;
    private final boolean precompiledContractsAllowed;
    private Coin totalPaidFees;

    public TransactionListExecutor(
            List<Transaction> transactions,
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
            int firstTxIndex,
            Coin totalPaidFees,
            boolean remascEnabled,
            boolean precompiledContractsAllowed,
            long sublistGasLimit) {
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
        this.totalGas = 0L;
        this.i = firstTxIndex;
        this.totalPaidFees = totalPaidFees;
        this.remascEnabled = remascEnabled;
        this.precompiledContractsAllowed = precompiledContractsAllowed;
        this.sublistGasLimit = sublistGasLimit;
    }

    @Override
    public Boolean call() {
        long totalGasUsed = 0;

        for (Transaction tx : transactions) {

            int numberOfTransactions = block.getTransactionsList().size();
            boolean isRemascTransaction = tx.isRemascTransaction(this.i, numberOfTransactions);

            addFeesToRemascIfEnabled(isRemascTransaction);

            TransactionExecutor txExecutor = transactionExecutorFactory.newInstance(
                    tx,
                    i,
                    block.getCoinbase(),
                    track,
                    block,
                    totalGasUsed,
                    vmTrace,
                    vmTraceOptions,
                    deletedAccounts,
                    true,
                    sublistGasLimit);
            boolean transactionSucceeded = txExecutor.executeTransaction();
            if (!this.precompiledContractsAllowed && txExecutor.precompiledContractHasBeenCalled()) {
                transactionSucceeded = false;
            }

            if (!acceptInvalidTransactions && !transactionSucceeded) {
                if (discardIfInvalid(tx, numberOfTransactions, isRemascTransaction)) {
                    return false;
                }
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

            long txGasUsed = txExecutor.getGasConsumed();
            totalGasUsed += txGasUsed;

            addPaidFeesToToal(txExecutor);

            // It's used just for testing, the last tx should be always the REMASC.
            payToRemascWhenThereIsNoRemascTx(numberOfTransactions, isRemascTransaction);

            deletedAccounts.addAll(txExecutor.getResult().getDeleteAccounts());

            TransactionReceipt receipt = createTransactionReceipt(totalGasUsed, tx, txExecutor, txGasUsed);

            logger.trace("block: [{}] executed tx: [{}]", block.getNumber(), tx.getHash());

            i++;

            logger.trace("tx[{}].receipt", i);

            receipts.put(i, receipt);

            logger.trace("tx[{}] done", i);
        }
        totalGas += totalGasUsed;
        return true;
    }

    private boolean discardIfInvalid(Transaction tx, int numberOfTransactions, boolean isRemascTransaction) {
        // It's used just for testing, the last tx should be always the REMASC.
        payToRemascWhenThereIsNoRemascTx(numberOfTransactions, isRemascTransaction);
        if (!discardInvalidTxs) {
            logger.warn("block: [{}] execution interrupted because of invalid tx: [{}]",
                    block.getNumber(), tx.getHash()
            );
            return true;
        }

        logger.warn("block: [{}] discarded tx: [{}]", block.getNumber(), tx.getHash());
        return false;
    }

    private TransactionReceipt createTransactionReceipt(long totalGasUsed, Transaction tx, TransactionExecutor txExecutor, long txGasUsed) {
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setGasUsed(txGasUsed);
        receipt.setCumulativeGas(totalGasUsed);

        receipt.setTxStatus(txExecutor.getReceipt().isSuccessful());
        receipt.setTransaction(tx);
        receipt.setLogInfoList(txExecutor.getVMLogs());
        receipt.setStatus(txExecutor.getReceipt().getStatus());
        return receipt;
    }

    private void addPaidFeesToToal(TransactionExecutor txExecutor) {
        Coin txPaidFees = txExecutor.getPaidFees();
        if (txPaidFees != null) {
            totalPaidFees = totalPaidFees.add(txPaidFees);
        }
    }

    private void addFeesToRemascIfEnabled(boolean isRemascTransaction) {
        if (this.remascEnabled && isRemascTransaction) {
            addFeesToRemasc();
        }
    }

    private void payToRemascWhenThereIsNoRemascTx(int numberOfTransactions, boolean isRemascTransaction) {
        boolean isLastTx = this.i == numberOfTransactions - 1;
        if (this.remascEnabled && isLastTx && !isRemascTransaction) {
            addFeesToRemasc();
        }
    }

    private void addFeesToRemasc() {
        if (this.totalPaidFees.compareTo(Coin.ZERO) > 0) {
            logger.trace("Adding fee to remasc contract account");
            track.addBalance(PrecompiledContracts.REMASC_ADDR, this.totalPaidFees);
        }
    }

    public Repository getRepository() {
        return this.track;
    }

    public Set<DataWord> getDeletedAccounts() {
        return new HashSet<>(this.deletedAccounts);
    }

    public Map<Integer, TransactionReceipt> getReceipts() {
        return this.receipts;
    }

    public Map<Integer, Transaction> getExecutedTransactions() {
        return this.executedTransactions;
    }

    public Map<Keccak256, ProgramResult> getTransactionResults() {
        return this.transactionResults;
    }

    public Coin getTotalFees() {
        return this.totalPaidFees;
    }

    public long getTotalGas() {
        return this.totalGas;
    }
}
