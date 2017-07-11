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
import co.rsk.net.messages.*;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Blockchain;
import org.ethereum.core.ImportResult;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.manager.WorldManager;
import org.ethereum.net.server.ChannelManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import java.util.*;
import java.util.stream.Collectors;

/**
 * NodeBlockProcessor processes blocks to add into a blockchain.
 * If a block is not ready to be added to the blockchain, it will be on hold in a BlockStore.
 * <p>
 * Created by ajlopez on 5/11/2016.
 */
public class NodeBlockProcessor implements BlockProcessor {
    private static final int NBLOCKS_TO_SYNC = 30;

    private final Object syncLock = new Object();
    @GuardedBy("syncLock")
    private volatile int nsyncs = 0;
    @GuardedBy("syncLock")
    private volatile boolean syncing = false;

    private long processedBlocksCounter;
    private static final Logger logger = LoggerFactory.getLogger("blockprocessor");

    private final Object statusLock = new Object();
    @GuardedBy("statusLock")
    private volatile long lastStatusBestBlock = 0;

    private final BlockStore store;
    private final Blockchain blockchain;
    private final ChannelManager channelManager;
    private final BlockNodeInformation nodeInformation; // keep tabs on which nodes know which blocks.
    private long lastKnownBlockNumber = 0;

    private Map<ByteArrayWrapper, Integer> unknownBlockHashes = new HashMap<>();

    private long lastStatusTime;
    private long blocksForPeers;
    private boolean ignoreAdvancedBlocks = true;

    /**
     * Creates a new NodeBlockProcessor using the given BlockStore and Blockchain.
     *
     * @param store        A BlockStore to store the blocks that are not ready for the Blockchain.
     * @param blockchain   The blockchain in which to insert the blocks.
     * @param worldManager The parent worldManager (used to set the reference)
     */
    // TODO define NodeBlockProcessor as a spring component
    public NodeBlockProcessor(@Nonnull final BlockStore store, @Nonnull final Blockchain blockchain, @Nonnull WorldManager worldManager) {
        this.store = store;
        this.blockchain = blockchain;
        this.nodeInformation = new BlockNodeInformation();
        worldManager.setNodeBlockProcessor(this);
        this.channelManager = worldManager.getChannelManager();
        this.blocksForPeers = RskSystemProperties.RSKCONFIG.getBlocksForPeers();
    }

    /**
     * Creates a new NodeBlockProcessor using the given BlockStore and Blockchain.
     *
     * @param store      A BlockStore to store the blocks that are not ready for the Blockchain.
     * @param blockchain The blockchain in which to insert the blocks.
     */
    public NodeBlockProcessor(@Nonnull final BlockStore store, @Nonnull final Blockchain blockchain) {
        this.store = store;
        this.blockchain = blockchain;
        this.nodeInformation = new BlockNodeInformation();
        this.channelManager = null;
        this.blocksForPeers = RskSystemProperties.RSKCONFIG.getBlocksForPeers();
    }

    @Override
    @Nonnull
    public Blockchain getBlockchain() {
        return this.blockchain;
    }

    @Override
    public long getLastKnownBlockNumber() {
        return this.lastKnownBlockNumber;
    }

    /**
     * processNewBlockHashesMessage processes a "NewBlockHashes" message. This means that we received hashes
     * from new blocks and we should request all the blocks that we don't have.
     *
     * @param sender  The message sender
     * @param message A message containing a list of block hashes.
     */
    @Override
    public void processNewBlockHashesMessage(@Nonnull final MessageSender sender, @Nonnull final NewBlockHashesMessage message) {
        // TODO(mvanotti): Implement retrieval via GetBlockHeaders and GetBlockBodies.
        message.getBlockIdentifiers().stream()
                .map(bi -> new ByteArrayWrapper(bi.getHash()))
                .collect(Collectors.toSet()) // Eliminate duplicates
                .stream()
                .filter(b -> !hasBlock(b.getData()))
                .forEach(
                        b -> {
                            sender.sendMessage(new GetBlockMessage(b.getData()));
                            nodeInformation.addBlockToNode(b, sender.getNodeID());
                        }
                );
    }


    @Override
    public void processBlockHeaders(@Nonnull final MessageSender sender, @Nonnull final List<BlockHeader> blockHeaders) {
        // TODO(mvanotti): Implement missing functionality.

        // sort block headers in ascending order, so we can process them in that order.
        blockHeaders.sort((a, b) -> Long.compare(a.getNumber(), b.getNumber()));

        blockHeaders.stream()
                .filter(h -> !hasHeader(h))
                .forEach(h -> processBlockHeader(sender, h));
    }

    private boolean hasHeader(@Nonnull final BlockHeader h) {
        if (hasBlock(h.getHash())) {
            return true;
        }

        if (store.hasHeader(h.getHash())) {
            return true;
        }
        
        return false;
    }

    private void processBlockHeader(@Nonnull final MessageSender sender, @Nonnull final BlockHeader header) {
        sender.sendMessage(new GetBlockMessage(header.getHash()));

        this.store.saveHeader(header);
    }

