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

import co.rsk.core.bc.BlockChainStatus;
import co.rsk.core.bc.BlockUtils;
import co.rsk.net.messages.*;
import co.rsk.net.sync.SyncConfiguration;
import org.ethereum.core.*;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.net.server.ChannelManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.*;

/**
 * BlockSyncService processes blocks to add into a blockchain.
 * If a block is not ready to be added to the blockchain, it will be on hold in a BlockStore.
 * <p>
 * This class is tightly coupled with NodeBlockProcessor
 */
public class BlockSyncService {
    private static final int NBLOCKS_TO_SYNC = 30;

    private volatile int nsyncs = 0;
    private volatile boolean syncing = false;

    private Map<ByteArrayWrapper, Integer> unknownBlockHashes = new HashMap<>();
    private long processedBlocksCounter;
    private long lastKnownBlockNumber = 0;
    private long lastStatusTime;
    private boolean ignoreAdvancedBlocks = true;

    private final Object statusLock = new Object();

    private static final Logger logger = LoggerFactory.getLogger(BlockSyncService.class);
    private final BlockStore store;
    private final Blockchain blockchain;
    private SyncConfiguration syncConfiguration;
    private final ChannelManager channelManager;
    private final BlockNodeInformation nodeInformation; // keep tabs on which nodes know which blocks.

    // this is tightly coupled with NodeProcessorService and SyncProcessor,
    // and we should use the same objects everywhere to ensure consistency
    public BlockSyncService(
            @Nonnull final BlockStore store,
            @Nonnull final Blockchain blockchain,
            @Nonnull final BlockNodeInformation nodeInformation,
            final SyncConfiguration syncConfiguration,
            final ChannelManager channelManager) {
        this.store = store;
        this.blockchain = blockchain;
        this.syncConfiguration = syncConfiguration;
        this.channelManager = channelManager;
        this.nodeInformation = nodeInformation;
    }

    public BlockProcessResult processBlock(MessageChannel sender, @Nonnull Block block) {
        long bestBlockNumber = this.getBestBlockNumber();
        long blockNumber = block.getNumber();

        if ((++processedBlocksCounter % 200) == 0) {
            long minimal = store.minimalHeight();
            long maximum = store.maximumHeight();
            logger.trace("Blocks in block processor {} from height {} to height {}", this.store.size(), minimal, maximum);

            if (minimal < bestBlockNumber - 1000)
                store.releaseRange(minimal, minimal + 1000);

            sendStatus(blockchain, sender);
        }

        // On incoming block, refresh status if needed
        trySendStatusToActivePeers();

        store.removeHeader(block.getHeader());

        final ByteArrayWrapper blockHash = new ByteArrayWrapper(block.getHash());

        unknownBlockHashes.remove(blockHash);

        lastKnownBlockNumber = Math.max(blockNumber, lastKnownBlockNumber);

        int syncMaxDistance = syncConfiguration.getMaxSkeletonChunks() * syncConfiguration.getChunkSize();
        if (ignoreAdvancedBlocks && blockNumber > bestBlockNumber + syncMaxDistance) {
            logger.trace("Block too advanced {} {} from {} ", blockNumber, block.getShortHash(), sender != null ? sender.getPeerNodeID().toString() : "N/A");
            return new BlockProcessResult(false, null);
        }

        if (sender != null) {
            nodeInformation.addBlockToNode(blockHash, sender.getPeerNodeID());
        }

        // already in a blockchain
        if (BlockUtils.blockInSomeBlockChain(block, blockchain)) {
            logger.trace("Block already in a chain " + blockNumber + " " + block.getShortHash());
            return new BlockProcessResult(false, null);
        }

        final Set<ByteArrayWrapper> unknownHashes = BlockUtils.unknownDirectAncestorsHashes(block, blockchain, store);

        this.processMissingHashes(sender, unknownHashes);

        trySaveStore(block);

        // We can't add the block if there are missing ancestors or uncles. Request the missing blocks to the sender.
        if (!unknownHashes.isEmpty()) {
            logger.trace("Missing hashes for block " + blockNumber + " " + block.getShortHash());
            return new BlockProcessResult(false, null);
        }

        logger.trace("Trying to add to blockchain");

        BlockProcessResult result = new BlockProcessResult(true,
                connectBlocksAndDescendants(sender,
                BlockUtils.sortBlocksByNumber(this.getParentsNotInBlockchain(block))));

        // After adding a long blockchain, refresh status if needed
        trySendStatusToActivePeers();

        return result;
    }

    public synchronized boolean isSyncingBlocks() {
        if (!hasBetterBlockToSync()) {
            syncing = false;
            return false;
        }

        if (!syncing) {
            nsyncs++;
        }
        syncing = true;

        return nsyncs < 2;
    }

    public boolean hasBetterBlockToSync() {
        long last = this.getLastKnownBlockNumber();
        long current = this.getBestBlockNumber();

        return last >= current + NBLOCKS_TO_SYNC;
    }

    public long getLastKnownBlockNumber() {
        return this.lastKnownBlockNumber;
    }

    public void setLastKnownBlockNumber(long lastKnownBlockNumber) {
        this.lastKnownBlockNumber = lastKnownBlockNumber;
    }

    public long getBestBlockNumber() {
        return this.blockchain.getBestBlock().getNumber();
    }

