package co.rsk.net;

import co.rsk.core.bc.BlockChainStatus;
import co.rsk.net.messages.*;
import co.rsk.net.sync.PeersInformation;
import co.rsk.net.sync.SyncConfiguration;
import co.rsk.net.sync.SyncState;
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

/**
 * Created by ajlopez on 16/11/2018.
 */
public class AlternativeSyncProcessor implements SyncProcessor {
    private static final Logger logger = LoggerFactory.getLogger("syncprocessor");

    private final Blockchain blockchain;
    private final PeersInformation peersInformation;
    private final ChannelManager channelManager;
    private final SyncConfiguration syncConfiguration;

    private long messageId;

    public AlternativeSyncProcessor(Blockchain blockchain,
                                    SyncConfiguration syncConfiguration,
                                    ChannelManager channelManager,
                                    PeersInformation peersInformation) {
        this.blockchain = blockchain;
        this.syncConfiguration = syncConfiguration;
        this.peersInformation = peersInformation;
        this.channelManager = channelManager;
    }

    @Override
    public void processStatus(MessageChannel sender, Status status) {
        logger.debug("Receiving syncState from node {} block {} {}", sender.getPeerNodeID(), status.getBestBlockNumber(), HashUtil.shortHash(status.getBestBlockHash()));
        peersInformation.registerPeer(sender.getPeerNodeID()).setStatus(status);

        BlockChainStatus currentStatus = this.blockchain.getStatus();

        if (currentStatus.getTotalDifficulty().compareTo(status.getTotalDifficulty()) >=0) {
            return;
        }

        long number = currentStatus.getBestBlockNumber();

        MessageWithId message = new SkeletonRequestMessage(messageId++, number);

        this.channelManager.sendMessageTo(sender.getPeerNodeID(), message);
    }

    @Override
    public void processSkeletonResponse(MessageChannel peer, SkeletonResponseMessage skeletonResponse) {
        List<BlockIdentifier> blockIdentifiers = skeletonResponse.getBlockIdentifiers();

        for (BlockIdentifier blockIdentifier: blockIdentifiers) {
            byte[] blockHash = blockIdentifier.getHash();

            if (this.blockchain.isBlockExist(blockHash)) {
                continue;
            }

            Message message = new BlockHeadersRequestMessage(this.messageId++, blockHash, this.syncConfiguration.getChunkSize());

            this.channelManager.sendMessageTo(peer.getPeerNodeID(), message);
        }
    }

    @Override
    public void processBlockHashResponse(MessageChannel peer, BlockHashResponseMessage blockHashResponse) {

    }

    @Override
    public void processBlockHeadersResponse(MessageChannel peer, BlockHeadersResponseMessage blockHeadersResponse) {
        List<BlockHeader> headers = blockHeadersResponse.getBlockHeaders();

        for (int k = headers.size(); k-- > 0;) {
            BlockHeader header = headers.get(k);

            byte[] blockHash = header.getHash().getBytes();

            if (this.blockchain.isBlockExist(blockHash)) {
                continue;
            }

            Message message = new GetBlockMessage(blockHash);

            this.channelManager.sendMessageTo(peer.getPeerNodeID(), message);
        }
    }

    @Override
    public void processBodyResponse(MessageChannel peer, BodyResponseMessage message) {

    }

    @Override
    public void processNewBlockHash(MessageChannel peer, NewBlockHashMessage message) {

    }

    @Override
    public void processBlockResponse(MessageChannel peer, BlockResponseMessage message) {

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

    }
}
