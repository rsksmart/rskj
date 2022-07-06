/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.core.bc;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.core.TransactionListExecutor;
import co.rsk.crypto.Keccak256;
import co.rsk.db.RepositoryLocator;
import co.rsk.metrics.profilers.Metric;
import co.rsk.metrics.profilers.Profiler;
import co.rsk.metrics.profilers.ProfilerFactory;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.ProgramResult;
import org.ethereum.vm.trace.ProgramTraceProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAccumulator;

import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP126;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP85;

/**
 * This is a stateless class with methods to execute blocks with its transactions.
 * There are two main use cases:
 * - execute and validate the block final state
 * - execute and complete the block final state
 * <p>
 * Note that this class IS NOT guaranteed to be thread safe because its dependencies might hold state.
 */
public class BlockExecutor {
    private static final Logger logger = LoggerFactory.getLogger("blockexecutor");
    private static final Profiler profiler = ProfilerFactory.getInstance();

    private final RepositoryLocator repositoryLocator;
    private final TransactionExecutorFactory transactionExecutorFactory;
    private final ActivationConfig activationConfig;

    private final Map<Keccak256, ProgramResult> transactionResults = new ConcurrentHashMap<>();
    private boolean registerProgramResults;

    public BlockExecutor(
            ActivationConfig activationConfig,
            RepositoryLocator repositoryLocator,
            TransactionExecutorFactory transactionExecutorFactory) {
        this.repositoryLocator = repositoryLocator;
        this.transactionExecutorFactory = transactionExecutorFactory;
        this.activationConfig = activationConfig;
    }

    /**
     * Precompiled contracts storage is setup like any other contract for consistency. Here, we apply this logic on the
     * exact activation block.
     * This method is called automatically for every block except for the Genesis (which makes an explicit call).
     */
    public static void maintainPrecompiledContractStorageRoots(Repository track, ActivationConfig.ForBlock activations) {
        if (activations.isActivating(RSKIP126)) {
            for (RskAddress addr : PrecompiledContracts.GENESIS_ADDRESSES) {
                if (!track.isExist(addr)) {
                    track.createAccount(addr);
                }
                track.setupContract(addr);
            }
        }

        for (Map.Entry<RskAddress, ConsensusRule> e : PrecompiledContracts.CONSENSUS_ENABLED_ADDRESSES.entrySet()) {
            ConsensusRule contractActivationRule = e.getValue();
            if (activations.isActivating(contractActivationRule)) {
                RskAddress addr = e.getKey();
                track.createAccount(addr);
                track.setupContract(addr);
            }
        }
    }

    @VisibleForTesting
    public static byte[] calculateLogsBloom(List<TransactionReceipt> receipts) {
        Bloom logBloom = new Bloom();

        for (TransactionReceipt receipt : receipts) {
            logBloom.or(receipt.getBloomFilter());
        }

        return logBloom.getData();
    }

    /**
     * Execute and complete a block.
     *
     * @param block  A block to execute and complete
     * @param parent The parent of the block.
     */
    public BlockResult executeAndFill(Block block, BlockHeader parent) {
        BlockResult result = executeForMining(block, parent, true, false, false);
        fill(block, result);
        return result;
    }

    @VisibleForTesting
    public void executeAndFillAll(Block block, BlockHeader parent) {
        BlockResult result = executeForMining(block, parent, false, true, false);
        fill(block, result);
    }

    @VisibleForTesting
    public void executeAndFillReal(Block block, BlockHeader parent) {
        BlockResult result = executeForMining(block, parent, false, false, false);
        if (result != BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT) {
            fill(block, result);
        }
    }

