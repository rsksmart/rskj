package co.rsk.net;

import co.rsk.core.bc.BlockChainStatus;
import co.rsk.net.messages.*;
import co.rsk.net.sync.PeersInformation;
import co.rsk.net.sync.SyncStatusHandler;
import co.rsk.validators.BlockDifficultyRule;
import co.rsk.validators.BlockParentDependantValidationRule;
import co.rsk.validators.BlockValidationRule;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;
import org.ethereum.core.Blockchain;
import org.ethereum.validator.ProofOfWorkRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.util.*;

/**
 * Created by ajlopez on 29/08/2017.
 */
public class SyncProcessor {
    private static final Logger logger = LoggerFactory.getLogger("syncprocessor");

    private static BlockValidationRule blockValidationRule = new ProofOfWorkRule();
    private static BlockParentDependantValidationRule blockParentValidationRule = new BlockDifficultyRule();

    private long lastRequestId;
    private Blockchain blockchain;
    private BlockSyncService blockSyncService;
    private PeersInformation peerStatuses = new PeersInformation();
    private Map<Long, PendingBodyResponse> pendingBodyResponses = new HashMap<>();

    private SyncStatusHandler statusHandler = new SyncStatusHandler(peerStatuses);

    public SyncProcessor(Blockchain blockchain, BlockSyncService blockSyncService) {
        this.blockchain = blockchain;
        this.blockSyncService = blockSyncService;
        // TODO(mc) implement FollowBestChain
        this.statusHandler.onFinishedWaitingForPeers(this::findConnectionPointOfBestPeer);
    }

    public void processStatus(MessageChannel sender, Status status) {
        logger.trace("Receiving status from node {} block {} {}", sender.getPeerNodeID(), status.getBestBlockNumber(), Hex.toHexString(status.getBestBlockHash()).substring(0, 6), status.getBestBlockHash());
        statusHandler.newPeerStatus(sender, status);
//        SyncPeerStatus peerStatus = this.getPeerStatusAndUpdateSender(sender);
//
//        peerStatus.setStatus(status);
//
//        if (!this.blockchain.getStatus().hasLowerDifficulty(status)) {
//            if (peerStatus.isSyncing())
//                peerStatus.stopSyncing();
//
//            return;
//        }
//
//        if (!peerStatus.isSyncing() || !peerStatus.getConnectionPoint().isPresent() && !peerStatus.isFindingConnectionPoint()) {
//            this.findConnectionPoint(sender, status);
//            return;
//        }
    }


    public void sendSkeletonRequestTo(MessageChannel peer, long height) {
        logger.trace("Send skeleton request to node {} height {}", peer.getPeerNodeID(), height);
        SyncPeerStatus peerStatus = this.getPeerStatusAndUpdateSender(peer);
        peer.sendMessage(new SkeletonRequestMessage(++lastRequestId, height));
        peerStatus.registerExpectedResponse(lastRequestId, MessageType.SKELETON_RESPONSE_MESSAGE);
    }

    public void processSkeletonResponse(MessageChannel sender, SkeletonResponseMessage message) {
        logger.trace("Process skeleton response from node {}", sender.getPeerNodeID());
        SyncPeerStatus peerStatus = this.getPeerStatusAndUpdateSender(sender);

        if (!peerStatus.isExpectedResponse(message.getId(), message.getMessageType()))
            return;

        peerStatus.setSkeleton(message.getBlockIdentifiers());

        this.sendNextBlockHeadersRequestTo(sender, peerStatus);
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

        return this.peerStatuses.countSyncing();
    }

    private void findConnectionPointOfBestPeer() {
        Optional<MessageChannel> senderOptional = this.peerStatuses.getBestPeerSender();

        senderOptional.ifPresent(bestPeer -> {
            SyncPeerStatus peerStatus = getPeerStatusAndUpdateSender(bestPeer);
            Status status = peerStatus.getStatus();
            if (!this.blockchain.getStatus().hasLowerDifficulty(status)) {
                if (peerStatus.isSyncing())
                    peerStatus.stopSyncing();

                return;
            }

            if (!peerStatus.isSyncing() || !peerStatus.getConnectionPoint().isPresent() && !peerStatus.isFindingConnectionPoint()) {
                this.findConnectionPointOf(bestPeer, status);
            }
        });
    }

    private void sendNextBlockHeadersRequestTo(MessageChannel peer, SyncPeerStatus peerStatus) {
        if (!peerStatus.hasSkeleton())
            return;

        List<BlockIdentifier> skeleton = peerStatus.getSkeleton();

        if (skeleton.isEmpty())
            return;

        long connectionPoint = peerStatus.getConnectionPoint().orElseGet(() -> {
            logger.error("Sending BlockHeaders request to peer {} but the connection point is missing", peer.getPeerNodeID());
            return 0L;
        });

        if (peerStatus.getLastRequestedLinkIndex().isPresent()) {
            int index = peerStatus.getLastRequestedLinkIndex().get();
            byte[] hash = skeleton.get(index).getHash();

            if (this.blockSyncService.getBlockFromStoreOrBlockchain(hash) == null)
                return;
        }

        // We use -1 so we start iterarting from the first element
        int linkIndex = peerStatus.getLastRequestedLinkIndex().orElse(-1) + 1;

        for (int k = linkIndex; k < skeleton.size(); k++) {
            byte[] hash = skeleton.get(k).getHash();

            if (this.blockSyncService.getBlockFromStoreOrBlockchain(hash) != null)
                continue;

            long height = skeleton.get(k).getNumber();

            long lastHeight = k > 0 ? skeleton.get(k - 1).getNumber() : connectionPoint;
            long previousKnownHeight = Math.max(lastHeight, connectionPoint);

            int count = (int)(height - previousKnownHeight);

            logger.trace("Send headers request to node {}", peer.getPeerNodeID());
            peer.sendMessage(new BlockHeadersRequestMessage(++lastRequestId, hash, count));
            peerStatus.registerExpectedResponse(lastRequestId, MessageType.BLOCK_HEADERS_RESPONSE_MESSAGE);
            peerStatus.setLastRequestedLinkIndex(k);

            return;
        }

        peerStatus.stopSyncing();
    }

