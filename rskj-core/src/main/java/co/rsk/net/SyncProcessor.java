package co.rsk.net;

import co.rsk.core.bc.BlockChainStatus;
import co.rsk.net.messages.*;
import co.rsk.net.sync.*;
import co.rsk.scoring.EventType;
import co.rsk.scoring.PeerScoringManager;
import co.rsk.validators.BlockHeaderValidationRule;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;
import org.ethereum.core.Blockchain;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.ethereum.validator.DependentBlockHeaderRule;
import org.ethereum.validator.DifficultyRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.*;

/**
 * Created by ajlopez on 29/08/2017.
 * This class' methods are executed one at a time because NodeMessageHandler is synchronized.
 */
public class SyncProcessor implements SyncEventsHandler {
    private static final Logger logger = LoggerFactory.getLogger("syncprocessor");

    private final SyncConfiguration syncConfiguration;

    private Blockchain blockchain;
    private BlockSyncService blockSyncService;
    private PeersInformation peerStatuses;

    private SyncState syncState;
    private NodeID selectedPeerId;
    private PendingMessages pendingMessages;
    private SyncInformationImpl syncInformation;
    private PeerScoringManager peerScoringManager;

    public SyncProcessor(Blockchain blockchain, BlockSyncService blockSyncService, PeerScoringManager peerScoringManager, SyncConfiguration syncConfiguration, BlockHeaderValidationRule blockHeaderValidationRule) {
        // TODO(mc) implement FollowBestChain
        this.blockchain = blockchain;
        this.blockSyncService = blockSyncService;
        this.syncConfiguration = syncConfiguration;
        this.syncInformation = new SyncInformationImpl(blockHeaderValidationRule);
        this.peerStatuses = new PeersInformation(syncConfiguration, syncInformation);
        this.pendingMessages = new PendingMessages();
        this.peerScoringManager = peerScoringManager;
        setSyncState(new DecidingSyncState(this.syncConfiguration, this, syncInformation, peerStatuses));
    }

    public void processStatus(MessageChannel sender, Status status) {
        logger.trace("Receiving syncState from node {} block {} {}", sender.getPeerNodeID(), status.getBestBlockNumber(), HashUtil.shortHash(status.getBestBlockHash()), status.getBestBlockHash());
        // we keep track of best known block
        final long peerBestBlockNumber = status.getBestBlockNumber();

        if (peerBestBlockNumber > blockSyncService.getLastKnownBlockNumber())
            blockSyncService.setLastKnownBlockNumber(peerBestBlockNumber);

        this.peerStatuses.getOrRegisterPeer(sender).setStatus(status);
        this.syncState.newPeerStatus();
    }

    public void processSkeletonResponse(MessageChannel peer, SkeletonResponseMessage message) {
        logger.trace("Process skeleton response from node {}", peer.getPeerNodeID());
        peerStatuses.getOrRegisterPeer(peer);

        if (!pendingMessages.isPending(message))
            peerScoringManager.recordEvent(peer.getPeerNodeID(), null, EventType.UNEXPECTED_MESSAGE);
            return;
        }