    private void fill(Block block, BlockResult result) {
        Metric metric = profiler.start(Profiler.PROFILING_TYPE.FILLING_EXECUTED_BLOCK);
        BlockHeader header = block.getHeader();
        block.setTransactionsList(result.getExecutedTransactions());
        boolean isRskip126Enabled = activationConfig.isActive(RSKIP126, block.getNumber());
        header.setTransactionsRoot(BlockHashesHelper.getTxTrieRoot(block.getTransactionsList(), isRskip126Enabled));
        header.setReceiptsRoot(BlockHashesHelper.calculateReceiptsTrieRoot(result.getTransactionReceipts(), isRskip126Enabled));
        header.setStateRoot(result.getFinalState().getHash().getBytes());
        header.setGasUsed(result.getGasUsed());
        header.setPaidFees(result.getPaidFees());
        header.setLogsBloom(calculateLogsBloom(result.getTransactionReceipts()));
        header.setTxExecutionSublistsEdges(result.getTxEdges());

        block.flushRLP();
        profiler.stop(metric);
    }

    /**
     * Execute and validate the final state of a block.
     *
     * @param block  A block to execute and complete
     * @param parent The parent of the block.
     * @return true if the block final state is equalBytes to the calculated final state.
     */
    @VisibleForTesting
    public boolean executeAndValidate(Block block, BlockHeader parent) {
        BlockResult result = execute(null, 0, block, parent, false, false, false);

        return this.validate(block, result);
    }

    /**
     * Validate the final state of a block.
     *
     * @param block  A block to validate
     * @param result A block result (state root, receipts root, etc...)
     * @return true if the block final state is equalBytes to the calculated final state.
     */
    public boolean validate(Block block, BlockResult result) {
        Metric metric = profiler.start(Profiler.PROFILING_TYPE.BLOCK_FINAL_STATE_VALIDATION);
        if (result == BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT) {
            logger.error("Block {} [{}] execution was interrupted because of an invalid transaction", block.getNumber(), block.getPrintableHash());
            profiler.stop(metric);
            return false;
        }

        boolean isValidStateRoot = validateStateRoot(block.getHeader(), result);
        if (!isValidStateRoot) {
            logger.error("Block {} [{}] given State Root is invalid", block.getNumber(), block.getPrintableHash());
            profiler.stop(metric);
            return false;
        }

        boolean isValidReceiptsRoot = validateReceiptsRoot(block.getHeader(), result);
        if (!isValidReceiptsRoot) {
            logger.error("Block {} [{}] given Receipt Root is invalid", block.getNumber(), block.getPrintableHash());
            profiler.stop(metric);
            return false;
        }

        boolean isValidLogsBloom = validateLogsBloom(block.getHeader(), result);
        if (!isValidLogsBloom) {
            logger.error("Block {} [{}] given Logs Bloom is invalid", block.getNumber(), block.getPrintableHash());
            profiler.stop(metric);
            return false;
        }

        if (result.getGasUsed() != block.getGasUsed()) {
            logger.error("Block {} [{}] given gasUsed doesn't match: {} != {}", block.getNumber(), block.getPrintableHash(), block.getGasUsed(), result.getGasUsed());
            profiler.stop(metric);
            return false;
        }

        Coin paidFees = result.getPaidFees();
        Coin feesPaidToMiner = block.getFeesPaidToMiner();

        if (!paidFees.equals(feesPaidToMiner)) {
            logger.error("Block {} [{}] given paidFees doesn't match: {} != {}", block.getNumber(), block.getPrintableHash(), feesPaidToMiner, paidFees);
            profiler.stop(metric);
            return false;
        }

        List<Transaction> executedTransactions = result.getExecutedTransactions();
        List<Transaction> transactionsList = block.getTransactionsList();

        if (!executedTransactions.equals(transactionsList)) {
            logger.error("Block {} [{}] given txs doesn't match: {} != {}", block.getNumber(), block.getPrintableHash(), transactionsList, executedTransactions);
            profiler.stop(metric);
            return false;
        }

        profiler.stop(metric);
        return true;
    }

