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
import co.rsk.net.messages.MessageVersionValidator;
import co.rsk.net.sync.SyncConfiguration;
import co.rsk.validators.BlockValidator;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.ImportResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.util.*;

/**
 * BlockSyncService processes blocks to add into a blockchain.
 * If a block is not ready to be added to the blockchain, it will be on hold in a BlockStore.
 */
public class BlockSyncService {
    private static final Logger logger = LoggerFactory.getLogger("blocksyncservice");

    private static final int PROCESSED_BLOCKS_TO_CHECK_STORE = 200;
    private static final int RELEASED_RANGE = 1000;

    private long processedBlocksCounter;
    private long lastKnownBlockNumber = 0;

    private final NetBlockStore store;
    private final Blockchain blockchain;
    private final SyncConfiguration syncConfiguration;
    private final BlockNodeInformation nodeInformation; // keep tabs on which nodes know which blocks.
    private final RskSystemProperties config;
    private final BlockValidator blockHeaderValidator;
    private final MessageVersionValidator messageVersionValidator;

    // this is tightly coupled with NodeProcessorService and SyncProcessor,
    // and we should use the same objects everywhere to ensure consistency
    public BlockSyncService(
            @Nonnull final RskSystemProperties config,
            @Nonnull final NetBlockStore store,
            @Nonnull final Blockchain blockchain,
            @Nonnull final BlockNodeInformation nodeInformation,
            @Nonnull final SyncConfiguration syncConfiguration,
            @Nonnull final BlockValidator blockHeaderValidator,
            @Nonnull final MessageVersionValidator messageVersionValidator) {
        this.store = store;
        this.blockchain = blockchain;
        this.syncConfiguration = syncConfiguration;
        this.nodeInformation = nodeInformation;
        this.config = config;
        this.blockHeaderValidator = blockHeaderValidator;
        this.messageVersionValidator = messageVersionValidator;
    }

    @VisibleForTesting
    public BlockSyncService(
            @Nonnull final RskSystemProperties config,
            @Nonnull final NetBlockStore store,
            @Nonnull final Blockchain blockchain,
            @Nonnull final BlockNodeInformation nodeInformation,
            @Nonnull final SyncConfiguration syncConfiguration,
            @Nonnull final BlockValidator blockHeaderValidator) {
        this(config, store, blockchain, nodeInformation, syncConfiguration, blockHeaderValidator, null);
    }

    /**
     * Does initial preprocessing of the {@code block}.
     * 
     * @return block and its ancestors (if any), which are not connected yet. Returns an empty list,
     * if the block is too advanced, already connected or some of its ancestors are still being awaited from a network.
     */
    protected List<Block> preprocessBlock(@Nonnull Block block, Peer sender, boolean ignoreMissingHashes) {
        final long bestBlockNumber = this.getBestBlockNumber();
        final long blockNumber = block.getNumber();
        final Keccak256 blockHash = block.getHash();
        final int syncMaxDistance = syncConfiguration.getChunkSize() * syncConfiguration.getMaxSkeletonChunks();

        tryReleaseStore(bestBlockNumber);
        store.removeHeader(block.getHeader());

        if (blockNumber > bestBlockNumber + syncMaxDistance) {
            logger.trace("Block too advanced {} {} from {} ", blockNumber, block.getPrintableHash(),
                    sender != null ? sender.getPeerNodeID().toString() : "N/A");
            return Collections.emptyList();
        }

        if (sender != null) {
            nodeInformation.addBlockToNode(blockHash, sender.getPeerNodeID());
        }

        // already in a blockchain
        if (BlockUtils.blockInSomeBlockChain(block, blockchain)) {
            logger.trace("Block already in a chain {} {}", blockNumber, block.getPrintableHash());
            return Collections.emptyList();
        }
        trySaveStore(block);

        Set<Keccak256> unknownHashes = BlockUtils.unknownDirectAncestorsHashes(block, blockchain, store);
        // We can't add the block if there are missing ancestors or uncles. Request the missing blocks to the sender.
        if (!unknownHashes.isEmpty()) {
            if (!ignoreMissingHashes) {
                logger.trace("Missing hashes for block in process {} {}", blockNumber, block.getPrintableHash());
                requestMissingHashes(sender, unknownHashes);
            }
            return Collections.emptyList();
        }

        return BlockUtils.sortBlocksByNumber(this.getParentsNotInBlockchain(block));
    }

    public BlockProcessResult processBlock(@Nonnull Block block, Peer sender, boolean ignoreMissingHashes) {
        final Instant start = Instant.now();
        
        // Validate block header first to see if its PoW is valid at all
        if (!isBlockHeaderValid(block)) {
            logger.warn("Invalid block with number {} {} from {} ", block.getNumber(), block.getHash(), sender);
            return invalidBlockResult(block, start);
        }

        List<Block> blocksToConnect = preprocessBlock(block, sender, ignoreMissingHashes);
        if (blocksToConnect.isEmpty()) {
            return BlockProcessResult.ignoreBlockResult(block, start);
        }

        logger.trace("Trying to add to blockchain");

        Map<Keccak256, ImportResult> connectResult = connectBlocksAndDescendants(sender, blocksToConnect, ignoreMissingHashes);
        return BlockProcessResult.connectResult(block, start, connectResult);
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

    protected Map<Keccak256, ImportResult> connectBlocksAndDescendants(Peer sender, List<Block> blocks, boolean ignoreMissingHashes) {
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
            logger.trace("Trying to add block {} {}", block.getNumber(), block.getPrintableHash());

            Set<Keccak256> missingHashes = BlockUtils.unknownDirectAncestorsHashes(block, blockchain, store);

            if (!missingHashes.isEmpty()) {
                if (!ignoreMissingHashes) {
                    logger.trace("Missing hashes for block in process {} {}", block.getNumber(), block.getPrintableHash());
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
        
        logger.trace("Missing block {}", hash.toHexString());

        int localVersion = messageVersionValidator.getLocalVersion();
        sender.sendMessage(new GetBlockMessage(localVersion, hash.getBytes()));
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

    private boolean isBlockHeaderValid(Block block) {
        return blockHeaderValidator.isValid(block);
    }

    private static BlockProcessResult invalidBlockResult(@Nonnull Block block, @Nonnull Instant start) {
        Map<Keccak256, ImportResult> result = Collections.singletonMap(block.getHash(), ImportResult.INVALID_BLOCK);
        return BlockProcessResult.connectResult(block, start, result);
    }
}