    public void sendBlockHashRequestTo(MessageChannel peer, long height) {
        logger.trace("Send hash request to node {} height {}", peer.getPeerNodeID(), height);
        SyncPeerStatus peerStatus = this.getPeerStatusAndUpdateSender(peer);
        peer.sendMessage(new BlockHashRequestMessage(++lastRequestId, height));
        peerStatus.registerExpectedResponse(lastRequestId, MessageType.BLOCK_HASH_RESPONSE_MESSAGE);
    }

    public void findConnectionPointOf(MessageChannel peer, Status status) {
        logger.trace("Find connection point with node {}", peer.getPeerNodeID());
        SyncPeerStatus peerStatus = this.getPeerStatusAndUpdateSender(peer);

        peerStatus.startFindConnectionPoint(status.getBestBlockNumber());
        this.sendBlockHashRequestTo(peer, peerStatus.getFindingHeight());
    }

    public void processBlockHashResponse(MessageChannel sender, BlockHashResponseMessage message) {
        logger.trace("Process block hash response from node {} hash {}", sender.getPeerNodeID(), Hex.toHexString(message.getHash()).substring(0, 6));
        SyncPeerStatus peerStatus = this.getPeerStatusAndUpdateSender(sender);

        if (!peerStatus.isExpectedResponse(message.getId(), message.getMessageType()))
            return;

        Block block = this.blockchain.getBlockByHash(message.getHash());

        if (block != null)
            peerStatus.updateFound();
        else
            peerStatus.updateNotFound();

        Optional<Long> cp = peerStatus.getConnectionPoint();
        if (cp.isPresent()) {
            sendSkeletonRequestTo(sender, cp.get());
            return;
        }

        sendBlockHashRequestTo(sender, peerStatus.getFindingHeight());
    }

    public void processBlockHeadersResponse(MessageChannel sender, BlockHeadersResponseMessage message) {
        logger.trace("Process block headers response from node {}", sender.getPeerNodeID());
        SyncPeerStatus peerStatus = this.getPeerStatusAndUpdateSender(sender);

        if (!peerStatus.isExpectedResponse(message.getId(), message.getMessageType()))
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

            if (parent == null || !validateBlockWithHeader(block, parent))
                continue;

            logger.trace("Send body request to node {} hash {}", sender.getPeerNodeID(), Hex.toHexString(header.getHash()).substring(0, 6));
            sender.sendMessage(new BodyRequestMessage(++lastRequestId, header.getHash()));
            peerStatus.registerExpectedResponse(lastRequestId, MessageType.BODY_RESPONSE_MESSAGE);
            pendingBodyResponses.put(lastRequestId, new PendingBodyResponse(sender.getPeerNodeID(), header));

            parent = block;
        }
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
        SyncPeerStatus peerStatus = this.getPeerStatusAndUpdateSender(sender);

        if (!peerStatus.isExpectedResponse(message.getId(), message.getMessageType()))
            return;

        PendingBodyResponse expected = pendingBodyResponses.get(message.getId());

        if (expected == null || !sender.getPeerNodeID().equals(expected.nodeID)) {
            // Don't waste time on spam or expired responses.
            return;
        }

        // TODO(mc): validate transactions and uncles are part of this block (with header)
        blockSyncService.processBlock(sender, Block.fromValidData(expected.header, message.getTransactions(), message.getUncles()));

        this.sendNextBlockHeadersRequestTo(sender, this.getPeerStatusAndUpdateSender(sender));
    }

    public void processBlockResponse(MessageChannel sender, BlockResponseMessage message) {
        logger.trace("Process block response from node {} block {} {}", sender.getPeerNodeID(), message.getBlock().getNumber(), message.getBlock().getShortHash());
        SyncPeerStatus peerStatus = this.getPeerStatusAndUpdateSender(sender);

        if (!peerStatus.isExpectedResponse(message.getId(), message.getMessageType()))
            return;

        blockSyncService.processBlock(sender, message.getBlock());
    }

    public void processNewBlockHash(MessageChannel sender, NewBlockHashMessage message) {
        logger.trace("Process new block hash from node {} hash {}", sender.getPeerNodeID(), Hex.toHexString(message.getBlockHash()).substring(0, 6));
        byte[] hash = message.getBlockHash();

        if (this.blockSyncService.getBlockFromStoreOrBlockchain(hash) != null)
            return;

        sender.sendMessage(new BlockRequestMessage(++lastRequestId, hash));
        this.getPeerStatusAndUpdateSender(sender).registerExpectedResponse(lastRequestId, MessageType.BLOCK_RESPONSE_MESSAGE);
    }

    public Set<NodeID> getKnownPeersNodeIDs() {
        return this.peerStatuses.knownNodeIds();
    }

    public SyncPeerStatus getPeerStatus(NodeID nodeID) {
        return this.peerStatuses.getOrCreate(nodeID);
    }

    public SyncPeerStatus getPeerStatusAndUpdateSender(MessageChannel sender) {
        return this.peerStatuses.getOrCreateWithSender(sender);
    }

    @VisibleForTesting
    public void expectBodyResponseFor(long requestId, NodeID nodeID, BlockHeader header) {
        pendingBodyResponses.put(requestId, new PendingBodyResponse(nodeID, header));
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
