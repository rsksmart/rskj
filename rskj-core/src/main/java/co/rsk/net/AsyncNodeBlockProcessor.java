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
import co.rsk.net.sync.SyncConfiguration;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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

    private final BlockingQueue<PeerBlockPair> blocksToProcess = new LinkedBlockingQueue<>();

    private final Thread thread = new Thread(this,"async block processor");

    private final Listener listener;

    private volatile boolean stopped;

    public AsyncNodeBlockProcessor(@Nonnull NetBlockStore store, @Nonnull Blockchain blockchain, @Nonnull BlockNodeInformation nodeInformation,
                                   @Nonnull BlockSyncService blockSyncService, @Nonnull SyncConfiguration syncConfiguration,
                                   @Nullable Listener listener) {
        super(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);
        this.listener = listener;
    }

    @Override
    public BlockProcessResult processBlock(@Nullable Peer sender, @Nonnull Block block) {
        // TODO: add block validation

        BlockProcessResult blockProcessResult = blockSyncService.processBlock(block, sender, false, false);
        if (blockProcessResult.isScheduledForProcessing()) {
            blocksToProcess.offer(new PeerBlockPair(sender, block));
        }

        return blockProcessResult;
    }

    @Override
    public void start() {
        thread.start();
    }

    @Override
    public void stop() {
        stop(0L);
    }

    /**
     * Stop the service and wait until a working thread is stopped for {@code waitMillis} milliseconds,
     * if {@code waitMillis} greater than zero. If {@code waitMillis} is zero, then immediately returns.
     */
    public void stop(long waitMillis) {
        stopped = true;

        try {
            thread.interrupt();
            if (waitMillis > 0L) {
                thread.join(waitMillis);
            }
        } catch (InterruptedException e) {
            logger.error("Failed to join the thread", e);
        }
    }

    @Override
    public void run() {
        while (!stopped) {
            Peer sender = null;
            Block block = null;

            try {
                logger.trace("Get peer/block pair");

                PeerBlockPair pair = blocksToProcess.take();

                sender = pair.peer;
                block = pair.block;

                logger.trace("Start block processing");
                BlockProcessResult blockProcessResult = blockSyncService.processBlock(block, sender, false, true);
                logger.trace("Finished block processing");

                if (listener != null) {
                    listener.onBlockProcessed(this, sender, block, blockProcessResult);
                }
            } catch (InterruptedException e) {
                logger.trace("Thread has been interrupted");

                return;
            } catch (Exception e) {
                logger.error("Unexpected error processing block {} from peer {}", block, sender, e);
            }
        }
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

    private static class PeerBlockPair {
        final Peer peer;
        final Block block;

        PeerBlockPair(@Nullable Peer peer, @Nonnull Block block) {
            this.peer = peer;
            this.block = block;
        }
    }
}
