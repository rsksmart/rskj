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

import co.rsk.core.commons.Keccak256;
import co.rsk.net.messages.*;
import co.rsk.net.sync.SyncConfiguration;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;
import org.ethereum.core.Blockchain;
import org.ethereum.crypto.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * NodeBlockProcessor processes blocks to add into a blockchain.
 * If a block is not ready to be added to the blockchain, it will be on hold in a BlockStore.
 * <p>
 * Created by ajlopez on 5/11/2016.
 */
public class NodeBlockProcessor implements BlockProcessor {
    private static final Logger logger = LoggerFactory.getLogger("blockprocessor");

    private final BlockStore store;
    private final Blockchain blockchain;
    private final BlockNodeInformation nodeInformation;
    // keep tabs on which nodes know which blocks.
    private final BlockSyncService blockSyncService;
    private final SyncConfiguration syncConfiguration;
    // keeps on a map the hashes that belongs to the skeleton
    private final Map <Long, Keccak256> skeletonCache = new HashMap<>();

    /**
     * Creates a new NodeBlockProcessor using the given BlockStore and Blockchain.
     *
     * @param store        A BlockStore to store the blocks that are not ready for the Blockchain.
     * @param blockchain   The blockchain in which to insert the blocks.
     * @param nodeInformation
     * @param blockSyncService
     */
    public NodeBlockProcessor(
            @Nonnull final BlockStore store,
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
    @Override
    @Nonnull
    public Blockchain getBlockchain() {
        return this.blockchain;
    }

    /**
     * processNewBlockHashesMessage processes a "NewBlockHashes" message. This means that we received hashes
     * from new blocks and we should request all the blocks that we don't have.
     *
     * @param sender  The message sender
     * @param message A message containing a list of block hashes.
     */
    @Override
    public void processNewBlockHashesMessage(@Nonnull final MessageChannel sender, @Nonnull final NewBlockHashesMessage message) {
        message.getBlockIdentifiers().stream()
                .map(bi -> bi.getHash())
                .collect(Collectors.toSet()) // Eliminate duplicates
                .stream()
                .filter(b -> !hasBlock(b))
                .forEach(
                        b -> {
                            sender.sendMessage(new GetBlockMessage(b));
                            nodeInformation.addBlockToNode(b, sender.getPeerNodeID());
                        }
                );
    }


    @Override
    public void processBlockHeaders(@Nonnull final MessageChannel sender, @Nonnull final List<BlockHeader> blockHeaders) {
        blockHeaders.stream()
                .filter(h -> !hasHeader(h.getHash()))
                // sort block headers in ascending order, so we can process them in that order.
                .sorted(Comparator.comparingLong(BlockHeader::getNumber))
                .forEach(h -> processBlockHeader(sender, h));
    }

    private boolean hasHeader(@Nonnull final Keccak256 hash) {
        return hasBlock(hash) || store.hasHeader(hash);
    }

    private void processBlockHeader(@Nonnull final MessageChannel sender, @Nonnull final BlockHeader header) {
        sender.sendMessage(new GetBlockMessage(header.getHash()));

        this.store.saveHeader(header);
    }

    /**
     * processGetBlock sends a requested block to a peer if the block is available.
     *
     * @param sender the sender of the GetBlock message.
     * @param hash   the requested block's hash.
     */
    @Override
    public void processGetBlock(@Nonnull final MessageChannel sender, @Nonnull final Keccak256 hash) {
        logger.trace("Processing get block {} from {}",
                HashUtil.getHashTillIndex(hash, 10),
                sender.getPeerNodeID().toString());
        final Block block = blockSyncService.getBlockFromStoreOrBlockchain(hash);

        if (block == null) {
            return;
        }

        nodeInformation.addBlockToNode(hash, sender.getPeerNodeID());
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
    public void processBlockRequest(@Nonnull final MessageChannel sender, long requestId, @Nonnull final Keccak256 hash) {
        String hashString = hash.toString();
        logger.trace("Processing get block by hash {} {} from {}", requestId, HashUtil.getHashTillIndex(hash, 10), sender.getPeerNodeID().toString());
        final Block block = blockSyncService.getBlockFromStoreOrBlockchain(hash);

        if (block == null) {
            return;
        }

        nodeInformation.addBlockToNode(hash, sender.getPeerNodeID());
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
    public void processBlockHeadersRequest(@Nonnull final MessageChannel sender, long requestId, @Nonnull final Keccak256 hash, int count) {
        Block block = blockSyncService.getBlockFromStoreOrBlockchain(hash);

        if (block == null) {
            return;
        }

        List<BlockHeader> headers = new ArrayList<>();

        headers.add(block.getHeader());

        for (int k = 1; k < count; k++) {
            block = blockSyncService.getBlockFromStoreOrBlockchain(block.getParentHash());

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
    public void processBodyRequest(@Nonnull final MessageChannel sender, long requestId, @Nonnull final Keccak256 hash) {
        logger.trace("Processing body request {} {} from {}", requestId, HashUtil.getHashTillIndex(hash, 10), sender.getPeerNodeID().toString());
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
    public void processBlockHashRequest(@Nonnull final MessageChannel sender, long requestId, long height) {
        logger.trace("Processing block hash request {} {} from {}", requestId, height, sender.getPeerNodeID().toString());
        if (height == 0){
            return;
        }

        final Block block = this.getBlockFromBlockchainStore(height);

        if (block == null) {
            // Don't waste time sending an empty response.
            return;
        }

        BlockHashResponseMessage responseMessage = new BlockHashResponseMessage(requestId, block.getHash());
        sender.sendMessage(responseMessage);
    }

    /**
     * @param sender the sender of the SkeletonRequest message.
     * @param requestId the id of the request.
     * @param startNumber the starting block's hash to get the skeleton.
     */
    @Override
    public void processSkeletonRequest(@Nonnull final MessageChannel sender, long requestId, long startNumber) {
        logger.trace("Processing block hash request {} {} {} from {}", requestId, startNumber, sender.getPeerNodeID());
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
        long maxSkeletonNumber = Math.min(this.getBestBlockNumber(), skeletonStartHeight + skeletonStep * maxSkeletonChunks);

        for (; skeletonNumber < maxSkeletonNumber; skeletonNumber += skeletonStep) {
            Keccak256 skeletonHash = getSkeletonHash(skeletonNumber);
            blockIdentifiers.add(new BlockIdentifier(skeletonHash, skeletonNumber));
        }

        // We always include the best block as part of the Skeleton response
        skeletonNumber = Math.min(this.getBestBlockNumber(), skeletonNumber + skeletonStep);
        Keccak256 skeletonHash = getSkeletonHash(skeletonNumber);
        blockIdentifiers.add(new BlockIdentifier(skeletonHash, skeletonNumber));
        SkeletonResponseMessage responseMessage = new SkeletonResponseMessage(requestId, blockIdentifiers);

        sender.sendMessage(responseMessage);
    }

    /**
     *
     * @param skeletonBlockNumber a block number that belongs to the skeleton
     * @return the proper hash for the block
     */
    private Keccak256 getSkeletonHash(long skeletonBlockNumber) {
        // if block number is too close to best block then its not stored in cache
        // in order to avoid caching forked blocks
        if (blockchain.getBestBlock().getNumber() - skeletonBlockNumber < syncConfiguration.getChunkSize()){
            Block block = getBlockFromBlockchainStore(skeletonBlockNumber);
            if (block != null){
                return block.getHash();
            }
        }

        Keccak256 hash = skeletonCache.get(skeletonBlockNumber);
        if (hash == null){
            Block block = getBlockFromBlockchainStore(skeletonBlockNumber);
            if (block != null){
                hash = block.getHash();
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
    public boolean hasBlock(@Nonnull final Keccak256 hash) {
        return hasBlockInProcessorStore(hash) || hasBlockInSomeBlockchain(hash);
    }

    @Override
    public boolean hasBlockInProcessorStore(@Nonnull final Keccak256 hash) {
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
    public BlockProcessResult processBlock(@Nullable final MessageChannel sender, @Nonnull final Block block) {
        return blockSyncService.processBlock(sender, block, false);
    }

    @Override
    public boolean hasBlockInSomeBlockchain(@Nonnull final Keccak256 hash) {
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
