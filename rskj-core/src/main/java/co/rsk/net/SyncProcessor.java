package co.rsk.net;

import co.rsk.core.DifficultyCalculator;
import co.rsk.core.bc.BlockChainStatus;
import co.rsk.core.bc.ConsensusValidationMainchainView;
import co.rsk.net.messages.*;
import co.rsk.net.sync.*;
import co.rsk.scoring.EventType;
import co.rsk.validators.BlockCompositeRule;
import co.rsk.validators.BlockHeaderValidationRule;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.*;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.BlockStore;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.validator.DifficultyRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.*;

/**
 * This class' methods are executed one at a time because NodeMessageHandler is synchronized.
 */
public class SyncProcessor implements SyncEventsHandler {
    private static final int MAX_PENDING_MESSAGES = 100_000;
    private static final Logger logger = LoggerFactory.getLogger("syncprocessor");

    private final SyncConfiguration syncConfiguration;
    private final Blockchain blockchain;
    private final BlockStore blockStore;
    private final ConsensusValidationMainchainView consensusValidationMainchainView;
    private final BlockSyncService blockSyncService;
    private final ChannelManager channelManager;
    private final BlockFactory blockFactory;
    private final BlockHeaderValidationRule blockHeaderValidationRule;
    private final BlockCompositeRule blockValidationRule;
    private final DifficultyRule difficultyRule;
    private final Genesis genesis;

    private final PeersInformation peersInformation;
    private final Map<Long, MessageType> pendingMessages;

    private SyncState syncState;
    private long lastRequestId;

    public SyncProcessor(Blockchain blockchain,
                         BlockStore blockStore,
                         ConsensusValidationMainchainView consensusValidationMainchainView,
                         BlockSyncService blockSyncService,
                         ChannelManager channelManager,
                         SyncConfiguration syncConfiguration,
                         BlockFactory blockFactory,
                         BlockHeaderValidationRule blockHeaderValidationRule,
                         BlockCompositeRule blockValidationRule,
                         DifficultyCalculator difficultyCalculator,
                         PeersInformation peersInformation,
                         Genesis genesis) {
        this.blockchain = blockchain;
        this.blockStore = blockStore;
        this.consensusValidationMainchainView = consensusValidationMainchainView;
        this.blockSyncService = blockSyncService;
        this.channelManager = channelManager;
        this.syncConfiguration = syncConfiguration;
        this.blockFactory = blockFactory;
        this.blockHeaderValidationRule = blockHeaderValidationRule;
        this.blockValidationRule = blockValidationRule;
        this.difficultyRule = new DifficultyRule(difficultyCalculator);
        this.genesis = genesis;
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

        this.peersInformation = peersInformation;
        setSyncState(new DecidingSyncState(syncConfiguration, this, peersInformation, blockStore));
    }

    public void processStatus(MessageChannel sender, Status status) {
        logger.debug("Receiving syncState from node {} block {} {}", sender.getPeerNodeID(), status.getBestBlockNumber(), HashUtil.shortHash(status.getBestBlockHash()));
        peersInformation.registerPeer(sender.getPeerNodeID()).setStatus(status);
        syncState.newPeerStatus();
    }

    public void processSkeletonResponse(MessageChannel peer, SkeletonResponseMessage message) {
        logger.debug("Process skeleton response from node {}", peer.getPeerNodeID());
        peersInformation.getOrRegisterPeer(peer);

        long messageId = message.getId();
        MessageType messageType = message.getMessageType();
        if (isPending(messageId, messageType)) {
            removePendingMessage(messageId, messageType);
            syncState.newSkeleton(message.getBlockIdentifiers(), peer);
        } else {
            peersInformation.reportEvent(peer.getPeerNodeID(), EventType.UNEXPECTED_MESSAGE);
        }
    }

    public void processBlockHashResponse(MessageChannel peer, BlockHashResponseMessage message) {
        NodeID nodeID = peer.getPeerNodeID();
        logger.debug("Process block hash response from node {} hash {}", nodeID, HashUtil.shortHash(message.getHash()));
        peersInformation.getOrRegisterPeer(peer);

        long messageId = message.getId();
        MessageType messageType = message.getMessageType();
        if (isPending(messageId, messageType)) {
            removePendingMessage(messageId, messageType);
            syncState.newConnectionPointData(message.getHash());
        } else {
            peersInformation.reportEvent(peer.getPeerNodeID(), EventType.UNEXPECTED_MESSAGE);
        }
    }

