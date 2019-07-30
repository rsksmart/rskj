package co.rsk.net;

import co.rsk.core.DifficultyCalculator;
import co.rsk.core.bc.BlockChainStatus;
import co.rsk.core.bc.ConsensusValidationMainchainView;
import co.rsk.net.messages.*;
import co.rsk.net.sync.*;
import co.rsk.scoring.EventType;
import co.rsk.scoring.PeerScoringManager;
import co.rsk.validators.BlockCompositeRule;
import co.rsk.validators.BlockHeaderValidationRule;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.*;
import org.ethereum.crypto.HashUtil;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.validator.DifficultyRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * This class' methods are executed one at a time because NodeMessageHandler is synchronized.
 */
public class SyncProcessor implements SyncEventsHandler {
    private static final int MAX_SIZE_FAILURE_RECORDS = 10;
    private static final int TIME_LIMIT_FAILURE_RECORD = 600;
    private static final int MAX_PENDING_MESSAGES = 100_000;
    private static final Logger logger = LoggerFactory.getLogger("syncprocessor");

    private final Blockchain blockchain;
    private final ConsensusValidationMainchainView consensusValidationMainchainView;
    private final BlockSyncService blockSyncService;
    private final PeerScoringManager peerScoringManager;
    private final ChannelManager channelManager;
    private final SyncConfiguration syncConfiguration;
    private final BlockFactory blockFactory;
    private final BlockHeaderValidationRule blockHeaderValidationRule;
    private final BlockCompositeRule blockValidationRule;
    private final DifficultyRule difficultyRule;
    private final PeersInformation peerStatuses;

    private final Map<Long, MessageType> pendingMessages;
    private final Map<NodeID, Instant> failedPeers;
    private SyncState syncState;
    private NodeID selectedPeerId;
    private long lastRequestId;

