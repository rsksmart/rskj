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
import org.spongycastle.util.encoders.Hex;

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

    private long lastRequestId;
    private Blockchain blockchain;
    private BlockSyncService blockSyncService;
    private PeersInformation peerStatuses;
    private Map<Long, PendingBodyResponse> pendingBodyResponses = new HashMap<>();
    private Queue<BlockHeader> bodiesToRequest = new ArrayDeque<>();

    private Map<NodeID, MessageChannel> senders = new HashMap<>();
    private SyncState syncState;
    private SyncPeerProcessor syncPeerProcessor;
    private MessageChannel selectedPeer;

    public SyncProcessor(Blockchain blockchain, BlockSyncService blockSyncService, SyncConfiguration syncConfiguration, BlockValidationRule blockValidationRule) {
        // TODO(mc) implement FollowBestChain
        this.blockchain = blockchain;
        this.blockSyncService = blockSyncService;
        this.syncConfiguration = syncConfiguration;
        this.blockValidationRule = blockValidationRule;
        this.peerStatuses = new PeersInformation(syncConfiguration);
        this.syncState = new DecidingSyncState(this.syncConfiguration, this, peerStatuses);
        this.syncPeerProcessor = new SyncPeerProcessor();
    }

    public void processStatus(MessageChannel sender, Status status) {
        logger.trace("Receiving syncState from node {} block {} {}", sender.getPeerNodeID(), status.getBestBlockNumber(), Hex.toHexString(status.getBestBlockHash()).substring(0, 6), status.getBestBlockHash());
        this.peerStatuses.getOrRegisterPeer(sender).setStatus(status);
        this.syncState.newPeerStatus();
    }

    public void sendSkeletonRequestTo(MessageChannel peer, long height) {
        logger.trace("Send skeleton request to node {} height {}", peer.getPeerNodeID(), height);
        syncState.messageSent();
        peer.sendMessage(new SkeletonRequestMessage(++lastRequestId, height));
        syncPeerProcessor.registerExpectedResponse(lastRequestId, MessageType.SKELETON_RESPONSE_MESSAGE);
    }

    public void processSkeletonResponse(MessageChannel sender, SkeletonResponseMessage message) {
        logger.trace("Process skeleton response from node {}", sender.getPeerNodeID());
        this.getPeerStatusAndSaveSender(sender);

        if (!syncPeerProcessor.isExpectedResponse(message.getId(), message.getMessageType()))
            return;

        syncPeerProcessor.setSkeleton(message.getBlockIdentifiers());
        this.sendNextBlockHeadersRequestTo(sender);
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

    private void sendNextBlockHeadersRequestTo(MessageChannel peer) {
        if (!syncPeerProcessor.hasSkeleton())
            return;

        List<BlockIdentifier> skeleton = syncPeerProcessor.getSkeleton();

        // We use 0 so we start iterarting from the second element,
        // because we always have the first element in our blockchain
        int linkIndex = syncPeerProcessor.getLastRequestedLinkIndex().orElse(0) + 1;

        if (linkIndex >= skeleton.size() || linkIndex > syncConfiguration.getMaxSkeletonChunks()) {
            logger.trace("Finished verifying headers from peer {}", peer.getPeerNodeID());
            sendNextBodyRequestTo(peer);
            return;
        }

        long connectionPoint = syncPeerProcessor.getConnectionPoint().orElseGet(() -> {
            logger.error("Sending BlockHeaders request to peer {} but the connection point is missing", peer.getPeerNodeID());
            return 0L;
        });

        byte[] hash = skeleton.get(linkIndex).getHash();
        long height = skeleton.get(linkIndex).getNumber();

        long lastHeight = skeleton.get(linkIndex - 1).getNumber();
        long previousKnownHeight = Math.max(lastHeight, connectionPoint);

        int count = (int)(height - previousKnownHeight);

        logger.trace("Send headers request to node {}", peer.getPeerNodeID());
        syncState.messageSent();
        peer.sendMessage(new BlockHeadersRequestMessage(++lastRequestId, hash, count));
        syncPeerProcessor.registerExpectedResponse(lastRequestId, MessageType.BLOCK_HEADERS_RESPONSE_MESSAGE);
        syncPeerProcessor.setLastRequestedLinkIndex(linkIndex);
    }

    private void sendNextBodyRequestTo(MessageChannel peer) {
        // We request one body at a time, from oldest to newest
        BlockHeader header = this.bodiesToRequest.poll();
        if (header == null) {
            logger.trace("Finished syncing with peer {}", peer.getPeerNodeID());
            stopSyncing();
            return;
        }

        logger.trace("Send body request block {} hash {} to peer {}", header.getNumber(), HashUtil.shortHash(header.getHash()), peer.getPeerNodeID());
        peer.sendMessage(new BodyRequestMessage(++lastRequestId, header.getHash()));
        syncPeerProcessor.registerExpectedResponse(lastRequestId, MessageType.BODY_RESPONSE_MESSAGE);
        pendingBodyResponses.put(lastRequestId, new PendingBodyResponse(peer.getPeerNodeID(), header));
    }

    public void sendBlockHashRequestTo(MessageChannel peer, long height) {
        logger.trace("Send hash request to node {} height {}", peer.getPeerNodeID(), height);
        syncState.messageSent();
        peer.sendMessage(new BlockHashRequestMessage(++lastRequestId, height));
        syncPeerProcessor.registerExpectedResponse(lastRequestId, MessageType.BLOCK_HASH_RESPONSE_MESSAGE);
    }

    private void findConnectionPointOf(NodeID nodeID, Status status) {
        logger.trace("Find connection point with node {}", nodeID);
        syncPeerProcessor.startFindConnectionPoint(status.getBestBlockNumber());
        MessageChannel channel = peerStatuses.getPeer(nodeID).getMessageChannel();
        this.sendBlockHashRequestTo(channel, syncPeerProcessor.getFindingHeight());
    }

    public void processBlockHashResponse(MessageChannel sender, BlockHashResponseMessage message) {
        logger.trace("Process block hash response from node {} hash {}", sender.getPeerNodeID(), Hex.toHexString(message.getHash()).substring(0, 6));
        this.getPeerStatusAndSaveSender(sender);

        if (!syncPeerProcessor.isExpectedResponse(message.getId(), message.getMessageType()))
            return;

        Block block = this.blockchain.getBlockByHash(message.getHash());

        if (block != null)
            syncPeerProcessor.updateFound();
        else
            syncPeerProcessor.updateNotFound();

        Optional<Long> cp = syncPeerProcessor.getConnectionPoint();
        if (cp.isPresent()) {
            sendSkeletonRequestTo(sender, cp.get());
            return;
        }

        sendBlockHashRequestTo(sender, syncPeerProcessor.getFindingHeight());
    }

    public void processBlockHeadersResponse(MessageChannel sender, BlockHeadersResponseMessage message) {
        logger.trace("Process block headers response from node {}", sender.getPeerNodeID());
        this.getPeerStatusAndSaveSender(sender);

        if (!syncPeerProcessor.isExpectedResponse(message.getId(), message.getMessageType()))
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
                logger.trace("Couldn't validate block header {} hash {} from peer {}", header.getNumber(), HashUtil.shortHash(header.getHash()), sender.getPeerNodeID());
                stopSyncing();
                return;
            }

            bodiesToRequest.add(header);

            parent = block;
        }

        this.sendNextBlockHeadersRequestTo(sender);
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

    public void processBodyResponse(MessageChannel sender, BodyResponseMessage message) {
        logger.trace("Process body response from node {}", sender.getPeerNodeID());
        this.getPeerStatusAndSaveSender(sender);

        if (!syncPeerProcessor.isExpectedResponse(message.getId(), message.getMessageType()))
            return;

        PendingBodyResponse expected = pendingBodyResponses.get(message.getId());

        if (expected == null || !sender.getPeerNodeID().equals(expected.nodeID)) {
            // TODO(mc) do peer scoring and banning
            return;
        }

        // TODO(mc): validate transactions and uncles are part of this block (with header)
        blockSyncService.processBlock(sender, Block.fromValidData(expected.header, message.getTransactions(), message.getUncles()));
        this.getPeerStatusAndSaveSender(sender);

        this.sendNextBodyRequestTo(sender);
    }

    public void processBlockResponse(MessageChannel sender, BlockResponseMessage message) {
        logger.trace("Process block response from node {} block {} {}", sender.getPeerNodeID(), message.getBlock().getNumber(), message.getBlock().getShortHash());
        this.getPeerStatusAndSaveSender(sender);

        if (!syncPeerProcessor.isExpectedResponse(message.getId(), message.getMessageType()))
            return;

        blockSyncService.processBlock(sender, message.getBlock());
    }

    public void processNewBlockHash(MessageChannel sender, NewBlockHashMessage message) {
        logger.trace("Process new block hash from node {} hash {}", sender.getPeerNodeID(), Hex.toHexString(message.getBlockHash()).substring(0, 6));
        byte[] hash = message.getBlockHash();

        if (this.blockSyncService.getBlockFromStoreOrBlockchain(hash) != null)
            return;

        if (!syncState.isSyncing()) {
            sender.sendMessage(new BlockRequestMessage(++lastRequestId, hash));
            this.getPeerStatusAndSaveSender(sender);
            syncPeerProcessor.registerExpectedResponse(lastRequestId, MessageType.BLOCK_RESPONSE_MESSAGE);
        }
    }

    public Set<NodeID> getKnownPeersNodeIDs() {
        return this.peerStatuses.knownNodeIds();
    }

    public SyncPeerStatus getPeerStatus(NodeID nodeID) {
        return this.peerStatuses.getPeer(nodeID);
    }

    @VisibleForTesting
    public SyncPeerProcessor getSyncPeerProcessor() {
        return syncPeerProcessor;
    }

    public SyncPeerStatus getPeerStatusAndSaveSender(MessageChannel sender) {
        SyncPeerStatus peerStatus = peerStatuses.getOrRegisterPeer(sender);
        return peerStatus;
    }

    @VisibleForTesting
    public boolean isPeerSyncing(NodeID nodeID) {
        return syncState.isSyncing() && selectedPeer.getPeerNodeID() == nodeID;
    }

    @VisibleForTesting
    public void expectBodyResponseFor(long requestId, NodeID nodeID, BlockHeader header) {
        pendingBodyResponses.put(requestId, new PendingBodyResponse(nodeID, header));
    }

    @VisibleForTesting
    public Map<Long, MessageType> getExpectedBodyResponses() {
        return this.syncPeerProcessor.getExpectedResponses();
    }

    public void setSyncState(SyncState syncState) {
        this.syncState = syncState;
    }

    public void onTimePassed(Duration timePassed) {
        this.syncState.tick(timePassed);
    }

    public void canStartSyncing() {
        Optional<MessageChannel> bestPeerOptional = this.peerStatuses.getBestPeer();
        bestPeerOptional.ifPresent(bp -> {
            Status status = getPeerStatus(bp.getPeerNodeID()).getStatus();
            if (this.blockchain.getStatus().hasLowerDifficulty(status)) {
                setSyncState(new SyncingWithPeerSyncState(this.syncConfiguration, this));
                this.syncPeerProcessor = new SyncPeerProcessor();
                this.selectedPeer = bp;
                this.findConnectionPointOf(bp.getPeerNodeID(), status);
            }
        });
    }

    public void stopSyncing() {
        // TODO(mc) do better cleanup
        this.selectedPeer = null;
//        this.syncPeerProcessor = null;
        this.bodiesToRequest.clear();
        this.pendingBodyResponses.clear();
        setSyncState(new DecidingSyncState(this.syncConfiguration, this, peerStatuses));
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