    /**
     * processBlock processes a block and tries to add it to the blockchain.
     * It will also add all pending blocks (that depend on this block) into the blockchain.
     *
     * @param sender the message sender. If more data is needed, NodeProcessor might send a message to the sender
     *               requesting that data (for example, a missing parent block).
     * @param block  the block to process.
     */
    @Override
    public BlockProcessResult processBlock(@Nullable final MessageSender sender, @Nonnull final Block block) {
        long bestBlockNumber = this.getBestBlockNumber();
        long blockNumber = block.getNumber();

        if (block == null)  {
            logger.error("Block not received");
            return new BlockProcessResult(false, null);
        }

        if ((++processedBlocksCounter % 200) == 0) {
            long minimal = store.minimalHeight();
            long maximum = store.maximumHeight();
            logger.trace("Blocks in block processor {} from height {} to height {}", this.store.size(), minimal, maximum);

            if (minimal < bestBlockNumber - 1000)
                store.releaseRange(minimal, minimal + 1000);

            sendStatus(sender);
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

        BlockProcessResult result = new BlockProcessResult(true, connectBlocksAndDescendants(sender, BlockUtils.sortBlocksByNumber(getBlocksNotInBlockchain(block))));

        // After adding a long blockchain, refresh status if needed
        if (this.hasBetterBlockToSync())
            sendStatusToAll();

        return result;
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

            blocks = this.getChildrenInStore(connected);
        }
        return connectionsResult;
    }

    private void processMissingHashes(MessageSender sender, Set<ByteArrayWrapper> hashes) {
        logger.trace("Missing blocks to process " + hashes.size());

        for (ByteArrayWrapper hash : hashes)
            processMissingHash(sender, hash);
    }

