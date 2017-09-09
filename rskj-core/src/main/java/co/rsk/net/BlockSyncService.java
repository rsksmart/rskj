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
import org.ethereum.core.*;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.net.server.ChannelManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import java.math.BigInteger;
import java.util.*;

/**
 * BlockSyncService processes blocks to add into a blockchain.
 * If a block is not ready to be added to the blockchain, it will be on hold in a BlockStore.
 * <p>
 * This class is tightly coupled with
 */
public class BlockSyncService {
    private static final int NBLOCKS_TO_SYNC = 30;

    private final Object syncLock = new Object();
    @GuardedBy("syncLock") private volatile int nsyncs = 0;
    @GuardedBy("syncLock") private volatile boolean syncing = false;

    private Map<ByteArrayWrapper, Integer> unknownBlockHashes = new HashMap<>();
    private long processedBlocksCounter;
    private long lastKnownBlockNumber = 0;
    private long lastStatusTime;
    private boolean ignoreAdvancedBlocks = true;

    private final Object statusLock = new Object();

    private static final Logger logger = LoggerFactory.getLogger(BlockSyncService.class);
    private final BlockStore store;
    private final Blockchain blockchain;
    private final ChannelManager channelManager;
    private final BlockNodeInformation nodeInformation; // keep tabs on which nodes know which blocks.

    // this is tightly coupled with NodeProcessorService and SyncProcessor,
    // and we should use the same objects everywhere to ensure consistency
    public BlockSyncService(
            @Nonnull final BlockStore store,
            @Nonnull final Blockchain blockchain,
            @Nonnull final BlockNodeInformation nodeInformation,
            final ChannelManager channelManager) {
        this.store = store;
        this.blockchain = blockchain;
        this.channelManager = channelManager;
        this.nodeInformation = nodeInformation;
    }

    public BlockProcessResult processBlock(MessageSender sender, Block block) {
        long bestBlockNumber = this.getBestBlockNumber();
        long blockNumber = block.getNumber();

        if (block == null) {
            logger.error("Block not received");
            return new BlockProcessResult(false, null);
        }

        if ((++processedBlocksCounter % 200) == 0) {
            long minimal = store.minimalHeight();
            long maximum = store.maximumHeight();
            logger.trace("Blocks in block processor {} from height {} to height {}", this.store.size(), minimal, maximum);

            if (minimal < bestBlockNumber - 1000)
                store.releaseRange(minimal, minimal + 1000);

            sendStatus(blockchain, sender);
        }

        // On incoming block, refresh status if needed
        if (this.hasBetterBlockToSync())
            sendStatusToAll();

        this.store.removeHeader(block.getHeader());

        final ByteArrayWrapper blockHash = new ByteArrayWrapper(block.getHash());

        unknownBlockHashes.remove(blockHash);

        if (blockNumber > this.lastKnownBlockNumber)
            this.lastKnownBlockNumber = blockNumber;

        if (ignoreAdvancedBlocks && blockNumber >= bestBlockNumber + 1000) {
            logger.trace("Block too advanced {} {} from {} ", blockNumber, block.getShortHash(), sender != null ? sender.getNodeID().toString() : "N/A");
            return new BlockProcessResult(false, null);
        }

        if (sender != null) {
            nodeInformation.addBlockToNode(blockHash, sender.getNodeID());
        }

        // already in a blockchain
        if (BlockUtils.blockInSomeBlockChain(block, blockchain)) {
            logger.trace("Block already in a chain " + blockNumber + " " + block.getShortHash());
            return new BlockProcessResult(false, null);
        }

        final Set<ByteArrayWrapper> unknownHashes = BlockUtils.unknownDirectAncestorsHashes(block, blockchain, store);

        this.processMissingHashes(sender, unknownHashes);

        // We can't add the block if there are missing ancestors or uncles. Request the missing blocks to the sender.
        if (!unknownHashes.isEmpty()) {
            logger.trace("Missing hashes for block " + blockNumber + " " + block.getShortHash());

            if (!this.store.hasBlock(block))
                this.store.saveBlock(block);

            return new BlockProcessResult(false, null);
        }

        if (!this.store.hasBlock(block))
            this.store.saveBlock(block);

        logger.trace("Trying to add to blockchain");

        BlockProcessResult result = new BlockProcessResult(true, connectBlocksAndDescendants(sender, BlockUtils.sortBlocksByNumber(this.getParentsNotInBlockchain(block))));

        // After adding a long blockchain, refresh status if needed
        if (this.hasBetterBlockToSync())
            sendStatusToAll();

        return result;
    }

    public boolean isSyncingBlocks() {
        synchronized (syncLock) {
            long last = this.getLastKnownBlockNumber();
            long current = this.getBestBlockNumber();

            if (last >= current + NBLOCKS_TO_SYNC) {
                if (!syncing)
                    nsyncs++;

                syncing = true;

                if (nsyncs > 1)
                    return false;

                return true;
            }

            syncing = false;

            return false;
        }
    }

    public boolean hasBetterBlockToSync() {
        synchronized (syncLock) {
            long last = this.getLastKnownBlockNumber();
            long current = this.getBestBlockNumber();

            if (last >= current + NBLOCKS_TO_SYNC)
                return true;

            return false;
        }
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

    // This does not send status to ALL anymore.
    // Should be renamed to something like sendStatusToSome.
    // Not renamed yet to avoid merging hell.
    public void sendStatusToAll() {
        synchronized (statusLock) {
            if (this.channelManager == null)
                return;

            BlockChainStatus blockChainStatus = this.blockchain.getStatus();

            if (blockChainStatus == null)
                return;

            Block block = blockChainStatus.getBestBlock();
            BigInteger totalDifficulty = blockChainStatus.getTotalDifficulty();

            if (block == null)
                return;

            Status status = new Status(block.getNumber(), block.getHash(), block.getParentHash(), totalDifficulty);

            long currentTime = System.currentTimeMillis();

            if (currentTime - lastStatusTime < 1000)
                return;

            lastStatusTime = currentTime;

            logger.trace("Sending status best block to all {} {}", status.getBestBlockNumber(), Hex.toHexString(status.getBestBlockHash()).substring(0, 8));

            this.channelManager.broadcastStatus(status);
        }
    }

    public void acceptAnyBlock()
    {
        this.ignoreAdvancedBlocks = false;
    }

    private static void sendStatus(Blockchain blockchain, MessageSender sender) {
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
        logger.trace("Sending status best block {} to {}", status.getBestBlockNumber(), sender.getNodeID().toString());
        StatusMessage msg = new StatusMessage(status);
        sender.sendMessage(msg);
    }

    private Map<ByteArrayWrapper, ImportResult> connectBlocksAndDescendants(MessageSender sender, List<Block> blocks) {
        Map<ByteArrayWrapper, ImportResult> connectionsResult = new HashMap<>();
        while (!blocks.isEmpty()) {
            List<Block> connected = new ArrayList<>();

            for (Block block : blocks) {
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

            blocks = this.store.getChildrenOf(connected);
        }

        return connectionsResult;
    }

    private void processMissingHashes(MessageSender sender, Set<ByteArrayWrapper> hashes) {
        logger.trace("Missing blocks to process " + hashes.size());

        for (ByteArrayWrapper hash : hashes)
            this.processMissingHash(sender, hash);
    }

    private void processMissingHash(MessageSender sender, ByteArrayWrapper hash) {
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

        while (block != null && !blockchain.hasBlockInSomeBlockchain(block.getHash())) {
            BlockUtils.addBlockToList(blocks, block);

            block = getBlockFromStoreOrBlockchain(block.getParentHash());
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
