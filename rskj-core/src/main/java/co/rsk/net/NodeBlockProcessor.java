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

import co.rsk.crypto.Keccak256;
import co.rsk.net.messages.*;
import co.rsk.net.sync.SyncConfiguration;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;
import org.ethereum.core.Blockchain;
import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * NodeBlockProcessor processes blocks to add into a blockchain.
 * If a block is not ready to be added to the blockchain, it will be on hold in a BlockStore.
 * <p>
 * Created by ajlopez on 5/11/2016.
 */
public class NodeBlockProcessor implements BlockProcessor {
    private static final Logger logger = LoggerFactory.getLogger("blockprocessor");

    private final Blockchain blockchain;
    private final BlockNodeInformation nodeInformation;
    private final SyncConfiguration syncConfiguration;
    // keeps on a map the hashes that belongs to the skeleton
    private final Map <Long, byte[]> skeletonCache = new HashMap<>();

    protected final NetBlockStore store;
    // keep tabs on which nodes know which blocks.
    protected final BlockSyncService blockSyncService;

    /**
     * Creates a new NodeBlockProcessor using the given BlockStore and Blockchain.
     *
     * @param store        A BlockStore to store the blocks that are not ready for the Blockchain.
     * @param blockchain   The blockchain in which to insert the blocks.
     * @param nodeInformation
     * @param blockSyncService
     */
    public NodeBlockProcessor(
            @Nonnull final NetBlockStore store,
            @Nonnull final Blockchain blockchain,
            @Nonnull final BlockNodeInformation nodeInformation,
            @Nonnull final BlockSyncService blockSyncService,
            @Nonnull final SyncConfiguration syncConfiguration) {
        this.store = store;
        this.blockchain = blockchain;
        this.nodeInformation = nodeInformation;
        this.blockSyncService = blockSyncService;
        this.syncConfiguration = syncConfiguration;
    }

    /**
     * Detect a block number that is too advanced
     * based on sync chunk size and maximum number of chuncks
     *
     * @param blockNumber   the block number to check
     * @return  true if the block number is too advanced
     */
    @Override
    public boolean isAdvancedBlock(long blockNumber) {
        int syncMaxDistance = syncConfiguration.getChunkSize() * syncConfiguration.getMaxSkeletonChunks();
        long bestBlockNumber = this.getBestBlockNumber();

        return blockNumber > bestBlockNumber + syncMaxDistance;
    }

    /**
     * processNewBlockHashesMessage processes a "NewBlockHashes" message. This means that we received hashes
     * from new blocks and we should request all the blocks that we don't have.
     *
     * @param sender  The message sender
     * @param message A message containing a list of block hashes.
     */
    @Override
    public void processNewBlockHashesMessage(final Peer sender, final NewBlockHashesMessage message) {
        message.getBlockIdentifiers().stream()
                .map(bi -> new Keccak256(bi.getHash()))
                .distinct()
                .filter(b -> !hasBlock(b.getBytes()))
                .forEach(
                        b -> {
                            sender.sendMessage(new GetBlockMessage(b.getBytes()));
                            nodeInformation.addBlockToNode(b, sender.getPeerNodeID());
                        }
                );
    }


    @Override
    public void processBlockHeaders(@Nonnull final Peer sender, @Nonnull final List<BlockHeader> blockHeaders) {
        blockHeaders.stream()
                .filter(h -> !hasHeader(h.getHash()))
                // sort block headers in ascending order, so we can process them in that order.
                .sorted(Comparator.comparingLong(BlockHeader::getNumber))
                .forEach(h -> processBlockHeader(sender, h));
    }

    private boolean hasHeader(Keccak256 hash) {
        return hasBlock(hash.getBytes()) || store.hasHeader(hash);
    }

    private void processBlockHeader(@Nonnull final Peer sender, @Nonnull final BlockHeader header) {
        sender.sendMessage(new GetBlockMessage(header.getHash().getBytes()));
        this.store.saveHeader(header);
    }

    /**
     * processGetBlock sends a requested block to a peer if the block is available.
     *
     * @param sender the sender of the GetBlock message.
     * @param hash   the requested block's hash.
     */
    @Override
    public void processGetBlock(@Nonnull final Peer sender, @Nonnull final byte[] hash) {
        logger.trace("Processing get block {} from {}", ByteUtil.toHexString(hash), sender.getPeerNodeID());
        final Block block = blockSyncService.getBlockFromStoreOrBlockchain(hash);

        if (block == null) {
            return;
        }

        nodeInformation.addBlockToNode(new Keccak256(hash), sender.getPeerNodeID());
        sender.sendMessage(new BlockMessage(block));
    }

