package co.rsk.net;

import co.rsk.core.bc.BlockChainStatus;
import co.rsk.net.messages.*;
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
import java.util.concurrent.TimeUnit;

/**
 * Created by ajlopez on 29/08/2017.
 */
public class SyncProcessor {
    private static final Logger logger = LoggerFactory.getLogger("syncprocessor");

    private static final long peerTimeout = TimeUnit.MINUTES.toMillis(10);

    private static BlockValidationRule blockValidationRule = new ProofOfWorkRule();
    private static BlockParentDependantValidationRule blockParentValidationRule = new BlockDifficultyRule();

    private long lastRequestId;
    private Blockchain blockchain;
    private BlockSyncService blockSyncService;
    private Map<NodeID, SyncPeerStatus> peerStatuses = new HashMap<>();
    private Map<Long, PendingBodyResponse> pendingBodyResponses = new HashMap<>();

    public SyncProcessor(Blockchain blockchain, BlockSyncService blockSyncService) {
        this.blockchain = blockchain;
        this.blockSyncService = blockSyncService;
    }

    public int getNoPeers() {
        return this.peerStatuses.size();
    }

    public int getNoAdvancedPeers() {
        BlockChainStatus chainStatus = this.blockchain.getStatus();

        if (chainStatus == null)
            return this.peerStatuses.size();

        long count = this.peerStatuses.values().stream()
                .filter(s -> s.isSyncing())
                .count();

        return Math.toIntExact(count);
    }

    public void processStatus(MessageSender sender, Status status) {
        logger.trace("Receiving status from node {} block {} {}", sender.getNodeID(), status.getBestBlockNumber(), Hex.toHexString(status.getBestBlockHash()).substring(0, 6), status.getBestBlockHash());
        SyncPeerStatus peerStatus = this.getPeerStatus(sender.getNodeID());

        peerStatus.setStatus(status);

        if (!this.blockchain.getStatus().hasLowerDifficulty(status)) {
            if (peerStatus.isSyncing())
                peerStatus.stopSyncing();

            return;
        }

        if (!peerStatus.isSyncing() || !peerStatus.getConnectionPoint().isPresent() && !peerStatus.isFindingConnectionPoint()) {
            this.findConnectionPoint(sender, status);
            return;
        }
    }

    public void sendSkeletonRequest(MessageSender sender, long height) {
        logger.trace("Send skeleton request to node {} height {}", sender.getNodeID(), height);
        SyncPeerStatus peerStatus = this.getPeerStatus(sender.getNodeID());
        sender.sendMessage(new SkeletonRequestMessage(++lastRequestId, height));
        peerStatus.registerExpectedResponse(lastRequestId, MessageType.SKELETON_RESPONSE_MESSAGE);
    }

    public void processSkeletonResponse(MessageSender sender, SkeletonResponseMessage message) {
        logger.trace("Process skeleton response from node {}", sender.getNodeID());
        SyncPeerStatus peerStatus = this.getPeerStatus(sender.getNodeID());

        if (!peerStatus.isExpectedResponse(message.getId(), message.getMessageType()))
            return;

        peerStatus.setSkeleton(message.getBlockIdentifiers());

        this.sendNextBlockHeadersRequest(sender, peerStatus);
    }

