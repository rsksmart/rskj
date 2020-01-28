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

import co.rsk.config.RskSystemProperties;
import co.rsk.core.bc.BlockUtils;
import co.rsk.crypto.Keccak256;
import co.rsk.net.messages.GetBlockMessage;
import co.rsk.net.sync.SyncConfiguration;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.ImportResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * BlockSyncService processes blocks to add into a blockchain.
 * If a block is not ready to be added to the blockchain, it will be on hold in a BlockStore.
 */
public class BlockSyncService {
    public static final int CHUNK_PART_LIMIT = 8;
    public static final int PROCESSED_BLOCKS_TO_CHECK_STORE = 200;
    public static final int RELEASED_RANGE = 1000;
    private Map<Keccak256, Integer> unknownBlockHashes;
    private long processedBlocksCounter;
    private long lastKnownBlockNumber = 0;

    private static final Logger logger = LoggerFactory.getLogger("blocksyncservice");
    private final NetBlockStore store;
    private final Blockchain blockchain;
    private final SyncConfiguration syncConfiguration;
    private final BlockNodeInformation nodeInformation; // keep tabs on which nodes know which blocks.
    private final RskSystemProperties config;

    // this is tightly coupled with NodeProcessorService and SyncProcessor,
    // and we should use the same objects everywhere to ensure consistency
    public BlockSyncService(
            @Nonnull final RskSystemProperties config,
            @Nonnull final NetBlockStore store,
            @Nonnull final Blockchain blockchain,
            @Nonnull final BlockNodeInformation nodeInformation,
            @Nonnull final SyncConfiguration syncConfiguration) {
        this.store = store;
        this.blockchain = blockchain;
        this.syncConfiguration = syncConfiguration;
        this.nodeInformation = nodeInformation;
        this.unknownBlockHashes = new HashMap<>();
        this.config = config;
    }

    public BlockProcessResult processBlock(@Nonnull Block block, Peer sender, boolean ignoreMissingHashes) {
        Instant start = Instant.now();
        long bestBlockNumber = this.getBestBlockNumber();
        long blockNumber = block.getNumber();
        final Keccak256 blockHash = block.getHash();
        int syncMaxDistance = syncConfiguration.getChunkSize() * syncConfiguration.getMaxSkeletonChunks();

        tryReleaseStore(bestBlockNumber);
        store.removeHeader(block.getHeader());
        unknownBlockHashes.remove(blockHash);

        if (blockNumber > bestBlockNumber + syncMaxDistance) {
            logger.trace("Block too advanced {} {} from {} ", blockNumber, block.getShortHash(),
                    sender != null ? sender.getPeerNodeID().toString() : "N/A");
            return new BlockProcessResult(false, null, block.getShortHash(),
                    Duration.between(start, Instant.now()));
        }

        if (sender != null) {
            nodeInformation.addBlockToNode(blockHash, sender.getPeerNodeID());
        }

        // already in a blockchain
        if (BlockUtils.blockInSomeBlockChain(block, blockchain)) {
            logger.trace("Block already in a chain {} {}", blockNumber, block.getShortHash());
            return new BlockProcessResult(false, null, block.getShortHash(),
                    Duration.between(start, Instant.now()));
        }
        trySaveStore(block);

        Set<Keccak256> unknownHashes = BlockUtils.unknownDirectAncestorsHashes(block, blockchain, store);
        // We can't add the block if there are missing ancestors or uncles. Request the missing blocks to the sender.
        if (!unknownHashes.isEmpty()) {
            if (!ignoreMissingHashes){
                logger.trace("Missing hashes for block in process {} {}", blockNumber, block.getShortHash());
                requestMissingHashes(sender, unknownHashes);
            }
            return new BlockProcessResult(false, null, block.getShortHash(),
                    Duration.between(start, Instant.now()));
        }

        logger.trace("Trying to add to blockchain");

        Map<Keccak256, ImportResult> connectResult = connectBlocksAndDescendants(sender,
                BlockUtils.sortBlocksByNumber(this.getParentsNotInBlockchain(block)), ignoreMissingHashes);

        return new BlockProcessResult(true, connectResult, block.getShortHash(),
                Duration.between(start, Instant.now()));
    }

