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
    private Map<NodeID, Status> peers = new HashMap<>();
    private Map<NodeID, SyncPeerStatus> peerStatuses = new HashMap<>();
    private Map<Long, NodeID> pendingResponses = new HashMap<>();

    public SyncProcessor(Blockchain blockchain) {
        this.blockchain = blockchain;
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

        long bestNumber = this.blockchain.getStatus().getBestBlockNumber();

        for (BlockIdentifier bi : message.getBlockIdentifiers()) {
            long height = bi.getNumber();

            if (height > bestNumber) {
                int count = (int)(height - bestNumber);
                sender.sendMessage(new BlockHeadersRequestMessage(++nextId, bi.getHash(), count));
                return;
            }
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

        // to do: decide whether we also want to check the message type
        NodeID expectedNodeId = pendingResponses.remove(message.getId());
        if (sender.getNodeID() != expectedNodeId) {
            // Don't waste time on spam or expired responses.
            return;
        }


        // to do: decide whether we have to request the body immediately if we don't have it,
        // or maybe only after we have validated it
        List<BlockHeader> headers = message.getBlockHeaders();
        for (BlockHeader header : headers) {
            if (this.blockchain.getBlockByHash(header.getHash()) == null) {
                sender.sendMessage(new BlockRequestMessage(++nextId, header.getHash()));
            }
        }
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
    public void expectMessage(long requestId, NodeID nodeID) {
        pendingResponses.put(requestId, nodeID);
    }
}

