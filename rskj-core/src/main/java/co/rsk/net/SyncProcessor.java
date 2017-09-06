package co.rsk.net;

import co.rsk.core.bc.BlockChainStatus;
import co.rsk.net.messages.*;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;
import org.ethereum.core.Blockchain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.*;

/**
 * Created by ajlopez on 29/08/2017.
 */
public class SyncProcessor {
    private static final Logger logger = LoggerFactory.getLogger(SyncProcessor.class);

    private long lastRequestId;
    private Blockchain blockchain;
    private BlockSyncService blockSyncService;
    private Map<NodeID, Status> peers = new HashMap<>();
    private Map<NodeID, SyncPeerStatus> peerStatuses = new HashMap<>();
    private Map<Long, PendingBodyResponse> pendingBodyResponses = new HashMap<>();

    public SyncProcessor(Blockchain blockchain, BlockSyncService blockSyncService) {
        this.blockchain = blockchain;
        this.blockSyncService = blockSyncService;
    }

    public int getNoPeers() {
        return this.peers.size();
    }

    public int getNoAdvancedPeers() {
        BlockChainStatus chainStatus = this.blockchain.getStatus();

        if (chainStatus == null)
            return this.peers.size();

        BigInteger totalDifficulty = chainStatus.getTotalDifficulty();
        int count = 0;

        for (Status status : this.peers.values())
            if (status.getTotalDifficulty().compareTo(totalDifficulty) > 0)
                count++;

        return count;
    }

    public void processStatus(MessageSender sender, Status status) {
        peers.put(sender.getNodeID(), status);

        if (status.getTotalDifficulty().compareTo(this.blockchain.getTotalDifficulty()) > 0)
            this.findConnectionPoint(sender, status);
    }

    public void sendSkeletonRequest(MessageSender sender, long height) {
        SyncPeerStatus peerStatus = this.getPeerStatus(sender.getNodeID());
        sender.sendMessage(new SkeletonRequestMessage(++lastRequestId, height));
        peerStatus.registerExpectedResponse(lastRequestId, MessageType.SKELETON_RESPONSE_MESSAGE);
    }

    public void processSkeletonResponse(MessageSender sender, SkeletonResponseMessage message) {
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

            sender.sendMessage(new BlockHeadersRequestMessage(++lastRequestId, hash, count));
            peerStatus.registerExpectedResponse(lastRequestId, MessageType.BLOCK_HEADERS_RESPONSE_MESSAGE);
            peerStatus.setLastRequestedLinkIndex(k);

            return;
        }
    }

    public void sendBlockHashRequest(MessageSender sender, long height) {
        SyncPeerStatus peerStatus = this.getPeerStatus(sender.getNodeID());
        sender.sendMessage(new BlockHashRequestMessage(++lastRequestId, height));
        peerStatus.registerExpectedResponse(lastRequestId, MessageType.BLOCK_HASH_RESPONSE_MESSAGE);
    }

    public void findConnectionPoint(MessageSender sender, Status status) {
        SyncPeerStatus peerStatus = this.getPeerStatus(sender.getNodeID());

        if (peerStatus.getStatus() == null) {
            peerStatus.setStatus(status);
            peerStatus.startFindConnectionPoint(status.getBestBlockNumber());
            this.sendBlockHashRequest(sender, peerStatus.getFindingHeight());
        }
        else {
            this.sendSkeletonRequest(sender, this.blockchain.getStatus().getBestBlockNumber());
            peerStatus.setStatus(status);
        }
    }

    public void processBlockHashResponse(MessageSender sender, BlockHashResponseMessage message) {
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
        SyncPeerStatus peerStatus = this.getPeerStatus(sender.getNodeID());

        if (!peerStatus.isExpectedResponse(message.getId(), message.getMessageType()))
            return;

        // to validate:
        // - PoW
        // - Parent exists
        // - consecutive numbers
        // - consistent difficulty


        // TODO: review logic, in two nodes sync it returns
        /*
        if (!sender.getNodeID().equals(expectedNodeId)) {
            // Don't waste time on spam or expired responses.
            return;
        }
        */

        // to do: decide whether we have to request the body immediately if we don't have it,
        // or maybe only after we have validated it
        List<BlockHeader> headers = message.getBlockHeaders();
        for (int k = headers.size(); k-- > 0;) {
            BlockHeader header = headers.get(k);
            if (this.blockchain.getBlockByHash(header.getHash()) == null) {
                sender.sendMessage(new BodyRequestMessage(++lastRequestId, header.getHash()));
                peerStatus.registerExpectedResponse(lastRequestId, MessageType.BODY_RESPONSE_MESSAGE);
                pendingBodyResponses.put(lastRequestId, new PendingBodyResponse(sender.getNodeID(), header));
            }
        }

    }

    public void processBodyResponse(MessageSender sender, BodyResponseMessage message) {
        SyncPeerStatus peerStatus = this.getPeerStatus(sender.getNodeID());

        if (!peerStatus.isExpectedResponse(message.getId(), message.getMessageType()))
            return;

        PendingBodyResponse expected = pendingBodyResponses.get(message.getId());
        if (expected == null || !sender.getNodeID().equals(expected.nodeID)) {
            // Don't waste time on spam or expired responses.
            return;
        }

        // TODO(mc): validate transactions and uncles are part of this block (with header)
        blockSyncService.processBlock(sender, new Block(expected.header, message.getTransactions(), message.getUncles()));

        this.sendNextBlockHeadersRequest(sender, this.getPeerStatus(sender.getNodeID()));
    }

    public void processBlockResponse(MessageSender sender, BlockResponseMessage message) {
        SyncPeerStatus peerStatus = this.getPeerStatus(sender.getNodeID());

        if (!peerStatus.isExpectedResponse(message.getId(), message.getMessageType()))
            return;

        blockSyncService.processBlock(sender, message.getBlock());
    }

    public void processNewBlockHash(MessageSender sender, NewBlockHashMessage message) {
        byte[] hash = message.getBlockHash();

        if (this.blockSyncService.getBlockFromStoreOrBlockchain(hash) != null)
            return;

        sender.sendMessage(new BlockRequestMessage(++lastRequestId, hash));
        this.getPeerStatus(sender.getNodeID()).registerExpectedResponse(lastRequestId, MessageType.BLOCK_RESPONSE_MESSAGE);
    }

    public Set<NodeID> getKnownPeersNodeIDs() {
        Set<NodeID> ids = new HashSet<>();

        ids.addAll(this.peerStatuses.keySet());

        return ids;
    }

    private SyncPeerStatus createPeerStatus(NodeID nodeID) {
        SyncPeerStatus peerStatus = new SyncPeerStatus();
        peerStatuses.put(nodeID, peerStatus);
        return peerStatus;
    }

    @VisibleForTesting
    public SyncPeerStatus getPeerStatus(NodeID nodeID) {
        SyncPeerStatus peerStatus = this.peerStatuses.get(nodeID);

        if (peerStatus != null)
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