    private void tryReleaseStore(long bestBlockNumber) {
        if ((++processedBlocksCounter % PROCESSED_BLOCKS_TO_CHECK_STORE) == 0) {
            long minimal = store.minimalHeight();
            long maximum = store.maximumHeight();
            logger.trace("Blocks in block processor {} from height {} to height {}", this.store.size(), minimal, maximum);

            if (minimal < bestBlockNumber - RELEASED_RANGE) {
                store.releaseRange(minimal, minimal + RELEASED_RANGE);
            }
        }
    }

    public boolean hasBetterBlockToSync() {
        return getLastKnownBlockNumber() >= getBestBlockNumber() + syncConfiguration.getLongSyncLimit();
    }

    public boolean canBeIgnoredForUnclesRewards(long blockNumber) {
        int blockDistance = config.getNetworkConstants().getUncleGenerationLimit();
        return blockNumber < getBestBlockNumber() - blockDistance;
    }

    public long getLastKnownBlockNumber() {
        return this.lastKnownBlockNumber;
    }

    public void setLastKnownBlockNumber(long lastKnownBlockNumber) {
        this.lastKnownBlockNumber = lastKnownBlockNumber;
    }

    private long getBestBlockNumber() {
        return this.blockchain.getBestBlock().getNumber();
    }

    private void trySaveStore(@Nonnull Block block) {
        if (!this.store.hasBlock(block)) {
            this.store.saveBlock(block);
        }
    }

    private Map<Keccak256, ImportResult> connectBlocksAndDescendants(Peer sender, List<Block> blocks, boolean ignoreMissingHashes) {
        Map<Keccak256, ImportResult> connectionsResult = new HashMap<>();
        List<Block> remainingBlocks = blocks;
        while (!remainingBlocks.isEmpty()) {
            Set<Block> connected = getConnectedBlocks(remainingBlocks, sender, connectionsResult, ignoreMissingHashes);
            remainingBlocks = this.store.getChildrenOf(connected);
        }

        return connectionsResult;
    }

    private Set<Block> getConnectedBlocks(List<Block> remainingBlocks, Peer sender, Map<Keccak256, ImportResult> connectionsResult, boolean ignoreMissingHashes) {
        Set<Block> connected = new HashSet<>();

        for (Block block : remainingBlocks) {
            logger.trace("Trying to add block {} {}", block.getNumber(), block.getShortHash());

            Set<Keccak256> missingHashes = BlockUtils.unknownDirectAncestorsHashes(block, blockchain, store);

            if (!missingHashes.isEmpty()) {
                if (!ignoreMissingHashes){
                    logger.trace("Missing hashes for block in process {} {}", block.getNumber(), block.getShortHash());
                    requestMissingHashes(sender, missingHashes);
                }
                continue;
            }

            connectionsResult.put(block.getHash(), blockchain.tryToConnect(block));

            if (BlockUtils.blockInSomeBlockChain(block, blockchain)) {
                this.store.removeBlock(block);
                connected.add(block);
            }
        }
        return connected;
    }

    private void requestMissingHashes(Peer sender, Set<Keccak256> hashes) {
        logger.trace("Missing blocks to process {}", hashes.size());

        for (Keccak256 hash : hashes) {
            this.requestMissingHash(sender, hash);
        }
    }

    private void requestMissingHash(Peer sender, Keccak256 hash) {
        if (sender == null) {
            return;
        }

        unknownBlockHashes.put(hash, 1);

        logger.trace("Missing block {}", hash.toString().substring(0, 10));

        sender.sendMessage(new GetBlockMessage(hash.getBytes()));
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
        while (currentBlock != null && !blockchain.hasBlockInSomeBlockchain(currentBlock.getHash().getBytes())) {
            BlockUtils.addBlockToList(blocks, currentBlock);

            currentBlock = getBlockFromStoreOrBlockchain(currentBlock.getParentHash().getBytes());
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
