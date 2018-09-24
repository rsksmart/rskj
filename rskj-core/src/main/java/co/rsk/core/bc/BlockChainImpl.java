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

import co.rsk.blocks.BlockRecorder;
import co.rsk.core.BlockDifficulty;
import co.rsk.net.Metrics;
import co.rsk.panic.PanicProcessor;
import co.rsk.validators.BlockValidator;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.*;
import org.ethereum.db.BlockInformation;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.TransactionInfo;
import org.ethereum.listener.EthereumListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
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
    private static final Logger logger = LoggerFactory.getLogger("blockchain");
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    private final Repository repository;
    private final BlockStore blockStore;
    private final ReceiptStore receiptStore;
    private final TransactionPool transactionPool;
    private final EthereumListener listener;
    private BlockValidator blockValidator;

    private volatile BlockChainStatus status = new BlockChainStatus(null, BlockDifficulty.ZERO);

    private final Object connectLock = new Object();
    private final Object accessLock = new Object();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private final boolean flushEnabled;
    private final int flushNumberOfBlocks;
    private final BlockExecutor blockExecutor;
    private BlockRecorder blockRecorder;
    private boolean noValidation;

    public BlockChainImpl(Repository repository,
                          BlockStore blockStore,
                          ReceiptStore receiptStore,
                          TransactionPool transactionPool,
                          EthereumListener listener,
                          BlockValidator blockValidator,
                          boolean flushEnabled,
                          int flushNumberOfBlocks,
                          BlockExecutor blockExecutor) {
        this.repository = repository;
        this.blockStore = blockStore;
        this.receiptStore = receiptStore;
        this.listener = listener;
        this.blockValidator = blockValidator;
        this.flushEnabled = flushEnabled;
        this.flushNumberOfBlocks = flushNumberOfBlocks;
        this.blockExecutor = blockExecutor;
        this.transactionPool = transactionPool;
    }

    @Override
    public Repository getRepository() {
        return repository;
    }

    @Override
    public BlockStore getBlockStore() { return blockStore; }

    @VisibleForTesting
    public void setBlockValidator(BlockValidator validator) {
        this.blockValidator = validator;
    }

    @Override
    public long getSize() {
        return status.getBestBlock().getNumber() + 1;
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
        this.lock.readLock().lock();

        try {
            if (block == null) {
                return ImportResult.INVALID_BLOCK;
            }

            if (!block.isSealed()) {
                panicProcessor.panic("unsealedblock", String.format("Unsealed block %s %s", block.getNumber(), block.getHash()));
                block.seal();
            }

            if (blockRecorder != null) {
                blockRecorder.writeBlock(block);
            }

            try {
                logger.trace("Try connect block hash: {}, number: {}",
                             block.getShortHash(),
                             block.getNumber());

                synchronized (connectLock) {
                    logger.trace("Start try connect");
                    long saveTime = System.nanoTime();
                    ImportResult result = internalTryToConnect(block);
                    long totalTime = System.nanoTime() - saveTime;
                    logger.info("block: num: [{}] hash: [{}], processed after: [{}]nano, result {}", block.getNumber(), block.getShortHash(), totalTime, result);
                    return result;
                }
            } catch (Throwable t) {
                logger.error("Unexpected error: ", t);
                return ImportResult.INVALID_BLOCK;
            }
        }
        finally {
            this.lock.readLock().unlock();
        }
    }

    @Override
    public void suspendProcess() {
        this.lock.writeLock().lock();
    }

    @Override
    public void resumeProcess() {
        this.lock.writeLock().unlock();
    }

    private ImportResult internalTryToConnect(Block block) {
        if (blockStore.getBlockByHash(block.getHash().getBytes()) != null &&
                !BlockDifficulty.ZERO.equals(blockStore.getTotalDifficultyForHash(block.getHash().getBytes()))) {
            logger.debug("Block already exist in chain hash: {}, number: {}",
                         block.getShortHash(),
                         block.getNumber());

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
                return ImportResult.NO_PARENT;
            }

            parentTotalDifficulty = blockStore.getTotalDifficultyForHash(parent.getHash().getBytes());

            if (parentTotalDifficulty == null || parentTotalDifficulty.equals(BlockDifficulty.ZERO)) {
                return ImportResult.NO_PARENT;
            }
        }

        // Validate incoming block before its processing
        if (!isValid(block)) {
            long blockNumber = block.getNumber();
            logger.warn("Invalid block with number: {}", blockNumber);
            panicProcessor.panic("invalidblock", String.format("Invalid block %s %s", blockNumber, block.getHash()));
            return ImportResult.INVALID_BLOCK;
        }

        BlockResult result = null;

        if (parent != null) {
            long saveTime = System.nanoTime();
            logger.trace("execute start");

            if (this.noValidation) {
                result = blockExecutor.executeAll(block, parent.getStateRoot());
            } else {
                result = blockExecutor.execute(block, parent.getStateRoot(), false);
            }

            logger.trace("execute done");

            boolean isValid = noValidation ? true : blockExecutor.validate(block, result);

            logger.trace("validate done");

            if (!isValid) {
                return ImportResult.INVALID_BLOCK;
            }
            // Now that we know it's valid, we can commit the changes made by the block
            // to the parent's repository.

            long totalTime = System.nanoTime() - saveTime;
            logger.trace("block: num: [{}] hash: [{}], executed after: [{}]nano", block.getNumber(), block.getShortHash(), totalTime);
        }

        // the new accumulated difficulty
        BlockDifficulty totalDifficulty = parentTotalDifficulty.add(block.getCumulativeDifficulty());
        logger.trace("TD: updated to {}", totalDifficulty);

        // It is the new best block
        if (SelectionRule.shouldWeAddThisBlock(totalDifficulty, status.getTotalDifficulty(),block, bestBlock)) {
            if (bestBlock != null && !bestBlock.isParentOf(block)) {
                logger.trace("Rebranching: {} ~> {} From block {} ~> {} Difficulty {} Challenger difficulty {}",
                        bestBlock.getShortHash(), block.getShortHash(), bestBlock.getNumber(), block.getNumber(),
                        status.getTotalDifficulty().toString(), totalDifficulty.toString());
                BlockFork fork = new BlockFork();
                fork.calculate(bestBlock, block, blockStore);
                Metrics.rebranch(bestBlock, block, fork.getNewBlocks().size() + fork.getOldBlocks().size());
                blockStore.reBranch(block);
            }

            logger.trace("Start switchToBlockChain");
            switchToBlockChain(block, totalDifficulty);
            logger.trace("Start saveReceipts");
            saveReceipts(block, result);
            logger.trace("Start processBest");
            processBest(block);
            logger.trace("Start onBestBlock");
            onBestBlock(block, result);
            logger.trace("Start onBlock");
            onBlock(block, result);
            logger.trace("Start flushData");
            flushData();

            logger.trace("Better block {} {}", block.getNumber(), block.getShortHash());

            logger.debug("block added to the blockChain: index: [{}]", block.getNumber());
            if (block.getNumber() % 100 == 0) {
                logger.info("*** Last block added [ #{} ]", block.getNumber());
            }

            return ImportResult.IMPORTED_BEST;
        }
        // It is not the new best block
        else {
            if (bestBlock != null && !bestBlock.isParentOf(block)) {
                logger.trace("No rebranch: {} ~> {} From block {} ~> {} Difficulty {} Challenger difficulty {}",
                        bestBlock.getShortHash(), block.getShortHash(), bestBlock.getNumber(), block.getNumber(),
                        status.getTotalDifficulty().toString(), totalDifficulty.toString());
            }

            logger.trace("Start extendAlternativeBlockChain");
            extendAlternativeBlockChain(block, totalDifficulty);
            logger.trace("Start saveReceipts");
            saveReceipts(block, result);
            logger.trace("Start onBlock");
            onBlock(block, result);
            logger.trace("Start flushData");
            flushData();

            if (bestBlock != null && block.getNumber() > bestBlock.getNumber()) {
                logger.warn("Strange block number state");
            }

            logger.trace("Block not imported {} {}", block.getNumber(), block.getShortHash());

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
            repository.syncToRoot(block.getStateRoot());
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
     * @param hash      the hash of the transaction
     * @return transaction info, null if the transaction does not exist
     */
    @Override
    public TransactionInfo getTransactionInfo(byte[] hash) {
        TransactionInfo txInfo = receiptStore.get(hash);

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

    @Override
    public void setBlockRecorder(BlockRecorder blockRecorder) {
        this.blockRecorder = blockRecorder;
    }

    private void switchToBlockChain(Block block, BlockDifficulty totalDifficulty) {
        synchronized (accessLock) {
            storeBlock(block, totalDifficulty, true);
            status = new BlockChainStatus(block, totalDifficulty);
            repository.syncToRoot(block.getStateRoot());
        }
    }

    private void extendAlternativeBlockChain(Block block, BlockDifficulty totalDifficulty) {
        storeBlock(block, totalDifficulty, false);
    }

    private void storeBlock(Block block, BlockDifficulty totalDifficulty, boolean inBlockChain) {
        blockStore.saveBlock(block, totalDifficulty, inBlockChain);
        logger.trace("Block saved: number: {}, hash: {}, TD: {}",
                block.getNumber(), block.getShortHash(), totalDifficulty);
    }

    private void saveReceipts(Block block, BlockResult result) {
        if (result == null) {
            return;
        }

        if (result.getTransactionReceipts().isEmpty()) {
            return;
        }

        receiptStore.saveMultiple(block.getHash().getBytes(), result.getTransactionReceipts());
    }

    private void processBest(final Block block) {
        EventDispatchThread.invokeLater(() -> transactionPool.processBest(block));
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
        if (block.isGenesis()) {
            return true;
        }

        return blockValidator.isValid(block);
    }

    // Rolling counter that helps doing flush every RskSystemProperties.CONFIG.flushNumberOfBlocks() flush attempts
    // We did this because flush is slow, and doing flush for every block degrades the node performance.
    private int nFlush = 0;

    private void flushData() {
        if (flushEnabled && nFlush == 0)  {
            long saveTime = System.nanoTime();
            repository.flush();
            long totalTime = System.nanoTime() - saveTime;
            logger.trace("repository flush: [{}]nano", totalTime);
            saveTime = System.nanoTime();
            blockStore.flush();
            totalTime = System.nanoTime() - saveTime;
            logger.trace("blockstore flush: [{}]nano", totalTime);
        }
        nFlush++;
        nFlush = nFlush % flushNumberOfBlocks;
    }
}
