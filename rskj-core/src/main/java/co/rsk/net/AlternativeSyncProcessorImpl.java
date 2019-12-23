package co.rsk.net;

import co.rsk.core.bc.BlockChainStatus;
import co.rsk.crypto.Keccak256;
import co.rsk.net.messages.*;
import co.rsk.net.sync.PeersInformation;
import co.rsk.net.sync.SyncConfiguration;
import io.netty.util.internal.ConcurrentSet;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;
import org.ethereum.core.Blockchain;
import org.ethereum.crypto.HashUtil;
import org.ethereum.net.server.ChannelManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Set;

public class AlternativeSyncProcessorImpl implements SyncProcessor {
    private static final Logger logger = LoggerFactory.getLogger("syncprocessor");

    private final Blockchain blockchain;
    private final PeersInformation peersInformation;
    private final SyncConfiguration syncConfiguration;

    private Set<Keccak256> sent = new ConcurrentSet<>();

    private long messageId;
    private boolean paused;
    private boolean stopped;

    public AlternativeSyncProcessorImpl(Blockchain blockchain,
                                        SyncConfiguration syncConfiguration,
                                        PeersInformation peersInformation) {
        this.blockchain = blockchain;
        this.syncConfiguration = syncConfiguration;
        this.peersInformation = peersInformation;
    }

    @Override
    public void processStatus(Peer sender, Status status) {
        logger.debug("Receiving syncState from node {} block {} {}", sender.getPeerNodeID(), status.getBestBlockNumber(), HashUtil.shortHash(status.getBestBlockHash()));
        peersInformation.registerPeer(sender).setStatus(status);

        BlockChainStatus currentStatus = this.blockchain.getStatus();

        if (currentStatus.getTotalDifficulty().compareTo(status.getTotalDifficulty()) >=0) {
            return;
        }

        if (paused || stopped) {
            return;
        }

        long number = currentStatus.getBestBlockNumber();

        MessageWithId message = new SkeletonRequestMessage(messageId++, Math.max(1, number - syncConfiguration.getChunkSize()));

        sender.sendMessage(message);
    }

    @Override
    public void processSkeletonResponse(Peer peer, SkeletonResponseMessage skeletonResponse) {
        if (paused || stopped) {
            return;
        }

        int nsent = 0;

        List<BlockIdentifier> blockIdentifiers = skeletonResponse.getBlockIdentifiers();
        long number = this.blockchain.getStatus().getBestBlock().getNumber();

        for (BlockIdentifier blockIdentifier: blockIdentifiers) {
            byte[] blockHash = blockIdentifier.getHash();
            Keccak256 hash = new Keccak256(blockHash);

            if (blockIdentifier.getNumber() < number && this.blockchain.hasBlockInSomeBlockchain(blockHash)) {
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

            Message message = new GetBlockMessage(blockHash);

            peer.sendMessage(message);
        }
    }

    @Override
    public void processBodyResponse(Peer peer, BodyResponseMessage message) {

    }

    @Override
    public void processNewBlockHash(Peer peer, NewBlockHashMessage message) {

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
