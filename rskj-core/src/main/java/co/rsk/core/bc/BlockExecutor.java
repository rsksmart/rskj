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

import co.rsk.config.RskSystemProperties;
import co.rsk.core.*;
import co.rsk.crypto.Keccak256;
import co.rsk.db.RepositoryLocator;
import co.rsk.db.StateRootHandler;
import co.rsk.metrics.profilers.Metric;
import co.rsk.metrics.profilers.Profiler;
import co.rsk.metrics.profilers.ProfilerFactory;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.trace.ProgramTraceProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP126;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP85;

/**
 * This is a stateless class with methods to execute blocks with its transactions.
 * There are two main use cases:
 * - execute and validate the block final state
 * - execute and complete the block final state
 *
 * Note that this class IS NOT guaranteed to be thread safe because its dependencies might hold state.
 */
public class BlockExecutor {
    private static final Logger logger = LoggerFactory.getLogger("blockexecutor");
    private static final Profiler profiler = ProfilerFactory.getInstance();

    private final RepositoryLocator repositoryLocator;
    private final TransactionExecutorFactory transactionExecutorFactory;
    private final StateRootHandler stateRootHandler;
    private final ActivationConfig activationConfig;
    private final RskSystemProperties config;

    public BlockExecutor(
            RskSystemProperties config,
            RepositoryLocator repositoryLocator,
            StateRootHandler stateRootHandler,
            TransactionExecutorFactory transactionExecutorFactory) {
        this.repositoryLocator = repositoryLocator;
        this.transactionExecutorFactory = transactionExecutorFactory;
        this.stateRootHandler = stateRootHandler;
        this.config = config;
        this.activationConfig = config.getActivationConfig();
    }

    /**
     * Execute and complete a block.
     *
     * @param block        A block to execute and complete
     * @param parent       The parent of the block.
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
        header.setGasUsed(result.getGasUsed());
        header.setPaidFees(result.getPaidFees());
        header.setStateRoot(stateRootHandler.convert(header, result.getFinalState()).getBytes());
        header.setLogsBloom(calculateLogsBloom(result.getTransactionReceipts()));

        block.flushRLP();
        profiler.stop(metric);
    }

    /**
     * Execute and validate the final state of a block.
     *
     * @param block        A block to execute and complete
     * @param parent       The parent of the block.
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
     * @param block        A block to validate
     * @param result       A block result (state root, receipts root, etc...)
     * @return true if the block final state is equalBytes to the calculated final state.
     */
    public boolean validate(Block block, BlockResult result) {
        Metric metric = profiler.start(Profiler.PROFILING_TYPE.BLOCK_FINAL_STATE_VALIDATION);
        if (result == BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT) {
            logger.error("Block {} [{}] execution was interrupted because of an invalid transaction", block.getNumber(), block.getShortHash());
            profiler.stop(metric);
            return false;
        }

        boolean isValidStateRoot = validateStateRoot(block.getHeader(), result);
        if (!isValidStateRoot) {
            logger.error("Block {} [{}] given State Root is invalid", block.getNumber(), block.getShortHash());
            profiler.stop(metric);
            return false;
        }

        boolean isValidReceiptsRoot = validateReceiptsRoot(block.getHeader(), result);
        if (!isValidReceiptsRoot) {
            logger.error("Block {} [{}] given Receipt Root is invalid", block.getNumber(), block.getShortHash());
            profiler.stop(metric);
            return false;
        }

        boolean isValidLogsBloom = validateLogsBloom(block.getHeader(), result);
        if (!isValidLogsBloom) {
            logger.error("Block {} [{}] given Logs Bloom is invalid", block.getNumber(), block.getShortHash());
            profiler.stop(metric);
            return false;
        }

        if (result.getGasUsed() != block.getGasUsed()) {
            logger.error("Block {} [{}] given gasUsed doesn't match: {} != {}", block.getNumber(), block.getShortHash(), block.getGasUsed(), result.getGasUsed());
            profiler.stop(metric);
            return false;
        }

        Coin paidFees = result.getPaidFees();
        Coin feesPaidToMiner = block.getFeesPaidToMiner();

        if (!paidFees.equals(feesPaidToMiner))  {
            logger.error("Block {} [{}] given paidFees doesn't match: {} != {}", block.getNumber(), block.getShortHash(), feesPaidToMiner, paidFees);
            profiler.stop(metric);
            return false;
        }

        List<Transaction> executedTransactions = result.getExecutedTransactions();
        List<Transaction> transactionsList = block.getTransactionsList();

        if (!executedTransactions.equals(transactionsList))  {
            logger.error("Block {} [{}] given txs doesn't match: {} != {}", block.getNumber(), block.getShortHash(), transactionsList, executedTransactions);
            profiler.stop(metric);
            return false;
        }

        profiler.stop(metric);
        return true;
    }

