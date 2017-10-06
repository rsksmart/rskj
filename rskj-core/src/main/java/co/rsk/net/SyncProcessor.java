package co.rsk.net;

import co.rsk.core.bc.BlockChainStatus;
import co.rsk.net.messages.*;
import co.rsk.net.sync.*;
import co.rsk.validators.BlockDifficultyRule;
import co.rsk.validators.BlockParentDependantValidationRule;
import co.rsk.validators.BlockValidationRule;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;
import org.ethereum.core.Blockchain;
import org.ethereum.crypto.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;

/**
 * Created by ajlopez on 29/08/2017.
 * This class' methods are executed one at a time because NodeMessageHandler is synchronized.
 */
public class SyncProcessor implements SyncEventsHandler {
    private static final Logger logger = LoggerFactory.getLogger("syncprocessor");

    private BlockValidationRule blockValidationRule;
    private static BlockParentDependantValidationRule blockParentValidationRule = new BlockDifficultyRule();
    private final SyncConfiguration syncConfiguration;

    private Blockchain blockchain;
    private BlockSyncService blockSyncService;
    private PeersInformation peerStatuses;
    private Map<Long, PendingBodyResponse> pendingBodyResponses = new HashMap<>();
    private Queue<BlockHeader> pendingHeaders = new ArrayDeque<>();

    private SyncState syncState;
    private SkeletonDownloadHelper skeletonDownloadHelper;
    private ExpectedMessages expectedMessages;
    private SyncInformation syncInformation;
    private ConnectionPointFinder connectionPointFinder;

    public SyncProcessor(Blockchain blockchain, BlockSyncService blockSyncService, SyncConfiguration syncConfiguration, BlockValidationRule blockValidationRule) {
        // TODO(mc) implement FollowBestChain
        this.blockchain = blockchain;
        this.blockSyncService = blockSyncService;
        this.syncConfiguration = syncConfiguration;
        this.blockValidationRule = blockValidationRule;
        this.peerStatuses = new PeersInformation(syncConfiguration);
        this.syncInformation = new SyncInformationImpl();
        this.syncState = new DecidingSyncState(this.syncConfiguration, this, syncInformation, peerStatuses);
        this.expectedMessages = new ExpectedMessages();
        this.connectionPointFinder = new ConnectionPointFinder();
    }

    public void processStatus(MessageChannel sender, Status status) {
        logger.trace("Receiving syncState from node {} block {} {}", sender.getPeerNodeID(), status.getBestBlockNumber(), HashUtil.shortHash(status.getBestBlockHash()).substring(0, 6), status.getBestBlockHash());
        this.peerStatuses.getOrRegisterPeer(sender).setStatus(status);
        this.syncState.newPeerStatus();
    }

    public void processSkeletonResponse(MessageChannel sender, SkeletonResponseMessage message) {
        logger.trace("Process skeleton response from node {}", sender.getPeerNodeID());
        peerStatuses.getOrRegisterPeer(sender);

        if (!expectedMessages.isExpectedMessage(message.getId(), message.getMessageType()))
            return;

        syncState.newSkeleton(message.getBlockIdentifiers());
    }

    public void processBlockHashResponse(MessageChannel sender, BlockHashResponseMessage message) {
        logger.trace("Process block hash response from node {} hash {}", sender.getPeerNodeID(), HashUtil.shortHash(message.getHash()));
        peerStatuses.getOrRegisterPeer(sender);

        if (!expectedMessages.isExpectedMessage(message.getId(), message.getMessageType()))
            return;

        this.syncState.newConnectionPointData(message.getHash());
    }

    public void processBlockHeadersResponse(MessageChannel peer, BlockHeadersResponseMessage message) {
        logger.trace("Process block headers response from node {}", peer.getPeerNodeID());
        peerStatuses.getOrRegisterPeer(peer);

        if (!expectedMessages.isExpectedMessage(message.getId(), message.getMessageType()))
            return;

        // to validate:
        // - PoW
        // - Parent exists
        // - consecutive numbers
        // - consistent difficulty

        // to do: decide whether we have to request the body immediately if we don't have it,
        // or maybe only after we have validated it
        List<BlockHeader> headers = message.getBlockHeaders();
        Block parent = null;

        for (int k = headers.size(); k-- > 0;) {
            BlockHeader header = headers.get(k);
            Block block = Block.fromValidData(header, null, null);

            if (parent == null)
                parent = this.blockchain.getBlockByHash(header.getParentHash());

            if (parent != null && !validateBlockWithHeader(block, parent)) {
                logger.trace("Couldn't validate block header {} hash {} from peer {}", header.getNumber(), HashUtil.shortHash(header.getHash()), peer.getPeerNodeID());
                stopSyncing();
                return;
            }

            pendingHeaders.add(header);

            parent = block;
        }

        List<BlockIdentifier> skeleton = skeletonDownloadHelper.getSkeleton();

        // We use 0 so we start iterarting from the second element,
        // because we always have the first element in our blockchain
        int linkIndex = skeletonDownloadHelper.getLastRequestedLinkIndex() + 1;
        if (linkIndex >= skeleton.size() || linkIndex > syncConfiguration.getMaxSkeletonChunks()) {
            logger.trace("Finished verifying headers from peer {}", peer.getPeerNodeID());
            sendNextBodyRequestTo(peer);
            return;
        }

        this.sendNextBlockHeadersRequestTo(peer);
    }

