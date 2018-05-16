package co.rsk.net;

import co.rsk.core.DifficultyCalculator;
import co.rsk.core.bc.BlockChainStatus;
import co.rsk.net.messages.*;
import co.rsk.net.sync.*;
import co.rsk.scoring.EventType;
import co.rsk.scoring.PeerScoringManager;
import co.rsk.util.MaxSizeHashMap;
import co.rsk.validators.BlockHeaderValidationRule;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;
import org.ethereum.core.Blockchain;
import org.ethereum.crypto.HashUtil;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.validator.DependentBlockHeaderRule;
import org.ethereum.validator.DifficultyRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Created by ajlopez on 29/08/2017.
 * This class' methods are executed one at a time because NodeMessageHandler is synchronized.
 */
public class SyncProcessor implements SyncEventsHandler {
    public static final int MAX_SIZE_FAILURE_RECORDS = 10;
    public static final int TIME_LIMIT_FAILURE_RECORD = 600;
    private static final Logger logger = LoggerFactory.getLogger("syncprocessor");

    private final Blockchain blockchain;
    private final BlockSyncService blockSyncService;
    private final PeerScoringManager peerScoringManager;
    private final ChannelManager channelManager;
    private final SyncConfiguration syncConfiguration;
    private final PeersInformation peerStatuses;

    private final PendingMessages pendingMessages;
    private final SyncInformationImpl syncInformation;
    private final Map<NodeID, Instant> failedPeers;
    private SyncState syncState;
    private NodeID selectedPeerId;

    public SyncProcessor(
            Blockchain blockchain, BlockSyncService blockSyncService, PeerScoringManager peerScoringManager,
            ChannelManager channelManager, SyncConfiguration syncConfiguration,
            BlockHeaderValidationRule blockHeaderValidationRule, DifficultyCalculator difficultyCalculator) {

        this.blockchain = blockchain;
        this.blockSyncService = blockSyncService;
        this.peerScoringManager = peerScoringManager;
        this.channelManager = channelManager;
        this.syncConfiguration = syncConfiguration;
        this.syncInformation = new SyncInformationImpl(blockHeaderValidationRule, difficultyCalculator);
        this.peerStatuses = new PeersInformation(syncInformation, channelManager, syncConfiguration);
        this.pendingMessages = new PendingMessages();
        this.failedPeers = new MaxSizeHashMap(MAX_SIZE_FAILURE_RECORDS);

        setSyncState(new DecidingSyncState(this.syncConfiguration, this, syncInformation, peerStatuses));
    }

    public void processStatus(MessageChannel sender, Status status) {
        logger.trace("Receiving syncState from node {} block {} {}", sender.getPeerNodeID(), status.getBestBlockNumber(), HashUtil.shortHash(status.getBestBlockHash()), status.getBestBlockHash());
        peerStatuses.registerPeer(sender.getPeerNodeID()).setStatus(status);
        syncState.newPeerStatus();
    }

    public void processSkeletonResponse(MessageChannel peer, SkeletonResponseMessage message) {
        logger.trace("Process skeleton response from node {}", peer.getPeerNodeID());
        peerStatuses.getOrRegisterPeer(peer.getPeerNodeID());

        if (!pendingMessages.isPending(message)){
            peerScoringManager.recordEvent(peer.getPeerNodeID(), null, EventType.UNEXPECTED_MESSAGE);
            return;
        }

        syncState.newSkeleton(message.getBlockIdentifiers(), peer);
    }

    public void processBlockHashResponse(MessageChannel peer, BlockHashResponseMessage message) {
        NodeID nodeID = peer.getPeerNodeID();
        logger.trace("Process block hash response from node {} hash {}", nodeID, HashUtil.shortHash(message.getHash()));
        peerStatuses.getOrRegisterPeer(nodeID);

        if (!pendingMessages.isPending(message)){
            peerScoringManager.recordEvent(nodeID, null, EventType.UNEXPECTED_MESSAGE);
            return;
        }

        syncState.newConnectionPointData(message.getHash());
    }

