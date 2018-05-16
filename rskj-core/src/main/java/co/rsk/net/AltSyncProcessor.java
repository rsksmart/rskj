package co.rsk.net;

import co.rsk.config.RskSystemProperties;
import co.rsk.core.bc.BlockChainStatus;
import co.rsk.net.messages.*;
import co.rsk.net.sync.SyncConfiguration;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;
import org.ethereum.core.Blockchain;
import org.ethereum.net.server.ChannelManager;

import java.util.*;

/**
 * Created by ajlopez on 19/03/2018.
 */
public class AltSyncProcessor {
    private final RskSystemProperties config;
    private final Blockchain blockchain;
    private final BlockSyncService blockSyncService;
    private final SyncConfiguration syncConfiguration;
    private final ChannelManager channelManager;

    private long messageId;

    public AltSyncProcessor(RskSystemProperties config,
                            Blockchain blockchain,
                            BlockSyncService blockSyncService,
                            SyncConfiguration syncConfiguration,
                            ChannelManager channelManager) {
        this.config = config;
        this.blockchain = blockchain;
        this.blockSyncService = blockSyncService;
        this.syncConfiguration = syncConfiguration;
        this.channelManager = channelManager;
    }

    public Set<NodeID> getKnownPeersNodeIDs() {
        return new HashSet<>();
    }

    public void processStatus(NodeID sender, Status status) {
        BlockChainStatus currentStatus = this.blockchain.getStatus();

        if (currentStatus.getTotalDifficulty().compareTo(status.getTotalDifficulty()) >=0) {
            return;
        }

        long number = currentStatus.getBestBlockNumber();

        MessageWithId message = new SkeletonRequestMessage(messageId++, number);

        this.channelManager.sendMessageTo(sender, message);
    }

    public void processSkeletonResponse(NodeID sender, SkeletonResponseMessage skeletonResponse) {
        List<BlockIdentifier> blockIdentifiers = skeletonResponse.getBlockIdentifiers();

        for (BlockIdentifier blockIdentifier: blockIdentifiers) {
            byte[] blockHash = blockIdentifier.getHash();

            if (this.blockchain.isBlockExist(blockHash)) {
                continue;
            }

            MessageWithId message = new BlockHeadersRequestMessage(this.messageId++, blockHash, this.syncConfiguration.getChunkSize());

            this.channelManager.sendMessageTo(sender, message);
        }
    }

    public void processBlockHeadersResponse(NodeID sender, BlockHeadersResponseMessage blockHeadersResponse) {
        List<BlockHeader> headers = blockHeadersResponse.getBlockHeaders();

        for (int k = headers.size(); k-- > 0;) {
            BlockHeader header = headers.get(k);

            byte[] blockHash = header.getHash().getBytes();

            if (this.blockchain.isBlockExist(blockHash)) {
                continue;
            }

            Message message = new GetBlockMessage(blockHash);

            this.channelManager.sendMessageTo(sender, message);
        }
    }
}
