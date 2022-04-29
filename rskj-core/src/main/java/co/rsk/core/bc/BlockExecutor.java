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
    private static final int THREAD_COUNT = 4;

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
        BlockResult result = execute(block, parent, true, false);
        fill(block, result);
        return result;
    }

    @VisibleForTesting
    public void executeAndFillAll(Block block, BlockHeader parent) {
        BlockResult result = execute(block, parent, false, true);
        fill(block, result);
    }

    @VisibleForTesting
    public void executeAndFillReal(Block block, BlockHeader parent) {
        BlockResult result = execute(block, parent, false, false);
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
        header.setTxExecutionListsEdges(result.getTxEdges());

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
        BlockResult result = execute(block, parent, false, false);

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

    @VisibleForTesting
    public BlockResult execute(Block block, BlockHeader parent, boolean discardInvalidTxs) {
        return execute(block, parent, discardInvalidTxs, false);
    }

    public BlockResult execute(Block block, BlockHeader parent, boolean discardInvalidTxs, boolean ignoreReadyToExecute) {
        return executeInternal(null, 0, block, parent, discardInvalidTxs, ignoreReadyToExecute);
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
        executeInternal(
                Objects.requireNonNull(programTraceProcessor), vmTraceOptions, block, parent, discardInvalidTxs, ignoreReadyToExecute
        );
    }

    private BlockResult executeInternal(
            @Nullable ProgramTraceProcessor programTraceProcessor,
            int vmTraceOptions,
            Block block,
            BlockHeader parent,
            boolean discardInvalidTxs,
            boolean acceptInvalidTransactions) {

        if (block.getHeader().getTxExecutionListsEdges() != null) {
            return executeParallel(programTraceProcessor, vmTraceOptions, block, parent, discardInvalidTxs, acceptInvalidTransactions);
        } else {
            boolean rskip144Active = activationConfig.isActive(ConsensusRule.RSKIP144, block.getHeader().getNumber());
            if (rskip144Active) {
                return executeSequential(programTraceProcessor, vmTraceOptions, block, parent, discardInvalidTxs, acceptInvalidTransactions);
            }
            return executeOldSequential(programTraceProcessor, vmTraceOptions, block, parent, discardInvalidTxs, acceptInvalidTransactions);
        }
    }

    private BlockResult executeOldSequential(
            @Nullable ProgramTraceProcessor programTraceProcessor,
            int vmTraceOptions,
            Block block,
            BlockHeader parent,
            boolean discardInvalidTxs,
            boolean acceptInvalidTransactions) {
        boolean vmTrace = programTraceProcessor != null;
        logger.trace("Start executeInternal.");
        logger.trace("applyBlock: block: [{}] tx.list: [{}]", block.getNumber(), block.getTransactionsList().size());

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
            logger.trace("apply block: [{}] tx: [{}] ", block.getNumber(), i);

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
                if (discardInvalidTxs) {
                    logger.warn("block: [{}] discarded tx: [{}]", block.getNumber(), tx.getHash());
                    continue;
                } else {
                    logger.warn("block: [{}] execution interrupted because of invalid tx: [{}]",
                            block.getNumber(), tx.getHash());
                    profiler.stop(metric);
                    return BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT;
                }
            }

            executedTransactions.add(tx);

            if (this.registerProgramResults) {
                this.transactionResults.put(tx.getHash(), txExecutor.getResult());
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
        }

        logger.trace("End txs executions.");
        if (!vmTrace) {
            logger.trace("Saving track.");
            track.save();
            logger.trace("End saving track.");
        }

        logger.trace("Building execution results.");
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
        logger.trace("End executeInternal.");
        return result;
    }

    private BlockResult executeParallel(
            @Nullable ProgramTraceProcessor programTraceProcessor,
            int vmTraceOptions,
            Block block,
            BlockHeader parent,
            boolean discardInvalidTxs,
            boolean acceptInvalidTransactions) {
        boolean vmTrace = programTraceProcessor != null;
        logger.trace("Start executeInternal.");
        logger.trace("applyBlock: block: [{}] tx.list: [{}]", block.getNumber(), block.getTransactionsList().size());

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

        Repository track = repositoryLocator.startTrackingAt(parent);

        maintainPrecompiledContractStorageRoots(track, activationConfig.forBlock(block.getNumber()));

        LongAccumulator totalGasUsed = new LongAccumulator(Long::sum, 0);
        LongAccumulator totalPaidFees = new LongAccumulator(Long::sum, 0);
        Map<Integer, TransactionReceipt> receipts = new ConcurrentSkipListMap<>();
        Map<Integer, Transaction> executedTransactions = new ConcurrentSkipListMap<>();
        Set<DataWord> deletedAccounts = ConcurrentHashMap.newKeySet();
        LongAccumulator remascFees = new LongAccumulator(Long::sum, 0);

        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        CompletionService completionService = new ExecutorCompletionService(executorService);
        int nTasks = 0;

        // execute parallel subsets of transactions
        short start = 0;
        for (short end : block.getHeader().getTxExecutionListsEdges()) {
            List<Transaction> sublist = block.getTransactionsList().subList(start, end);
            TransactionListExecutor txListExecutor = new TransactionListExecutor(
                    sublist,
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
                if (!success.get()) {
                    executorService.shutdownNow();
                    profiler.stop(metric);
                    return BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT;
                }
            } catch (InterruptedException e) {
                logger.warn("block: [{}] execution was interrupted", block.getNumber());
                logger.trace("", e);
                profiler.stop(metric);
                return BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT;
            } catch (ExecutionException e) {
                logger.warn("block: [{}] execution failed", block.getNumber());
                logger.trace("", e);
                profiler.stop(metric);
                return BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT;
            }
        }

        // execute remaining transactions after the parallel subsets
        List<Transaction> sublist = block.getTransactionsList().subList(start, block.getTransactionsList().size());
        TransactionListExecutor txListExecutor = new TransactionListExecutor(
                sublist,
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
        if (!success) {
            return BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT;
        }

        addFeesToRemasc(remascFees, track);

        logger.trace("End txs executions.");
        if (!vmTrace) {
            logger.trace("Saving track.");
            track.save();
            logger.trace("End saving track.");
        }

        logger.trace("Building execution results.");
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
        logger.trace("End executeInternal.");
        return result;
    }

    private void addFeesToRemasc(LongAccumulator remascFees, Repository track) {
        long fee = remascFees.get();
        if (fee > 0) {
            track.addBalance(PrecompiledContracts.REMASC_ADDR, Coin.valueOf(fee));
        }
    }

    private BlockResult executeSequential(
            @Nullable ProgramTraceProcessor programTraceProcessor,
            int vmTraceOptions,
            Block block,
            BlockHeader parent,
            boolean discardInvalidTxs,
            boolean acceptInvalidTransactions) {
        boolean vmTrace = programTraceProcessor != null;
        logger.trace("Start executeInternal.");
        List<Transaction> transactionsList = block.getTransactionsList();
        logger.trace("applyBlock: block: [{}] tx.list: [{}]", block.getNumber(), transactionsList.size());

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

        ReadWrittenKeysTracker readWrittenKeysTracker = new ReadWrittenKeysTracker();
        Repository track = repositoryLocator.startTrackingAt(parent, readWrittenKeysTracker);

        maintainPrecompiledContractStorageRoots(track, activationConfig.forBlock(block.getNumber()));

        int i = 1;
        long totalGasUsed = 0;
        Coin totalPaidFees = Coin.ZERO;
        Map<Transaction, TransactionReceipt> receiptsByTx = new HashMap<>();
        Set<DataWord> deletedAccounts = new HashSet<>();
        LongAccumulator remascFees = new LongAccumulator(Long::sum, 0);
        short buckets = 2;

        //TODO(Juli): Is there a better way to calculate the bucket gas limit?
        ParallelizeTransactionHandler parallelizeTransactionHandler = new ParallelizeTransactionHandler(buckets, GasCost.toGas(block.getGasLimit())/buckets);

        int txindex = 0;

        for (Transaction tx : transactionsList) {
            logger.trace("apply block: [{}] tx: [{}] ", block.getNumber(), i);

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
                    remascFees); //TODO(Juli): Check how to differ this behavior between RSKIPs
            boolean transactionExecuted = txExecutor.executeTransaction();

            if (!acceptInvalidTransactions && !transactionExecuted) {
                if (discardInvalidTxs) {
                    logger.warn("block: [{}] discarded tx: [{}]", block.getNumber(), tx.getHash());
                    continue;
                } else {
                    logger.warn("block: [{}] execution interrupted because of invalid tx: [{}]",
                                block.getNumber(), tx.getHash());
                    profiler.stop(metric);
                    return BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT;
                }
            }

            Optional<Long> bucketGasAccumulated;
            if (tx.isRemascTransaction(txindex, transactionsList.size())) {
                bucketGasAccumulated = parallelizeTransactionHandler.addRemascTransaction(tx, txExecutor.getGasUsed());
            } else {
                bucketGasAccumulated = parallelizeTransactionHandler.addTransaction(tx, readWrittenKeysTracker.getTemporalReadKeys(), readWrittenKeysTracker.getTemporalWrittenKeys(), txExecutor.getGasUsed());
            }

            if (!acceptInvalidTransactions && !bucketGasAccumulated.isPresent()) {
                if (discardInvalidTxs) {
                    logger.warn("block: [{}] discarded tx: [{}]", block.getNumber(), tx.getHash());
                    continue;
                } else {
                    logger.warn("block: [{}] execution interrupted because of invalid tx: [{}]",
                            block.getNumber(), tx.getHash());
                    profiler.stop(metric);
                    return BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT;
                }
            }

            readWrittenKeysTracker.clear();

            if (this.registerProgramResults) {
                this.transactionResults.put(tx.getHash(), txExecutor.getResult());
            }

            if (vmTrace) {
                txExecutor.extractTrace(programTraceProcessor);
            }

            logger.trace("tx executed");

            // No need to commit the changes here. track.commit();

            logger.trace("track commit");

            long gasUsed = txExecutor.getGasUsed();
            totalGasUsed = parallelizeTransactionHandler.getGasUsedInSequential();

            Coin paidFees = txExecutor.getPaidFees();
            if (paidFees != null) {
                totalPaidFees = totalPaidFees.add(paidFees);
            }

            deletedAccounts.addAll(txExecutor.getResult().getDeleteAccounts());

            TransactionReceipt receipt = new TransactionReceipt();
            receipt.setGasUsed(gasUsed);

            if (bucketGasAccumulated.isPresent()) {
                receipt.setCumulativeGas(bucketGasAccumulated.get());
            } else {
                //This line is used for testing only when acceptInvalidTransactions is set.
                receipt.setCumulativeGas(parallelizeTransactionHandler.getGasUsedIn(buckets));
            }

            receipt.setTxStatus(txExecutor.getReceipt().isSuccessful());
            receipt.setTransaction(tx);
            receipt.setLogInfoList(txExecutor.getVMLogs());
            receipt.setStatus(txExecutor.getReceipt().getStatus());

            logger.trace("block: [{}] executed tx: [{}]", block.getNumber(), tx.getHash());

            logger.trace("tx[{}].receipt", i);

            i++;

            receiptsByTx.put(tx, receipt);

            logger.trace("tx done");
        }

        addFeesToRemasc(remascFees, track);

        logger.trace("End txs executions.");
        if (!vmTrace) {
            logger.trace("Saving track.");
            track.save();
            logger.trace("End saving track.");
        }

        logger.trace("Building execution results.");

        List<Transaction> executedTransactions = parallelizeTransactionHandler.getTransactionsInOrder();
        short[] bucketOrder = parallelizeTransactionHandler.getTransactionsPerBucketInOrder();
        List<TransactionReceipt> receipts = new ArrayList<>();

        for (Transaction tx : executedTransactions) {
            receipts.add(receiptsByTx.get(tx));
        }

        BlockResult result = new BlockResult(
                block,
                executedTransactions,
                receipts,
                bucketOrder,
                totalGasUsed, // totalBlock = parallel1 + parallel2 + sequential?
                totalPaidFees,
                vmTrace ? null : track.getTrie()
        );
        profiler.stop(metric);
        logger.trace("End executeInternal.");
        return result;
    }

    public ProgramResult getProgramResult(Keccak256 txhash) {
        return this.transactionResults.get(txhash);
    }

    public void setRegisterProgramResults(boolean value) {
        this.registerProgramResults = value;
        this.transactionResults.clear();
    }
}
