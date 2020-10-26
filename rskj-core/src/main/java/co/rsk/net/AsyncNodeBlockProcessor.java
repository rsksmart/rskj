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

    private final BlockValidator blockRelayValidator;

    private final Thread thread = new Thread(this,"async block processor");

    private final Listener listener;

    private volatile boolean stopped;

    public AsyncNodeBlockProcessor(@Nonnull NetBlockStore store, @Nonnull Blockchain blockchain, @Nonnull BlockNodeInformation nodeInformation,
                                   @Nonnull BlockSyncService blockSyncService, @Nonnull SyncConfiguration syncConfiguration,
                                   @Nonnull BlockValidator blockRelayValidator, @Nullable Listener listener) {
        super(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);
        this.listener = listener;
        this.blockRelayValidator = blockRelayValidator;
    }

    public AsyncNodeBlockProcessor(@Nonnull NetBlockStore store, @Nonnull Blockchain blockchain, @Nonnull BlockNodeInformation nodeInformation,
                                   @Nonnull BlockSyncService blockSyncService, @Nonnull SyncConfiguration syncConfiguration,
                                   @Nonnull BlockValidator blockRelayValidator) {
        this(store, blockchain, nodeInformation, blockSyncService, syncConfiguration, blockRelayValidator, null);
    }

    @Override
    public BlockProcessResult processBlock(@Nullable Peer sender, @Nonnull Block block) {
        final Instant start = Instant.now();

        final long blockNumber = block.getNumber();
        final String blockHash = block.getPrintableHash();
        final String peer = sender != null ? sender.getPeerNodeID().toString() : "N/A";

        if (store.hasBlock(block)) {
            logger.trace("Ignored block with number {} and hash {} from {} as it's already in the queue", blockNumber, blockHash, peer);
            return ignoredResult(start, blockHash, null);
        }

        boolean looksGood = blockSyncService.preprocessBlock(block, sender, false);
        if (looksGood) {
            if (isValid(block)) {
                boolean offer = blocksToProcess.offer(new BlockInfo(sender, block));
                if (offer) {
                    logger.trace("Added block with number {} and hash {} from {} to the queue", blockNumber, blockHash, peer);
                } else {
                    // This should not happen as the queue is unbounded
                    logger.warn("Cannot add block for processing into the queue with number {} {} from {}", blockNumber, blockHash, peer);
                }

                return scheduledForProcessingResult(start, blockHash);
            }

            logger.warn("Invalid block with number {} {} from {} ", blockNumber, blockHash, peer);
            Map<Keccak256, ImportResult> result = Collections.singletonMap(block.getHash(), ImportResult.INVALID_BLOCK);
            return ignoredResult(start, blockHash, result);
        }

        return ignoredResult(start, blockHash, null);
    }

    @Override
    public void start() {
        thread.start();
    }

    @Override
    public void stop() {
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
                logger.trace("Awaiting block for processing from the queue...");

                BlockInfo blockInfo = blocksToProcess.take();

                sender = blockInfo.peer;
                block = blockInfo.block;

                if (logger.isTraceEnabled()) {
                    logger.trace("Start block processing with number {} and hash {} from {}", block.getNumber(), block.getPrintableHash(), sender);
                }

                BlockProcessResult blockProcessResult = blockSyncService.processBlock(block, sender, false);
                logger.trace("Finished block processing");

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

    private boolean isValid(Block block) {
        return blockRelayValidator.isValid(block);
    }

    private static BlockProcessResult scheduledForProcessingResult(@Nonnull Instant start, @Nonnull String blockHash) {
        return new BlockProcessResult(true, null, blockHash, Duration.between(start, Instant.now()));
    }

    private static BlockProcessResult ignoredResult(@Nonnull Instant start, @Nonnull String blockHash, @Nullable Map<Keccak256, ImportResult> result) {
        return new BlockProcessResult(false, result, blockHash, Duration.between(start, Instant.now()));
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
