/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk.net.messages;

import co.rsk.config.RskSystemProperties;
import co.rsk.crypto.Keccak256;
import co.rsk.net.*;
import co.rsk.net.light.LightProcessor;
import co.rsk.scoring.EventType;
import co.rsk.scoring.PeerScoringManager;
import org.ethereum.core.Block;
import org.ethereum.core.BlockIdentifier;
import org.ethereum.core.Transaction;
import org.ethereum.net.server.ChannelManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The MessageVisitor handles the received wire messages resolution.
 * <p>
 * It should only visit a message once per instantiation.
 */
public class MessageVisitor {

    private static final Logger logger = LoggerFactory.getLogger("messagehandler");
    private static final Logger loggerMessageProcess = LoggerFactory.getLogger("messageProcess");

    private final BlockProcessor blockProcessor;
    private final SyncProcessor syncProcessor;
    private final LightProcessor lightProcessor;
    private final TransactionGateway transactionGateway;
    private final Peer sender;
    private final PeerScoringManager peerScoringManager;
    private final RskSystemProperties config;
    private final ChannelManager channelManager;

    public MessageVisitor(RskSystemProperties config,
                          BlockProcessor blockProcessor,
                          SyncProcessor syncProcessor,
                          LightProcessor lightProcessor,
                          TransactionGateway transactionGateway,
                          PeerScoringManager peerScoringManager,
                          ChannelManager channelManager,
                          Peer sender) {

        this.blockProcessor = blockProcessor;
        this.syncProcessor = syncProcessor;
        this.lightProcessor = lightProcessor;
        this.transactionGateway = transactionGateway;
        this.peerScoringManager = peerScoringManager;
        this.channelManager = channelManager;
        this.config = config;
        this.sender = sender;
    }

    /**
     * Processes a BlockMessage message, adding the block to the blockchain if appropriate, or
     * forwarding it to peers that are missing the Block.
     *
     * @param message the BlockMessage.
     */
    public void apply(BlockMessage message) {
        final Block block = message.getBlock();

        logger.trace("Process block {} {}", block.getNumber(), block.getShortHash());

        if (block.isGenesis()) {
            logger.trace("Skip block processing {} {}", block.getNumber(), block.getShortHash());
            return;
        }

        long blockNumber = block.getNumber();

        if (this.blockProcessor.isAdvancedBlock(blockNumber)) {
            logger.trace("Too advanced block {} {}", blockNumber, block.getShortHash());
            return;
        }

        if (blockProcessor.canBeIgnoredForUnclesRewards(block.getNumber())){
            logger.trace("Block ignored: too far from best block {} {}", blockNumber, block.getShortHash());
            return;
        }

        if (blockProcessor.hasBlockInSomeBlockchain(block.getHash().getBytes())){
            logger.trace("Block ignored: it's included in blockchain {} {}", blockNumber, block.getShortHash());
            return;
        }

        BlockProcessResult result = this.blockProcessor.processBlock(sender, block);

        if (result.isInvalidBlock()) {
            logger.trace("Invalid block {} {}", blockNumber, block.getShortHash());
            recordEvent(sender, EventType.INVALID_BLOCK);
            return;
        }

        tryRelayBlock(block, result);

        if (result.isBest()) {
            sender.imported(true);
        } else {
            sender.imported(false);
        }

        recordEvent(sender, EventType.VALID_BLOCK);
    }

    public void apply(StatusMessage message) {
        final Status status = message.getStatus();
        logger.trace("Process status {}", status.getBestBlockNumber());
        this.syncProcessor.processStatus(sender, status);
    }

    public void apply(GetBlockMessage message) {
        final byte[] hash = message.getBlockHash();
        this.blockProcessor.processGetBlock(sender, hash);
    }

    public void apply(BlockRequestMessage message) {
        final byte[] hash = message.getBlockHash();
        this.blockProcessor.processBlockRequest(sender, message.getId(), hash);
    }