    @VisibleForTesting
    boolean validateStateRoot(BlockHeader header, BlockResult result) {
        boolean isRskip85Enabled = activationConfig.isActive(RSKIP85, header.getNumber());
        if (!isRskip85Enabled) {
            return true;
        }

        boolean isRskip126Enabled = activationConfig.isActive(RSKIP126, header.getNumber());
        if (!isRskip126Enabled) {
            return true;
        }

        // we only validate state roots of blocks after RSKIP 126 activation
        return Arrays.equals(result.getFinalState().getHash().getBytes(), header.getStateRoot());
    }

    private boolean validateReceiptsRoot(BlockHeader header, BlockResult result) {
        boolean isRskip126Enabled = activationConfig.isActive(RSKIP126, header.getNumber());
        byte[] receiptsTrieRoot = BlockHashesHelper.calculateReceiptsTrieRoot(result.getTransactionReceipts(), isRskip126Enabled);
        return Arrays.equals(receiptsTrieRoot, header.getReceiptsRoot());
    }

    private boolean validateLogsBloom(BlockHeader header, BlockResult result) {
        return Arrays.equals(calculateLogsBloom(result.getTransactionReceipts()), header.getLogsBloom());
    }

    public BlockResult executeForMining(Block block, BlockHeader parent, boolean discardInvalidTxs, boolean ignoreReadyToExecute, boolean saveState) {
        boolean rskip144Active = activationConfig.isActive(ConsensusRule.RSKIP144, block.getHeader().getNumber());
        if (rskip144Active || (block.getHeader().getTxExecutionSublistsEdges() != null)) {
            return executeForMiningAfterRSKIP144(block, parent, discardInvalidTxs, ignoreReadyToExecute, saveState);
        } else {
            return executePreviousRSKIP144(null, 0, block, parent, discardInvalidTxs, ignoreReadyToExecute, saveState);
        }
    }

    /**
     * Execute a block while saving the execution trace in the trace processor
     */
    public void traceBlock(
            ProgramTraceProcessor programTraceProcessor,
            int vmTraceOptions,
            Block block,
            BlockHeader parent,
            boolean discardInvalidTxs,
            boolean ignoreReadyToExecute) {
            execute(
                Objects.requireNonNull(programTraceProcessor), vmTraceOptions, block, parent, discardInvalidTxs, ignoreReadyToExecute, false
        );
    }

    public BlockResult execute(
            @Nullable ProgramTraceProcessor programTraceProcessor,
            int vmTraceOptions,
            Block block,
            BlockHeader parent,
            boolean discardInvalidTxs,
            boolean acceptInvalidTransactions,
            boolean saveState
        ) {
            boolean rskip144Active = activationConfig.isActive(ConsensusRule.RSKIP144, block.getHeader().getNumber());

            if (rskip144Active && block.getHeader().getTxExecutionSublistsEdges() != null) {
                return executeParallel(programTraceProcessor, vmTraceOptions, block, parent, discardInvalidTxs, acceptInvalidTransactions, saveState);
            } else {
                return executePreviousRSKIP144(programTraceProcessor, vmTraceOptions, block, parent, discardInvalidTxs, acceptInvalidTransactions, saveState);
            }
    }

