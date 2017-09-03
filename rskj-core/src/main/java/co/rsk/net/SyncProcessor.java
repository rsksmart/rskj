package co.rsk.net;

import co.rsk.core.bc.BlockChainStatus;
import co.rsk.net.messages.*;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;
import org.ethereum.core.Blockchain;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by ajlopez on 29/08/2017.
 */
public class SyncProcessor {
    private long nextId;
    private Blockchain blockchain;
    private BlockSyncService blockSyncService;
    private Map<NodeID, Status> peers = new HashMap<>();
    private Map<NodeID, SyncPeerStatus> peerStatuses = new HashMap<>();
    private Map<Long, NodeID> pendingResponses = new HashMap<>();
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
            this.findConnectionPoint(sender, status.getBestBlockNumber());
    }

    public void sendSkeletonRequest(MessageSender sender, long height) {
        sender.sendMessage(new SkeletonRequestMessage(++nextId, height));
    }

    public void processSkeletonResponse(MessageSender sender, SkeletonResponseMessage message) {
        SyncPeerStatus peerStatus = this.getPeerStatus(sender.getNodeID());

        peerStatus.setBlockIdentifiers(message.getBlockIdentifiers());

        this.sendNextBlockHeadersRequest(sender, peerStatus);
    }

    private void sendNextBlockHeadersRequest(MessageSender sender, SyncPeerStatus peerStatus) {
        List<BlockIdentifier> blockIdentifiers = peerStatus.getBlockIdentifiers();

        if (blockIdentifiers == null)
            return;

        int nbids = blockIdentifiers.size();
        int lastBlockIdentifier = peerStatus.getLastBlockIdentifierRequested();

        for (int k = lastBlockIdentifier + 1; k < nbids; k++) {
            byte[] hash = blockIdentifiers.get(k).getHash();

            if (this.blockSyncService.getBlockFromStoreOrBlockchain(hash) != null)
                continue;

            long height = blockIdentifiers.get(k).getNumber();

            long lastHeight = k > 0 ? blockIdentifiers.get(k - 1).getNumber() : peerStatus.getConnectionPoint();

            long previousKnownHeight = Math.max(lastHeight, peerStatus.getConnectionPoint());

            int count = (int)(height - previousKnownHeight);

            sender.sendMessage(new BlockHeadersRequestMessage(++nextId, hash, count));

            peerStatus.setLastBlockIdentifierRequested(k);

            return;
        }
    }

    public void sendBlockHashRequest(MessageSender sender, long height) {
        sender.sendMessage(new BlockHashRequestMessage(++nextId, height));
    }

    public void findConnectionPoint(MessageSender sender, long height) {
        SyncPeerStatus peerStatus = this.createPeerStatus(sender.getNodeID());
        peerStatus.startFindConnectionPoint(height);
        this.sendBlockHashRequest(sender, peerStatus.getFindingHeight());
    }

    public void processBlockHashResponse(MessageSender sender, BlockHashResponseMessage message) {
        SyncPeerStatus peerStatus = this.getPeerStatus(sender.getNodeID());

        Block block = this.blockchain.getBlockByHash(message.getHash());

        if (block != null)
            peerStatus.updateFound();
        else
            peerStatus.updateNotFound();

        if (peerStatus.hasConnectionPoint()) {
            sendSkeletonRequest(sender, peerStatus.getConnectionPoint());
            return;
        }

        sendBlockHashRequest(sender, peerStatus.getFindingHeight());
    }

    public void processBlockHeadersResponse(MessageSender sender, BlockHeadersResponseMessage message) {
        // to validate:
        // - PoW
        // - Parent exists
        // - consecutive numbers
        // - consistent difficulty

        NodeID expectedNodeId = pendingResponses.get(message.getId());

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
                sender.sendMessage(new BodyRequestMessage(++nextId, header.getHash()));
                pendingBodyResponses.put(nextId, new PendingBodyResponse(sender.getNodeID(), header));
            }
        }

        pendingResponses.remove(message.getId());
    }

    public void processBodyResponse(MessageSender sender, BodyResponseMessage message) {
        PendingBodyResponse expected = pendingBodyResponses.get(message.getId());
        if (expected == null || !sender.getNodeID().equals(expected.nodeID)) {
            // Don't waste time on spam or expired responses.
            return;
        }

        // TODO(mc): validate transactions and uncles are part of this block (with header)
        blockSyncService.processBlock(sender, new Block(expected.header, message.getTransactions(), message.getUncles()));

        this.sendNextBlockHeadersRequest(sender, this.getPeerStatus(sender.getNodeID()));
    }

    public SyncPeerStatus createPeerStatus(NodeID nodeID) {
        SyncPeerStatus peerStatus = new SyncPeerStatus();
        peerStatuses.put(nodeID, peerStatus);
        return peerStatus;
    }

    public SyncPeerStatus getPeerStatus(NodeID nodeID) {
        SyncPeerStatus peerStatus = this.peerStatuses.get(nodeID);

        if (peerStatus != null)
            return peerStatus;

        return this.createPeerStatus(nodeID);
    }

    @VisibleForTesting
    public void expectMessageFrom(long requestId, NodeID nodeID) {
        pendingResponses.put(requestId, nodeID);
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