    /**
     * processBlockRequest sends a requested block to a peer if the block is available.
     *
     * @param sender the sender of the BlockRequest message.
     * @param requestId the id of the request
     * @param hash   the requested block's hash.
     */
    @Override
    public void processBlockRequest(@Nonnull final Peer sender, long requestId, @Nonnull final byte[] hash) {
        logger.trace("Processing get block by hash {} {} from {}", requestId, ByteUtil.toHexString(hash), sender.getPeerNodeID());
        final Block block = blockSyncService.getBlockFromStoreOrBlockchain(hash);

        if (block == null) {
            return;
        }

        nodeInformation.addBlockToNode(new Keccak256(hash), sender.getPeerNodeID());
        sender.sendMessage(new BlockResponseMessage(requestId, block));
    }

    /**
     * processBlockHeadersRequest sends a list of block headers.
     *
     * @param sender the sender of the BlockHeadersRequest message.
     * @param requestId the id of the request
     * @param hash   the hash of the block to be processed
     * @param count  the number of headers to send
     */
    @Override
    public void processBlockHeadersRequest(@Nonnull final Peer sender, long requestId, @Nonnull final byte[] hash, int count) {
        logger.trace("Processing headers request {} {} from {}", requestId, ByteUtil.toHexString(hash), sender.getPeerNodeID());

        if (count > syncConfiguration.getChunkSize()) {
            logger.trace("Headers request from {} failed because size {}", sender.getPeerNodeID(), count);
            return;
        }

        Block block = blockSyncService.getBlockFromStoreOrBlockchain(hash);

        if (block == null) {
            return;
        }

        List<BlockHeader> headers = new ArrayList<>();
        headers.add(block.getHeader());

        for (int k = 1; k < count; k++) {
            block = blockSyncService.getBlockFromStoreOrBlockchain(block.getParentHash().getBytes());

            if (block == null) {
                break;
            }

            headers.add(block.getHeader());
        }

        BlockHeadersResponseMessage response = new BlockHeadersResponseMessage(requestId, headers);

        sender.sendMessage(response);
    }

    /**
     * processBodyRequest sends the requested block body to a peer if it is available.
     *
     * @param sender the sender of the BodyRequest message.
     * @param requestId the id of the request
     * @param hash   the requested block's hash.
     */
    @Override
    public void processBodyRequest(@Nonnull final Peer sender, long requestId, @Nonnull final byte[] hash) {
        logger.trace("Processing body request {} {} from {}", requestId, ByteUtil.toHexString(hash), sender.getPeerNodeID());
        final Block block = blockSyncService.getBlockFromStoreOrBlockchain(hash);

        if (block == null) {
            // Don't waste time sending an empty response.
            return;
        }

        Message responseMessage = new BodyResponseMessage(requestId, block.getTransactionsList(), block.getUncleList());
        sender.sendMessage(responseMessage);
    }

    /**
     * processBlockHashRequest sends the requested block body to a peer if it is available.
     *  @param sender the sender of the BlockHashRequest message.
     * @param requestId the id of the request
     * @param height   the requested block's hash.
     */
    @Override
    public void processBlockHashRequest(@Nonnull final Peer sender, long requestId, long height) {
        logger.trace("Processing block hash request {} {} from {}", requestId, height, sender.getPeerNodeID());

        if (height == 0){
            return;
        }

        final Block block = this.getBlockFromBlockchainStore(height);

        if (block == null) {
            // Don't waste time sending an empty response.
            return;
        }

        BlockHashResponseMessage responseMessage = new BlockHashResponseMessage(requestId, block.getHash().getBytes());
        sender.sendMessage(responseMessage);
    }