    private boolean validateStateRoot(BlockHeader header, BlockResult result) {
        boolean isRskip85Enabled = activationConfig.isActive(RSKIP85, header.getNumber());
        if (!isRskip85Enabled) {
            return true;
        }

        boolean isRskip126Enabled = activationConfig.isActive(RSKIP126, header.getNumber());
        if (!isRskip126Enabled) {
            byte[] orchidStateRoot = stateRootHandler.convert(header, result.getFinalState()).getBytes();
            return Arrays.equals(orchidStateRoot, header.getStateRoot());
        }

        // we only validate state roots of blocks newer than 0.5.0 activation
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
        return executeInternal(null, block, parent, discardInvalidTxs, ignoreReadyToExecute);
    }

    /**
     * Execute a block while saving the execution trace in the trace processor
     */
    public void traceBlock(
            ProgramTraceProcessor programTraceProcessor,
            Block block,
            BlockHeader parent,
            boolean discardInvalidTxs,
            boolean ignoreReadyToExecute) {
        executeInternal(
                Objects.requireNonNull(programTraceProcessor), block, parent, discardInvalidTxs, ignoreReadyToExecute
        );
    }

    /**
     * Stores (and identifies) the data concurrently shared between transaction executions
     */
    public class BlockSharedData {
        long totalGasUsed = 0;
        Coin totalPaidFees = Coin.ZERO;
        Repository track;
        List<TransactionReceipt> receipts = new ArrayList<>();
        List<Transaction> executedTransactions = new ArrayList<>();
        Set<DataWord> deletedAccounts = new HashSet<>();
        ProgramTraceProcessor programTraceProcessor;
        BlockSharedData(Repository track, ProgramTraceProcessor programTraceProcessor) {
            this.track = track;
            this.programTraceProcessor = programTraceProcessor;
        }

        public synchronized void addPaidFees(Coin paidFees) {
            totalPaidFees = totalPaidFees.add(paidFees);
        }

        public synchronized void addGasUsed(long gasUsed) {
            this.totalGasUsed += gasUsed;
        }

        public synchronized int addReceipt(TransactionReceipt receipt) {
            if (receipts.add(receipt)) {
                return receipts.size() - 1;
            }
            return -1;
        }

        public synchronized void addExecutedTransaction(Transaction tx) {
            executedTransactions.add(tx);
        }

        public synchronized void addDeletedAccounts(Collection<DataWord> deletedAccountsToAdd) {
            deletedAccounts.addAll(deletedAccountsToAdd);
        }

        public Coin getTotalPaidFees() {
            return totalPaidFees;
        }

        public long getTotalGasUsed() {
            return totalGasUsed;
        }

        public List<TransactionReceipt> getReceipts() {
            return receipts;
        }

        public List<Transaction> getExecutedTransactions() {
            return executedTransactions;
        }

        public Set<DataWord> getDeletedAccounts() {
            return deletedAccounts;
        }

        public Repository getRepository() {
            return track;
        }

        public ProgramTraceProcessor getProgramTraceProcessor() {
            return programTraceProcessor;
        }

    }

    public class TransactionConflictException extends Exception {
        public TransactionConflictException(String message) {
            super(message);
        }
    }