    private BlockResult executePreviousRSKIP144(
            @Nullable ProgramTraceProcessor programTraceProcessor,
            int vmTraceOptions,
            Block block,
            BlockHeader parent,
            boolean discardInvalidTxs,
            boolean acceptInvalidTransactions,
            boolean saveState) {
        boolean vmTrace = programTraceProcessor != null;
        logger.trace("Start execute pre RSKIP144.");
        loggingApplyBlock(block);

        // Forks the repo, does not change "repository". It will have a completely different
        // image of the repo, where the middle caches are immediately ignored.
        // In fact, while cloning everything, it asserts that no cache elements remains.
        // (see assertNoCache())
        // Which means that you must commit changes and save them to be able to recover
        // in the next block processed.
        // Note that creating a snapshot is important when the block is executed twice
        // (e.g. once while building the block in tests/mining, and the other when trying
        // to connect the block). This is because the first execution will change the state
        // of the repository to the state post execution, so it's necessary to get it to
        // the state prior execution again.
        Metric metric = profiler.start(Profiler.PROFILING_TYPE.BLOCK_EXECUTE);

        Repository track = repositoryLocator.startTrackingAt(parent);

        maintainPrecompiledContractStorageRoots(track, activationConfig.forBlock(block.getNumber()));

        int i = 1;
        long totalGasUsed = 0;
        Coin totalPaidFees = Coin.ZERO;
        List<TransactionReceipt> receipts = new ArrayList<>();
        List<Transaction> executedTransactions = new ArrayList<>();
        Set<DataWord> deletedAccounts = new HashSet<>();
        LongAccumulator remascFees = new LongAccumulator(Long::sum, 0);

        int txindex = 0;

        for (Transaction tx : block.getTransactionsList()) {
            loggingApplyBlockToTx(block, i);

            TransactionExecutor txExecutor = transactionExecutorFactory.newInstance(
                    tx,
                    txindex++,
                    block.getCoinbase(),
                    track,
                    block,
                    totalGasUsed,
                    vmTrace,
                    vmTraceOptions,
                    deletedAccounts,
                    remascFees);
            boolean transactionExecuted = txExecutor.executeTransaction();

            if (!acceptInvalidTransactions && !transactionExecuted) {
                if (!discardInvalidTxs) {
                    return getBlockResultAndLogExecutionInterrupted(block, metric, tx);
                }
                loggingDiscardedBlock(block, tx);
                continue;
            }

            executedTransactions.add(tx);

            if (this.registerProgramResults) {
                this.transactionResults.put(tx.getHash(), txExecutor.getResult());
            }

            if (vmTrace) {
                txExecutor.extractTrace(programTraceProcessor);
            }

            loggingTxExecuted();
            long gasUsed = txExecutor.getGasUsed();
            totalGasUsed += gasUsed;
            Coin paidFees = txExecutor.getPaidFees();
            if (paidFees != null) {
                totalPaidFees = totalPaidFees.add(paidFees);
            }

            deletedAccounts.addAll(txExecutor.getResult().getDeleteAccounts());

            TransactionReceipt receipt = buildTransactionReceipt(tx, txExecutor, gasUsed, totalGasUsed);

            loggingExecuteTxAndReceipt(block, i, tx);

            i++;

            receipts.add(receipt);

            loggingTxDone();
        }

        addFeesToRemasc(remascFees, track);
        saveOrCommitTrackState(saveState, track);

        BlockResult result = new BlockResult(
                block,
                executedTransactions,
                receipts,
                null,
                totalGasUsed,
                totalPaidFees,
                vmTrace ? null : track.getTrie()

        );
        profiler.stop(metric);
        logger.trace("End execute pre RSKIP144.");
        return result;
    }