    /**
     * @param sender the sender of the SkeletonRequest message.
     * @param requestId the id of the request.
     * @param startNumber the starting block's hash to get the skeleton.
     */
    @Override
    public void processSkeletonRequest(@Nonnull final Peer sender, long requestId, long startNumber) {
        logger.trace("Processing skeleton request {} {} from {}", requestId, startNumber, sender.getPeerNodeID());
        int skeletonStep = syncConfiguration.getChunkSize();
        Block blockStart = this.getBlockFromBlockchainStore(startNumber);

        // If we don't have a block with the requested number, we ignore the message
        if (blockStart == null) {
            // Don't waste time sending an empty response.
            return;
        }

        // We always include the skeleton block immediately before blockStart, even if it's Genesis
        long skeletonStartHeight = (blockStart.getNumber() / skeletonStep) * skeletonStep;
        List<BlockIdentifier> blockIdentifiers = new ArrayList<>();
        long skeletonNumber = skeletonStartHeight;
        int maxSkeletonChunks = syncConfiguration.getMaxSkeletonChunks();
        long maxSkeletonNumber = Math.min(this.getBestBlockNumber(), skeletonStartHeight + skeletonStep * (long) maxSkeletonChunks);

        for (; skeletonNumber < maxSkeletonNumber; skeletonNumber += skeletonStep) {
            byte[] skeletonHash = getSkeletonHash(skeletonNumber);
            blockIdentifiers.add(new BlockIdentifier(skeletonHash, skeletonNumber));
        }

        // We always include the best block as part of the Skeleton response
        skeletonNumber = Math.min(this.getBestBlockNumber(), skeletonNumber);
        byte[] skeletonHash = getSkeletonHash(skeletonNumber);
        blockIdentifiers.add(new BlockIdentifier(skeletonHash, skeletonNumber));
        SkeletonResponseMessage responseMessage = new SkeletonResponseMessage(requestId, blockIdentifiers);

        sender.sendMessage(responseMessage);
    }

    @Override
    public boolean canBeIgnoredForUnclesRewards(long blockNumber) {
        return blockSyncService.canBeIgnoredForUnclesRewards(blockNumber);
    }

    /**
     *
     * @param skeletonBlockNumber a block number that belongs to the skeleton
     * @return the proper hash for the block
     */
    private byte[] getSkeletonHash(long skeletonBlockNumber) {
        // if block number is too close to best block then its not stored in cache
        // in order to avoid caching forked blocks
        if (blockchain.getBestBlock().getNumber() - skeletonBlockNumber < syncConfiguration.getChunkSize()){
            Block block = getBlockFromBlockchainStore(skeletonBlockNumber);
            if (block != null){
                return block.getHash().getBytes();
            }
        }

        byte[] hash = skeletonCache.get(skeletonBlockNumber);
        if (hash == null){
            Block block = getBlockFromBlockchainStore(skeletonBlockNumber);
            if (block != null){
                hash = block.getHash().getBytes();
                skeletonCache.put(skeletonBlockNumber, hash);
            }
        }
        return hash;
    }

    @Override
    public BlockNodeInformation getNodeInformation() {
        return nodeInformation;
    }

    /**
     * getBlockFromBlockchainStore retrieves the block with the given height from the blockchain, if available.
     *
     * @param height the desired block's height.
     * @return a Block with the given height if available, null otherwise.
     */
    @CheckForNull
    private Block getBlockFromBlockchainStore(long height) {
        return this.blockchain.getBlockByNumber(height);
    }

    /**
     * getBestBlockNumber returns the current blockchain best block's number.
     *
     * @return the blockchain's best block's number.
     */
    public long getBestBlockNumber() {
        return blockchain.getBestBlock().getNumber();
    }

    /**
     * hasBlock checks if a given hash is in the store or in the blockchain, or in the blockchain index.
     *
     * @param hash the block's hash.
     * @return true if the block is in the store, or in the blockchain.
     */
    @Override
    public boolean hasBlock(@Nonnull final byte[] hash) {
        return hasBlockInProcessorStore(hash) || hasBlockInSomeBlockchain(hash);
    }

    @Override
    public boolean hasBlockInProcessorStore(@Nonnull final byte[] hash) {
        return this.store.hasBlock(hash);
    }

    // Below are methods delegated to BlockSyncService, but should eventually be deleted

    /**
     * processBlock processes a block and tries to add it to the blockchain.
     * It will also add all pending blocks (that depend on this block) into the blockchain.
     *
     * @param sender the message sender. If more data is needed, NodeProcessor might send a message to the sender
     *               requesting that data (for example, a missing parent block).
     * @param block  the block to process.
     */
    @Override
    public BlockProcessResult processBlock(@Nullable final Peer sender, @Nonnull final Block block) {
        return blockSyncService.processBlock(block, sender, false);
    }

    @Override
    public boolean hasBlockInSomeBlockchain(@Nonnull final byte[] hash) {
        return this.blockchain.hasBlockInSomeBlockchain(hash);
    }

    @Override
    public boolean hasBetterBlockToSync() {
        return blockSyncService.hasBetterBlockToSync();
    }

    @Override
    public long getLastKnownBlockNumber() {
        return blockSyncService.getLastKnownBlockNumber();
    }
}
