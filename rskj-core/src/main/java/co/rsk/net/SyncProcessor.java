package co.rsk.net;

import co.rsk.core.bc.BlockChainStatus;
import co.rsk.net.messages.BlockHashRequestMessage;
import co.rsk.net.messages.SkeletonRequestMessage;
import org.ethereum.core.Blockchain;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ajlopez on 29/08/2017.
 */
public class SyncProcessor {
    private long nextId;
    private Blockchain blockchain;
    private Map<NodeID, SyncPeerStatus> peers = new HashMap<>();

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

        for (SyncPeerStatus peer : this.peers.values())
            if (peer.getStatus().getTotalDifficulty().compareTo(totalDifficulty) > 0)
                count++;

        return count;
    }

    public void processStatus(MessageSender sender, Status status) {
        peers.put(sender.getNodeID(), new SyncPeerStatus(sender, status));
    }

    public void sendSkeletonRequest(MessageSender sender, long height) {
        sender.sendMessage(new SkeletonRequestMessage(++nextId, height));
    }

    public void sendBlockHashRequest(MessageSender sender, long height) {
        sender.sendMessage(new BlockHashRequestMessage(++nextId, height));
    }
}