    private void sendNextBlockHeadersRequest(MessageSender sender, SyncPeerStatus peerStatus) {
        if (!peerStatus.hasSkeleton())
            return;

        List<BlockIdentifier> skeleton = peerStatus.getSkeleton();

        if (skeleton.isEmpty())
            return;

        long connectionPoint = peerStatus.getConnectionPoint().orElseGet(() -> {
            logger.error("Sending BlockHeaders request to peer {} but the connection point is missing", sender.getNodeID());
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

            logger.trace("Send headers request to node {}", sender.getNodeID());
            sender.sendMessage(new BlockHeadersRequestMessage(++lastRequestId, hash, count));
            peerStatus.registerExpectedResponse(lastRequestId, MessageType.BLOCK_HEADERS_RESPONSE_MESSAGE);
            peerStatus.setLastRequestedLinkIndex(k);

            return;
        }

        peerStatus.stopSyncing();
    }

    public void sendBlockHashRequest(MessageSender sender, long height) {
        logger.trace("Send hash request to node {} height {}", sender.getNodeID(), height);
        SyncPeerStatus peerStatus = this.getPeerStatus(sender.getNodeID());
        sender.sendMessage(new BlockHashRequestMessage(++lastRequestId, height));
        peerStatus.registerExpectedResponse(lastRequestId, MessageType.BLOCK_HASH_RESPONSE_MESSAGE);
    }

    public void findConnectionPoint(MessageSender sender, Status status) {
        logger.trace("Find connection point with node {}", sender.getNodeID());
        SyncPeerStatus peerStatus = this.getPeerStatus(sender.getNodeID());

        peerStatus.startFindConnectionPoint(status.getBestBlockNumber());
        this.sendBlockHashRequest(sender, peerStatus.getFindingHeight());
    }

    public void processBlockHashResponse(MessageSender sender, BlockHashResponseMessage message) {
        logger.trace("Process block hash response from node {} hash {}", sender.getNodeID(), Hex.toHexString(message.getHash()).substring(0, 6));
        SyncPeerStatus peerStatus = this.getPeerStatus(sender.getNodeID());

        if (!peerStatus.isExpectedResponse(message.getId(), message.getMessageType()))
            return;

        Block block = this.blockchain.getBlockByHash(message.getHash());

        if (block != null)
            peerStatus.updateFound();
        else
            peerStatus.updateNotFound();

        Optional<Long> cp = peerStatus.getConnectionPoint();
        if (cp.isPresent()) {
            sendSkeletonRequest(sender, cp.get());
            return;
        }

        sendBlockHashRequest(sender, peerStatus.getFindingHeight());
    }

    public void processBlockHeadersResponse(MessageSender sender, BlockHeadersResponseMessage message) {
        logger.trace("Process block headers response from node {}", sender.getNodeID());
        SyncPeerStatus peerStatus = this.getPeerStatus(sender.getNodeID());

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

            logger.trace("Send body request to node {} hash {}", sender.getNodeID(), Hex.toHexString(header.getHash()).substring(0, 6));
            sender.sendMessage(new BodyRequestMessage(++lastRequestId, header.getHash()));
            peerStatus.registerExpectedResponse(lastRequestId, MessageType.BODY_RESPONSE_MESSAGE);
            pendingBodyResponses.put(lastRequestId, new PendingBodyResponse(sender.getNodeID(), header));

            parent = block;
        }
    }

    private boolean validateBlockWithHeader(Block block, Block parent) {
        if (this.blockchain.getBlockByHash(block.getHash()) != null)
            return false;

        if (!Arrays.equals(parent.getHash(), block.getParentHash()))
            return false;

        if (parent.getNumber() + 1 != block.getNumber())
            return false;

        if (!blockParentValidationRule.isValid(block, parent))
            return false;

        if (!blockValidationRule.isValid(block))
            return false;

        return true;
    }

    public void processBodyResponse(MessageSender sender, BodyResponseMessage message) {
        logger.trace("Process body response from node {}", sender.getNodeID());
        SyncPeerStatus peerStatus = this.getPeerStatus(sender.getNodeID());

        if (!peerStatus.isExpectedResponse(message.getId(), message.getMessageType()))
            return;

        PendingBodyResponse expected = pendingBodyResponses.get(message.getId());

        if (expected == null || !sender.getNodeID().equals(expected.nodeID)) {
            // Don't waste time on spam or expired responses.
            return;
        }

        // TODO(mc): validate transactions and uncles are part of this block (with header)
        blockSyncService.processBlock(sender, Block.fromValidData(expected.header, message.getTransactions(), message.getUncles()));

        this.sendNextBlockHeadersRequest(sender, this.getPeerStatus(sender.getNodeID()));
    }

    public void processBlockResponse(MessageSender sender, BlockResponseMessage message) {
        logger.trace("Process block response from node {} block {} {}", sender.getNodeID(), message.getBlock().getNumber(), message.getBlock().getShortHash());
        SyncPeerStatus peerStatus = this.getPeerStatus(sender.getNodeID());

        if (!peerStatus.isExpectedResponse(message.getId(), message.getMessageType()))
            return;

        blockSyncService.processBlock(sender, message.getBlock());
    }

    public void processNewBlockHash(MessageSender sender, NewBlockHashMessage message) {
        logger.trace("Process new block hash from node {} hash {}", sender.getNodeID(), Hex.toHexString(message.getBlockHash()).substring(0, 6));
        byte[] hash = message.getBlockHash();

        if (this.blockSyncService.getBlockFromStoreOrBlockchain(hash) != null)
            return;

        sender.sendMessage(new BlockRequestMessage(++lastRequestId, hash));
        this.getPeerStatus(sender.getNodeID()).registerExpectedResponse(lastRequestId, MessageType.BLOCK_RESPONSE_MESSAGE);
    }

    public Set<NodeID> getKnownPeersNodeIDs() {
        return this.peerStatuses.keySet();
    }

    private SyncPeerStatus createPeerStatus(NodeID nodeID) {
        SyncPeerStatus peerStatus = new SyncPeerStatus();
        peerStatuses.put(nodeID, peerStatus);
        return peerStatus;
    }

    @VisibleForTesting
    public SyncPeerStatus getPeerStatus(NodeID nodeID) {
        SyncPeerStatus peerStatus = this.peerStatuses.get(nodeID);

        if (peerStatus != null && !peerStatus.isExpired(peerTimeout))
            return peerStatus;

        return this.createPeerStatus(nodeID);
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
