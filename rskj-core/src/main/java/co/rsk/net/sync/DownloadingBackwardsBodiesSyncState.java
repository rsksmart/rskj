package co.rsk.net.sync;

import co.rsk.core.BlockDifficulty;
import co.rsk.net.MessageChannel;
import co.rsk.net.NodeID;
import co.rsk.net.messages.BodyResponseMessage;
import co.rsk.scoring.EventType;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Genesis;
import org.ethereum.db.BlockStore;

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

    private final PeersInformation peersInformation;
    private final Genesis genesis;
    private final BlockFactory blockFactory;
    private final BlockStore blockStore;
    private final Queue<BlockHeader> toRequest;
    private final NodeID selectedPeerId;

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
            NodeID selectedPeerId) {

        super(syncEventsHandler, syncConfiguration);
        this.peersInformation = peersInformation;
        this.genesis = genesis;
        this.blockFactory = blockFactory;
        this.blockStore = blockStore;
        this.toRequest = new LinkedList<>(toRequest);
        this.child = child;
        this.selectedPeerId = selectedPeerId;
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
    public void newBody(BodyResponseMessage body, MessageChannel peer) {
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
            syncEventsHandler.stopSyncing();
            return;
        }

        while (!toRequest.isEmpty() && inTransit.size() < syncConfiguration.getMaxRequestedBodies()) {
            requestBodies(toRequest.remove());
        }

        if (toRequest.isEmpty() && inTransit.isEmpty()) {
            blockStore.flush();
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
        Long requestNumber = syncEventsHandler.sendBodyRequest(headerToRequest, selectedPeerId);
        if (requestNumber == null) {
            syncEventsHandler.onSyncIssue("Channel failed to sent on {} to {}",
                    this.getClass(), selectedPeerId);
        }
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
        syncEventsHandler.onErrorSyncing(selectedPeerId,
                "Timeout waiting requests {}", EventType.TIMEOUT_MESSAGE, this.getClass(), selectedPeerId);
    }
}