    public void processBlockHeadersResponse(MessageChannel peer, BlockHeadersResponseMessage message) {
        logger.debug("Process block headers response from node {}", peer.getPeerNodeID());
        peersInformation.getOrRegisterPeer(peer);

        long messageId = message.getId();
        MessageType messageType = message.getMessageType();
        if (isPending(messageId, messageType)) {
            removePendingMessage(messageId, messageType);
            syncState.newBlockHeaders(message.getBlockHeaders());
        } else {
            peersInformation.reportEvent(peer.getPeerNodeID(), EventType.UNEXPECTED_MESSAGE);
        }
    }

    public void processBodyResponse(MessageChannel peer, BodyResponseMessage message) {
        logger.debug("Process body response from node {}", peer.getPeerNodeID());
        peersInformation.getOrRegisterPeer(peer);

        long messageId = message.getId();
        MessageType messageType = message.getMessageType();
        if (isPending(messageId, messageType)) {
            removePendingMessage(messageId, messageType);
            syncState.newBody(message, peer);
        } else {
            peersInformation.reportEvent(peer.getPeerNodeID(), EventType.UNEXPECTED_MESSAGE);
        }
    }

    public void processNewBlockHash(MessageChannel peer, NewBlockHashMessage message) {
        NodeID nodeID = peer.getPeerNodeID();
        logger.debug("Process new block hash from node {} hash {}", nodeID, HashUtil.shortHash(message.getBlockHash()));
        byte[] hash = message.getBlockHash();

        if (syncState instanceof DecidingSyncState && blockSyncService.getBlockFromStoreOrBlockchain(hash) == null) {
            peersInformation.getOrRegisterPeer(peer);
            sendMessage(nodeID, new BlockRequestMessage(++lastRequestId, hash));
        }
    }

    public void processBlockResponse(MessageChannel peer, BlockResponseMessage message) {
        NodeID nodeID = peer.getPeerNodeID();
        logger.debug("Process block response from node {} block {} {}", nodeID, message.getBlock().getNumber(), message.getBlock().getShortHash());
        peersInformation.getOrRegisterPeer(peer);

        long messageId = message.getId();
        MessageType messageType = message.getMessageType();
        if (isPending(messageId, messageType)) {
            removePendingMessage(messageId, messageType);
            blockSyncService.processBlock(message.getBlock(), peer, false);
        } else {
            peersInformation.reportEvent(peer.getPeerNodeID(), EventType.UNEXPECTED_MESSAGE);
        }
    }

    @Override
    public boolean sendSkeletonRequest(NodeID nodeID, long height) {
        logger.debug("Send skeleton request to node {} height {}", nodeID, height);
        MessageWithId message = new SkeletonRequestMessage(++lastRequestId, height);
        return sendMessage(nodeID, message);
    }

    @Override
    public boolean sendBlockHashRequest(long height, NodeID peerId) {
        logger.debug("Send hash request to node {} height {}", peerId, height);
        BlockHashRequestMessage message = new BlockHashRequestMessage(++lastRequestId, height);
        return sendMessage(peerId, message);
    }

    @Override
    public boolean sendBlockHeadersRequest(ChunkDescriptor chunk, NodeID peerId) {
        logger.debug("Send headers request to node {}", peerId);

        BlockHeadersRequestMessage message = new BlockHeadersRequestMessage(++lastRequestId, chunk.getHash(), chunk.getCount());
        return sendMessage(peerId, message);
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
        return this.peersInformation.knownNodeIds();
    }

    public void onTimePassed(Duration timePassed) {
        this.syncState.tick(timePassed);
    }

    @Override
    public void startSyncing(NodeID peerId) {
        logger.info("Start syncing with node {}", peerId);
        byte[] bestBlockHash = peersInformation.getPeer(peerId).getStatus().getBestBlockHash();
        setSyncState(new CheckingBestHeaderSyncState(syncConfiguration,
                this, blockHeaderValidationRule, peerId, bestBlockHash));
    }