    public SyncProcessor(Blockchain blockchain,
                         ConsensusValidationMainchainView consensusValidationMainchainView,
                         BlockSyncService blockSyncService,
                         PeerScoringManager peerScoringManager,
                         ChannelManager channelManager,
                         SyncConfiguration syncConfiguration,
                         BlockFactory blockFactory,
                         BlockHeaderValidationRule blockHeaderValidationRule,
                         BlockCompositeRule blockValidationRule,
                         DifficultyCalculator difficultyCalculator) {
        this.blockchain = blockchain;
        this.consensusValidationMainchainView = consensusValidationMainchainView;
        this.blockSyncService = blockSyncService;
        this.peerScoringManager = peerScoringManager;
        this.channelManager = channelManager;
        this.syncConfiguration = syncConfiguration;
        this.blockFactory = blockFactory;
        this.blockHeaderValidationRule = blockHeaderValidationRule;
        this.blockValidationRule = blockValidationRule;
        this.difficultyRule = new DifficultyRule(difficultyCalculator);
        this.pendingMessages = new LinkedHashMap<Long, MessageType>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, MessageType> eldest) {
                boolean shouldDiscard = size() > MAX_PENDING_MESSAGES;
                if (shouldDiscard) {
                    logger.trace("Pending {}@{} DISCARDED", eldest.getValue(), eldest.getKey());
                }
                return shouldDiscard;
            }
        };
        this.failedPeers = new LinkedHashMap<NodeID, Instant>(MAX_SIZE_FAILURE_RECORDS, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<NodeID, Instant> eldest) {
                return size() > MAX_SIZE_FAILURE_RECORDS;
            }
        };
        this.peerStatuses = new PeersInformation(channelManager, syncConfiguration, blockchain, failedPeers, peerScoringManager);

        setSyncState(new DecidingSyncState(this.syncConfiguration, this, peerStatuses));

    }

    public void processStatus(MessageChannel sender, Status status) {
        logger.debug("Receiving syncState from node {} block {} {}", sender.getPeerNodeID(), status.getBestBlockNumber(), HashUtil.shortHash(status.getBestBlockHash()));
        peerStatuses.registerPeer(sender.getPeerNodeID()).setStatus(status);
        syncState.newPeerStatus();
    }

    public void processSkeletonResponse(MessageChannel peer, SkeletonResponseMessage message) {
        logger.debug("Process skeleton response from node {}", peer.getPeerNodeID());
        peerStatuses.getOrRegisterPeer(peer.getPeerNodeID());

        long messageId = message.getId();
        MessageType messageType = message.getMessageType();
        if (isPending(messageId, messageType)) {
            removePendingMessage(messageId, messageType);
            syncState.newSkeleton(message.getBlockIdentifiers(), peer);
        } else {
            peerScoringManager.recordEvent(peer.getPeerNodeID(), null, EventType.UNEXPECTED_MESSAGE);
        }
    }

    public void processBlockHashResponse(MessageChannel peer, BlockHashResponseMessage message) {
        NodeID nodeID = peer.getPeerNodeID();
        logger.debug("Process block hash response from node {} hash {}", nodeID, HashUtil.shortHash(message.getHash()));
        peerStatuses.getOrRegisterPeer(nodeID);

        long messageId = message.getId();
        MessageType messageType = message.getMessageType();
        if (isPending(messageId, messageType)) {
            removePendingMessage(messageId, messageType);
            syncState.newConnectionPointData(message.getHash());
        } else {
            peerScoringManager.recordEvent(nodeID, null, EventType.UNEXPECTED_MESSAGE);
        }
    }

    public void processBlockHeadersResponse(MessageChannel peer, BlockHeadersResponseMessage message) {
        logger.debug("Process block headers response from node {}", peer.getPeerNodeID());
        peerStatuses.getOrRegisterPeer(peer.getPeerNodeID());

        long messageId = message.getId();
        MessageType messageType = message.getMessageType();
        if (isPending(messageId, messageType)) {
            removePendingMessage(messageId, messageType);
            syncState.newBlockHeaders(message.getBlockHeaders());
        } else {
            peerScoringManager.recordEvent(peer.getPeerNodeID(), null, EventType.UNEXPECTED_MESSAGE);
        }
    }

    public void processBodyResponse(MessageChannel peer, BodyResponseMessage message) {
        logger.debug("Process body response from node {}", peer.getPeerNodeID());
        peerStatuses.getOrRegisterPeer(peer.getPeerNodeID());

        long messageId = message.getId();
        MessageType messageType = message.getMessageType();
        if (isPending(messageId, messageType)) {
            removePendingMessage(messageId, messageType);
            syncState.newBody(message, peer);
        } else {
            peerScoringManager.recordEvent(peer.getPeerNodeID(), null, EventType.UNEXPECTED_MESSAGE);
        }
    }

    public void processNewBlockHash(MessageChannel peer, NewBlockHashMessage message) {
        NodeID nodeID = peer.getPeerNodeID();
        logger.debug("Process new block hash from node {} hash {}", nodeID, HashUtil.shortHash(message.getBlockHash()));
        byte[] hash = message.getBlockHash();

        if (syncState instanceof DecidingSyncState && blockSyncService.getBlockFromStoreOrBlockchain(hash) == null) {
            peerStatuses.getOrRegisterPeer(nodeID);
            sendMessage(nodeID, new BlockRequestMessage(++lastRequestId, hash));
        }
    }

    public void processBlockResponse(MessageChannel peer, BlockResponseMessage message) {
        NodeID nodeID = peer.getPeerNodeID();
        logger.debug("Process block response from node {} block {} {}", nodeID, message.getBlock().getNumber(), message.getBlock().getShortHash());
        peerStatuses.getOrRegisterPeer(nodeID);

        long messageId = message.getId();
        MessageType messageType = message.getMessageType();
        if (isPending(messageId, messageType)) {
            removePendingMessage(messageId, messageType);
            blockSyncService.processBlock(message.getBlock(), peer, false);
        } else {
            peerScoringManager.recordEvent(nodeID, null, EventType.UNEXPECTED_MESSAGE);
        }
    }

    @Override
    public boolean sendSkeletonRequest(NodeID nodeID, long height) {
        logger.debug("Send skeleton request to node {} height {}", nodeID, height);
        MessageWithId message = new SkeletonRequestMessage(++lastRequestId, height);
        return sendMessage(nodeID, message);
    }

    @Override
    public boolean sendBlockHashRequest(long height) {
        logger.debug("Send hash request to node {} height {}", selectedPeerId, height);
        BlockHashRequestMessage message = new BlockHashRequestMessage(++lastRequestId, height);
        return sendMessage(selectedPeerId, message);
    }

    @Override
    public boolean sendBlockHeadersRequest(ChunkDescriptor chunk) {
        logger.debug("Send headers request to node {}", selectedPeerId);

        BlockHeadersRequestMessage message = new BlockHeadersRequestMessage(++lastRequestId, chunk.getHash(), chunk.getCount());
        return sendMessage(selectedPeerId, message);
    }

    @Override
    public Long sendBodyRequest(@Nonnull BlockHeader header, NodeID peerId) {
        logger.debug("Send body request block {} hash {} to peer {}", header.getNumber(), HashUtil.shortHash(header.getHash().getBytes()), peerId);

        BodyRequestMessage message = new BodyRequestMessage(++lastRequestId, header.getHash().getBytes());
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
        logger.info("Start syncing with node {}", nodeID);
        byte[] bestBlockHash = peerStatuses.getPeer(selectedPeerId).getStatus().getBestBlockHash();
        setSyncState(new CheckingBestHeaderSyncState(this.syncConfiguration, this, blockHeaderValidationRule, selectedPeerId, bestBlockHash));
    }

    @Override
    public void startDownloadingBodies(List<Deque<BlockHeader>> pendingHeaders, Map<NodeID, List<BlockIdentifier>> skeletons) {
        // we keep track of best known block and we start to trust it when all headers are validated
        List<BlockIdentifier> selectedSkeleton = skeletons.get(selectedPeerId);
        final long peerBestBlockNumber = selectedSkeleton.get(selectedSkeleton.size() - 1).getNumber();

        if (peerBestBlockNumber > blockSyncService.getLastKnownBlockNumber()) {
            blockSyncService.setLastKnownBlockNumber(peerBestBlockNumber);
        }

        setSyncState(new DownloadingBodiesSyncState(syncConfiguration,
                this,
                peerStatuses,
                blockchain,
                blockFactory,
                blockSyncService,
                blockValidationRule,
                pendingHeaders,
                skeletons));
    }

    @Override
    public void startDownloadingHeaders(Map<NodeID, List<BlockIdentifier>> skeletons, long connectionPoint) {
        setSyncState(
                new DownloadingHeadersSyncState(
                        this.syncConfiguration,
                        this,
                        consensusValidationMainchainView,
                        difficultyRule,
                        blockHeaderValidationRule,
                        selectedPeerId, skeletons,
                        connectionPoint));
    }

    @Override
    public void startDownloadingSkeleton(long connectionPoint) {
        setSyncState(new DownloadingSkeletonSyncState(
                this.syncConfiguration, this, peerStatuses, selectedPeerId, connectionPoint));
    }

    @Override
    public void startFindingConnectionPoint() {
        logger.debug("Find connection point with node {}", selectedPeerId);
        long bestBlockNumber = peerStatuses.getPeer(selectedPeerId).getStatus().getBestBlockNumber();
        setSyncState(new FindingConnectionPointSyncState(
                this.syncConfiguration, this, blockchain, selectedPeerId, bestBlockNumber));
    }

    @Override
    public void stopSyncing() {
        selectedPeerId = null;
        int pendingMessagesCount = pendingMessages.size();
        pendingMessages.clear();
        logger.trace("Pending {} CLEAR", pendingMessagesCount);
        // always that a syncing process ends unexpectedly the best block number is reset
        blockSyncService.setLastKnownBlockNumber(blockchain.getBestBlock().getNumber());
        clearOldFailureEntries();
        setSyncState(new DecidingSyncState(this.syncConfiguration, this, peerStatuses));
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
        logger.info("Completed syncing phase with node {}", selectedPeerId);
        stopSyncing();
    }

    private boolean sendMessage(NodeID nodeID, MessageWithId message) {
        boolean sent = sendMessageTo(nodeID, message);
        if (sent){
            MessageType messageType = message.getResponseMessageType();
            long messageId = message.getId();
            pendingMessages.put(messageId, messageType);
            logger.trace("Pending {}@{} ADDED for {}", messageType, messageId, nodeID);
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
        pendingMessages.put(message.getId(), message.getMessageType());
    }

    @VisibleForTesting
    public void setSelectedPeer(MessageChannel peer, Status status, long height) {
        selectedPeerId = peer.getPeerNodeID();
        peerStatuses.getOrRegisterPeer(selectedPeerId).setStatus(status);
        FindingConnectionPointSyncState newState =
                new FindingConnectionPointSyncState(syncConfiguration, this, blockchain, selectedPeerId, height);
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
        return pendingMessages;
    }

    private boolean isPending(long messageId, MessageType messageType) {
        return pendingMessages.containsKey(messageId) && pendingMessages.get(messageId) == messageType;
    }

    private void removePendingMessage(long messageId, MessageType messageType) {
        pendingMessages.remove(messageId);
        logger.trace("Pending {}@{} REMOVED", messageType, messageId);
    }
}
