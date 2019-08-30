package co.rsk.net.syncrefactor;

import co.rsk.crypto.Keccak256;
import co.rsk.net.NodeID;
import co.rsk.net.sync.PeersInformation;
import co.rsk.net.sync.SyncConfiguration;
import co.rsk.scoring.EventType;
import co.rsk.validators.BlockHeaderValidationRule;
import org.ethereum.core.BlockHeader;
import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class CheckingBestHeaderSyncState implements SyncState {

    private static final Logger logger  = LoggerFactory.getLogger("syncprocessor");

    private final SyncStateFactory factory;
    private final BlockHeaderValidationRule blockHeaderValidationRule;
    private final PeersInformation peersInformation;
    private final SyncMessager syncMessager;
    private final NodeID selectedPeerId;
    private final Keccak256 bestBlockHash;

    public CheckingBestHeaderSyncState(
            SyncStateFactory syncStateFactory,
            SyncMessager syncMessager,
            PeersInformation peersInformation,
            BlockHeaderValidationRule blockHeaderValidationRule,
            NodeID selectedPeerId,
            Keccak256 bestBlockHash) {
        this.factory = syncStateFactory;
        this.blockHeaderValidationRule = blockHeaderValidationRule;
        this.peersInformation = peersInformation;
        this.syncMessager = syncMessager;
        this.selectedPeerId = selectedPeerId;
        this.bestBlockHash = bestBlockHash;
    }

    private CompletableFuture<SyncState> newBlockHeaders(List<BlockHeader> chunk){
        BlockHeader header = chunk.get(0);
        if (!ByteUtil.fastEquals(header.getHash().getBytes(), bestBlockHash.getBytes()) ||
                !blockHeaderValidationRule.isValid(header)) {

            logger.warn("Invalid chunk received from node {}", selectedPeerId);
            peersInformation.reportEvent(selectedPeerId, EventType.INVALID_HEADER);
            return CompletableFuture.completedFuture(factory.newDecidingSyncState());
        }

        return CompletableFuture
                .completedFuture(factory.newDecidingSyncState());
    }

    @Override
    public SyncState execute() {
        syncMessager.requestBlockHeaders(selectedPeerId, bestBlockHash, 1)
                .thenCompose(m -> newBlockHeaders(m.getBlockHeaders())/*deciding*/)
                .exceptionally(e -> this);
        return this;
    }
}