    private void processMissingHash(MessageSender sender, ByteArrayWrapper hash) {
        if (sender == null)
            return;

        if (unknownBlockHashes.containsKey(hash)) {
            int counter = unknownBlockHashes.get(hash).intValue();

            counter++;

            if (counter <= 20) {
                unknownBlockHashes.put(hash, new Integer(counter));
                return;
            }
        }

        unknownBlockHashes.put(hash, new Integer(1));

        logger.trace("Missing block " + hash.toString().substring(0, 10));

        sender.sendMessage(new GetBlockMessage(hash.getData()));

        return;
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
    public void processStatus(@Nonnull final MessageSender sender, @Nonnull final Status status) {
        logger.trace("Processing status " + status.getBestBlockNumber() + " " + Hex.toHexString(status.getBestBlockHash()).substring(0, 10) + " from " + sender.getNodeID().toString());

        final byte[] hash = status.getBestBlockHash();
        nodeInformation.addBlockToNode(new ByteArrayWrapper(hash), sender.getNodeID());

        if (!this.hasBlock(hash))
            sender.sendMessage(new GetBlockMessage(hash));

        final long bestBlockNumber = this.getBestBlockNumber();
        final long peerBestBlockNumber = status.getBestBlockNumber();

        if (peerBestBlockNumber > this.lastKnownBlockNumber)
            this.lastKnownBlockNumber = peerBestBlockNumber;

        for (long n = peerBestBlockNumber; n <= bestBlockNumber && n < peerBestBlockNumber + this.blocksForPeers; n++) {
            logger.trace("Trying to send block {}", n);
            
            final Block b = this.blockchain.getBlockByNumber(n);

            if (b == null)
                continue;

            nodeInformation.addBlockToNode(new ByteArrayWrapper(b.getHash()), sender.getNodeID());
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
    public void processGetBlock(@Nonnull final MessageSender sender, @Nullable final byte[] hash) {
        logger.trace("Processing get block " + Hex.toHexString(hash).substring(0, 10) + " from " + sender.getNodeID().toString());
        final Block block = this.getBlock(hash);

        if (block == null) {
            return;
        }

        nodeInformation.addBlockToNode(new ByteArrayWrapper(hash), sender.getNodeID());
        sender.sendMessage(new BlockMessage(block));
    }

    /**
     * processGetBlock sends a requested block to a peer if the block is available.
     *
     * @param sender the sender of the GetBlock message.
     * @param hash   the requested block's hash.
     */
    @Override
    public void processGetBlockHeaders(@Nonnull final MessageSender sender,
                                       @Nonnull final byte[] hash) {
        processGetBlockHeaders(sender, 0, hash, 1, 0, false);
    }


    @Override
    public void processGetBlockHeaders(@Nonnull final MessageSender sender,
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
            block = this.getBlock(hash);
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
            block = this.getBlock(hash);
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
        for (int j = 0; j < skip; j++) {
            hash = block.getParentHash();
            block = this.getBlock(hash);
            if (block == null) {
                break;
            }
        }
        return block;
    }

    @Override
    public BlockNodeInformation getNodeInformation() {
        return nodeInformation;
    }

    /**
     * getBlocksNotInBlockchain returns all the ancestors of the block (including the block itself) that are not
     * on the blockchain.
     *
     * @param block the base block.
     * @return A list with the blocks sorted by ascending block number (the base block would be the last element).
     */
    @Nonnull
    private List<Block> getBlocksNotInBlockchain(@Nullable Block block) {
        final List<Block> blocks = new ArrayList<>();

        while (block != null && !this.hasBlockInSomeBlockchain(block.getHash())) {
            BlockUtils.addBlockToList(blocks, block);

            block = this.getBlock(block.getParentHash());
        }

        return blocks;
    }

    /**
     * isBlockInBlockchainIndex returns true if a given block is indexed in the blockchain (it might not be the in the
     * canonical branch).
     *
     * @param block the block to check for.
     * @return true if there is a block in the blockchain with that hash.
     */
    private boolean isBlockInBlockhainIndex(@Nonnull final Block block) {
        final ByteArrayWrapper key = new ByteArrayWrapper(block.getHash());
        final List<Block> blocks = this.blockchain.getBlocksByNumber(block.getNumber());

        for (final Block b : blocks) {
            if (new ByteArrayWrapper(b.getHash()).equals(key)) {
                return true;
            }
        }

        return false;
    }

    /**
     * getChildrenInStore returns all the children of a list of blocks that are in the BlockStore.
     *
     * @param blocks the list of blocks to retrieve the children.
     * @return A list with all the children of the given list of blocks.
     */
    @Nonnull
    private List<Block> getChildrenInStore(@Nonnull final List<Block> blocks) {
        final List<Block> children = new ArrayList<Block>();

        for (final Block block : blocks)
            BlockUtils.addBlocksToList(children, this.store.getBlocksByParentHash(block.getHash()));

        return children;
    }

    /**
     * getBlockFromStore retrieves the block with the given hash from the BlockStore, if available.
     *
     * @param hash the desired block's hash.
     * @return a Block with the given hash if available, null otherwise.
     */
    @CheckForNull
    private Block getBlockFromStore(@Nonnull final byte[] hash) {
        return this.store.getBlockByHash(hash);
    }

    /**
     * getBlockFromBlockchainStore retrieves the block with the given hash from the blockchain, if available.
     *
     * @param hash the desired block's hash.
     * @return a Block with the given hash if available, null otherwise.
     */
    @CheckForNull
    private Block getBlockFromBlockchainStore(@Nonnull final byte[] hash) {
        return this.blockchain.getBlockByHash(hash);
    }

    /**
     * getBlock retrieves a block from the store or the blockchain if it's available, in that order.
     *
     * @param hash the desired block's hash.
     * @return a Block with the given hash if available, null otherwise.
     */
    @CheckForNull
    private Block getBlock(@Nonnull final byte[] hash) {
        final Block block = getBlockFromStore(hash);

        if (block != null) {
            return block;
        }

        return getBlockFromBlockchainStore(hash);
    }

    /**
     * getBestBlockNumber returns the current blockchain best block's number.
     *
     * @return the blockchain's best block's number.
     */
    public long getBestBlockNumber() {
        return this.blockchain.getBestBlock().getNumber();
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

    @Override
    public boolean hasBlockInSomeBlockchain(@Nonnull final byte[] hash) {
        if (this.blockchain == null)
            return false;

        final Block block = this.blockchain.getBlockByHash(hash);

        if (block == null) {
            return false;
        }

        return this.isBlockInBlockhainIndex(block);
    }

    @Override
    public boolean hasBetterBlockToSync() {
        synchronized (syncLock) {
            long last = this.getLastKnownBlockNumber();
            long current = this.getBestBlockNumber();

            if (last >= current + NBLOCKS_TO_SYNC)
                return true;

            return false;
        }
    }

    @Override
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

    // This does not send status to ALL anymore.
    // Should be renamed to something like sendStatusToSome.
    // Not renamed yet to avoid merging hell.
    @Override
    public void sendStatusToAll() {
        synchronized (statusLock) {
            if (this.channelManager == null)
                return;

            Block block = this.blockchain.getBestBlock();

            if (block == null)
                return;

            Status status = new Status(block.getNumber(), block.getHash());

            long currentTime = System.currentTimeMillis();

            if (currentTime - lastStatusTime < 1000)
                return;

            lastStatusTime = currentTime;

            lastStatusBestBlock = status.getBestBlockNumber();

            logger.trace("Sending status best block {} to all", status.getBestBlockNumber());
            this.channelManager.broadcastStatus(status);
        }
    }

    @Override
    public void acceptAnyBlock()
    {
        this.ignoreAdvancedBlocks = false;
    }

    private void sendStatus(MessageSender sender) {
        if (sender == null || this.blockchain == null)
            return;

        Block block = this.blockchain.getBestBlock();

        if (block == null)
            return;

        Status status = new Status(block.getNumber(), block.getHash());
        logger.trace("Sending status best block {} to {}", status.getBestBlockNumber(), sender.getNodeID().toString());
        StatusMessage msg = new StatusMessage(status);
        sender.sendMessage(msg);
    }
}