    public void apply(BlockResponseMessage message) {
        this.syncProcessor.processBlockResponse(sender, message);
    }

    public void apply(SkeletonRequestMessage message) {
        final long startNumber = message.getStartNumber();
        this.blockProcessor.processSkeletonRequest(sender, message.getId(), startNumber);
    }

    public void apply(BlockHeadersRequestMessage message) {
        final byte[] hash = message.getHash();
        final int count = message.getCount();
        this.blockProcessor.processBlockHeadersRequest(sender, message.getId(), hash, count);
    }

    public void apply(BlockHashRequestMessage message) {
        this.blockProcessor.processBlockHashRequest(sender, message.getId(), message.getHeight());
    }

    public void apply(BlockHashResponseMessage message) {
        this.syncProcessor.processBlockHashResponse(sender, message);
    }

    public void apply(NewBlockHashMessage message) {
        this.syncProcessor.processNewBlockHash(sender, message);
    }

    public void apply(SkeletonResponseMessage message) {
        this.syncProcessor.processSkeletonResponse(sender, message);
    }

    public void apply(BlockHeadersResponseMessage message) {
        this.syncProcessor.processBlockHeadersResponse(sender, message);
    }

    public void apply(BodyRequestMessage message) {
        final byte[] hash = message.getBlockHash();
        this.blockProcessor.processBodyRequest(sender, message.getId(), hash);
    }

    public void apply(BodyResponseMessage message) {
        this.syncProcessor.processBodyResponse(sender, message);
    }

    public void apply(NewBlockHashesMessage message) {
        if (blockProcessor.hasBetterBlockToSync()) {
            loggerMessageProcess.debug("Message[{}] not processed.", message.getMessageType());
            return;
        }
        blockProcessor.processNewBlockHashesMessage(sender, message);
    }

    public void apply(TransactionsMessage message) {
        if (blockProcessor.hasBetterBlockToSync()) {
            loggerMessageProcess.debug("Message[{}] not processed.", message.getMessageType());
            return;
        }

        long start = System.nanoTime();
        loggerMessageProcess.debug("Tx message about to be process: {}", message.getMessageContentInfo());

        List<Transaction> messageTxs = message.getTransactions();
        List<Transaction> txs = new LinkedList<>();

        for (Transaction tx : messageTxs) {
            if (!tx.acceptTransactionSignature(config.getNetworkConstants().getChainId())) {
                recordEvent(sender, EventType.INVALID_TRANSACTION);
            } else {
                txs.add(tx);
                recordEvent(sender, EventType.VALID_TRANSACTION);
            }
        }

        transactionGateway.receiveTransactionsFrom(txs, sender.getPeerNodeID());

        loggerMessageProcess.debug("Tx message process finished after [{}] nano.", System.nanoTime() - start);
    }

    private void recordEvent(Peer sender, EventType event) {
        if (sender == null) {
            return;
        }

        this.peerScoringManager.recordEvent(sender.getPeerNodeID(), sender.getAddress(), event);
    }

    private void  tryRelayBlock(Block block, BlockProcessResult result) {
        // is new block and it is not orphan, it is in some blockchain
        if (result.wasBlockAdded(block) && !this.blockProcessor.hasBetterBlockToSync()) {
            relayBlock(block);
        }
    }

    private void relayBlock(Block block) {
        Keccak256 blockHash = block.getHash();
        final BlockNodeInformation nodeInformation = this.blockProcessor.getNodeInformation();
        final Set<NodeID> nodesWithBlock = nodeInformation.getNodesByBlock(block.getHash());
        final Set<NodeID> newNodes = this.syncProcessor.getKnownPeersNodeIDs().stream()
                .filter(p -> !nodesWithBlock.contains(p))
                .collect(Collectors.toSet());


        List<BlockIdentifier> identifiers = new ArrayList<>();
        identifiers.add(new BlockIdentifier(blockHash.getBytes(), block.getNumber()));
        channelManager.broadcastBlockHash(identifiers, newNodes);
    }
}