    public void processBlockHeadersResponse(MessageChannel peer, BlockHeadersResponseMessage message) {
        logger.trace("Process block headers response from node {}", peer.getPeerNodeID());
        peerStatuses.getOrRegisterPeer(peer.getPeerNodeID());

        if (!pendingMessages.isPending(message)){
            peerScoringManager.recordEvent(peer.getPeerNodeID(), null, EventType.UNEXPECTED_MESSAGE);
            return;
        }

        syncState.newBlockHeaders(message.getBlockHeaders());
    }

    public void processBodyResponse(MessageChannel peer, BodyResponseMessage message) {
        logger.trace("Process body response from node {}", peer.getPeerNodeID());
        peerStatuses.getOrRegisterPeer(peer.getPeerNodeID());

        if (!pendingMessages.isPending(message)){
            peerScoringManager.recordEvent(peer.getPeerNodeID(), null, EventType.UNEXPECTED_MESSAGE);
            return;
        }

        this.syncState.newBody(message, peer);
    }

    public void processNewBlockHash(MessageChannel peer, NewBlockHashMessage message) {
        NodeID nodeID = peer.getPeerNodeID();
        logger.trace("Process new block hash from node {} hash {}", nodeID, HashUtil.shortHash(message.getBlockHash()));
        byte[] hash = message.getBlockHash();

        if (syncState instanceof DecidingSyncState && blockSyncService.getBlockFromStoreOrBlockchain(hash) == null) {
            peerStatuses.getOrRegisterPeer(nodeID);
            sendMessage(nodeID, new BlockRequestMessage(pendingMessages.getNextRequestId(), hash));
        }
    }

    public void processBlockResponse(MessageChannel peer, BlockResponseMessage message) {
        NodeID nodeID = peer.getPeerNodeID();
        logger.trace("Process block response from node {} block {} {}", nodeID, message.getBlock().getNumber(), message.getBlock().getShortHash());
        peerStatuses.getOrRegisterPeer(nodeID);

        if (!pendingMessages.isPending(message)){
            peerScoringManager.recordEvent(nodeID, null, EventType.UNEXPECTED_MESSAGE);
            return;
        }

        blockSyncService.processBlock(message.getBlock(), peer, false);
    }

    @Override
    public boolean sendSkeletonRequest(NodeID nodeID, long height) {
        logger.trace("Send skeleton request to node {} height {}", nodeID, height);
        MessageWithId message = new SkeletonRequestMessage(pendingMessages.getNextRequestId(), height);
        return sendMessage(nodeID, message);
    }

    @Override
    public boolean sendBlockHashRequest(long height) {
        logger.trace("Send hash request to node {} height {}", selectedPeerId, height);
        BlockHashRequestMessage message = new BlockHashRequestMessage(pendingMessages.getNextRequestId(), height);
        return sendMessage(selectedPeerId, message);
    }

    @Override
    public boolean sendBlockHeadersRequest(ChunkDescriptor chunk) {
        logger.trace("Send headers request to node {}", selectedPeerId);

        BlockHeadersRequestMessage message = new BlockHeadersRequestMessage(pendingMessages.getNextRequestId(), chunk.getHash(), chunk.getCount());
        return sendMessage(selectedPeerId, message);
    }

    @Override
    public Long sendBodyRequest(@Nonnull BlockHeader header, NodeID peerId) {
        logger.trace("Send body request block {} hash {} to peer {}", header.getNumber(), HashUtil.shortHash(header.getHash().getBytes()), peerId);

        BodyRequestMessage message = new BodyRequestMessage(pendingMessages.getNextRequestId(), header.getHash().getBytes());
        if (!sendMessage(peerId, message)){
            return null;
        }
        return message.getId();
    }

    public Set<NodeID> getKnownPeersNodeIDs() {
        return this.peerStatuses.knownNodeIds();
    }

    public void onTimePassed(Duration timePassed) {
//        logger.trace("Time passed on node {}", timePassed);
        this.syncState.tick(timePassed);
    }

