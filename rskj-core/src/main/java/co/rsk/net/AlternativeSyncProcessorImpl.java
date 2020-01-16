package co.rsk.net;

import co.rsk.core.bc.BlockChainStatus;
import co.rsk.crypto.Keccak256;
import co.rsk.net.messages.*;
import co.rsk.net.sync.PeersInformation;
import co.rsk.net.sync.SyncConfiguration;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;
import org.ethereum.core.Blockchain;
import org.ethereum.crypto.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Set;

public class AlternativeSyncProcessorImpl implements SyncProcessor {
    private static final Logger logger = LoggerFactory.getLogger("syncprocessor");
    private static final int BLOCKS_GAP = 10;

    private final Blockchain blockchain;
    private final PeersInformation peersInformation;
    private final BlockNodeInformation nodeInformation;
    private final SyncConfiguration syncConfiguration;

    private long messageId;
    private boolean stopped;

    public AlternativeSyncProcessorImpl(Blockchain blockchain,
                                        SyncConfiguration syncConfiguration,
                                        PeersInformation peersInformation,
                                        BlockNodeInformation nodeInformation) {
        this.blockchain = blockchain;
        this.syncConfiguration = syncConfiguration;
        this.peersInformation = peersInformation;
        this.nodeInformation = nodeInformation;
    }

    @Override
    public void processStatus(Peer sender, Status status) {
        logger.debug("Receiving syncState from node {} block {} {}", sender.getPeerNodeID(), status.getBestBlockNumber(), HashUtil.shortHash(status.getBestBlockHash()));
        peersInformation.registerPeer(sender).setStatus(status);

        BlockChainStatus currentStatus = this.blockchain.getStatus();

        if (currentStatus.getTotalDifficulty().compareTo(status.getTotalDifficulty()) >=0) {
            return;
        }

        if (stopped) {
            return;
        }

        long number = currentStatus.getBestBlockNumber();

        if (Math.abs(number - status.getBestBlockNumber()) <= BLOCKS_GAP) {
            return;
        }

        MessageWithId message = new SkeletonRequestMessage(messageId++, Math.max(1, number - syncConfiguration.getChunkSize()));

        sender.sendMessage(message);
    }

    @Override
    public void processSkeletonResponse(Peer peer, SkeletonResponseMessage skeletonResponse) {
        if (stopped) {
            return;
        }

        int nsent = 0;

        List<BlockIdentifier> blockIdentifiers = skeletonResponse.getBlockIdentifiers();

        for (BlockIdentifier blockIdentifier: blockIdentifiers) {
            byte[] blockHash = blockIdentifier.getHash();

            if (this.blockchain.hasBlockInSomeBlockchain(blockHash)) {
                continue;
            }

            Message message = new BlockHeadersRequestMessage(this.messageId++, blockHash, this.syncConfiguration.getChunkSize());

            peer.sendMessage(message);

            nsent++;

            if (nsent >= 3) {
                break;
            }
        }
    }

    @Override
    public void processBlockHashResponse(Peer peer, BlockHashResponseMessage blockHashResponse) {

    }

    @Override
    public void processBlockHeadersResponse(Peer peer, BlockHeadersResponseMessage blockHeadersResponse) {
        if (stopped) {
            return;
        }

        long number = this.blockchain.getStatus().getBestBlock().getNumber();
        List<BlockHeader> headers = blockHeadersResponse.getBlockHeaders();

        for (int k = headers.size(); k-- > 0;) {
            BlockHeader header = headers.get(k);

            Keccak256 hash = header.getHash();

            byte[] blockHash = hash.getBytes();

            if (header.getNumber() < number && this.blockchain.hasBlockInSomeBlockchain(blockHash)) {
                continue;
            }

            // To prevent relay to other nodes when the block arrives
            // TODO review if near to complete sync
            for (NodeID nodeId : this.peersInformation.knownNodeIds()) {
                this.nodeInformation.addBlockToNode(hash, nodeId);
            }

            Message message = new GetBlockMessage(blockHash);

            peer.sendMessage(message);
        }
    }

    @Override
    public void processBodyResponse(Peer peer, BodyResponseMessage message) {

    }

    @Override
    public void processNewBlockHash(Peer peer, NewBlockHashMessage message) {
        NodeID nodeID = peer.getPeerNodeID();
        logger.debug("Process new block hash from node {} hash {}", nodeID, HashUtil.shortHash(message.getBlockHash()));
        byte[] hash = message.getBlockHash();

        if (this.blockchain.getBlockByHash(hash) == null) {
            peersInformation.getOrRegisterPeer(peer);
            peer.sendMessage(new GetBlockMessage(hash));
        }
    }

    @Override
    public void processBlockResponse(Peer peer, BlockResponseMessage message) {

    }

    @Override
    public Set<NodeID> getKnownPeersNodeIDs() {
        return this.peersInformation.knownNodeIds();
    }

    @Override
    public void onTimePassed(Duration timePassed) {

    }

    @Override
    public void stopSyncing() {
        this.stopped = true;
    }
}
