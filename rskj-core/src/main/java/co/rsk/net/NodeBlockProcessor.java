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
import co.rsk.net.messages.*;
import co.rsk.net.sync.SyncConfiguration;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;
import org.ethereum.core.Blockchain;
import org.ethereum.db.ByteArrayWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

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

    private final Object statusLock = new Object();

    private final BlockStore store;
    private final Blockchain blockchain;
    private final BlockNodeInformation nodeInformation; // keep tabs on which nodes know which blocks.
    private final BlockSyncService blockSyncService;
    private SyncConfiguration syncConfiguration;
    private final Map <Long, byte[]> skeletonCache = new HashMap<>();

    private long blocksForPeers;

    /**
     * Creates a new NodeBlockProcessor using the given BlockStore and Blockchain.
     *
     * @param config         RSK Configuration.
     * @param store        A BlockStore to store the blocks that are not ready for the Blockchain.
     * @param blockchain   The blockchain in which to insert the blocks.
     * @param nodeInformation
     * @param blockSyncService
     */
    public NodeBlockProcessor(
            @Nonnull final RskSystemProperties config,
            @Nonnull final BlockStore store,
            @Nonnull final Blockchain blockchain,
            @Nonnull final BlockNodeInformation nodeInformation,
            @Nonnull final BlockSyncService blockSyncService,
            @Nonnull final SyncConfiguration syncConfiguration) {

        this.store = store;
        this.blockchain = blockchain;
        this.nodeInformation = nodeInformation;
        this.blocksForPeers = config.getBlocksForPeers();
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
        // TODO(mvanotti): Implement retrieval via GetBlockHeaders and GetBlockBodies.
        message.getBlockIdentifiers().stream()
                .map(bi -> new ByteArrayWrapper(bi.getHash()))
                .collect(Collectors.toSet()) // Eliminate duplicates
                .stream()
                .filter(b -> !hasBlock(b.getData()))
                .forEach(
                        b -> {
                            sender.sendMessage(new GetBlockMessage(b.getData()));
                            nodeInformation.addBlockToNode(b, sender.getPeerNodeID());
                        }
                );
    }


    @Override
    public void processBlockHeaders(@Nonnull final MessageChannel sender, @Nonnull final List<BlockHeader> blockHeaders) {
        // TODO(mvanotti): Implement missing functionality.

        blockHeaders.stream()
                .filter(h -> !hasHeader(h.getHash()))
                // sort block headers in ascending order, so we can process them in that order.
                .sorted(Comparator.comparingLong(BlockHeader::getNumber))
                .forEach(h -> processBlockHeader(sender, h));
    }

    private boolean hasHeader(@Nonnull final byte[] hash) {
        return hasBlock(hash) || store.hasHeader(hash);
    }

    private void processBlockHeader(@Nonnull final MessageChannel sender, @Nonnull final BlockHeader header) {
        sender.sendMessage(new GetBlockMessage(header.getHash()));

        this.store.saveHeader(header);
    }

    /**
     * processStatus processes a Status containing another node's status (its bestBlock).
     * If the sender has a better best block, it will be requested.
     * Otherwise, all the blocks that the sender is missing will be sent to it.
     *
     * @param sender the message sender. This should be the node that sent the status message.
     * @param status The status message containing the other node's best block.
     */
    @Override
    public void processStatus(@Nonnull final MessageChannel sender, @Nonnull final Status status) {
        logger.trace("Processing status {} {} from {}", status.getBestBlockNumber(),
                Hex.toHexString(status.getBestBlockHash()).substring(0, 10), sender.getPeerNodeID().toString());

        final byte[] hash = status.getBestBlockHash();
        nodeInformation.addBlockToNode(new ByteArrayWrapper(hash), sender.getPeerNodeID());

        if (!this.hasBlock(hash))
            sender.sendMessage(new GetBlockMessage(hash));

        final long bestBlockNumber = this.getBestBlockNumber();
        final long peerBestBlockNumber = status.getBestBlockNumber();

        if (peerBestBlockNumber > blockSyncService.getLastKnownBlockNumber())
            blockSyncService.setLastKnownBlockNumber(peerBestBlockNumber);

        for (long n = peerBestBlockNumber; n <= bestBlockNumber && n < peerBestBlockNumber + this.blocksForPeers; n++) {
            logger.trace("Trying to send block {}", n);
            
            final Block b = this.blockchain.getBlockByNumber(n);

            if (b == null)
                continue;

            nodeInformation.addBlockToNode(new ByteArrayWrapper(b.getHash()), sender.getPeerNodeID());
            logger.trace("Sending block {} {}", b.getNumber(), b.getShortHash());
            sender.sendMessage(new BlockMessage(b));
        }
    }

    /**
     * processGetBlock sends a requested block to a peer if the block is available.
     *
     * @param sender the sender of the GetBlock message.
     * @param hash   the requested block's hash.
     */
    @Override
    public void processGetBlock(@Nonnull final MessageChannel sender, @Nonnull final byte[] hash) {
        logger.trace("Processing get block {} from {}", Hex.toHexString(hash).substring(0, 10), sender.getPeerNodeID().toString());
        final Block block = blockSyncService.getBlockFromStoreOrBlockchain(hash);

        if (block == null) {
            return;
        }

        nodeInformation.addBlockToNode(new ByteArrayWrapper(hash), sender.getPeerNodeID());
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
    public void processBlockRequest(@Nonnull final MessageChannel sender, long requestId, @Nonnull final byte[] hash) {
        logger.trace("Processing get block by hash {} {} from {}", requestId, Hex.toHexString(hash).substring(0, 10), sender.getPeerNodeID().toString());
        final Block block = blockSyncService.getBlockFromStoreOrBlockchain(hash);

        if (block == null)
            return;

        nodeInformation.addBlockToNode(new ByteArrayWrapper(hash), sender.getPeerNodeID());
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
    public void processBlockHeadersRequest(@Nonnull final MessageChannel sender, long requestId, @Nonnull final byte[] hash, int count) {
        Block block = blockSyncService.getBlockFromStoreOrBlockchain(hash);

        if (block == null)
            return;

        List<BlockHeader> headers = new ArrayList<>();

        headers.add(block.getHeader());

        for (int k = 1; k < count; k++) {
            block = blockSyncService.getBlockFromStoreOrBlockchain(block.getParentHash());

            if (block == null)
                break;

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
    public void processBodyRequest(@Nonnull final MessageChannel sender, long requestId, @Nonnull final byte[] hash) {
        logger.trace("Processing body request {} {} from {}", requestId, Hex.toHexString(hash).substring(0, 10), sender.getPeerNodeID().toString());
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
        logger.trace("Processing block hash request {} {} {} from {}", requestId, startNumber, sender.getPeerNodeID().toString());
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
        for (long skeletonNumber = skeletonStartHeight; skeletonNumber < this.getBestBlockNumber(); skeletonNumber += skeletonStep) {
            byte[] skeletonHash = getSkeletonHash(skeletonNumber);
            blockIdentifiers.add(new BlockIdentifier(skeletonHash, skeletonNumber));
        }

        // We always include the best block as part of the Skeleton response
        blockIdentifiers.add(new BlockIdentifier(this.getBestBlockHash(), this.getBestBlockNumber()));
        SkeletonResponseMessage responseMessage = new SkeletonResponseMessage(requestId, blockIdentifiers);

        sender.sendMessage(responseMessage);
    }

    /**
     *
     * @param skeletonBlockNumber a block number that belongs to the skeleton
     * @return the proper hash for the block
     */
    private byte[] getSkeletonHash(long skeletonBlockNumber) {
        // if block number is too close to best block then its not stored in cache
        // in order to avoid caching forked blocks
        if (blockchain.getBestBlock().getNumber() - skeletonBlockNumber < 192){
            Block block = getBlockFromBlockchainStore(skeletonBlockNumber);
            if (block != null){
                return block.getHash();
            }
        }

        byte[] hash = skeletonCache.get(skeletonBlockNumber);
        if (hash == null){
            Block block = getBlockFromBlockchainStore(skeletonBlockNumber);
            if (block != null){
                hash = block.getHash();
                skeletonCache.put(skeletonBlockNumber, hash);
            }
        }
        return hash;
    }

    /**
     * processGetBlock sends a requested block to a peer if the block is available.
     *
     * @param sender the sender of the GetBlock message.
     * @param hash   the requested block's hash.
     */
    @Override
    public void processGetBlockHeaders(@Nonnull final MessageChannel sender,
                                       @Nonnull final byte[] hash) {
        processGetBlockHeaders(sender, 0, hash, 1, 0, false);
    }


    @Override
    public void processGetBlockHeaders(@Nonnull final MessageChannel sender,
                                       final long blockNumber,
                                       @Nullable byte[] hash,
                                       final int maxHeaders,
                                       final int skip,
                                       final boolean reverse) {
        // TODO(mvanotti): Implement reverse retrieval.
        Block block;
        if (hash == null) {
            block = this.getBlockchain().getBlockByNumber(blockNumber);
        } else {
            block = blockSyncService.getBlockFromStoreOrBlockchain(hash);
        }

        List<BlockHeader> result = new LinkedList<>();
        for (int i = 0; i < maxHeaders; i += 1) {
            if (block == null) {
                break;
            }

            result.add(block.getHeader());

            block = skipNBlocks(block, skip);
            if (block == null) {
                break;
            }

            hash = block.getParentHash();
            block = blockSyncService.getBlockFromStoreOrBlockchain(hash);
        }

        if (result.isEmpty()) {
            // Don't waste time sending an empty response.
            return;
        }
        // TODO(mvanotti): Add information NodeBlockHeader information.
        sender.sendMessage(new BlockHeadersMessage(result));
    }

    @CheckForNull
    private Block skipNBlocks(@Nonnull Block block, final int skip) {
        byte[] hash;
        Block result = block;
        for (int j = 0; j < skip; j++) {
            hash = result.getParentHash();
            result = blockSyncService.getBlockFromStoreOrBlockchain(hash);
            if (result == null) {
                return null;
            }
        }
        return result;
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
        return blockSyncService.getBestBlockNumber();
    }

    /**
     * getBestBlockHash returns the current blockchain best block's hash.
     *
     * @return the blockchain's best block's hash.
     */
    private byte[] getBestBlockHash() {
        return this.blockchain.getBestBlock().getHash();
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
        if (this.store == null)
            return false;

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
        return blockSyncService.processBlock(sender, block);
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
    public boolean isSyncingBlocks() {
        return blockSyncService.isSyncingBlocks();
    }

    @Override
    public void acceptAnyBlock() {
        blockSyncService.acceptAnyBlock();
    }

    @Override
    public long getLastKnownBlockNumber() {
        return blockSyncService.getLastKnownBlockNumber();
    }
}
