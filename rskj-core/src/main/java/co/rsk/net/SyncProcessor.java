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

import co.rsk.core.DifficultyCalculator;
import co.rsk.core.bc.BlockChainStatus;
import co.rsk.core.bc.ConsensusValidationMainchainView;
import co.rsk.net.messages.*;
import co.rsk.net.sync.*;
import co.rsk.scoring.EventType;
import co.rsk.validators.BlockHeaderValidationRule;
import co.rsk.validators.SyncBlockValidatorRule;
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.ArrayUtils;
import org.ethereum.core.*;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.BlockStore;
import org.ethereum.listener.EthereumListener;
import org.ethereum.validator.DifficultyRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.InetAddress;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class' methods are executed one at a time because NodeMessageHandler is synchronized.
 */
public class SyncProcessor implements SyncEventsHandler {
    private static final int MAX_PENDING_MESSAGES = 100_000;

    private static final int ROUNDTRIP_TIME_TO_WARN_PERIOD = 10; // seconds
    private static final int ROUNDTRIP_TIME_TO_WARN_LIMIT = 4; // seconds

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
    private final Map<Long, MessageInfo> pendingMessages;
    private final AtomicBoolean isSyncing = new AtomicBoolean();
    private final SnapshotProcessor snapshotProcessor;

    private volatile long initialBlockNumber;
    private volatile long highestBlockNumber;

    private volatile long lastDelayWarn = System.currentTimeMillis();

    private SyncState syncState;
    private long lastRequestId;

    // TODO(snap-poc) tmp field until we can spot from DB (stateRoot?) if further Snap sync is required
    private boolean snapSyncFinished;

    @VisibleForTesting
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