    public void processBodyResponse(MessageChannel peer, BodyResponseMessage message) {
        logger.trace("Process body response from node {}", peer.getPeerNodeID());
        peerStatuses.getOrRegisterPeer(peer);

        if (!expectedMessages.isExpectedMessage(message.getId(), message.getMessageType()))
            return;

        PendingBodyResponse expected = pendingBodyResponses.get(message.getId());

        if (expected == null || !peer.getPeerNodeID().equals(expected.nodeID)) {
            // TODO(mc) do peer scoring and banning
            return;
        }

        // TODO(mc): validate transactions and uncles are part of this block (with header)
        blockSyncService.processBlock(peer, Block.fromValidData(expected.header, message.getTransactions(), message.getUncles()));

        if (pendingHeaders.isEmpty()) {
            logger.trace("Finished syncing with peer {}", peer.getPeerNodeID());
            stopSyncing();
            return;
        }

        this.sendNextBodyRequestTo(peer);
    }

    public void processNewBlockHash(MessageChannel sender, NewBlockHashMessage message) {
        logger.trace("Process new block hash from node {} hash {}", sender.getPeerNodeID(), HashUtil.shortHash(message.getBlockHash()));
        byte[] hash = message.getBlockHash();

        if (this.blockSyncService.getBlockFromStoreOrBlockchain(hash) != null)
            return;

        if (!syncState.isSyncing()) {
            long lastRequestId = expectedMessages.registerExpectedMessage(MessageType.BLOCK_RESPONSE_MESSAGE);
            sender.sendMessage(new BlockRequestMessage(lastRequestId, hash));
            peerStatuses.getOrRegisterPeer(sender);
        }
    }

    @Override
    public void sendSkeletonRequest(long height) {
        NodeID peerId = skeletonDownloadHelper.getSelectedPeerId();
        logger.trace("Send skeleton request to node {} height {}", peerId, height);
        syncState.messageSent();
        long lastRequestId = expectedMessages.registerExpectedMessage(MessageType.SKELETON_RESPONSE_MESSAGE);
        MessageChannel channel = peerStatuses.getPeer(peerId).getMessageChannel();
        channel.sendMessage(new SkeletonRequestMessage(lastRequestId, height));
    }

    @Override
    public void sendBlockHashRequest(long height) {
        NodeID peerId = skeletonDownloadHelper.getSelectedPeerId();
        logger.trace("Send hash request to node {} height {}", peerId, height);
        syncState.messageSent();
        long lastRequestId = expectedMessages.registerExpectedMessage(MessageType.BLOCK_HASH_RESPONSE_MESSAGE);
        MessageChannel channel = peerStatuses.getPeer(peerId).getMessageChannel();
        channel.sendMessage(new BlockHashRequestMessage(lastRequestId, height));
    }

    @Override
    public void startRequestingHeaders(List<BlockIdentifier> skeleton) {
        skeletonDownloadHelper.setSkeleton(skeleton);
        NodeID peerId = skeletonDownloadHelper.getSelectedPeerId();
        MessageChannel peer = peerStatuses.getPeer(peerId).getMessageChannel();
        sendNextBlockHeadersRequestTo(peer);
    }

