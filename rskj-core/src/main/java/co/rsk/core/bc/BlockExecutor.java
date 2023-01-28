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

import co.rsk.core.*;
import co.rsk.crypto.Keccak256;
import co.rsk.db.RepositoryLocator;
import co.rsk.metrics.profilers.Metric;
import co.rsk.metrics.profilers.Profiler;
import co.rsk.metrics.profilers.ProfilerFactory;
import co.rsk.remasc.RemascTransaction;
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
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP126;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP85;
import static org.ethereum.util.BIUtil.toBI;

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
    private boolean remascEnabled;
    private boolean isPlay;
    private boolean isMetrics;

    private final Map<Keccak256, ProgramResult> transactionResults = new ConcurrentHashMap<>();
    private boolean registerProgramResults;
    private final String filePath_validRule;
    private final String filePath_forMetrics;
    private final String filePath_timesSplitted;
    private final String filePath_timesValidity;
    private final String filePath_times;
    private final String filePath_timesSplitted_saveReceipts;

    public BlockExecutor(
            ActivationConfig activationConfig,
            RepositoryLocator repositoryLocator,
            TransactionExecutorFactory transactionExecutorFactory,
            boolean remascEnabled,
            boolean isPlay,
            boolean isMetrics) {
        this.repositoryLocator = repositoryLocator;
        this.transactionExecutorFactory = transactionExecutorFactory;
        this.activationConfig = activationConfig;
        this.remascEnabled = remascEnabled;
        this.isPlay = isPlay;
        this.isMetrics = isMetrics;
        this.filePath_times = "/home/ubuntu/output/times.csv";
        this.filePath_forMetrics = "/home/ubuntu/output/metrics.csv";
        this.filePath_timesSplitted = "/home/ubuntu/output/timesSplitted.csv";
        this.filePath_timesValidity = "/home/ubuntu/output/validitySplitted.csv";
        this.filePath_validRule = "/home/ubuntu/output/blockTxsValidRuleSplitted.csv";
        this.filePath_timesSplitted_saveReceipts = "/home/ubuntu/output/saveReceiptsSplitted.csv";
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

    public boolean isMetrics() {
        return isMetrics;
    }

    public String getFilePath_forMetrics() {
        return filePath_forMetrics;
    }

    public String getFilePath_times() {
        return filePath_times;
    }

    public String getFilePath_timesSplitted() {
        return filePath_timesSplitted;
    }

    public String getFilePath_timesSplitted_sr() {
        return filePath_timesSplitted_saveReceipts;
    }

    public String getFilePath_timesValidity() {
        return filePath_timesValidity;
    }

    public String getFilePath_timesRules() {
        return filePath_validRule;
    }

    @VisibleForTesting
    public static byte[] calculateLogsBloom(List<TransactionReceipt> receipts) {
        Bloom logBloom = new Bloom();

        for (TransactionReceipt receipt : receipts) {
            logBloom.or(receipt.getBloomFilter());
        }

        return logBloom.getData();
    }

    public ActivationConfig getActivationConfig() {
        return activationConfig;
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
            return executePreviousRSKIP144(null, 0, block, parent, discardInvalidTxs, ignoreReadyToExecute, saveState, true);
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
                return executePreviousRSKIP144(programTraceProcessor, vmTraceOptions, block, parent, discardInvalidTxs, acceptInvalidTransactions, saveState, false);
            }
    }

    public boolean isPlay() {
        return this.isPlay;
    }

    private BlockResult executePreviousRSKIP144(
            @Nullable ProgramTraceProcessor programTraceProcessor,
            int vmTraceOptions,
            Block block,
            BlockHeader parent,
            boolean discardInvalidTxs,
            boolean acceptInvalidTransactions,
            boolean saveState,
            boolean mining) {
        int numTxInBlock = block.getTransactionsList().size();
        long startTimeSetup = System.nanoTime();
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
        long endTimeSetup = System.nanoTime();
        int txindex = 0;
        long startTimeExecution = System.nanoTime();
        for (Transaction tx : block.getTransactionsList()) {

            addFeesToRemascIfRemascTx(block, track, totalPaidFees, txindex, tx);

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
                    deletedAccounts
            );
            boolean transactionExecuted = txExecutor.executeTransaction();

            if (!acceptInvalidTransactions && !transactionExecuted) {
                if (!discardInvalidTxs) {
                    return getBlockResultAndLogExecutionInterrupted(block, metric, tx);
                }
                loggingDiscardedBlock(block, tx);
                continue;
            }

            registerExecutedTx(programTraceProcessor, vmTrace, executedTransactions, tx, txExecutor);
            long gasUsed = txExecutor.getGasUsed();
            totalGasUsed += gasUsed;

            totalPaidFees = addTotalPaidFees(totalPaidFees, txExecutor);

            deletedAccounts.addAll(txExecutor.getResult().getDeleteAccounts());

            TransactionReceipt receipt = buildTransactionReceipt(tx, txExecutor, gasUsed, totalGasUsed);

            loggingExecuteTxAndReceipt(block, i, tx);

            i++;

            receipts.add(receipt);

            loggingTxDone();
        }
        long endTimeExecution = System.nanoTime();
        long startTimeFinish = System.nanoTime();
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
        long endTimeFinish = System.nanoTime();

        String playOrGenerate = isPlay()? "play" : "generate";

        if (!isMetrics()) {
            Path file_times = Paths.get(getFilePath_timesSplitted());
            String header_times = "playOrGenerate,rskip144,moment,bnumber,time\r";
            long blockNumber = block.getNumber();
            boolean isRskip144Actived = activationConfig.isActive(ConsensusRule.RSKIP144, blockNumber);
            String data_times_setup = playOrGenerate+","+ isRskip144Actived +",setup,"+ blockNumber +","+(endTimeSetup-startTimeSetup)+ "\r";
            String data_times_seq = playOrGenerate+","+ isRskip144Actived +",execSequential,"+ blockNumber +","+(endTimeExecution-startTimeExecution)+ "\r";
            String data_times_finish = playOrGenerate+","+ isRskip144Actived +",finishExec,"+ blockNumber +","+(endTimeFinish-startTimeFinish)+ "\r";
            try {
                FileWriter myWriter_times = new FileWriter(getFilePath_timesSplitted(), true);

                if (!Files.exists(file_times)) {
                    myWriter_times.write(header_times);
                }
                myWriter_times.write(data_times_setup);
                myWriter_times.write(data_times_seq);
                myWriter_times.write(data_times_finish);
                myWriter_times.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            String moment = mining? "mining" : "tryToConnect";

            //Num tx in sequential and in parallel are -1 because we are in the execution pre-RSKIP144, there is no subliwsts
            String header = "playOrGenerate,rskip144,moment,bnumber,gasLimit,numTxInBlock,numExecutedTx,feeTotal,gasTotal,numTxInParallel,numTxInSequential\r";
            String data = playOrGenerate+","+ isRskip144Actived +","+moment+","+
                    blockNumber +","+ toBI(block.getGasLimit()).longValue()+"," + numTxInBlock +","+ result.getExecutedTransactions().size() +","+result.getPaidFees().toString()+","+ result.getGasUsed()+",-1,-1\r";

            try {
                String filePath = getFilePath_forMetrics();
                Path file = Paths.get(filePath);
                FileWriter myWriter;
                if (!Files.exists(file) || Files.size(file) == 0) {
                    myWriter = new FileWriter(filePath, true);
                    myWriter.write(header);
                    myWriter.write(data);
                } else {
                    myWriter = new FileWriter(filePath, true);
                    myWriter.write(data);
                }
                myWriter.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        profiler.stop(metric);
        logger.trace("End execute pre RSKIP144.");
        return result;
    }

    private void registerExecutedTx(ProgramTraceProcessor programTraceProcessor, boolean vmTrace, List<Transaction> executedTransactions, Transaction tx, TransactionExecutor txExecutor) {
        executedTransactions.add(tx);

        if (this.registerProgramResults) {
            this.transactionResults.put(tx.getHash(), txExecutor.getResult());
        }

        if (vmTrace) {
            txExecutor.extractTrace(programTraceProcessor);
        }

        loggingTxExecuted();
    }

    private void addFeesToRemascIfRemascTx(Block block, Repository track, Coin totalPaidFees, int txindex, Transaction tx) {
        if (this.isRemascEnabled() && tx.isRemascTransaction(block.getTransactionsList().size(), txindex)) {
            addFeesToRemasc(totalPaidFees, track);
        }
    }

    private Coin addTotalPaidFees(Coin totalPaidFees, TransactionExecutor txExecutor) {
        Coin paidFees = txExecutor.getPaidFees();
        if (paidFees != null) {
            totalPaidFees = totalPaidFees.add(paidFees);
        }
        return totalPaidFees;
    }

    private BlockResult executeParallel(
            @Nullable ProgramTraceProcessor programTraceProcessor,
            int vmTraceOptions,
            Block block,
            BlockHeader parent,
            boolean discardInvalidTxs,
            boolean acceptInvalidTransactions,
            boolean saveState) {
        int numTxInBlock = block.getTransactionsList().size();
        long startTimeSetup = System.nanoTime();
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
        ExecutorService executorService = Executors.newFixedThreadPool(Constants.getTransactionExecutionThreads());
        ExecutorCompletionService<Boolean> completionService = new ExecutorCompletionService<>(executorService);
        List<TransactionListExecutor> transactionListExecutors = new ArrayList<>();
        ReadWrittenKeysTracker aTracker = new ReadWrittenKeysTracker();
        Repository track = repositoryLocator.startTrackingAt(parent, aTracker);
        maintainPrecompiledContractStorageRoots(track, activationConfig.forBlock(block.getNumber()));
        aTracker.clear();
        int nTasks = 0;

        // execute parallel subsets of transactions
        long endTimeSetup = System.nanoTime();
        long startTimeParallel = System.nanoTime();

        short start = 0;
        for (short end : block.getHeader().getTxExecutionSublistsEdges()) {
            List<Transaction> sublist = block.getTransactionsList().subList(start, end);
            TransactionListExecutor txListExecutor = new TransactionListExecutor(
                    sublist,
                    block,
                    transactionExecutorFactory,
                    track.startTracking(),
                    vmTrace,
                    vmTraceOptions,
                    new HashSet<>(),
                    discardInvalidTxs,
                    acceptInvalidTransactions,
                    new HashMap<>(),
                    new HashMap<>(),
                    new HashMap<>(),
                    registerProgramResults,
                    programTraceProcessor,
                    start,
                    Coin.ZERO,
                    remascEnabled
            );
            completionService.submit(txListExecutor);
            transactionListExecutors.add(txListExecutor);
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

        long endTimeParallel = System.nanoTime();
        long startTimeDetectCollision = System.nanoTime();
        // Review collision
        if (aTracker.detectCollision()) {
            logger.warn("block: [{}] execution failed", block.getNumber());
            profiler.stop(metric);
            return BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT;
        }

        long endTimeDetectCollision = System.nanoTime();
        long startTimeMerge = System.nanoTime();
        // Merge maps.
        Map<Integer, Transaction> executedTransactions = new HashMap<>();
        Set<DataWord> deletedAccounts = new HashSet<>();
        Map<Integer, TransactionReceipt> receipts = new HashMap<>();
        Map<Keccak256, ProgramResult> mergedTransactionResults = new HashMap<>();
        Coin totalPaidFees = Coin.ZERO;
        long totalGasUsed = 0;

        for (TransactionListExecutor tle : transactionListExecutors) {
            tle.getRepository().commit();
            deletedAccounts.addAll(tle.getDeletedAccounts());
            executedTransactions.putAll(tle.getExecutedTransactions());
            receipts.putAll(tle.getReceipts());
            mergedTransactionResults.putAll(tle.getTransactionResults());
            totalPaidFees = totalPaidFees.add(tle.getTotalFees());
            totalGasUsed += tle.getTotalGas();
        }

        long executedTransactionsInParallel = executedTransactions.size();
        long endTimeMerge = System.nanoTime();
        long startTimeSequential = System.nanoTime();

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
                mergedTransactionResults,
                registerProgramResults,
                programTraceProcessor,
                start,
                totalPaidFees,
                remascEnabled
        );

        long endTimeSequential = System.nanoTime();
        long startTimeFinish = System.nanoTime();
        Boolean success = txListExecutor.call();
        if (!Boolean.TRUE.equals(success)) {
            return BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT;
        }

        long executedTransactionsInSequential = executedTransactions.size() - executedTransactionsInParallel;

        Coin totalBlockPaidFees = txListExecutor.getTotalFees();
        totalGasUsed += txListExecutor.getTotalGas();

        saveOrCommitTrackState(saveState, track);

        BlockResult result = new BlockResult(
                block,
                new LinkedList<>(executedTransactions.values()),
                new LinkedList<>(receipts.values()),
                block.getHeader().getTxExecutionSublistsEdges(),
                totalGasUsed,
                totalBlockPaidFees,
                vmTrace ? null : track.getTrie()
        );
        long endTimeFinish = System.nanoTime();

        String playOrGenerate = isPlay()? "play" : "generate";
        if (!isMetrics()) {
            Path file_times = Paths.get(getFilePath_timesSplitted());
            String header_times = "playOrGenerate,rskip144,moment,bnumber,time\r";
            long blockNumber = block.getNumber();
            boolean isRskip144Active = activationConfig.isActive(ConsensusRule.RSKIP144, blockNumber);

            String data_times_setup = playOrGenerate + "," + isRskip144Active + ",setup," + blockNumber + "," + (endTimeSetup - startTimeSetup) + "\r";
            String data_times_parallel = playOrGenerate + "," + isRskip144Active + ",execParallel," + blockNumber + "," + (endTimeParallel - startTimeParallel) + "\r";
            String data_times_collision = playOrGenerate + "," + isRskip144Active + ",detectCollision," + blockNumber + "," + (endTimeDetectCollision - startTimeDetectCollision) + "\r";
            String data_times_merge = playOrGenerate + "," + isRskip144Active + ",mergeMaps," + blockNumber + "," + (endTimeMerge - startTimeMerge) + "\r";
            String data_times_seq = playOrGenerate + "," + isRskip144Active + ",execSequential," + blockNumber + "," + (endTimeSequential - startTimeSequential) + "\r";
            String data_times_finish = playOrGenerate + "," + isRskip144Active + ",finishExec," + blockNumber + "," + (endTimeFinish - startTimeFinish) + "\r";

            try {
                FileWriter myWriter_times = new FileWriter(getFilePath_timesSplitted(), true);

                if (!Files.exists(file_times) || Files.size(file_times) == 0) {
                    myWriter_times.write(header_times);
                }
                myWriter_times.write(data_times_setup);
                myWriter_times.write(data_times_parallel);
                myWriter_times.write(data_times_collision);
                myWriter_times.write(data_times_merge);
                myWriter_times.write(data_times_seq);
                myWriter_times.write(data_times_finish);
                myWriter_times.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

//            profiler.stop(metric);
//            logger.trace("End execute pre RSKIP144.");
//            return result;
//        }


            String filePath = getFilePath_forMetrics();
            Path file = Paths.get(filePath);
            int threads = Constants.getTransactionExecutionThreads();
            String header = "playOrGenerate,rskip144,moment,bnumber,gasLimit,numTxInBlock,numExecutedTx,feeTotal,gasTotal,numTxInParallel,numTxInSequential";

            for (int j = 0; j < threads; j++) {
                header = header.concat(",thread".concat(String.valueOf(j)));
            }

            for (int j = 0; j < threads; j++) {
                header = header.concat(",gasThread".concat(String.valueOf(j)));
            }

            header = header + "\r";

            short[] txExecutionSublistsEdges = block.getHeader().getTxExecutionSublistsEdges();

            String data = playOrGenerate + "," + activationConfig.isActive(ConsensusRule.RSKIP144, block.getNumber()) + ",tryToConnect," +
                    block.getNumber() + "," + toBI(block.getGasLimit()).longValue() + "," + numTxInBlock + "," + result.getExecutedTransactions().size() + "," + result.getPaidFees().toString() + "," + result.getGasUsed() + "," + executedTransactionsInParallel + "," + executedTransactionsInSequential;

            short lastNum = 0;
            short len = 0;
            for (short edge : txExecutionSublistsEdges) {
                data = data.concat("," + (edge - lastNum));
                lastNum = edge;
                len++;
            }

            if (len < threads) {
                for (int i = 0; i < threads - len; i++) {
                    data = data.concat("," + 0);
                }
            }

            for (int i = 0; i < threads; i++) {
                data = data.concat(',' + String.valueOf(-1));
            }

            data = data + "\r";


            try {
                FileWriter myWriter;

                if (!Files.exists(file)) {
                    myWriter = new FileWriter(filePath, true);
                    myWriter.write(header);
                    myWriter.write(data);
                } else {
                    myWriter = new FileWriter(filePath, true);
                    myWriter.write(data);
                }
                myWriter.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        profiler.stop(metric);
        logger.trace("End executeParallel.");
        return result;
    }

    private void addFeesToRemasc(Coin remascFees, Repository track) {
        if (remascFees.compareTo(Coin.ZERO) > 0) {
            logger.trace("Adding fee to remasc contract account");
            track.addBalance(PrecompiledContracts.REMASC_ADDR, remascFees);
        }
    }

    private boolean isRemascEnabled() {
        return this.remascEnabled;
    }

    private BlockResult executeForMiningAfterRSKIP144(
            Block block,
            BlockHeader parent,
            boolean discardInvalidTxs,
            boolean acceptInvalidTransactions,
            boolean saveState) {
        int numTxInBlock = block.getTransactionsList().size();
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
        readWrittenKeysTracker.clear();
        int i = 1;
        long gasUsedInBlock = 0;
        Coin totalPaidFees = Coin.ZERO;
        Map<Transaction, TransactionReceipt> receiptsByTx = new HashMap<>();
        Set<DataWord> deletedAccounts = new HashSet<>();

        //ejecucion 1 en los buckets paralelos la mitad de esto, y en el secuencial este nro para poder meter las mismas txs exactamente que tiene cada bloque de ethereum en uno de rsk paralelo
        ParallelizeTransactionHandler parallelizeTransactionHandler = new ParallelizeTransactionHandler((short) Constants.getTransactionExecutionThreads(), GasCost.toGas(block.getGasLimit()), GasCost.toGas(block.getGasLimit())/2);

        int txindex = 0;

        for (Transaction tx : transactionsList) {
            loggingApplyBlockToTx(block, i);

            int numberOfTransactions = transactionsList.size();
            boolean isRemascTransaction = tx.isRemascTransaction(txindex, numberOfTransactions) || tx.getClass() == RemascTransaction.class;

            addFeesToRemascIfRemascTxAndTrack(track, totalPaidFees, isRemascTransaction);

            TransactionExecutor txExecutor = transactionExecutorFactory.newInstance(
                    tx,
                    txindex,
                    block.getCoinbase(),
                    track,
                    block,
                    parallelizeTransactionHandler.getGasUsedInSequential(),
                    false,
                    0,
                    deletedAccounts
            );
            boolean transactionExecuted = txExecutor.executeTransaction();

            if (!acceptInvalidTransactions && !transactionExecuted) {
                if (discardIfInvalid(block, discardInvalidTxs, track, totalPaidFees, txindex, tx, numberOfTransactions, isRemascTransaction)) {
                    return getBlockResultAndLogExecutionInterrupted(block, metric, tx);
                }
                txindex++;
                continue;
            }

            Optional<Long> sublistGasAccumulated = calculateSublistGasAccumulated(readWrittenKeysTracker, parallelizeTransactionHandler, tx, isRemascTransaction, txExecutor, block);

            if (!acceptInvalidTransactions && !sublistGasAccumulated.isPresent()) {
                if (discardIfInvalid(block, discardInvalidTxs, track, totalPaidFees, txindex, tx, numberOfTransactions, isRemascTransaction)) {
                    return getBlockResultAndLogExecutionInterrupted(block, metric, tx);
                }
                txindex++;
                continue;
            }

            registerTxExecutedForMiningAfterRSKIP144(readWrittenKeysTracker, tx, txExecutor);

            long gasUsed = txExecutor.getGasUsed();
            gasUsedInBlock += gasUsed;
            totalPaidFees = addTotalPaidFees(totalPaidFees, txExecutor);

            payToRemascWhenThereIsNoRemascTx(track, totalPaidFees, txindex, numberOfTransactions, isRemascTransaction);

            deletedAccounts.addAll(txExecutor.getResult().getDeleteAccounts());

            //orElseGet is used for testing only when acceptInvalidTransactions is set.
            long cumulativeGas = sublistGasAccumulated
                    .orElseGet(() -> parallelizeTransactionHandler.getGasUsedIn((short) Constants.getTransactionExecutionThreads()));
            TransactionReceipt receipt = buildTransactionReceipt(tx, txExecutor, gasUsed, cumulativeGas);

            loggingExecuteTxAndReceipt(block, i, tx);

            i++;
            txindex++;

            receiptsByTx.put(tx, receipt);

            loggingTxDone();
        }


        saveOrCommitTrackState(saveState, track);


        List<Transaction> executedTransactions = parallelizeTransactionHandler.getTransactionsInOrder();
        short[] sublistOrder = parallelizeTransactionHandler.getTransactionsPerSublistInOrder();
        List<TransactionReceipt> receipts = getTransactionReceipts(receiptsByTx, executedTransactions);

        BlockResult result = new BlockResult(
                block,
                executedTransactions,
                receipts,
                sublistOrder,
                gasUsedInBlock,
                totalPaidFees,
                track.getTrie()
        );


//        if (!isMetrics()) {
//            profiler.stop(metric);
//            logger.trace("End execute pre RSKIP144.");
//            return result;
//        }

        String playOrGenerate = isPlay()? "play" : "generate";
        String filePath = getFilePath_forMetrics();
        Path file = Paths.get(filePath);

        int threads = Constants.getTransactionExecutionThreads();
        String header = "playOrGenerate,rskip144,moment,bnumber,gasLimit,numTxInBlock,numExecutedTx,feeTotal,gasTotal,numTxInParallel,numTxInSequential";

        for (int j = 0; j < threads; j++) {
            header = header.concat(",thread".concat(String.valueOf(j)));
        }

        for (int j = 0; j < threads; j++) {
            header = header.concat(",gasThread".concat(String.valueOf(j)));
        }

        header = header+"\r";

        String data = playOrGenerate+","+activationConfig.isActive(ConsensusRule.RSKIP144, block.getNumber())+",mining,"+
                block.getNumber() + ","+ toBI(block.getGasLimit()).longValue() + "," + numTxInBlock + "," + result.getExecutedTransactions().size() +","+result.getPaidFees().toString()+","+ result.getGasUsed()+","+ parallelizeTransactionHandler.getTxInParallel() +","+ parallelizeTransactionHandler.getTxInSequential();

        List<Short> transactionsInOrder = parallelizeTransactionHandler.getTxsPerSublist();
        for (Short txs : transactionsInOrder) {
            data = data.concat(','+String.valueOf(txs));
        }

        List<Long> gasPerSublist = parallelizeTransactionHandler.getGasPerSublist();
        for (Long gas : gasPerSublist) {
            data = data.concat(','+String.valueOf(gas));
        }

        data = data+"\r";

        try {
            FileWriter myWriter;
            if (!Files.exists(file)) {
                myWriter = new FileWriter(filePath, true);
                myWriter.write(header);
            } else {
                myWriter = new FileWriter(filePath,     true);
            }
            myWriter.write(data);
            myWriter.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        profiler.stop(metric);
        logger.trace("End executeForMining.");
        return result;
    }

    private void registerTxExecutedForMiningAfterRSKIP144(IReadWrittenKeysTracker readWrittenKeysTracker, Transaction tx, TransactionExecutor txExecutor) {
        readWrittenKeysTracker.clear();

        if (this.registerProgramResults) {
            this.transactionResults.put(tx.getHash(), txExecutor.getResult());
        }

        loggingTxExecuted();
    }

    private List<TransactionReceipt> getTransactionReceipts(Map<Transaction, TransactionReceipt> receiptsByTx, List<Transaction> executedTransactions) {
        List<TransactionReceipt> receipts = new ArrayList<>();

        for (Transaction tx : executedTransactions) {
            receipts.add(receiptsByTx.get(tx));
        }
        return receipts;
    }

    private boolean discardIfInvalid(Block block, boolean discardInvalidTxs, Repository track, Coin totalPaidFees, int txindex, Transaction tx, int numberOfTransactions, boolean isRemascTransaction) {
        payToRemascWhenThereIsNoRemascTx(track, totalPaidFees, txindex, numberOfTransactions, isRemascTransaction);

        if (!discardInvalidTxs) {
            return true;
        }

        loggingDiscardedBlock(block, tx);
        return false;
    }

    private Optional<Long> calculateSublistGasAccumulated(IReadWrittenKeysTracker readWrittenKeysTracker, ParallelizeTransactionHandler parallelizeTransactionHandler, Transaction tx, boolean isRemascTransaction, TransactionExecutor txExecutor, Block block) {
        Optional<Long> sublistGasAccumulated;
        if (isRemascTransaction) {
            sublistGasAccumulated = parallelizeTransactionHandler.addRemascTransaction(tx, txExecutor.getGasUsed());
        } else {
            sublistGasAccumulated = parallelizeTransactionHandler.addTransaction(tx, readWrittenKeysTracker.getThisThreadReadKeys(), readWrittenKeysTracker.getThisThreadWrittenKeys(), txExecutor.getGasUsed());
        }
        return sublistGasAccumulated;
    }

    private void addFeesToRemascIfRemascTxAndTrack(Repository track, Coin totalPaidFees, boolean isRemascTransaction) {
        if (this.isRemascEnabled() && isRemascTransaction) {
            addFeesToRemasc(totalPaidFees, track);
        }
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
    }

    // This method is used just when a block does not contain REMASC as last transaction. It happens only for testing.
    private void payToRemascWhenThereIsNoRemascTx(Repository track, Coin totalPaidFees, int txindex, int numberOfTransactions, boolean isRemascTransaction) {
        boolean isLastTx = txindex == numberOfTransactions - 1;
        if (this.remascEnabled && isLastTx && !isRemascTransaction) {
            addFeesToRemasc(totalPaidFees, track);
        }
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