    private void trySaveStore(@Nonnull Block block) {
        if (!this.store.hasBlock(block))
            this.store.saveBlock(block);
    }

    private void trySendStatusToActivePeers() {
        if (this.hasBetterBlockToSync())
            sendStatusToActivePeers();
    }

    public void sendStatusToActivePeers() {
        synchronized (statusLock) {
            if (this.channelManager == null) {
                return;
            }

            BlockChainStatus blockChainStatus = this.blockchain.getStatus();

            if (blockChainStatus == null) {
                return;
            }

            Block block = blockChainStatus.getBestBlock();
            BigInteger totalDifficulty = blockChainStatus.getTotalDifficulty();

            if (block == null) {
                return;
            }

            Status status = new Status(block.getNumber(), block.getHash(), block.getParentHash(), totalDifficulty);

            long currentTime = System.currentTimeMillis();

            if (currentTime - lastStatusTime < 1000) {
                return;
            }

            lastStatusTime = currentTime;

            logger.trace("Sending status best block to all {} {}", status.getBestBlockNumber(), Hex.toHexString(status.getBestBlockHash()).substring(0, 8));

            this.channelManager.broadcastStatus(status);
        }
    }

    public void acceptAnyBlock()
    {
        this.ignoreAdvancedBlocks = false;
    }

    private static void sendStatus(Blockchain blockchain, MessageChannel sender) {
        if (sender == null || blockchain == null)
            return;

        BlockChainStatus blockChainStatus = blockchain.getStatus();

        if (blockChainStatus == null)
            return;

        Block block = blockChainStatus.getBestBlock();
        BigInteger totalDifficulty = blockChainStatus.getTotalDifficulty();

        if (block == null)
            return;

        Status status = new Status(block.getNumber(), block.getHash(), block.getParentHash(), totalDifficulty);
        logger.trace("Sending status best block {} to {}", status.getBestBlockNumber(), sender.getPeerNodeID().toString());
        StatusMessage msg = new StatusMessage(status);
        sender.sendMessage(msg);
    }

    private Map<ByteArrayWrapper, ImportResult> connectBlocksAndDescendants(MessageChannel sender, List<Block> blocks) {
        Map<ByteArrayWrapper, ImportResult> connectionsResult = new HashMap<>();
        List<Block> remainingBlocks = blocks;
        while (!remainingBlocks.isEmpty()) {
            List<Block> connected = new ArrayList<>();

            for (Block block : remainingBlocks) {
                logger.trace("Trying to add block {} {}", block.getNumber(), block.getShortHash());

                Set<ByteArrayWrapper> missingHashes = BlockUtils.unknownDirectAncestorsHashes(block, blockchain, store);

                if (!missingHashes.isEmpty()) {
                    logger.trace("Missing hashes for block in process " + block.getNumber() + " " + block.getShortHash());
                    logger.trace("Missing hashes " + missingHashes.size());
                    this.processMissingHashes(sender, missingHashes);
                    continue;
                }

                connectionsResult.put(new ByteArrayWrapper(block.getHash()), blockchain.tryToConnect(block));

                if (BlockUtils.blockInSomeBlockChain(block, blockchain)) {
                    this.store.removeBlock(block);
                    BlockUtils.addBlockToList(connected, block);
                }
            }

            remainingBlocks = this.store.getChildrenOf(connected);
        }

        return connectionsResult;
    }

    private void processMissingHashes(MessageChannel sender, Set<ByteArrayWrapper> hashes) {
        logger.trace("Missing blocks to process " + hashes.size());

        for (ByteArrayWrapper hash : hashes)
            this.processMissingHash(sender, hash);
    }

    private void processMissingHash(MessageChannel sender, ByteArrayWrapper hash) {
        if (sender == null)
            return;

        unknownBlockHashes.put(hash, 1);

        logger.trace("Missing block " + hash.toString().substring(0, 10));

        sender.sendMessage(new GetBlockMessage(hash.getData()));
    }

    /**
     * getParentsNotInBlockchain returns all the ancestors of the block (including the block itself) that are not
     * on the blockchain. It should be part of BlockChainImpl but is here because
     * BlockChain is coupled with the old org.ethereum.db.BlockStore.
     *
     * @param block the base block.
     * @return A list with the blocks sorted by ascending block number (the base block would be the last element).
     */
    @Nonnull
    private List<Block> getParentsNotInBlockchain(@Nullable Block block) {
        final List<Block> blocks = new ArrayList<>();
        Block currentBlock = block;
        while (currentBlock != null && !blockchain.hasBlockInSomeBlockchain(currentBlock.getHash())) {
            BlockUtils.addBlockToList(blocks, currentBlock);

            currentBlock = getBlockFromStoreOrBlockchain(currentBlock.getParentHash());
        }

        return blocks;
    }

    /**
     * getBlockFromStoreOrBlockchain retrieves a block from the store if it's available,
     * or else from the blockchain. It should be part of BlockChainImpl but is here because
     * BlockChain is coupled with the old org.ethereum.db.BlockStore.
     */
    @CheckForNull
    public Block getBlockFromStoreOrBlockchain(@Nonnull final byte[] hash) {
        final Block block = store.getBlockByHash(hash);

        if (block != null) {
            return block;
        }

        return blockchain.getBlockByHash(hash);
    }
}