    private BlockResult executeParallel(
            @Nullable ProgramTraceProcessor programTraceProcessor,
            int vmTraceOptions,
            Block block,
            BlockHeader parent,
            boolean discardInvalidTxs,
            boolean acceptInvalidTransactions,
            boolean saveState) {
        boolean vmTrace = programTraceProcessor != null;
        logger.trace("Start executeParallel.");
        loggingApplyBlock(block);

        // Forks the repo, does not change "repository". It will have a completely different
        // image of the repo, where the middle caches are immediately ignored.
        // In fact, while cloning everything, it asserts that no cache elements remains.
        // (see assertNoCache())
        // Which means that you must commit changes and save them to be able to recover
        // in the next block processed.
        // Note that creating a snapshot is important when the block is executed twice
        // (e.g. once while building the block in tests/mining, and the other when trying
        // to conect the block). This is because the first execution will change the state
        // of the repository to the state post execution, so it's necessary to get it to
        // the state prior execution again.
        Metric metric = profiler.start(Profiler.PROFILING_TYPE.BLOCK_EXECUTE);
        IReadWrittenKeysTracker readWrittenKeysTracker = new ReadWrittenKeysTracker();
        Repository track = repositoryLocator.startTrackingAt(parent, readWrittenKeysTracker);
        maintainPrecompiledContractStorageRoots(track, activationConfig.forBlock(block.getNumber()));

        LongAccumulator totalGasUsed = new LongAccumulator(Long::sum, 0);
        LongAccumulator totalPaidFees = new LongAccumulator(Long::sum, 0);
        Map<Integer, TransactionReceipt> receipts = new ConcurrentSkipListMap<>();
        Map<Integer, Transaction> executedTransactions = new ConcurrentSkipListMap<>();
        Set<DataWord> deletedAccounts = ConcurrentHashMap.newKeySet();
        LongAccumulator remascFees = new LongAccumulator(Long::sum, 0);

        ExecutorService executorService = Executors.newFixedThreadPool(Constants.getTransactionExecutionThreads());
        ExecutorCompletionService<Boolean> completionService = new ExecutorCompletionService<>(executorService);
        int nTasks = 0;

        // execute parallel subsets of transactions
        short start = 0;
        for (short end : block.getHeader().getTxExecutionSublistsEdges()) {
            List<Transaction> sublist = block.getTransactionsList().subList(start, end);
            TransactionListExecutor txListExecutor = new TransactionListExecutor(
                    sublist,
                    readWrittenKeysTracker,
                    block,
                    transactionExecutorFactory,
                    track,
                    vmTrace,
                    vmTraceOptions,
                    deletedAccounts,
                    discardInvalidTxs,
                    acceptInvalidTransactions,
                    receipts,
                    executedTransactions,
                    transactionResults,
                    registerProgramResults,
                    programTraceProcessor,
                    remascFees,
                    totalPaidFees,
                    totalGasUsed,
                    start
            );
            completionService.submit(txListExecutor);
            nTasks++;
            start = end;
        }
        executorService.shutdown();

        for (int i = 0; i < nTasks; i++) {
            try {
                Future<Boolean> success = completionService.take();
                if (!Boolean.TRUE.equals(success.get())) {
                    executorService.shutdownNow();
                    profiler.stop(metric);
                    return BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT;
                }
            } catch (InterruptedException e) {
                logger.warn("block: [{}] execution was interrupted", block.getNumber());
                logger.trace("", e);
                Thread.currentThread().interrupt();
                profiler.stop(metric);
                return BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT;
            } catch (ExecutionException e) {
                logger.warn("block: [{}] execution failed", block.getNumber());
                logger.trace("", e);
                profiler.stop(metric);
                return BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT;
            }
        }

        readWrittenKeysTracker.clear();
        // execute remaining transactions after the parallel subsets
        List<Transaction> sublist = block.getTransactionsList().subList(start, block.getTransactionsList().size());
        TransactionListExecutor txListExecutor = new TransactionListExecutor(
                sublist,
                readWrittenKeysTracker,
                block,
                transactionExecutorFactory,
                track,
                vmTrace,
                vmTraceOptions,
                deletedAccounts,
                discardInvalidTxs,
                acceptInvalidTransactions,
                receipts,
                executedTransactions,
                transactionResults,
                registerProgramResults,
                programTraceProcessor,
                remascFees,
                totalPaidFees,
                totalGasUsed,
                start
        );
        Boolean success = txListExecutor.call();
        if (!Boolean.TRUE.equals(success)) {
            return BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT;
        }

        addFeesToRemasc(remascFees, track);
        saveOrCommitTrackState(saveState, track);

        BlockResult result = new BlockResult(
                block,
                new LinkedList<>(executedTransactions.values()),
                new LinkedList<>(receipts.values()),
                new short[0],
                totalGasUsed.longValue(),
                Coin.valueOf(totalPaidFees.longValue()),
                vmTrace ? null : track.getTrie()
        );
        profiler.stop(metric);
        logger.trace("End executeParallel.");
        return result;
    }