    public void processBlockResponse(MessageChannel sender, BlockResponseMessage message) {
        logger.trace("Process block response from node {} block {} {}", sender.getPeerNodeID(), message.getBlock().getNumber(), message.getBlock().getShortHash());
        peerStatuses.getOrRegisterPeer(sender);

        if (!expectedMessages.isExpectedMessage(message.getId(), message.getMessageType()))
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
    public void startSyncing(MessageChannel peer) {
        logger.trace("Find connection point with node {}", peer.getPeerNodeID());
        this.connectionPointFinder = new ConnectionPointFinder();
        this.skeletonDownloadHelper = new SkeletonDownloadHelper(peer.getPeerNodeID());
        // connectionPointFinder.startFindConnectionPoint is called in SyncingWithPeerSyncState's constructor
        setSyncState(new SyncingWithPeerSyncState(this.syncConfiguration, this, syncInformation));
    }

    @Override
    public void stopSyncing() {
        this.skeletonDownloadHelper = null;
        this.pendingHeaders.clear();
        this.pendingBodyResponses.clear();
        this.expectedMessages.clear();
        setSyncState(new DecidingSyncState(this.syncConfiguration, this, syncInformation, peerStatuses));
    }

    private void sendNextBlockHeadersRequestTo(MessageChannel peer) {
        long connectionPoint = connectionPointFinder.getConnectionPoint().get();
        List<BlockIdentifier> skeleton = skeletonDownloadHelper.getSkeleton();
        // We use 0 so we start iterarting from the second element,
        // because we always have the first element in our blockchain
        int linkIndex = skeletonDownloadHelper.getLastRequestedLinkIndex() + 1;
        byte[] hash = skeleton.get(linkIndex).getHash();
        long height = skeleton.get(linkIndex).getNumber();

        long lastHeight = skeleton.get(linkIndex - 1).getNumber();
        long previousKnownHeight = Math.max(lastHeight, connectionPoint);

        int count = (int)(height - previousKnownHeight);

        logger.trace("Send headers request to node {}", peer.getPeerNodeID());
        syncState.messageSent();
        long lastRequestId = expectedMessages.registerExpectedMessage(MessageType.BLOCK_HEADERS_RESPONSE_MESSAGE);
        peer.sendMessage(new BlockHeadersRequestMessage(lastRequestId, hash, count));
        skeletonDownloadHelper.setLastRequestedLinkIndex(linkIndex);
    }

    private void sendNextBodyRequestTo(MessageChannel peer) {
        // We request one body at a time, from oldest to newest
        BlockHeader header = this.pendingHeaders.poll();
        if (header == null) {
            logger.trace("Finished syncing with peer {}", peer.getPeerNodeID());
            stopSyncing();
            return;
        }

        logger.trace("Send body request block {} hash {} to peer {}", header.getNumber(), HashUtil.shortHash(header.getHash()), peer.getPeerNodeID());
        long lastRequestId = expectedMessages.registerExpectedMessage(MessageType.BODY_RESPONSE_MESSAGE);
        peer.sendMessage(new BodyRequestMessage(lastRequestId, header.getHash()));
        pendingBodyResponses.put(lastRequestId, new PendingBodyResponse(peer.getPeerNodeID(), header));
    }

    private SyncPeerStatus getPeerStatus(NodeID nodeID) {
        return this.peerStatuses.getPeer(nodeID);
    }

    private void setSyncState(SyncState syncState) {
        this.syncState = syncState;
        this.syncState.onEnter();
    }

    private boolean validateBlockWithHeader(Block block, Block parent) {
        if (this.blockchain.getBlockByHash(block.getHash()) != null)
            return false;

        if (!parent.isParentOf(block))
            return false;

        if (parent.getNumber() + 1 != block.getNumber())
            return false;

        if (!blockParentValidationRule.isValid(block, parent))
            return false;

        if (!blockValidationRule.isValid(block))
            return false;

        return true;
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
    public long registerExpectedMessage(MessageType message) {
        return expectedMessages.registerExpectedMessage(message);
    }

    @VisibleForTesting
    public void setSelectedPeer(MessageChannel peer, Status status) {
        peerStatuses.getOrRegisterPeer(peer).setStatus(status);
        this.connectionPointFinder = new ConnectionPointFinder();
        this.skeletonDownloadHelper = new SkeletonDownloadHelper(peer.getPeerNodeID());
        this.syncState = new SyncingWithPeerSyncState(this.syncConfiguration, this, syncInformation);
    }

    @VisibleForTesting
    public List<BlockIdentifier> getSkeleton() {
        return this.skeletonDownloadHelper.getSkeleton();
    }

    @VisibleForTesting
    public void setConnectionPoint(int height) {
        connectionPointFinder.setConnectionPoint(height);
    }

    @VisibleForTesting
    public boolean isPeerSyncing(NodeID nodeID) {
        return syncState.isSyncing() && skeletonDownloadHelper.getSelectedPeerId() == nodeID;
    }

    @VisibleForTesting
    public void expectBodyResponseFor(long requestId, NodeID nodeID, BlockHeader header) {
        pendingBodyResponses.put(requestId, new PendingBodyResponse(nodeID, header));
    }

    @VisibleForTesting
    public Map<Long, MessageType> getExpectedResponses() {
        return this.expectedMessages.getExpectedMessages();
    }
    private class SyncInformationImpl implements SyncInformation {

        @Override
        public boolean isKnownBlock(byte[] hash) {
            return blockchain.getBlockByHash(hash) != null;
        }

        @Override
        public ConnectionPointFinder getConnectionPointFinder() {
            return connectionPointFinder;
        }

        @Override
        public boolean hasLowerDifficulty(MessageChannel peer) {
            Status status = getPeerStatus(peer.getPeerNodeID()).getStatus();
            return blockchain.getStatus().hasLowerDifficulty(status);
        }

        @Override
        public Status getSelectedPeerStatus() {
            return getPeerStatus(skeletonDownloadHelper.getSelectedPeerId()).getStatus();
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