        this.syncState.newSkeleton(message.getBlockIdentifiers());
    }

    public void processBlockHashResponse(MessageChannel peer, BlockHashResponseMessage message) {
        logger.trace("Process block hash response from node {} hash {}", peer.getPeerNodeID(), HashUtil.shortHash(message.getHash()));
        peerStatuses.getOrRegisterPeer(peer);

        if (!pendingMessages.isPending(message))
            peerScoringManager.recordEvent(peer.getPeerNodeID(), null, EventType.UNEXPECTED_MESSAGE);
            return;
        }

        this.syncState.newConnectionPointData(message.getHash());
    }

    public void processBlockHeadersResponse(MessageChannel peer, BlockHeadersResponseMessage message) {
        logger.trace("Process block headers response from node {}", peer.getPeerNodeID());
        peerStatuses.getOrRegisterPeer(peer);

        if (!pendingMessages.isPending(message))
            peerScoringManager.recordEvent(peer.getPeerNodeID(), null, EventType.UNEXPECTED_MESSAGE);
            return;
        }

        syncState.newBlockHeaders(message.getBlockHeaders());
    }

    public void processBodyResponse(MessageChannel peer, BodyResponseMessage message) {
        logger.trace("Process body response from node {}", peer.getPeerNodeID());
        peerStatuses.getOrRegisterPeer(peer);

        if (!pendingMessages.isPending(message))
            peerScoringManager.recordEvent(peer.getPeerNodeID(), null, EventType.UNEXPECTED_MESSAGE);
            return;
        }

        this.syncState.newBody(message);
    }

    public void processNewBlockHash(MessageChannel sender, NewBlockHashMessage message) {
        logger.trace("Process new block hash from node {} hash {}", sender.getPeerNodeID(), HashUtil.shortHash(message.getBlockHash()));
        byte[] hash = message.getBlockHash();

        if (!syncState.isSyncing() && blockSyncService.getBlockFromStoreOrBlockchain(hash) == null) {
            sendMessage(sender, new BlockRequestMessage(pendingMessages.getNextRequestId(), hash));
            peerStatuses.getOrRegisterPeer(sender);
        }
    }

    @Override
    public void sendSkeletonRequest(long height) {
        logger.trace("Send skeleton request to node {} height {}", selectedPeerId, height);
        MessageWithId message = new SkeletonRequestMessage(pendingMessages.getNextRequestId(), height);
        MessageChannel channel = peerStatuses.getPeer(selectedPeerId).getMessageChannel();
        sendMessage(channel, message);
    }

    @Override
    public void sendBlockHashRequest(long height) {
        logger.trace("Send hash request to node {} height {}", selectedPeerId, height);
        BlockHashRequestMessage message = new BlockHashRequestMessage(pendingMessages.getNextRequestId(), height);
        MessageChannel channel = peerStatuses.getPeer(selectedPeerId).getMessageChannel();
        sendMessage(channel, message);
    }

    public void processBlockResponse(MessageChannel peer, BlockResponseMessage message) {
        logger.trace("Process block response from node {} block {} {}", peer.getPeerNodeID(), message.getBlock().getNumber(), message.getBlock().getShortHash());
        peerStatuses.getOrRegisterPeer(peer);

        if (!pendingMessages.isPending(message))
            peerScoringManager.recordEvent(peer.getPeerNodeID(), null, EventType.UNEXPECTED_MESSAGE);
            return;
        }

        blockSyncService.processBlock(peer, message.getBlock());
    }

    public Set<NodeID> getKnownPeersNodeIDs() {
        return this.peerStatuses.knownNodeIds();
    }

    public void onTimePassed(Duration timePassed) {
        this.syncState.tick(timePassed);
    }

    @Override
    public void sendBlockHeadersRequest(ChunkDescriptor chunk) {
        logger.trace("Send headers request to node {}", selectedPeerId);

        MessageChannel channel = peerStatuses.getPeer(selectedPeerId).getMessageChannel();
        BlockHeadersRequestMessage message = new BlockHeadersRequestMessage(pendingMessages.getNextRequestId(), chunk.getHash(), chunk.getCount());
        sendMessage(channel, message);
    }

    @Override
    public long sendBodyRequest(@Nonnull BlockHeader header) {
        logger.trace("Send body request block {} hash {} to peer {}", header.getNumber(), HashUtil.shortHash(header.getHash()), selectedPeerId);

        MessageChannel channel = peerStatuses.getPeer(selectedPeerId).getMessageChannel();
        BodyRequestMessage message = new BodyRequestMessage(pendingMessages.getNextRequestId(), header.getHash());
        sendMessage(channel, message);
        return message.getId();
    }

    @Override
    public void startSyncing(MessageChannel peer) {
        logger.trace("Find connection point with node {}", peer.getPeerNodeID());
        selectedPeerId = peer.getPeerNodeID();
        long bestBlockNumber = syncInformation.getPeerStatus(selectedPeerId).getStatus().getBestBlockNumber();
        setSyncState(new FindingConnectionPointSyncState(this.syncConfiguration, this, syncInformation, bestBlockNumber));
    }

    @Override
    public void startDownloadingBodies(Queue<BlockHeader> pendingHeaders) {
        setSyncState(new DownloadingBodiesSyncState(this.syncConfiguration, this, syncInformation, pendingHeaders));
    }

    @Override
    public void startDownloadingHeaders(List<BlockIdentifier> skeleton, long connectionPoint) {
        setSyncState(new DownloadingHeadersSyncState(this.syncConfiguration, this, syncInformation, skeleton, connectionPoint));
    }

    @Override
    public void startDownloadingSkeleton(long connectionPoint) {
        setSyncState(new DownloadingSkeletonSyncState(this.syncConfiguration, this, syncInformation, connectionPoint));
    }

    @Override
    public void stopSyncing() {
        selectedPeerId = null;
        this.pendingMessages.clear();
        setSyncState(new DecidingSyncState(this.syncConfiguration, this, syncInformation, peerStatuses));
    }

    @Override
    public void onErrorSyncing(String message, EventType eventType, Object... arguments) {
        peerScoringManager.recordEvent(this.selectedPeerId, null, eventType);
        logger.trace(message, arguments);
        stopSyncing();
    }

    @Override
    public void onCompletedSyncing() {
        logger.trace("Completed syncing phase with node {}", selectedPeerId);
        stopSyncing();
    }

    private void sendMessage(MessageChannel channel, MessageWithId message) {
        pendingMessages.register(message);
        channel.sendMessage(message);
    }

    private void setSyncState(SyncState syncState) {
        this.syncState = syncState;
        this.syncState.onEnter();
    }

    @VisibleForTesting
    int getPeersCount() {
        return this.peerStatuses.count();
    }

    @VisibleForTesting
    int getNoAdvancedPeers() {
        BlockChainStatus chainStatus = this.blockchain.getStatus();

        if (chainStatus == null)
            return this.peerStatuses.count();

        return this.peerStatuses.countIf(s -> chainStatus.hasLowerTotalDifficultyThan(s.getStatus()));
    }

    @VisibleForTesting
    public void registerExpectedMessage(MessageWithId message) {
        pendingMessages.registerExpectedMessage(message);
    }

    @VisibleForTesting
    public void setSelectedPeer(MessageChannel peer, Status status, long height) {
        peerStatuses.getOrRegisterPeer(peer).setStatus(status);
        selectedPeerId = peer.getPeerNodeID();
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

    @VisibleForTesting
    public boolean hasGoodReputation(NodeID nodeID) {
        return this.peerScoringManager.hasGoodReputation(nodeID);
    }

    private class SyncInformationImpl implements SyncInformation {
        private DependentBlockHeaderRule blockParentValidationRule = new DifficultyRule();
        private BlockHeaderValidationRule blockHeaderValidationRule;

        public SyncInformationImpl(BlockHeaderValidationRule blockHeaderValidationRule) {
            this.blockHeaderValidationRule = blockHeaderValidationRule;
        }

        @Override
        public boolean isKnownBlock(byte[] hash) {
            return blockchain.getBlockByHash(hash) != null;
        }

        @Override
        public boolean hasLowerDifficulty(NodeID nodeID) {
            Status status = getPeerStatus(nodeID).getStatus();
            return blockchain.getStatus().hasLowerTotalDifficultyThan(status);
        }

        @Override
        public BlockProcessResult processBlock(Block block) {
            return blockSyncService.processBlock(getSelectedPeerChannel(), block);
        }

        @Override
        public boolean blockHeaderIsValid(@Nonnull BlockHeader header, @Nonnull BlockHeader parentHeader) {
            if (isKnownBlock(header.getHash()))
                return false;

            if (!ByteUtil.fastEquals(parentHeader.getHash(), header.getParentHash()))
                return false;

            if (header.getNumber() != parentHeader.getNumber() + 1)
                return false;

            if (!blockParentValidationRule.validate(header, parentHeader))
                return false;

            return blockHeaderValidationRule.isValid(header);
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

        public MessageChannel getSelectedPeerChannel() {
            return peerStatuses.getPeer(selectedPeerId).getMessageChannel();
        }

        private SyncPeerStatus getPeerStatus(NodeID nodeID) {
            return peerStatuses.getPeer(nodeID);
        }
    }
}
