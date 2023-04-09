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

import co.rsk.core.BlockDifficulty;
import co.rsk.db.StateRootHandler;
import co.rsk.metrics.profilers.Metric;
import co.rsk.metrics.profilers.Profiler;
import co.rsk.metrics.profilers.ProfilerFactory;
import co.rsk.panic.PanicProcessor;
import co.rsk.util.FormatUtils;
import co.rsk.validators.BlockValidator;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.db.BlockInformation;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.TransactionInfo;
import org.ethereum.listener.EthereumListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Created by ajlopez on 29/07/2016.
 */

/**
 * Original comment:
 *
 * The Ethereum blockchain is in many ways similar to the Bitcoin blockchain,
 * although it does have some differences.
 * <p>
 * The main difference between Ethereum and Bitcoin with regard to the blockchain architecture
 * is that, unlike Bitcoin, Ethereum blocks contain a copy of both the transaction list
 * and the most recent state. Aside from that, two other values, the block number and
 * the difficulty, are also stored in the block.
 * </p>
 * The block validation algorithm in Ethereum is as follows:
 * <ol>
 * <li>Check if the previous block referenced exists and is valid.</li>
 * <li>Check that the timestamp of the block is greater than that of the referenced previous block and less than 15 minutes into the future</li>
 * <li>Check that the block number, difficulty, transaction root, uncle root and gas limit (various low-level Ethereum-specific concepts) are valid.</li>
 * <li>Check that the proof of work on the block is valid.</li>
 * <li>Let S[0] be the STATE_ROOT of the previous block.</li>
 * <li>Let TX be the block's transaction list, with n transactions.
 * For all in in 0...n-1, set S[i+1] = APPLY(S[i],TX[i]).
 * If any applications returns an error, or if the total gas consumed in the block
 * up until this point exceeds the GASLIMIT, return an error.</li>
 * <li>Let S_FINAL be S[n], but adding the block reward paid to the miner.</li>
 * <li>Check if S_FINAL is the same as the STATE_ROOT. If it is, the block is valid; otherwise, it is not valid.</li>
 * </ol>
 * See <a href="https://github.com/ethereum/wiki/wiki/White-Paper#blockchain-and-mining">Ethereum Whitepaper</a>
 *
 */

public class BlockChainImpl implements Blockchain {
    private static final Profiler profiler = ProfilerFactory.getInstance();
    private static final Logger logger = LoggerFactory.getLogger("blockchain");
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    private final BlockStore blockStore;
    private final ReceiptStore receiptStore;
    private final TransactionPool transactionPool;
    private final StateRootHandler stateRootHandler;
    private final EthereumListener listener;
    private BlockValidator blockValidator;

    private volatile BlockChainStatus status = new BlockChainStatus(null, BlockDifficulty.ZERO);

    private final Object connectLock = new Object();
    private final Object accessLock = new Object();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private final BlockExecutor blockExecutor;
    private boolean noValidation;
    private int numTxPreTC;
    private int executedTransactions;
    private int receipts;
    private int totalReceipts;

    public BlockChainImpl(BlockStore blockStore,
                          ReceiptStore receiptStore,
                          TransactionPool transactionPool,
                          EthereumListener listener,
                          BlockValidator blockValidator,
                          BlockExecutor blockExecutor,
                          StateRootHandler stateRootHandler) {
        this.numTxPreTC = 0;
        this.executedTransactions = 0;
        this.receipts = 0;
        this.totalReceipts = 0;
        this.blockStore = blockStore;
        this.receiptStore = receiptStore;
        this.listener = listener;
        this.blockValidator = blockValidator;
        this.blockExecutor = blockExecutor;
        this.transactionPool = transactionPool;
        this.stateRootHandler = stateRootHandler;
    }

    @VisibleForTesting
    public void setBlockValidator(BlockValidator validator) {
        this.blockValidator = validator;
    }

    @Override
    public long getSize() {
        return status.getBestBlock().getNumber() + 1;
    }

    public BlockStore getBlockStore() {
        return blockStore;
    }

    public ReceiptStore getReceiptStore() {
        return receiptStore;
    }

