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

package co.rsk.net;

import co.rsk.config.InternalService;
import co.rsk.crypto.Keccak256;
import co.rsk.net.sync.SyncConfiguration;
import co.rsk.util.FormatUtils;
import co.rsk.validators.BlockValidator;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.ImportResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * AsyncNodeBlockProcessor processes blockchain blocks that are received from other nodes.
 * If a block passes validation, it will immediately be propagated to other nodes and its execution will be scheduled on a separate thread.
 * Blocks are being executed and connected to the blockchain sequentially one after another.
 * <p>
 * If a block is not ready to be added to the blockchain, it will be on hold in a BlockStore.
 * <p>
 */
public class AsyncNodeBlockProcessor extends NodeBlockProcessor implements InternalService, Runnable {

    private static final Logger logger = LoggerFactory.getLogger("asyncblockprocessor");

    private final BlockingQueue<BlockInfo> blocksToProcess = new LinkedBlockingQueue<>();

    private final BlockValidator blockHeaderValidator;

    private final BlockValidator blockValidator;

    private final Thread thread = new Thread(this,"async block processor");

    private final Listener listener;

    private volatile boolean stopped;

    public AsyncNodeBlockProcessor(@Nonnull NetBlockStore store, @Nonnull Blockchain blockchain, @Nonnull BlockNodeInformation nodeInformation,
                                   @Nonnull BlockSyncService blockSyncService, @Nonnull SyncConfiguration syncConfiguration,
                                   @Nonnull BlockValidator blockHeaderValidator, @Nonnull BlockValidator blockValidator,
                                   @Nullable Listener listener) {
        super(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);
        this.blockHeaderValidator = blockHeaderValidator;
        this.blockValidator = blockValidator;
        this.listener = listener;
    }

    public AsyncNodeBlockProcessor(@Nonnull NetBlockStore store, @Nonnull Blockchain blockchain, @Nonnull BlockNodeInformation nodeInformation,
                                   @Nonnull BlockSyncService blockSyncService, @Nonnull SyncConfiguration syncConfiguration,
                                   @Nonnull BlockValidator blockHeaderValidator, @Nonnull BlockValidator blockValidator) {
        this(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, blockHeaderValidator, blockValidator, null);
    }

    @Override
    public BlockProcessResult processBlock(@Nullable Peer sender, @Nonnull Block block) {
        final Instant start = Instant.now();

        final long blockNumber = block.getNumber();
        final String blockHash = block.getPrintableHash();
        final String peer = sender != null ? sender.getPeerNodeID().toString() : "N/A";

        // Validate block header first to see if its PoW is valid at all
        if (!isBlockHeaderValid(block)) {
            logger.warn("Invalid block with number {} {} from {} ", blockNumber, blockHash, peer);
            return invalidBlockResult(block, start);
        }

        // Check if block is already in the queue
        if (store.hasBlock(block)) {
            logger.trace("Ignored block with number {} and hash {} from {} as it's already in the queue", blockNumber, blockHash, peer);
            return ignoreBlockResult(block, start);
        }

        // Check if block is ready for processing - if the block is not too advanced, its ancestor blocks are in place etc.
        List<Block> blocksToConnect = blockSyncService.preprocessBlock(block, sender, false);
        if (blocksToConnect.isEmpty()) {
            logger.trace("Ignored block with number {} and hash {} from {} as it's not ready for processing yet", blockNumber, blockHash, peer);
            return ignoreBlockResult(block, start);
        }

        boolean onlyOneBlock = blocksToConnect.size() == 1 && blocksToConnect.get(0).getHash().equals(block.getHash());
        // if there's only one block to connect without any ancestors, then schedule its processing ( if the block is valid ofc )
        if (onlyOneBlock) {
            // Validate block if it can be added to the queue for processing
            if (isBlockValid(block)) {
                scheduleForProcessing(new BlockInfo(sender, block), blockNumber, blockHash, peer);

                return scheduledForProcessingResult(block, start);
            }

            logger.warn("Invalid block with number {} {} from {} ", blockNumber, blockHash, peer);
            return invalidBlockResult(block, start);
        }

        // if besides the block there are some ancestors, connect them all synchronously
        Map<Keccak256, ImportResult> connectResult = blockSyncService.connectBlocksAndDescendants(sender, blocksToConnect, false);
        return BlockProcessResult.connectResult(block, start, connectResult);
    }