    @Override
    public void startSyncing(NodeID nodeID) {
        selectedPeerId = nodeID;
        logger.trace("Start syncing with node {}", nodeID);
        byte[] bestBlockHash = syncInformation.getPeerStatus(selectedPeerId).getStatus().getBestBlockHash();
        setSyncState(new CheckingBestHeaderSyncState(this.syncConfiguration, this, syncInformation, bestBlockHash));
    }

    @Override
    public void startDownloadingBodies(List<Deque<BlockHeader>> pendingHeaders, Map<NodeID, List<BlockIdentifier>> skeletons) {
        // we keep track of best known block and we start to trust it when all headers are validated
        List<BlockIdentifier> selectedSkeleton = skeletons.get(selectedPeerId);
        final long peerBestBlockNumber = selectedSkeleton.get(selectedSkeleton.size() - 1).getNumber();

        if (peerBestBlockNumber > blockSyncService.getLastKnownBlockNumber()) {
            blockSyncService.setLastKnownBlockNumber(peerBestBlockNumber);
        }

        setSyncState(new DownloadingBodiesSyncState(this.syncConfiguration, this, syncInformation, pendingHeaders, skeletons));
    }

    @Override
    public void startDownloadingHeaders(Map<NodeID, List<BlockIdentifier>> skeletons, long connectionPoint) {
        setSyncState(new DownloadingHeadersSyncState(this.syncConfiguration, this, syncInformation, skeletons, connectionPoint));
    }

    @Override
    public void startDownloadingSkeleton(long connectionPoint) {
        setSyncState(new DownloadingSkeletonSyncState(this.syncConfiguration, this, syncInformation, peerStatuses, connectionPoint));
    }

    @Override
    public void startFindingConnectionPoint() {
        logger.trace("Find connection point with node {}", selectedPeerId);
        long bestBlockNumber = syncInformation.getPeerStatus(selectedPeerId).getStatus().getBestBlockNumber();
        setSyncState(new FindingConnectionPointSyncState(this.syncConfiguration, this, syncInformation, bestBlockNumber));
    }

    @Override
    public void stopSyncing() {
        // always that a syncing process ends unexpectedly the best block number is reset
        blockSyncService.setLastKnownBlockNumber(blockchain.getBestBlock().getNumber());
        restartSyncingPhase();
        clearSyncingPhaseData();
    }

    @Override
    public void onErrorSyncing(String message, EventType eventType, Object... arguments) {
        failedPeers.put(selectedPeerId, Instant.now());
        peerScoringManager.recordEvent(selectedPeerId, null, eventType);
        logger.trace(message, arguments);
        stopSyncing();
    }

    @Override
    public void onSyncIssue(String message, Object... arguments) {
        logger.trace(message, arguments);
        stopSyncing();
    }

    @Override
    public void onCompletedSyncing() {
        logger.trace("Completed syncing phase with node {}", selectedPeerId);
        clearSyncingPhaseData();
        restartSyncingPhase();
    }

    private void clearSyncingPhaseData() {
        selectedPeerId = null;
        this.pendingMessages.clear();
        clearOldFailureEntries();
    }

    private void restartSyncingPhase() {
        setSyncState(new DecidingSyncState(syncConfiguration, this, syncInformation, peerStatuses));
    }

    private boolean sendMessage(NodeID nodeID, MessageWithId message) {
        boolean sent = sendMessageTo(nodeID, message);
        if (sent){
            pendingMessages.register(message);
        }
        return sent;
    }

    private boolean sendMessageTo(NodeID nodeID, MessageWithId message) {
        return channelManager.sendMessageTo(nodeID, message);
    }

    private void setSyncState(SyncState syncState) {
        this.syncState = syncState;
        this.syncState.onEnter();
    }

    private void clearOldFailureEntries() {
        Instant limit = Instant.now().minusSeconds(TIME_LIMIT_FAILURE_RECORD);
        failedPeers.values().removeIf(limit::isAfter);
    }

    @VisibleForTesting
    int getPeersCount() {
        return this.peerStatuses.count();
    }

    @VisibleForTesting
    int getNoAdvancedPeers() {
        BlockChainStatus chainStatus = this.blockchain.getStatus();

        if (chainStatus == null) {
            return this.peerStatuses.count();
        }

        return this.peerStatuses.countIf(s -> chainStatus.hasLowerTotalDifficultyThan(s.getStatus()));
    }

