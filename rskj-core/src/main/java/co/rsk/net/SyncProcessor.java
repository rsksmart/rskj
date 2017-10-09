package co.rsk.net;

import co.rsk.core.bc.BlockChainStatus;
import co.rsk.net.messages.*;
import co.rsk.net.sync.*;
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
    private Map<Long, PendingBodyResponse> pendingBodyResponses = new HashMap<>();

    private SyncState syncState;
    private NodeID selectedPeerId;
    private PendingMessages pendingMessages;
    private SyncInformationImpl syncInformation;

    public SyncProcessor(Blockchain blockchain, BlockSyncService blockSyncService, SyncConfiguration syncConfiguration, BlockHeaderValidationRule blockHeaderValidationRule) {
        // TODO(mc) implement FollowBestChain
        this.blockchain = blockchain;
        this.blockSyncService = blockSyncService;
        this.syncConfiguration = syncConfiguration;
        this.peerStatuses = new PeersInformation(syncConfiguration);
        this.syncInformation = new SyncInformationImpl(blockHeaderValidationRule);
        this.syncState = new DecidingSyncState(this.syncConfiguration, this, syncInformation, peerStatuses);
        this.pendingMessages = new PendingMessages();
    }

    public void processStatus(MessageChannel sender, Status status) {
        logger.trace("Receiving syncState from node {} block {} {}", sender.getPeerNodeID(), status.getBestBlockNumber(), HashUtil.shortHash(status.getBestBlockHash()), status.getBestBlockHash());
        this.peerStatuses.getOrRegisterPeer(sender).setStatus(status);
        this.syncState.newPeerStatus();
    }

    public void processSkeletonResponse(MessageChannel sender, SkeletonResponseMessage message) {
        logger.trace("Process skeleton response from node {}", sender.getPeerNodeID());
        peerStatuses.getOrRegisterPeer(sender);

        if (!pendingMessages.isPending(message.getId(), message.getMessageType()))
            return;

        this.syncState.newSkeleton(message.getBlockIdentifiers());
    }

    public void processBlockHashResponse(MessageChannel sender, BlockHashResponseMessage message) {
        logger.trace("Process block hash response from node {} hash {}", sender.getPeerNodeID(), HashUtil.shortHash(message.getHash()));
        peerStatuses.getOrRegisterPeer(sender);

        if (!pendingMessages.isPending(message.getId(), message.getMessageType()))
            return;

        this.syncState.newConnectionPointData(message.getHash());
    }

    public void processBlockHeadersResponse(MessageChannel peer, BlockHeadersResponseMessage message) {
        logger.trace("Process block headers response from node {}", peer.getPeerNodeID());
        peerStatuses.getOrRegisterPeer(peer);

        if (!pendingMessages.isPending(message.getId(), message.getMessageType()))
            return;

        syncState.newBlockHeaders(message.getBlockHeaders());
    }

    public void processBodyResponse(MessageChannel peer, BodyResponseMessage message) {
        logger.trace("Process body response from node {}", peer.getPeerNodeID());
        peerStatuses.getOrRegisterPeer(peer);

        if (!pendingMessages.isPending(message.getId(), message.getMessageType()))
            return;

        this.syncState.newBody(message);
    }

    public void processNewBlockHash(MessageChannel sender, NewBlockHashMessage message) {
        logger.trace("Process new block hash from node {} hash {}", sender.getPeerNodeID(), HashUtil.shortHash(message.getBlockHash()));
        byte[] hash = message.getBlockHash();

        if (this.blockSyncService.getBlockFromStoreOrBlockchain(hash) != null)
            return;

        if (!syncState.isSyncing()) {
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

    public void processBlockResponse(MessageChannel sender, BlockResponseMessage message) {
        logger.trace("Process block response from node {} block {} {}", sender.getPeerNodeID(), message.getBlock().getNumber(), message.getBlock().getShortHash());
        peerStatuses.getOrRegisterPeer(sender);

        if (!pendingMessages.isPending(message.getId(), message.getMessageType()))
            return;

        blockSyncService.processBlock(sender, message.getBlock());
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

        MessageChannel peer = peerStatuses.getPeer(selectedPeerId).getMessageChannel();
        BlockHeadersRequestMessage message = new BlockHeadersRequestMessage(pendingMessages.getNextRequestId(), chunk.getHash(), chunk.getCount());
        sendMessage(peer, message);
    }

    @Override
    public void sendBodyRequest(@Nonnull BlockHeader header) {
        logger.trace("Send body request block {} hash {} to peer {}", header.getNumber(), HashUtil.shortHash(header.getHash()), selectedPeerId);

        MessageChannel peer = peerStatuses.getPeer(selectedPeerId).getMessageChannel();
        BodyRequestMessage message = new BodyRequestMessage(pendingMessages.getNextRequestId(), header.getHash());
        sendMessage(peer, message);
        pendingBodyResponses.put(message.getId(), new PendingBodyResponse(peer.getPeerNodeID(), header));
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
        this.pendingBodyResponses.clear();
        this.pendingMessages.clear();
        setSyncState(new DecidingSyncState(this.syncConfiguration, this, syncInformation, peerStatuses));
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
    int getNoPeers() {
        return this.peerStatuses.count();
    }

    @VisibleForTesting
    int getNoAdvancedPeers() {
        BlockChainStatus chainStatus = this.blockchain.getStatus();

        if (chainStatus == null)
            return this.peerStatuses.count();

        return this.peerStatuses.countIf(s -> chainStatus.hasLowerDifficulty(s.getStatus()));
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
    public void expectBodyResponseFor(long requestId, NodeID nodeID, BlockHeader header) {
        pendingBodyResponses.put(requestId, new PendingBodyResponse(nodeID, header));
    }

    @VisibleForTesting
    public Map<Long, MessageType> getExpectedResponses() {
        return this.pendingMessages.getExpectedMessages();
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
        public boolean hasLowerDifficulty(MessageChannel peer) {
            Status status = getPeerStatus(peer.getPeerNodeID()).getStatus();
            return blockchain.getStatus().hasLowerDifficulty(status);
        }

        @Override
        public boolean isExpectedBody(long requestId) {
            PendingBodyResponse expected = pendingBodyResponses.get(requestId);
            return expected != null && selectedPeerId.equals(expected.nodeID);
        }

        @Override
        public void saveBlock(BodyResponseMessage message) {
            // we know it exists because it was called from a SyncEvent
            BlockHeader header = pendingBodyResponses.get(message.getId()).header;
            blockSyncService.processBlock(getSelectedPeerChannel(), Block.fromValidData(header, message.getTransactions(), message.getUncles()));
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

            if (!blockHeaderValidationRule.isValid(header))
                return false;

            return true;
        }

        public MessageChannel getSelectedPeerChannel() {
            return peerStatuses.getPeer(selectedPeerId).getMessageChannel();
        }

        private SyncPeerStatus getPeerStatus(NodeID nodeID) {
            return peerStatuses.getPeer(nodeID);
        }
    }

    private static class PendingBodyResponse {
        private NodeID nodeID;
        private BlockHeader header;

        PendingBodyResponse(NodeID nodeID, BlockHeader header) {
            this.nodeID = nodeID;
            this.header = header;
        }
    }
}