    /**
     * Try to add a block to a blockchain
     *
     * @param block        A block to try to add
     * @return IMPORTED_BEST if the block is the new best block
     *      IMPORTED_NOT_BEST if it was added to alternative chain
     *      NO_PARENT  the block parent is unknown yet
     *      INVALID_BLOCK   the block has invalida data/state
     *      EXISTS  the block was already processed
     */
    @Override
    public ImportResult tryToConnect(Block block) {
        long startPreInternalTryToConnect = System.nanoTime();
        this.lock.readLock().lock();

        try {
            if (block == null) {
                return ImportResult.INVALID_BLOCK;
            }

            if (!block.isSealed()) {
                panicProcessor.panic("unsealedblock", String.format("Unsealed block %s %s", block.getNumber(), block.getHash()));
                block.seal();
            }

            try {
                org.slf4j.MDC.put("blockHash", block.getHash().toHexString());
                org.slf4j.MDC.put("blockHeight", Long.toString(block.getNumber()));

                logger.trace("Try connect block hash: {}, number: {}",
                             block.getPrintableHash(),
                             block.getNumber());

                synchronized (connectLock) {
                    logger.trace("Start try connect");
                    long saveTime = System.nanoTime();
                    long endPreInternalTryToConnect = System.nanoTime();
                    ImportResult result = internalTryToConnect(block);
                    long startPostInternalTryToConnect = System.nanoTime();
                    long totalTime = System.nanoTime() - saveTime;
                    String timeInSeconds = FormatUtils.formatNanosecondsToSeconds(totalTime);

                    if (BlockUtils.tooMuchProcessTime(totalTime)) {
                        logger.warn("block: num: [{}] hash: [{}], processed after: [{}]seconds, result {}", block.getNumber(), block.getPrintableHash(), timeInSeconds, result);
                    }
                    else {
                        logger.info("block: num: [{}] hash: [{}], processed after: [{}]seconds, result {}", block.getNumber(), block.getPrintableHash(), timeInSeconds, result);
                    }
                    long endPostInternalTryToConnect = System.nanoTime();

                    if (!blockExecutor.isMetrics()) {
                        String playOrGenerate = blockExecutor.isPlay()? "play" : "generate";
                        String filePath_times = blockExecutor.getFilePath_timesSplitted();
                        long blockNumber = block.getNumber();
                        boolean isRskip144Actived = blockExecutor.getActivationConfig().isActive(ConsensusRule.RSKIP144, blockNumber);

                        Path file_times = Paths.get(filePath_times);
                        String header_times = "playOrGenerate,rskip144,moment,bnumber,time\r";
                        String data_times_pre = playOrGenerate + "," + isRskip144Actived + ",preInternalConnect," + blockNumber + "," + (endPreInternalTryToConnect - startPreInternalTryToConnect) + "\r";
                        String data_times_post = playOrGenerate + "," + isRskip144Actived + ",postInternalConnect," + blockNumber + "," + (endPostInternalTryToConnect - startPostInternalTryToConnect) + "\r";

                        try {
                            FileWriter myWriter_times = new FileWriter(filePath_times, true);

                            if (!Files.exists(file_times) || Files.size(file_times) == 0) {
                                myWriter_times.write(header_times);
                            }
                            myWriter_times.write(data_times_pre);
                            myWriter_times.write(data_times_post);
                            myWriter_times.close();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    return result;
                }
            } catch (Throwable t) {
                logger.error("Unexpected error: ", t);
                return ImportResult.INVALID_BLOCK;
            }
            finally {
                org.slf4j.MDC.remove("blockHash");
                org.slf4j.MDC.remove("blockHeight");

            }
        }
        finally {
            this.lock.readLock().unlock();
        }

    }

    private ImportResult internalTryToConnect(Block block) {
        Metric metric = profiler.start(Profiler.PROFILING_TYPE.BEFORE_BLOCK_EXEC);

        long startCheckExistenceOfBlock = System.nanoTime();
        if (blockStore.getBlockByHash(block.getHash().getBytes()) != null &&
                !BlockDifficulty.ZERO.equals(blockStore.getTotalDifficultyForHash(block.getHash().getBytes()))) {
            logger.debug("Block already exist in chain hash: {}, number: {}",
                         block.getPrintableHash(),
                         block.getNumber());
            profiler.stop(metric);
            return ImportResult.EXIST;
        }

        Block bestBlock;
        BlockDifficulty bestTotalDifficulty;

        logger.trace("get current state");

        // get current state
        synchronized (accessLock) {
            bestBlock = status.getBestBlock();
            bestTotalDifficulty = status.getTotalDifficulty();
        }

        Block parent;
        BlockDifficulty parentTotalDifficulty;

        long endCheckExistenceOfBlock = System.nanoTime();
        long startCheckParentBlock = System.nanoTime();

        // Incoming block is child of current best block
        if (bestBlock == null || bestBlock.isParentOf(block)) {
            parent = bestBlock;
            parentTotalDifficulty = bestTotalDifficulty;
        }
        // else, Get parent AND total difficulty
        else {
            logger.trace("get parent and total difficulty");
            parent = blockStore.getBlockByHash(block.getParentHash().getBytes());

            if (parent == null) {
                profiler.stop(metric);
                return ImportResult.NO_PARENT;
            }

            parentTotalDifficulty = blockStore.getTotalDifficultyForHash(parent.getHash().getBytes());

            if (parentTotalDifficulty == null || parentTotalDifficulty.equals(BlockDifficulty.ZERO)) {
                profiler.stop(metric);
                return ImportResult.NO_PARENT;
            }
        }
        long endCheckParentBlock = System.nanoTime();
        long startCheckBlockValidity = System.nanoTime();
        // Validate incoming block before its processing
        if (!isValid(block)) {
            long blockNumber = block.getNumber();
            logger.warn("Invalid block with number: {}", blockNumber);
            panicProcessor.panic("invalidblock", String.format("Invalid block %s %s", blockNumber, block.getHash()));
            profiler.stop(metric);
            return ImportResult.INVALID_BLOCK;
        }

        profiler.stop(metric);
        BlockResult result = null;
        long endCheckBlockValidity = System.nanoTime();

        long startResultValidation = 0;
        long endResultValidation = 0;
        long startRegisterResult = 0;
        long endRegisterResult = 0;
        if (parent != null) {
            long saveTime = System.nanoTime();
            logger.trace("execute start");
            numTxPreTC = block.getTransactionsList().size();
            result = blockExecutor.execute(null, 0, block, parent.getHeader(), false, noValidation,
                    true);
            executedTransactions = result.getExecutedTransactions().size();
            receipts = result.getTransactionReceipts().size();
            totalReceipts += result.getTransactionReceipts().size();

            startResultValidation = System.nanoTime();
            logger.trace("execute done");

            metric = profiler.start(Profiler.PROFILING_TYPE.AFTER_BLOCK_EXEC);
            boolean isValid = noValidation ? true : blockExecutor.validate(block, result);
            endResultValidation = System.nanoTime();
            logger.trace("validate done");

            if (!isValid) {
                profiler.stop(metric);
                return ImportResult.INVALID_BLOCK;
            }
            // Now that we know it's valid, we can commit the changes made by the block
            // to the parent's repository.

            long totalTime = System.nanoTime() - saveTime;
            String timeInSeconds = FormatUtils.formatNanosecondsToSeconds(totalTime);
            startRegisterResult = System.nanoTime();
            if (BlockUtils.tooMuchProcessTime(totalTime)) {
                logger.warn("block: num: [{}] hash: [{}], executed after: [{}]seconds", block.getNumber(), block.getPrintableHash(), timeInSeconds);
            }
            else {
                logger.trace("block: num: [{}] hash: [{}], executed after: [{}]seconds", block.getNumber(), block.getPrintableHash(), timeInSeconds);
            }

            // the block is valid at this point
            stateRootHandler.register(block.getHeader(), result.getFinalState());
            endRegisterResult = System.nanoTime();
            profiler.stop(metric);
        }

        metric = profiler.start(Profiler.PROFILING_TYPE.AFTER_BLOCK_EXEC);
        // the new accumulated difficulty
        BlockDifficulty totalDifficulty = parentTotalDifficulty.add(block.getCumulativeDifficulty());
        logger.trace("TD: updated to {}", totalDifficulty);

        // It is the new best block
        long startRebranching = System.nanoTime();
        if (SelectionRule.shouldWeAddThisBlock(totalDifficulty, status.getTotalDifficulty(),block, bestBlock)) {
            if (bestBlock != null && !bestBlock.isParentOf(block)) {
                logger.trace("Rebranching: {} ~> {} From block {} ~> {} Difficulty {} Challenger difficulty {}",
                        bestBlock.getPrintableHash(), block.getPrintableHash(), bestBlock.getNumber(), block.getNumber(),
                        status.getTotalDifficulty(), totalDifficulty);
                blockStore.reBranch(block);
            }
            long endRebranching = System.nanoTime();
            logger.trace("Start switchToBlockChain");
            long startSwitchToBlockchain = System.nanoTime();
            switchToBlockChain(block, totalDifficulty);
            long endSwitchToBlockchain = System.nanoTime();
            logger.trace("Start saveReceipts");
            long startSaveReceipts = System.nanoTime();
            saveReceipts(block, result);
            long endSaveReceipts = System.nanoTime();
            logger.trace("Start processBest");
            long startProcessBest = System.nanoTime();
            processBest(block);
            long endProcessBest = System.nanoTime();
            logger.trace("Start onBestBlock");
            long startOnBestBlock = System.nanoTime();
            onBestBlock(block, result);
            long endOnBestBlock = System.nanoTime();
            logger.trace("Start onBlock");
            long startOnBlock = System.nanoTime();
            onBlock(block, result);
            long endOnBlock = System.nanoTime();
            logger.trace("Start flushData");
            logger.trace("Better block {} {}", block.getNumber(), block.getPrintableHash());

            logger.debug("block added to the blockChain: index: [{}]", block.getNumber());
            if (block.getNumber() % 100 == 0) {
                logger.info("*** Last block added [ #{} ]", block.getNumber());
            }

            profiler.stop(metric);

            String playOrGenerate = blockExecutor.isPlay()? "play" : "generate";

            if (!blockExecutor.isMetrics()) {
                String filePath_times = blockExecutor.getFilePath_timesSplitted();
                Path file_times = Paths.get(filePath_times);
                String header_times = "playOrGenerate,rskip144,moment,bnumber,time\r";
                long blockNumber = block.getNumber();
                boolean rskip144Active = blockExecutor.getActivationConfig().isActive(ConsensusRule.RSKIP144, blockNumber);
                String existenceOfBlock_times = playOrGenerate+","+ rskip144Active +",checkExistenceOfBlock,"+ blockNumber +","+(endCheckExistenceOfBlock-startCheckExistenceOfBlock)+ "\r";
                String checkParentBlock_times = playOrGenerate+","+ rskip144Active +",checkParentBlock,"+ blockNumber +","+(endCheckParentBlock-startCheckParentBlock)+ "\r";
                String checkBlockValidty_times = playOrGenerate+","+ rskip144Active +",checkBlockValidty,"+ blockNumber +","+(endCheckBlockValidity-startCheckBlockValidity)+ "\r";
                String resultValidation_times = playOrGenerate+","+ rskip144Active +",resultValidation,"+ blockNumber +","+(endResultValidation-startResultValidation)+ "\r";
                String registerResult_times = playOrGenerate+","+ rskip144Active +",registerResult,"+ blockNumber +","+(endRegisterResult-startRegisterResult)+ "\r";
                String rebranching_times = playOrGenerate+","+ rskip144Active +",rebranching,"+ blockNumber +","+(endRebranching-startRebranching)+ "\r";
                String switchToBlockchain_times = playOrGenerate+","+ rskip144Active +",switchToBlockchain,"+ blockNumber +","+(endSwitchToBlockchain-startSwitchToBlockchain)+ "\r";
                String saveReceipts_times = playOrGenerate+","+ rskip144Active +",saveReceipts,"+ blockNumber +","+(endSaveReceipts-startSaveReceipts)+ "\r";
                String processBest_times = playOrGenerate+","+ rskip144Active +",processBest,"+ blockNumber +","+(endProcessBest-startProcessBest)+ "\r";
                String onBestBlock_times = playOrGenerate+","+ rskip144Active +",onBestBlock,"+ blockNumber +","+(endOnBestBlock-startOnBestBlock)+ "\r";
                String onBlock_times = playOrGenerate+","+ rskip144Active +",onBlock,"+ blockNumber +","+(endOnBlock-startOnBlock)+ "\r";

                try {
                    FileWriter myWriter_times = new FileWriter(filePath_times, true);

                    if (!Files.exists(file_times) || Files.size(file_times) == 0) {
                        myWriter_times.write(header_times);
                    }
                    myWriter_times.write(existenceOfBlock_times);
                    myWriter_times.write(checkParentBlock_times);
                    myWriter_times.write(checkBlockValidty_times);
                    myWriter_times.write(resultValidation_times);
                    myWriter_times.write(registerResult_times);
                    myWriter_times.write(rebranching_times);
                    myWriter_times.write(switchToBlockchain_times);
                    myWriter_times.write(saveReceipts_times);
                    myWriter_times.write(processBest_times);
                    myWriter_times.write(onBestBlock_times);
                    myWriter_times.write(onBlock_times);
                    myWriter_times.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            return ImportResult.IMPORTED_BEST;
        }
        // It is not the new best block
        else {
            if (bestBlock != null && !bestBlock.isParentOf(block)) {
                logger.trace("No rebranch: {} ~> {} From block {} ~> {} Difficulty {} Challenger difficulty {}",
                        bestBlock.getPrintableHash(), block.getPrintableHash(), bestBlock.getNumber(), block.getNumber(),
                        status.getTotalDifficulty(), totalDifficulty);
            }

            logger.trace("Start extendAlternativeBlockChain");
            extendAlternativeBlockChain(block, totalDifficulty);
            logger.trace("Start saveReceipts");
            saveReceipts(block, result);
            logger.trace("Start onBlock");
            onBlock(block, result);
            logger.trace("Start flushData");

            if (bestBlock != null && block.getNumber() > bestBlock.getNumber()) {
                logger.warn("Strange block number state");
            }

            logger.trace("Block not imported {} {}", block.getNumber(), block.getPrintableHash());
            profiler.stop(metric);
            return ImportResult.IMPORTED_NOT_BEST;
        }
    }

    @Override
    public BlockChainStatus getStatus() {
        return status;
    }

    /**
     * Change the blockchain status, to a new best block with difficulty
     *
     * @param block        The new best block
     * @param totalDifficulty   The total difficulty of the new blockchain
     */
    @Override
    public void setStatus(Block block, BlockDifficulty totalDifficulty) {
        synchronized (accessLock) {
            status = new BlockChainStatus(block, totalDifficulty);
            blockStore.saveBlock(block, totalDifficulty, true);
        }
    }

    @Override
    public Block getBlockByHash(byte[] hash) {
        return blockStore.getBlockByHash(hash);
    }

    @Override
    public List<Block> getBlocksByNumber(long number) {
        return blockStore.getChainBlocksByNumber(number);
    }

    @Override
    public List<BlockInformation> getBlocksInformationByNumber(long number) {
        synchronized (accessLock) {
            return this.blockStore.getBlocksInformationByNumber(number);
        }
    }

    @Override
    public boolean hasBlockInSomeBlockchain(@Nonnull final byte[] hash) {
        final Block block = this.getBlockByHash(hash);
        return block != null && this.blockIsInIndex(block);
    }

    /**
     * blockIsInIndex returns true if a given block is indexed in the blockchain (it might not be the in the
     * canonical branch).
     *
     * @param block the block to check for.
     * @return true if there is a block in the blockchain with that hash.
     */
    private boolean blockIsInIndex(@Nonnull final Block block) {
        final List<Block> blocks = this.getBlocksByNumber(block.getNumber());

        return blocks.stream().anyMatch(block::fastEquals);
    }

    @Override
    public void removeBlocksByNumber(long number) {
        this.lock.writeLock().lock();

        try {
            List<Block> blocks = this.getBlocksByNumber(number);

            for (Block block : blocks) {
                blockStore.removeBlock(block);
            }
        }
        finally {
            this.lock.writeLock().unlock();
        }
    }

    @Override
    public Block getBlockByNumber(long number) { return blockStore.getChainBlockByNumber(number); }

    @Override
    public Block getBestBlock() {
        return this.status.getBestBlock();
    }

    public void setNoValidation(boolean noValidation) {
        this.noValidation = noValidation;
    }

    /**
     * Returns transaction info by hash
     *
     * @param txHash the hash of the transaction
     * @return transaction info, null if the transaction does not exist
     */
    @Override
    public TransactionInfo getTransactionInfo(byte[] txHash) {
        TransactionInfo txInfo = receiptStore.getInMainChain(txHash, blockStore).orElse(null);

        if (txInfo == null) {
            return null;
        }

        Transaction tx = this.getBlockByHash(txInfo.getBlockHash()).getTransactionsList().get(txInfo.getIndex());
        txInfo.setTransaction(tx);

        return txInfo;
    }

    @Override
    public BlockDifficulty getTotalDifficulty() {
        return status.getTotalDifficulty();
    }

    @Override @VisibleForTesting
    public byte[] getBestBlockHash() {
        return getBestBlock().getHash().getBytes();
    }

    private void switchToBlockChain(Block block, BlockDifficulty totalDifficulty) {
        setStatus(block, totalDifficulty);
    }

    private void extendAlternativeBlockChain(Block block, BlockDifficulty totalDifficulty) {
        storeBlock(block, totalDifficulty, false);
    }

    private void storeBlock(Block block, BlockDifficulty totalDifficulty, boolean inBlockChain) {
        blockStore.saveBlock(block, totalDifficulty, inBlockChain);
        logger.trace("Block saved: number: {}, hash: {}, TD: {}",
                block.getNumber(), block.getPrintableHash(), totalDifficulty);
    }

    private void saveReceipts(Block block, BlockResult result) {
        if (result == null) {
            return;
        }

        long startGetTxReceipt = System.nanoTime();
        List<TransactionReceipt> transactionReceipts = result.getTransactionReceipts();
        int numberOfReceipts = transactionReceipts.size();
        if (transactionReceipts.isEmpty()) {
            return;
        }
        long endGetTxReceipt = System.nanoTime();
        long startSaveReceipts = System.nanoTime();
        receiptStore.saveMultiple(block.getHash().getBytes(), transactionReceipts);
        long endSaveReceipts = System.nanoTime();

        String filePath_sr = blockExecutor.getFilePath_timesSplitted_sr();
        String header_sr = "playOrGenerate,rskip144,moment,bnumber,time,receipts\r";
        Path file_sr = Paths.get(filePath_sr);
        String playOrGenerate = blockExecutor.isPlay() ? "play" : "generate";
        long blockNumber = block.getNumber();
        boolean isRskip144Active = blockExecutor.getActivationConfig().isActive(ConsensusRule.RSKIP144, blockNumber);

        try {
            FileWriter myWriter;
            if (!Files.exists(file_sr)) {
                myWriter = new FileWriter(filePath_sr, true);
                myWriter.write(header_sr);
            } else {
                myWriter = new FileWriter(filePath_sr,true);
            }
            myWriter.write(playOrGenerate +","+ isRskip144Active +","+"getTxReceipts"+","+ blockNumber +","+(endGetTxReceipt-startGetTxReceipt)+","+ numberOfReceipts +"\r");
            myWriter.write(playOrGenerate +","+ isRskip144Active +","+"saveMultipleReceipts"+","+ blockNumber +","+(endSaveReceipts-startSaveReceipts)+","+ numberOfReceipts+"\r");
            myWriter.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private void processBest(final Block block) {
        logger.debug("Starting to run transactionPool.processBest(block)");
        // this has to happen in the same thread so the TransactionPool is immediately aware of the new best block
        transactionPool.processBest(block);
        logger.debug("Finished running transactionPool.processBest(block)");

    }

    private void onBlock(Block block, BlockResult result) {
        if (result != null && listener != null) {
            listener.trace(String.format("Block chain size: [ %d ]", this.getSize()));
            listener.onBlock(block, result.getTransactionReceipts());
        }
    }

    private void onBestBlock(Block block, BlockResult result) {
        if (result != null && listener != null){
            listener.onBestBlock(block, result.getTransactionReceipts());
        }
    }

    private boolean isValid(Block block) {
        Metric metric = profiler.start(Profiler.PROFILING_TYPE.BLOCK_VALIDATION);
        boolean validation =  blockValidator.isValid(block);
        profiler.stop(metric);
        return validation;
    }

    @Override
    public int getNumTxPreTC() {
        return numTxPreTC;
    }

    @Override
    public int getExecutedTransactions() {
        return executedTransactions;
    }

    @Override
    public int getReceipts() {
        return receipts;
    }

    public int getTotalReceipts() {
        return totalReceipts;
    }
}
