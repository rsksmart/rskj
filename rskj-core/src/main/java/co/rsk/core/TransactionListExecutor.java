package co.rsk.core;

import co.rsk.core.bc.supply.BalanceLookup;
import co.rsk.core.bc.supply.SupplyBug;
import co.rsk.core.bc.supply.SupplyConservationCheck;
import co.rsk.core.bc.supply.SupplyDelta;
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
    private final Set<RskAddress> concurrentContractsDisallowed;
    private Coin totalPaidFees;

    /**
     * Balances as they stood below this sublist's repository, for accounts it has not touched yet.
     * Held separately from {@code track} so that reading it does not contend with the other
     * sublists running concurrently.
     */
    private final BalanceLookup baseBalances;

    private final SupplyConservationCheck supplyCheck;

    /**
     * Balance of each account this sublist has modified, as of the end of the last transaction.
     * This is the "before" view for the next transaction's supply scope: transactions in a sublist
     * share one repository, so the repository alone cannot say where one transaction's changes end
     * and the next one's begin.
     */
    private final Map<RskAddress, Coin> balancesAtLastTxEnd = new HashMap<>();

    /**
     * Fees credited in bulk at the start of the current iteration, which earlier transactions
     * already accounted for as pending inflow. Netted out so the credit is not read as a mint.
     */
    private Coin feesSettledThisIteration = Coin.ZERO;

    private final SupplyBug supplyBug;

    /**
     * Only the sublist holding the block's first transaction injects the deliberate bug, so that a
     * block mints exactly once however its transactions were split into sublists.
     */
    private boolean supplyBugInjected;

    private volatile boolean supplyViolation;

    private volatile boolean stopped;

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
            Set<RskAddress> concurrentContractsDisallowed,
            long sublistGasLimit,
            BalanceLookup baseBalances,
            SupplyBug supplyBug) {
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
        this.concurrentContractsDisallowed = Collections.unmodifiableSet(new HashSet<>(concurrentContractsDisallowed));
        this.sublistGasLimit = sublistGasLimit;
        this.baseBalances = baseBalances;
        this.supplyCheck = new SupplyConservationCheck(block);
        this.supplyBug = supplyBug;
        this.supplyBugInjected = firstTxIndex != 0;
    }

    @Override
    public Boolean call() {
        if (stopped) {
            return false;
        }

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
            if (stopped) {
                return false;
            }

            if (!this.concurrentContractsDisallowed.isEmpty() && txExecutor.precompiledContractsCalled().stream().anyMatch(this.concurrentContractsDisallowed::contains)) {
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

            if (!supplyBugInjected && supplyBug.isEnabled()) {
                supplyBug.mintFromNowhere(track);
                supplyBugInjected = true;
            }

            // Supply scope for this transaction. Measured here, after the fee bookkeeping, so that
            // a bulk credit of postponed fees falls inside the scope of the transaction that
            // triggered it rather than outside every scope. The sublist shares one repository
            // across its transactions, so the "before" view comes from the balances recorded at the
            // end of the previous one, falling back to the state below the sublist for accounts not
            // yet seen.
            SupplyDelta txDelta = supplyDeltaFor(txExecutor);

            supplyCheck.checkTransaction(tx, i, txDelta);

            if (txDelta.isCreation()) {
                supplyViolation = true;
                return false;
            }

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
            // Settles fees that earlier transactions already counted as pending inflow. Recorded so
            // the next supply scope, which contains this credit, does not read it as a mint.
            this.feesSettledThisIteration = this.feesSettledThisIteration.add(this.totalPaidFees);
        }
    }

    /**
     * Computes this transaction's supply delta and advances the recorded balances.
     *
     * Gas is debited from the sender here but the matching credit to the fee recipient is postponed
     * and applied in bulk later, so the fee is modelled explicitly as pending inflow. Without that,
     * every ordinary transaction would report a burn and the crediting step would be
     * indistinguishable from an actual mint.
     */
    private SupplyDelta supplyDeltaFor(TransactionExecutor txExecutor) {
        Set<RskAddress> modified = track.getModifiedAccounts();

        SupplyDelta delta = SupplyDelta.between(modified, this::balanceAtTxStart, track::getBalance)
                .withSettledInflow(feesSettledThisIteration)
                .withPendingInflow(paidFeesOf(txExecutor));

        feesSettledThisIteration = Coin.ZERO;

        // Advance the "before" view for the next transaction.
        for (RskAddress address : modified) {
            balancesAtLastTxEnd.put(address, track.getBalance(address));
        }

        return delta;
    }

    private Coin balanceAtTxStart(RskAddress address) {
        Coin recorded = balancesAtLastTxEnd.get(address);
        return recorded != null ? recorded : baseBalances.getBalance(address);
    }

    private static Coin paidFeesOf(TransactionExecutor txExecutor) {
        Coin paidFees = txExecutor.getPaidFees();
        return paidFees == null ? Coin.ZERO : paidFees;
    }

    /** Sum of B over the transactions this sublist executed, for the block-level cross-check. */
    public Coin getSupplyDeltaSum() {
        return supplyCheck.getSumOfTransactionDeltas();
    }

    /** True if a transaction in this sublist created native currency out of nothing. */
    public boolean hasSupplyViolation() {
        return supplyViolation;
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

    public void stop() {
        this.stopped = true;
    }
}
