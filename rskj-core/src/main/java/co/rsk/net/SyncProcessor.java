package co.rsk.net;

import co.rsk.core.DifficultyCalculator;
import co.rsk.core.bc.BlockChainStatus;
import co.rsk.core.bc.ConsensusValidationMainchainView;
import co.rsk.net.messages.*;
import co.rsk.net.sync.*;
import co.rsk.scoring.EventType;
import co.rsk.validators.BlockHeaderValidationRule;
import co.rsk.validators.SyncBlockValidatorRule;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.*;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.BlockStore;
import org.ethereum.listener.EthereumListener;
import org.ethereum.validator.DifficultyRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private final BlockFactory blockFactory;
    private final BlockHeaderValidationRule blockHeaderValidationRule;
    private final SyncBlockValidatorRule blockValidationRule;
    private final DifficultyRule difficultyRule;
    private final Genesis genesis;
    private final EthereumListener ethereumListener;

    private final PeersInformation peersInformation;
    private final Map<Long, MessageType> pendingMessages;
    private final AtomicBoolean isSyncing = new AtomicBoolean();

    private volatile long initialBlockNumber;
    private volatile long highestBlockNumber;

    private SyncState syncState;
    private long lastRequestId;

    public SyncProcessor(Blockchain blockchain,
                         BlockStore blockStore,
                         ConsensusValidationMainchainView consensusValidationMainchainView,
                         BlockSyncService blockSyncService,
                         SyncConfiguration syncConfiguration,
                         BlockFactory blockFactory,
                         BlockHeaderValidationRule blockHeaderValidationRule,
                         SyncBlockValidatorRule syncBlockValidatorRule,
                         DifficultyCalculator difficultyCalculator,
                         PeersInformation peersInformation,
                         Genesis genesis,
                         EthereumListener ethereumListener) {
        this.blockchain = blockchain;
        this.blockStore = blockStore;
        this.consensusValidationMainchainView = consensusValidationMainchainView;
        this.blockSyncService = blockSyncService;
        this.syncConfiguration = syncConfiguration;
        this.blockFactory = blockFactory;
        this.blockHeaderValidationRule = blockHeaderValidationRule;
        this.blockValidationRule = syncBlockValidatorRule;
        this.difficultyRule = new DifficultyRule(difficultyCalculator);
        this.genesis = genesis;
        this.ethereumListener = ethereumListener;
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

    public void processStatus(Peer sender, Status status) {
        logger.debug("Receiving syncState from node {} block {} {}", sender.getPeerNodeID(), status.getBestBlockNumber(), HashUtil.toPrintableHash(status.getBestBlockHash()));
        peersInformation.registerPeer(sender).setStatus(status);
        syncState.newPeerStatus();
    }

    public void processSkeletonResponse(Peer peer, SkeletonResponseMessage message) {
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

    public void processBlockHashResponse(Peer peer, BlockHashResponseMessage message) {
        NodeID nodeID = peer.getPeerNodeID();
        logger.debug("Process block hash response from node {} hash {}", nodeID, HashUtil.toPrintableHash(message.getHash()));
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

    public void processBlockHeadersResponse(Peer peer, BlockHeadersResponseMessage message) {
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

    public void processBodyResponse(Peer peer, BodyResponseMessage message) {
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

    public void processNewBlockHash(Peer peer, NewBlockHashMessage message) {
        NodeID nodeID = peer.getPeerNodeID();
        logger.debug("Process new block hash from node {} hash {}", nodeID, HashUtil.toPrintableHash(message.getBlockHash()));
        byte[] hash = message.getBlockHash();

        if (syncState instanceof DecidingSyncState && blockSyncService.getBlockFromStoreOrBlockchain(hash) == null) {
            peersInformation.getOrRegisterPeer(peer);
            sendMessage(peer, new BlockRequestMessage(++lastRequestId, hash));
        }
    }

    public void processBlockResponse(Peer peer, BlockResponseMessage message) {
        NodeID nodeID = peer.getPeerNodeID();
        logger.debug("Process block response from node {} block {} {}", nodeID, message.getBlock().getNumber(), message.getBlock().getPrintableHash());
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
    public void sendSkeletonRequest(Peer peer, long height) {
        logger.debug("Send skeleton request to node {} height {}", peer.getPeerNodeID(), height);
        MessageWithId message = new SkeletonRequestMessage(++lastRequestId, height);
        sendMessage(peer, message);
    }

    @Override
    public void sendBlockHashRequest(Peer peer, long height) {
        logger.debug("Send hash request to node {} height {}", peer.getPeerNodeID(), height);
        BlockHashRequestMessage message = new BlockHashRequestMessage(++lastRequestId, height);
        sendMessage(peer, message);
    }

    @Override
    public void sendBlockHeadersRequest(Peer peer, ChunkDescriptor chunk) {
        logger.debug("Send headers request to node {}", peer.getPeerNodeID());

        BlockHeadersRequestMessage message =
                new BlockHeadersRequestMessage(++lastRequestId, chunk.getHash(), chunk.getCount());
        sendMessage(peer, message);
    }

    @Override
    public long sendBodyRequest(Peer peer, @Nonnull BlockHeader header) {
        logger.debug("Send body request block {} hash {} to peer {}", header.getNumber(),
                HashUtil.toPrintableHash(header.getHash().getBytes()), peer.getPeerNodeID());

        BodyRequestMessage message = new BodyRequestMessage(++lastRequestId, header.getHash().getBytes());
        sendMessage(peer, message);
        return message.getId();
    }

    public Set<NodeID> getKnownPeersNodeIDs() {
        return this.peersInformation.knownNodeIds();
    }

    public void onTimePassed(Duration timePassed) {
        this.syncState.tick(timePassed);
    }

    @Override
    public void startSyncing(Peer peer) {
        NodeID nodeID = peer.getPeerNodeID();
        logger.info("Start syncing with node {}", nodeID);
        byte[] bestBlockHash = peersInformation.getPeer(peer).getStatus().getBestBlockHash();
        setSyncState(new CheckingBestHeaderSyncState(
                syncConfiguration,
                this,
                blockHeaderValidationRule, peer, bestBlockHash));
    }

    @Override
    public void startDownloadingBodies(
            List<Deque<BlockHeader>> pendingHeaders, Map<Peer, List<BlockIdentifier>> skeletons, Peer peer) {
        // we keep track of best known block and we start to trust it when all headers are validated
        List<BlockIdentifier> selectedSkeleton = skeletons.get(peer);
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
    public void startDownloadingHeaders(Map<Peer, List<BlockIdentifier>> skeletons, long connectionPoint, Peer peer) {
        setSyncState(new DownloadingHeadersSyncState(
                syncConfiguration,
                this,
                consensusValidationMainchainView,
                difficultyRule,
                blockHeaderValidationRule,
                peer,
                skeletons,
                connectionPoint));
    }

    @Override
    public void startDownloadingSkeleton(long connectionPoint, Peer peer) {
        setSyncState(new DownloadingSkeletonSyncState(
                syncConfiguration,
                this,
                peersInformation,
                peer,
                connectionPoint));
    }

    @Override
    public void startFindingConnectionPoint(Peer peer) {
        NodeID peerId = peer.getPeerNodeID();
        logger.debug("Find connection point with node {}", peerId);
        long bestBlockNumber = peersInformation.getPeer(peer).getStatus().getBestBlockNumber();
        setSyncState(new FindingConnectionPointSyncState(
                syncConfiguration, this, blockStore, peer, bestBlockNumber));
    }

    @Override
    public void backwardSyncing(Peer peer) {
        NodeID peerId = peer.getPeerNodeID();
        logger.debug("Starting backwards synchronization with node {}", peerId);
        setSyncState(new DownloadingBackwardsHeadersSyncState(
                syncConfiguration,
                this,
                blockStore,
                peer
        ));
    }

    @Override
    public void backwardDownloadBodies(Block child, List<BlockHeader> toRequest, Peer peer) {
        logger.debug("Starting backwards body download with node {}", peer.getPeerNodeID());
        setSyncState(new DownloadingBackwardsBodiesSyncState(
                syncConfiguration,
                this,
                peersInformation,
                genesis,
                blockFactory,
                blockStore,
                child,
                toRequest,
                peer
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

    public long getInitialBlockNumber() {
        return initialBlockNumber;
    }

    public long getHighestBlockNumber() {
        return highestBlockNumber;
    }

    public boolean isSyncing() {
        return isSyncing.get();
    }

    @Override
    public void onLongSyncUpdate(boolean isSyncing, @Nullable Long peerBestBlockNumber) {
        boolean wasSyncing = this.isSyncing.getAndSet(isSyncing);
        if (!wasSyncing && isSyncing) {
            initialBlockNumber = blockchain.getBestBlock().getNumber();
            highestBlockNumber = Optional.ofNullable(peerBestBlockNumber).orElse(initialBlockNumber);
            ethereumListener.onLongSyncStarted();
        } else if (wasSyncing && !isSyncing) {
            ethereumListener.onLongSyncDone();
        }
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

    private void sendMessage(Peer peer, MessageWithId message) {
        MessageType messageType = message.getResponseMessageType();
        long messageId = message.getId();
        pendingMessages.put(messageId, messageType);
        logger.trace("Pending {}@{} ADDED for {}", messageType, messageId, peer.getPeerNodeID());
        peer.sendMessage(message);
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