    @Override
    public void start() {
        logger.info("Starting...");
        thread.start();
    }

    @Override
    public void stop() {
        logger.info("Stopping...");
        stopThread();
    }

    /**
     * Stop the service and wait until a working thread is stopped for {@code waitMillis} milliseconds,
     * if {@code waitMillis} greater than zero. If {@code waitMillis} is zero, then immediately returns.
     */
    public void stopAndWait(long waitMillis) throws InterruptedException {
        stopThread();

        if (waitMillis > 0L) {
            thread.join(waitMillis);
        }
    }

    private void stopThread() {
        stopped = true;
        thread.interrupt();
    }

    @Override
    public void run() {
        while (!stopped) {
            Peer sender = null;
            Block block = null;

            try {
                logger.trace("Getting block from queue...");

                BlockInfo blockInfo = blocksToProcess.take();

                int size = blocksToProcess.size();
                if (size > 0) {
                    logger.info("Queued blocks to connect: {}", size);
                } else {
                    logger.debug("There are no more queued blocks");
                }

                sender = blockInfo.peer;
                block = blockInfo.block;

                final Instant start = Instant.now();
                if (logger.isTraceEnabled()) {
                    logger.trace("Start block processing with number {} and hash {} from {}", block.getNumber(), block.getPrintableHash(), sender);
                }
                
                Map<Keccak256, ImportResult> connectResult = blockSyncService.connectBlocksAndDescendants(sender, Collections.singletonList(block), false);
                BlockProcessResult blockProcessResult = BlockProcessResult.connectResult(block, start, connectResult);

                if (logger.isTraceEnabled()) {
                    logger.trace("Finished block processing after [{}] seconds.", FormatUtils.formatNanosecondsToSeconds(Duration.between(start, Instant.now()).toNanos()));
                }

                if (listener != null) {
                    listener.onBlockProcessed(this, sender, block, blockProcessResult);
                }
            } catch (InterruptedException e) {
                logger.trace("Thread has been interrupted");

                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.error("Unexpected error processing block {} from peer {}", block, sender, e);
            }
        }
    }

    private boolean isBlockHeaderValid(Block block) {
        return blockHeaderValidator.isValid(block);
    }

    private boolean isBlockValid(Block block) {
        return blockValidator.isValid(block);
    }

    private void scheduleForProcessing(BlockInfo blockInfo, long blockNumber, String blockHash, String peer) {
        if (stopped) {
            logger.warn("{} is stopped. Block with number {} and hash {} from {} may not be processed",
                    AsyncNodeBlockProcessor.class.getSimpleName(), blockNumber, blockHash, peer);
        }

        boolean offer = blocksToProcess.offer(blockInfo);
        if (offer) {
            logger.trace("Added block with number {} and hash {} from {} to the queue", blockNumber, blockHash, peer);
        } else {
            // This should not happen as the queue is unbounded
            logger.warn("Cannot add block for processing into the queue with number {} {} from {}", blockNumber, blockHash, peer);
        }
    }

    private static BlockProcessResult scheduledForProcessingResult(@Nonnull Block block, @Nonnull Instant start) {
        return BlockProcessResult.connectResult(block, start, null);
    }

    private static BlockProcessResult invalidBlockResult(@Nonnull Block block, @Nonnull Instant start) {
        Map<Keccak256, ImportResult> result = Collections.singletonMap(block.getHash(), ImportResult.INVALID_BLOCK);
        return BlockProcessResult.connectResult(block, start, result);
    }

    private static BlockProcessResult ignoreBlockResult(@Nonnull Block block, @Nonnull Instant start) {
        return BlockProcessResult.ignoreBlockResult(block, start);
    }

    public interface Listener {

        /**
         * Called after a block is processed.
         *
         * This callback method is executed by an {@link AsyncNodeBlockProcessor}'s working thread.
         */
        void onBlockProcessed(@Nonnull AsyncNodeBlockProcessor blockProcessor,
                              @Nullable Peer sender, @Nonnull Block block,
                              @Nonnull BlockProcessResult blockProcessResult);

    }

    private static class BlockInfo {
        private final Peer peer;
        private final Block block;

        BlockInfo(@Nullable Peer peer, @Nonnull Block block) {
            this.peer = peer;
            this.block = block;
        }
    }
}