    @VisibleForTesting
    public void registerExpectedMessage(MessageWithId message) {
        pendingMessages.registerExpectedMessage(message);
    }

    @VisibleForTesting
    public void setSelectedPeer(MessageChannel peer, Status status, long height) {
        selectedPeerId = peer.getPeerNodeID();
        peerStatuses.getOrRegisterPeer(selectedPeerId).setStatus(status);
        FindingConnectionPointSyncState newState = new FindingConnectionPointSyncState(this.syncConfiguration, this, syncInformation, height);
        newState.setConnectionPoint(height);
        this.syncState = newState;
    }

    @VisibleForTesting
    public SyncState getSyncState() {
        return this.syncState;
    }

    @VisibleForTesting
    public boolean isPeerSyncing(NodeID nodeID) {
        return syncState.isSyncing() && selectedPeerId == nodeID;
    }

    @VisibleForTesting
    public Map<Long, MessageType> getExpectedResponses() {
        return this.pendingMessages.getExpectedMessages();
    }
    private class SyncInformationImpl implements SyncInformation {

        private final DependentBlockHeaderRule blockParentValidationRule;
        private final BlockHeaderValidationRule blockHeaderValidationRule;

        public SyncInformationImpl(BlockHeaderValidationRule blockHeaderValidationRule, DifficultyCalculator difficultyCalculator) {
            this.blockHeaderValidationRule = blockHeaderValidationRule;
            this.blockParentValidationRule = new DifficultyRule(difficultyCalculator);
        }

        public boolean isKnownBlock(byte[] hash) {
            return blockchain.getBlockByHash(hash) != null;
        }

        @Override
        public boolean hasLowerDifficulty(NodeID nodeID) {
            Status status = getPeerStatus(nodeID).getStatus();

            boolean hasTotalDifficulty = status.getTotalDifficulty() != null;
            return  (hasTotalDifficulty && blockchain.getStatus().hasLowerTotalDifficultyThan(status)) ||
                    // this works only for testing purposes, real status without difficulty dont reach this far
                    (!hasTotalDifficulty && blockchain.getStatus().getBestBlockNumber() < status.getBestBlockNumber());
        }

        @Override
        public BlockProcessResult processBlock(Block block, MessageChannel channel) {
            // this is a controlled place where we ask for blocks, we never should look for missing hashes
            return blockSyncService.processBlock(block, channel, true);
        }

        @Override
        public boolean blockHeaderIsValid(@Nonnull BlockHeader header) {
            return blockHeaderValidationRule.isValid(header);
        }

        @Override
        public boolean blockHeaderIsValid(@Nonnull BlockHeader header, @Nonnull BlockHeader parentHeader) {
            if (!parentHeader.getHash().equals(header.getParentHash())) {
                return false;
            }

            if (header.getNumber() != parentHeader.getNumber() + 1) {
                return false;
            }

            if (!blockHeaderIsValid(header)) {
                return false;
            }

            return blockParentValidationRule.validate(header, parentHeader);
        }

        @CheckForNull
        @Override
        public NodeID getSelectedPeerId() {
            return selectedPeerId;
        }

        @Override
        public boolean hasGoodReputation(NodeID nodeID) {
            return peerScoringManager.hasGoodReputation(nodeID);
        }

        @Override
        public void reportEvent(String message, EventType eventType, NodeID peerId, Object... arguments) {
            logger.trace(message, arguments);
            peerScoringManager.recordEvent(peerId, null, eventType);
        }

        @Override
        public int getScore(NodeID peerId) {
            return peerScoringManager.getPeerScoring(peerId).getScore();
        }

        @Override
        public Instant getFailInstant(NodeID peerId) {
            Instant instant = failedPeers.get(peerId);
            if (instant != null){
                return instant;
            }
            return Instant.EPOCH;
        }


        private SyncPeerStatus getPeerStatus(NodeID nodeID) {
            return peerStatuses.getPeer(nodeID);
        }
    }
}