    private void addFeesToRemasc(LongAccumulator remascFees, Repository track) {
        long fee = remascFees.get();
        if (fee > 0) {
            track.addBalance(PrecompiledContracts.REMASC_ADDR, Coin.valueOf(fee));
        }
    }

    private BlockResult executeForMiningAfterRSKIP144(
            Block block,
            BlockHeader parent,
            boolean discardInvalidTxs,
            boolean acceptInvalidTransactions,
            boolean saveState) {
        logger.trace("Start executeForMining.");
        List<Transaction> transactionsList = block.getTransactionsList();
        loggingApplyBlock(block);

        // Forks the repo, does not change "repository". It will have a completely different
        // image of the repo, where the middle caches are immediately ignored.
        // In fact, while cloning everything, it asserts that no cache elements remains.
        // (see assertNoCache())
        // Which means that you must commit changes and save them to be able to recover
        // in the next block processed.
        // Note that creating a snapshot is important when the block is executed twice
        // (e.g. once while building the block in tests/mining, and the other when trying
        // to conect the block). This is because the first execution will change the state
        // of the repository to the state post execution, so it's necessary to get it to
        // the state prior execution again.
        Metric metric = profiler.start(Profiler.PROFILING_TYPE.BLOCK_EXECUTE);

        IReadWrittenKeysTracker readWrittenKeysTracker = new ReadWrittenKeysTracker();
        Repository track = repositoryLocator.startTrackingAt(parent, readWrittenKeysTracker);
        maintainPrecompiledContractStorageRoots(track, activationConfig.forBlock(block.getNumber()));

        int i = 1;
        long gasUsedInBlock = 0;
        Coin totalPaidFees = Coin.ZERO;
        Map<Transaction, TransactionReceipt> receiptsByTx = new HashMap<>();
        Set<DataWord> deletedAccounts = new HashSet<>();
        LongAccumulator remascFees = new LongAccumulator(Long::sum, 0);

        ParallelizeTransactionHandler parallelizeTransactionHandler = new ParallelizeTransactionHandler((short) Constants.getTransactionExecutionThreads(), GasCost.toGas(block.getGasLimit()));

        int txindex = 0;

        for (Transaction tx : transactionsList) {
            loggingApplyBlockToTx(block, i);

            TransactionExecutor txExecutor = transactionExecutorFactory.newInstance(
                    tx,
                    txindex++,
                    block.getCoinbase(),
                    track,
                    block,
                    parallelizeTransactionHandler.getGasUsedInSequential(),
                    false,
                    0,
                    deletedAccounts,
                    remascFees);
            boolean transactionExecuted = txExecutor.executeTransaction();

            if (!acceptInvalidTransactions && !transactionExecuted) {
                if (!discardInvalidTxs) {
                    return getBlockResultAndLogExecutionInterrupted(block, metric, tx);
                }

                loggingDiscardedBlock(block, tx);
                continue;
            }

            Optional<Long> sublistGasAccumulated;
            if (tx.isRemascTransaction(txindex, transactionsList.size())) {
                sublistGasAccumulated = parallelizeTransactionHandler.addRemascTransaction(tx, txExecutor.getGasUsed());
            } else {
                sublistGasAccumulated = parallelizeTransactionHandler.addTransaction(tx, readWrittenKeysTracker.getTemporalReadKeys(), readWrittenKeysTracker.getTemporalWrittenKeys(), txExecutor.getGasUsed());
            }

            if (!acceptInvalidTransactions && !sublistGasAccumulated.isPresent()) {
                if (!discardInvalidTxs) {
                    return getBlockResultAndLogExecutionInterrupted(block, metric, tx);
                }
                loggingDiscardedBlock(block, tx);
                continue;
            }

            readWrittenKeysTracker.clear();

            if (this.registerProgramResults) {
                this.transactionResults.put(tx.getHash(), txExecutor.getResult());
            }

            loggingTxExecuted();
            long gasUsed = txExecutor.getGasUsed();
            gasUsedInBlock += gasUsed;
            Coin paidFees = txExecutor.getPaidFees();
            if (paidFees != null) {
                totalPaidFees = totalPaidFees.add(paidFees);
            }

            deletedAccounts.addAll(txExecutor.getResult().getDeleteAccounts());


            //orElseGet is used for testing only when acceptInvalidTransactions is set.
            long cumulativeGas = sublistGasAccumulated
                    .orElseGet(() -> parallelizeTransactionHandler.getGasUsedIn((short) Constants.getTransactionExecutionThreads()));
            TransactionReceipt receipt = buildTransactionReceipt(tx, txExecutor, gasUsed, cumulativeGas);

            loggingExecuteTxAndReceipt(block, i, tx);

            i++;

            receiptsByTx.put(tx, receipt);

            loggingTxDone();
        }

        addFeesToRemasc(remascFees, track);
        saveOrCommitTrackState(saveState, track);


        List<Transaction> executedTransactions = parallelizeTransactionHandler.getTransactionsInOrder();
        short[] sublistOrder = parallelizeTransactionHandler.getTransactionsPerSublistInOrder();
        List<TransactionReceipt> receipts = new ArrayList<>();

        for (Transaction tx : executedTransactions) {
            receipts.add(receiptsByTx.get(tx));
        }

        BlockResult result = new BlockResult(
                block,
                executedTransactions,
                receipts,
                sublistOrder,
                gasUsedInBlock,
                totalPaidFees,
                track.getTrie()
        );
        profiler.stop(metric);
        logger.trace("End executeForMining.");
        return result;
    }