    @Override
    public void startDownloadingBodies(List<Deque<BlockHeader>> pendingHeaders, Map<NodeID, List<BlockIdentifier>> skeletons, NodeID peerId) {
        // we keep track of best known block and we start to trust it when all headers are validated
        List<BlockIdentifier> selectedSkeleton = skeletons.get(peerId);
        final long peerBestBlockNumber = selectedSkeleton.get(selectedSkeleton.size() - 1).getNumber();

        if (peerBestBlockNumber > blockSyncService.getLastKnownBlockNumber()) {
            blockSyncService.setLastKnownBlockNumber(peerBestBlockNumber);
        }

        setSyncState(new DownloadingBodiesSyncState(syncConfiguration,
                this,
                peersInformation,
                blockchain,
                blockFactory,
                blockSyncService,
                blockValidationRule,
                pendingHeaders,
                skeletons));
    }

    @Override
    public void startDownloadingHeaders(Map<NodeID, List<BlockIdentifier>> skeletons, long connectionPoint, NodeID peerId) {
        setSyncState(new DownloadingHeadersSyncState(
                syncConfiguration,
                this,
                consensusValidationMainchainView,
                difficultyRule,
                blockHeaderValidationRule,
                peerId,
                skeletons,
                connectionPoint));
    }

    @Override
    public void startDownloadingSkeleton(long connectionPoint, NodeID peerId) {
        setSyncState(new DownloadingSkeletonSyncState(
                syncConfiguration,
                this,
                peersInformation,
                peerId,
                connectionPoint));
    }

    @Override
    public void startFindingConnectionPoint(NodeID peerId) {
        logger.debug("Find connection point with node {}", peerId);
        long bestBlockNumber = peersInformation.getPeer(peerId).getStatus().getBestBlockNumber();
        setSyncState(new FindingConnectionPointSyncState(
                syncConfiguration, this, blockStore, peerId, bestBlockNumber));
    }

    @Override
    public void backwardSyncing(NodeID peerId) {
        logger.debug("Starting backwards synchronization with node {}", peerId);
        setSyncState(new DownloadingBackwardsHeadersSyncState(
                syncConfiguration,
                this,
                blockStore,
                peerId
        ));
    }

    @Override
    public void backwardDownloadBodies(NodeID peerId, Block child, List<BlockHeader> toRequest) {
        logger.debug("Starting backwards body download with node {}", peerId);
        setSyncState(new DownloadingBackwardsBodiesSyncState(
                syncConfiguration,
                this,
                peersInformation,
                genesis,
                blockFactory,
                blockStore,
                child,
                toRequest,
                peerId
        ));
    }

    @Override
    public void stopSyncing() {
        int pendingMessagesCount = pendingMessages.size();
        pendingMessages.clear();
        logger.trace("Pending {} CLEAR", pendingMessagesCount);
        // always that a syncing process ends unexpectedly the best block number is reset
        blockSyncService.setLastKnownBlockNumber(blockchain.getBestBlock().getNumber());
        peersInformation.clearOldFailedPeers();
        setSyncState(new DecidingSyncState(syncConfiguration,
                this,
                peersInformation,
                blockStore));
    }

    @Override
    public void onErrorSyncing(NodeID peerId, String message, EventType eventType, Object... arguments) {
        peersInformation.reportErrorEvent(peerId, message, eventType, arguments);
        stopSyncing();
    }

    @Override
    public void onSyncIssue(String message, Object... arguments) {
        logger.trace(message, arguments);
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

    @VisibleForTesting
    int getPeersCount() {
        return this.peersInformation.count();
    }

    @VisibleForTesting
    int getNoAdvancedPeers() {
        BlockChainStatus chainStatus = this.blockchain.getStatus();

        if (chainStatus == null) {
            return this.peersInformation.count();
        }

        return this.peersInformation.countIf(s -> chainStatus.hasLowerTotalDifficultyThan(s.getStatus()));
    }

    @VisibleForTesting
    public void registerExpectedMessage(MessageWithId message) {
        pendingMessages.put(message.getId(), message.getMessageType());
    }

    @VisibleForTesting
    public SyncState getSyncState() {
        return this.syncState;
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
