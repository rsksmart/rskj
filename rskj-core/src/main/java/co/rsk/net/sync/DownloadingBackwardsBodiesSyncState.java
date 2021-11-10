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
package co.rsk.net.sync;

import co.rsk.core.BlockDifficulty;
import co.rsk.net.Peer;
import co.rsk.net.messages.BodyResponseMessage;
import co.rsk.scoring.EventType;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Genesis;
import org.ethereum.db.BlockStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Given a sequential list of headers to request and an already stored child of the newest header
 * DownloadingBackwardsBodiesSyncState downloads the bodies of the requested headers and connects them to the child.
 * <p>
 * If the child is block number 1 or the requested list contains block number 1 it is connected to genesis.
 * <p>
 * Assuming that child is valid, the previous block can be validated by comparing it's hash to the child's parent.
 */
public class DownloadingBackwardsBodiesSyncState extends BaseSyncState {

    private static final Logger logger = LoggerFactory.getLogger("syncprocessor");

    private final PeersInformation peersInformation;
    private final Genesis genesis;
    private final BlockFactory blockFactory;
    private final BlockStore blockStore;
    private final Queue<BlockHeader> toRequest;
    private final Peer selectedPeer;

    private final PriorityQueue<Block> responses;
    private final Map<Long, BlockHeader> inTransit;
    private Block child;

    public DownloadingBackwardsBodiesSyncState(
            SyncConfiguration syncConfiguration,
            SyncEventsHandler syncEventsHandler,
            PeersInformation peersInformation,
            Genesis genesis,
            BlockFactory blockFactory,
            BlockStore blockStore,
            Block child,
            List<BlockHeader> toRequest,
            Peer peer) {

        super(syncEventsHandler, syncConfiguration);
        this.peersInformation = peersInformation;
        this.genesis = genesis;
        this.blockFactory = blockFactory;
        this.blockStore = blockStore;
        this.toRequest = new LinkedList<>(toRequest);
        this.child = child;
        this.selectedPeer = peer;
        this.responses = new PriorityQueue<>(Comparator.comparingLong(Block::getNumber).reversed());
        this.inTransit = new HashMap<>();
    }

    /**
     * Validates and connects the bodies in order.
     * <p>
     * As the responses may come in any order, they are stored in a priority queue by block number.
     * This allows connecting blocks sequentially with the current child.
     *
     * @param body The requested message containing the body
     * @param peer The peer sending the message.
     */
    @Override
    public void newBody(BodyResponseMessage body, Peer peer) {
        BlockHeader requestedHeader = inTransit.get(body.getId());
        if (requestedHeader == null) {
            peersInformation.reportEvent(peer.getPeerNodeID(), EventType.INVALID_MESSAGE);
            return;
        }

        Block block = blockFactory.newBlock(requestedHeader, body.getTransactions(), body.getUncles());
        block.seal();

        if (!block.getHash().equals(requestedHeader.getHash())) {
            peersInformation.reportEvent(peer.getPeerNodeID(), EventType.INVALID_MESSAGE);
            return;
        }

        resetTimeElapsed();
        inTransit.remove(body.getId());
        responses.add(block);

        while (!responses.isEmpty() && responses.peek().isParentOf(child)) {
            Block connectedBlock = responses.poll();
            BlockDifficulty blockchainDifficulty = blockStore.getTotalDifficultyForHash(child.getHash().getBytes());
            blockStore.saveBlock(
                    connectedBlock,
                    blockchainDifficulty.subtract(child.getCumulativeDifficulty()),
                    true);
            child = connectedBlock;
        }

        if (child.getNumber() == 1) {
            connectGenesis(child);
            blockStore.flush();
            logger.info("Backward syncing complete");
            syncEventsHandler.stopSyncing();
            return;
        }

        while (!toRequest.isEmpty() && inTransit.size() < syncConfiguration.getMaxRequestedBodies()) {
            requestBodies(toRequest.remove());
        }

        if (toRequest.isEmpty() && inTransit.isEmpty()) {
            blockStore.flush();
            logger.info("Backward syncing phase complete {}", block.getNumber());
            syncEventsHandler.stopSyncing();
        }
    }

    @Override
    public void onEnter() {
        if (child.getNumber() == 1L) {
            connectGenesis(child);
            blockStore.flush();
            syncEventsHandler.stopSyncing();
            return;
        }

        if (toRequest.isEmpty()) {
            syncEventsHandler.stopSyncing();
            return;
        }

        while (!toRequest.isEmpty() && inTransit.size() < syncConfiguration.getMaxRequestedBodies()) {
            BlockHeader headerToRequest = toRequest.remove();
                requestBodies(headerToRequest);
        }
    }

    private void requestBodies(BlockHeader headerToRequest) {
        long requestNumber = syncEventsHandler.sendBodyRequest(selectedPeer, headerToRequest);
        inTransit.put(requestNumber, headerToRequest);
    }

    private void connectGenesis(Block child) {
        if (!genesis.isParentOf(child)) {
            throw new IllegalStateException("Genesis does not connect to the current chain.");
        }

        BlockDifficulty blockchainDifficulty = blockStore.getTotalDifficultyForHash(this.child.getHash().getBytes());
        if (!blockchainDifficulty.subtract(this.child.getCumulativeDifficulty()).equals(genesis.getCumulativeDifficulty())) {
            throw new IllegalStateException("Blockchain difficulty does not match genesis.");
        }

        blockStore.saveBlock(genesis, genesis.getCumulativeDifficulty(), true);
    }

    @Override
    protected void onMessageTimeOut() {
        syncEventsHandler.onErrorSyncing(
                selectedPeer.getPeerNodeID(),
                "Timeout waiting requests {}",
                EventType.TIMEOUT_MESSAGE,
                this.getClass(),
                selectedPeer.getPeerNodeID());
    }
}
