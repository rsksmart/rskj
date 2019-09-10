package co.rsk.net.sync;

import co.rsk.crypto.Keccak256;
import co.rsk.net.NodeID;
import co.rsk.scoring.EventType;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.db.BlockStore;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Retrieves the oldest block in the storage and requests the headers that come before.
 */
public class DownloadingBackwardsHeadersSyncState extends BaseSyncState {

    private final Block child;
    private final NodeID selectedPeerId;

    public DownloadingBackwardsHeadersSyncState(
            SyncConfiguration syncConfiguration,
            SyncEventsHandler syncEventsHandler,
            BlockStore blockStore,
            NodeID selectedPeerId) {
        super(syncEventsHandler, syncConfiguration);
        this.selectedPeerId = selectedPeerId;
        this.child = blockStore.getChainBlockByNumber(blockStore.getMinNumber());
    }

    @Override
    public void newBlockHeaders(List<BlockHeader> toRequest) {
        syncEventsHandler.backwardDownloadBodies(
                selectedPeerId,
                child,
                toRequest.stream().skip(1).collect(Collectors.toList()));
    }

    @Override
    public void onEnter() {
        Keccak256 hashToRequest = child.getHash();
        ChunkDescriptor chunkDescriptor = new ChunkDescriptor(
                hashToRequest.getBytes(),
                syncConfiguration.getChunkSize());

        boolean sent = syncEventsHandler.sendBlockHeadersRequest(chunkDescriptor, selectedPeerId);
        if (!sent) {
            syncEventsHandler.onSyncIssue("Channel failed to sent on {} to {}",
                    this.getClass(), selectedPeerId);
        }
    }

    @Override
    protected void onMessageTimeOut() {
        syncEventsHandler.onErrorSyncing(selectedPeerId,
                "Timeout waiting requests {}", EventType.TIMEOUT_MESSAGE, this.getClass(), selectedPeerId);
    }
}