    private BlockResult executeInternal(
            @Nullable ProgramTraceProcessor programTraceProcessor,
            Block block,
            BlockHeader parent,
            boolean discardInvalidTxs,
            boolean acceptInvalidTransactions) {
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

        BlockSharedData blockSharedData = new BlockSharedData(track, programTraceProcessor);

        int txindex = 0;

        int[] partitionEnds = block.getPartitionEnds();
        // Iterator<Integer> itPartitionEnds = Arrays.ite.asList(partitionEnds).ite
        final Iterator<Integer> itPartitionEnds = IntStream.of(partitionEnds).boxed().iterator();
        int nextIndexPartition = itPartitionEnds.hasNext() ? itPartitionEnds.next() : -1;

        TransactionsPartitionExecutor partExecutor = TransactionsPartitionExecutor.newTransactionsPartitionExecutor();

        TransactionConflictDetector transactionConflictDetector = new TransactionConflictDetector();
        if (track.isCached()) {
            track.getCacheTracking().subscribe(transactionConflictDetector);
        }

        TransactionsPartitioner partitioner = null;
        if (!block.isSealed()) {
            // filling block
            partitioner = new TransactionsPartitioner();
            // TODO when cacheTracker is replaced with transactionConflictDetector
            //   transactionConflictDetector.addPartitioner(partitioner)
        }

        for ( Transaction tx : block.getTransactionsList() ) {

            TransactionExecutorTask txExecutorTask = new TransactionExecutorTask(
                    transactionExecutorFactory,
                    tx,
                    txindex,
                    block,
                    blockSharedData,
                    logger,
                    acceptInvalidTransactions,
                    discardInvalidTxs);

            partExecutor.addTransactionTask(txExecutorTask);

            if (nextIndexPartition == txindex) {
                nextIndexPartition = itPartitionEnds.hasNext() ? itPartitionEnds.next() : -1;
                partExecutor = TransactionsPartitionExecutor.newTransactionsPartitionExecutor();
            }

            txindex++;
        }

        // Wait for all thread terminate
        try {
            TransactionsPartitionExecutor.waitForAllThreadTermination(
                    10000,
                    new TransactionsPartitionExecutor.PeriodicCheck() {
                        @Override
                        public boolean check() {
                            return !transactionConflictDetector.hasConflict();
                        }

                        @Override
                        public String getFailureMessage() {
                            return transactionConflictDetector.getConflictMessage();
                        }
                    });
        } catch (InterruptedException | TimeoutException | TransactionsPartitionExecutor.PeriodicCheckException e) {
            profiler.stop(metric);
            return BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT;
        } finally {
            if (track.isCached()) {
                track.getCacheTracking().unsubscribe(transactionConflictDetector);
            }
        }

        // RSKIP144
        // Transfer fees to coinbase or Remasc contract creates a conflicts between all transactions,
        // that prevents us to execute them in parallel.
        // Then in this case we transfer fees after all transactions are processed.
        Coin summaryFee = blockSharedData.getTotalPaidFees();

        if (!summaryFee.equals(Coin.valueOf(0))) {
            //TODO: REMOVE THIS WHEN THE LocalBLockTests starts working with REMASC
            if(config.isRemascEnabled()) {
                logger.trace("Adding fee to remasc contract account");
                track.addBalance(PrecompiledContracts.REMASC_ADDR, summaryFee);
            } else {
                track.addBalance(block.getCoinbase(), summaryFee);
            }
        }

        track.save();

        // RSKIP144 - now the transactions can be executed by several threads in parallel, meaning that the real order
        // of executions (blockSharedData.getExecutedTransactions()) and the receipts list (blockSharedData.getReceipts())
        // are not ordered according to the submission order specified (validating block) or to be specified (filling block)
        // in the block.
        // Here we need to reorder the 2 lists in the submission order before saving them in the BlockResult instance
        // When filling the block, the submission order is determined taking into account the transaction partitioning
        // across threads.
        // When validating the block, the submission order is the one specified in the block to be validated
        List<Transaction> transactionsList;
        if (block.isSealed()) {
            // validating block
            transactionsList = block.getTransactionsList();
            reorderTransactionList(blockSharedData.getExecutedTransactions(), transactionsList);
            reorderTransactionReceiptList(blockSharedData.getReceipts(), transactionsList);
        } else {
            // filling block
            transactionsList = partitioner.getAllTransactionsSortedPerPartition();
            // TODO : now we dont use the partitioner during tx execution yet, then transactionsList is not correct yet
            //  reorderTransactionList(blockSharedData.getExecutedTransactions(), transactionsList);
            //  reorderTransactionReceiptList(blockSharedData.getReceipts(), transactionsList);
        }

        BlockResult result = new BlockResult(
                block,
                // TODO : RSKIP144 : rework executedTransactions so that there shall be ordered according to the partition scheme
                blockSharedData.getExecutedTransactions(),
                blockSharedData.getReceipts(),
                blockSharedData.getTotalGasUsed(),
                blockSharedData.getTotalPaidFees(),
                track.getTrie()
        );
        profiler.stop(metric);
        return result;
    }

    @VisibleForTesting
    public static void reorderTransactionList(List<Transaction> txs, List<Transaction> refList) {
        List<Transaction> lstCopy = new ArrayList<>(txs);
        txs.clear();
        for (Transaction tx : refList) {
            if (lstCopy.contains(tx)) {
                txs.add(tx);
            }
        }
    }

    @VisibleForTesting
    public static void reorderTransactionReceiptList(List<TransactionReceipt> receipts, List<Transaction> refList) {
        List<Transaction> lstTxs = receipts.stream().map(receipt -> receipt.getTransaction()).collect(
                                Collectors.toList());
        Map<Keccak256, TransactionReceipt> mapReceipts = receipts.stream().collect(
                Collectors.toMap(x -> x.getTransaction().getHash(), x -> x));
        receipts.clear();
        for (Transaction tx : refList) {
            if (lstTxs.contains(tx)) {
                receipts.add( mapReceipts.get(tx.getHash()) );
            }
        }
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
}