        this(blockchain, blockStore, consensusValidationMainchainView, blockSyncService, syncConfiguration, blockFactory, blockHeaderValidationRule, syncBlockValidatorRule, difficultyCalculator, peersInformation, genesis, ethereumListener, null);
    }

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
                         EthereumListener ethereumListener,
                         SnapshotProcessor snapshotProcessor) {
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
        this.pendingMessages = new LinkedHashMap<Long, MessageInfo>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, MessageInfo> eldest) {
                boolean shouldDiscard = size() > MAX_PENDING_MESSAGES;
                if (shouldDiscard) {
                    logger.trace("Pending {}@{} DISCARDED", eldest.getValue(), eldest.getKey());
                }
                return shouldDiscard;
            }
        };

        this.peersInformation = peersInformation;
        this.snapshotProcessor = snapshotProcessor;
        setSyncState(new PeerAndModeDecidingSyncState(syncConfiguration, this, peersInformation, blockStore));
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
            notifyUnexpectedMessageToPeerScoring(peer, "skeleton");
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
            notifyUnexpectedMessageToPeerScoring(peer, "block hash");
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
            notifyUnexpectedMessageToPeerScoring(peer, "block headers");
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
            notifyUnexpectedMessageToPeerScoring(peer, "body");
        }
    }

    public void processNewBlockHash(Peer peer, NewBlockHashMessage message) {
        NodeID nodeID = peer.getPeerNodeID();
        logger.debug("Process new block hash from node {} hash {}", nodeID, HashUtil.toPrintableHash(message.getBlockHash()));
        byte[] hash = message.getBlockHash();

        if (syncState instanceof PeerAndModeDecidingSyncState && blockSyncService.getBlockFromStoreOrBlockchain(hash) == null) {
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
            notifyUnexpectedMessageToPeerScoring(peer, "block");
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
    public void startBlockForwardSyncing(Peer peer) {
        NodeID nodeID = peer.getPeerNodeID();
        logger.info("Start syncing with node {}", nodeID);
        byte[] bestBlockHash = peersInformation.getPeer(peer).getStatus().getBestBlockHash();
        setSyncState(new CheckingBestHeaderSyncState(
                syncConfiguration,
                this,
                blockHeaderValidationRule, peer, bestBlockHash));
    }

    @Override
    public void startSnapSync(List<Peer> peers) {
        logger.info("Start Snap syncing with #{} nodes", peers.size());
        setSyncState(new SnapSyncState(this, snapshotProcessor, syncConfiguration, peers));
    }

    @Override
    public void snapSyncFinished() {
        this.snapSyncFinished = true;
    }

    @Override
    public boolean isSnapSyncFinished() {
        return snapSyncFinished;
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

        setSyncState(new PeerAndModeDecidingSyncState(syncConfiguration,
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
        boolean startedSyncing = !wasSyncing && isSyncing;
        boolean finishedSyncing = wasSyncing && !isSyncing;
        if (startedSyncing) {
            initialBlockNumber = blockchain.getBestBlock().getNumber();
            highestBlockNumber = Optional.ofNullable(peerBestBlockNumber).orElse(initialBlockNumber);
            ethereumListener.onLongSyncStarted();
        } else if (finishedSyncing) {
            ethereumListener.onLongSyncDone();
        }
    }

    @Override
    public void onErrorSyncing(Peer peer, EventType eventType, String message, Object... arguments) {
        peersInformation.processSyncingError(peer, eventType, message, arguments);
        stopSyncing();
    }

    @Override
    public void onSyncIssue(Peer peer, String message, Object... messageArgs) {
        logSyncIssue(peer, message, messageArgs);
        stopSyncing();
    }

    private void sendMessage(Peer peer, MessageWithId message) {
        MessageType messageType = message.getResponseMessageType();
        long messageId = message.getId();
        pendingMessages.put(messageId, new MessageInfo(messageType));
        logger.trace("Pending {}@{} ADDED for {}", messageType, messageId, peer.getPeerNodeID());
        peer.sendMessage(message);
    }

    private void setSyncState(SyncState syncState) {
        this.syncState = syncState;
        this.syncState.onEnter();
    }

    private void notifyUnexpectedMessageToPeerScoring(Peer peer, String messageType) {
        String message = "Unexpected " + messageType + " response received on {}";
        peersInformation.reportEventToPeerScoring(peer, EventType.UNEXPECTED_MESSAGE,
                message, this.getClass());
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
        pendingMessages.put(message.getId(), new MessageInfo(message.getMessageType()));
    }

    @VisibleForTesting
    public SyncState getSyncState() {
        return this.syncState;
    }

    @VisibleForTesting
    public Map<Long, MessageInfo> getExpectedResponses() {
        return pendingMessages;
    }

    private boolean isPending(long messageId, MessageType messageType) {
        MessageInfo messageInfo = pendingMessages.get(messageId);
        return messageInfo != null && messageInfo.type == messageType;
    }

    private void removePendingMessage(long messageId, MessageType messageType) {
        MessageInfo messageInfo = pendingMessages.remove(messageId);
        long taskWaitTimeInSeconds = messageInfo.getLifeTimeInSeconds();
        logger.trace("Pending {}@{} REMOVED after [{}]s", messageType, messageId, taskWaitTimeInSeconds);
        logDelays(messageId, messageType, taskWaitTimeInSeconds);
    }

    private void logDelays(long messageId, MessageType messageType, long taskWaitTimeInSeconds) {
        if (taskWaitTimeInSeconds < ROUNDTRIP_TIME_TO_WARN_LIMIT) {
            return;
        }

        long now = System.currentTimeMillis();
        Duration timeDelayWarn = Duration.ofMillis(now - lastDelayWarn);
        if (timeDelayWarn.getSeconds() > ROUNDTRIP_TIME_TO_WARN_PERIOD) {
            logger.warn("{}-{} round-trip took too much (either slow peer response or task waited too much on the queue): [{}]s", messageType, messageId, taskWaitTimeInSeconds);
            lastDelayWarn = now;
        }
    }

    private void logSyncIssue(Peer peer, String message, Object[] messageArgs) {
        String completeMessage = "Node [{}] " + message;
        String nodeInfo = peer.getPeerNodeID() + " - " + Optional.ofNullable(peer.getAddress()).map(InetAddress::getHostAddress).orElse("unknown");
        Object[] completeArguments = ArrayUtils.add(messageArgs, 0, nodeInfo);
        logger.trace(completeMessage, completeArguments);
    }

    private static class MessageInfo {
        private final MessageType type;
        private final long creationTime;

        public MessageInfo(MessageType type) {
            this.type = type;
            this.creationTime = System.currentTimeMillis();
        }

        public long getLifeTimeInSeconds() {
            return (System.currentTimeMillis() - creationTime) / 1000;
        }
    }
}