    private void saveOrCommitTrackState(boolean saveState, Repository track) {
        logger.trace("End txs executions.");
        if (saveState) {
            logger.trace("Saving track.");
            track.save();
            logger.trace("End saving track.");
        } else {
            logger.trace("Committing track.");
            track.commit();
            logger.trace("End committing track.");
        }

        logger.trace("Building execution results.");
    }

    private TransactionReceipt buildTransactionReceipt(Transaction tx, TransactionExecutor txExecutor, long gasUsed, long cumulativeGas) {
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setGasUsed(gasUsed);
        receipt.setTxStatus(txExecutor.getReceipt().isSuccessful());
        receipt.setTransaction(tx);
        receipt.setLogInfoList(txExecutor.getVMLogs());
        receipt.setStatus(txExecutor.getReceipt().getStatus());
        receipt.setCumulativeGas(cumulativeGas);
        return receipt;
    }

    private BlockResult getBlockResultAndLogExecutionInterrupted(Block block, Metric metric, Transaction tx) {
        logger.warn("block: [{}] execution interrupted because of invalid tx: [{}]",
                    block.getNumber(), tx.getHash());
        profiler.stop(metric);
        return BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT;
    }

    private void loggingTxExecuted() {
        logger.trace("tx executed");
    }

    private void loggingTxDone() {
        logger.trace("tx done");
    }

    private void loggingDiscardedBlock(Block block, Transaction tx) {
        logger.warn("block: [{}] discarded tx: [{}]", block.getNumber(), tx.getHash());
    }

    private void loggingApplyBlock(Block block) {
        logger.trace("applyBlock: block: [{}] tx.list: [{}]", block.getNumber(), block.getTransactionsList().size());
    }

    private void loggingApplyBlockToTx(Block block, int i) {
        logger.trace("apply block: [{}] tx: [{}] ", block.getNumber(), i);
    }

    private void loggingExecuteTxAndReceipt(Block block, int i, Transaction tx) {
        logger.trace("block: [{}] executed tx: [{}]", block.getNumber(), tx.getHash());
        logger.trace("tx[{}].receipt", i);
    }

    public ProgramResult getProgramResult(Keccak256 txhash) {
        return this.transactionResults.get(txhash);
    }

    public void setRegisterProgramResults(boolean value) {
        this.registerProgramResults = value;
        this.transactionResults.clear();
    }
}
