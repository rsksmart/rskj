package co.rsk.net.sync;

import co.rsk.crypto.Keccak256;
import co.rsk.net.Peer;
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
    private final Peer selectedPeer;

    public DownloadingBackwardsHeadersSyncState(
            SyncConfiguration syncConfiguration,
            SyncEventsHandler syncEventsHandler,
            BlockStore blockStore,
            Peer peer) {
        super(syncEventsHandler, syncConfiguration);
        this.selectedPeer = peer;
        this.child = blockStore.getChainBlockByNumber(blockStore.getMinNumber());
    }

    @Override
    public void newBlockHeaders(List<BlockHeader> toRequest) {
        syncEventsHandler.backwardDownloadBodies(
                child, toRequest.stream().skip(1).collect(Collectors.toList()), selectedPeer
        );
    }

    @Override
    public void onEnter() {
        Keccak256 hashToRequest = child.getHash();
        ChunkDescriptor chunkDescriptor = new ChunkDescriptor(
                hashToRequest.getBytes(),
                syncConfiguration.getChunkSize());

        boolean sent = syncEventsHandler.sendBlockHeadersRequest(chunkDescriptor, selectedPeer.getPeerNodeID());
        if (!sent) {
            syncEventsHandler.onSyncIssue("Channel failed to sent on {} to {}",
                    this.getClass(), selectedPeer);
        }
    }

    @Override
    protected void onMessageTimeOut() {
        syncEventsHandler.onErrorSyncing(
                selectedPeer.getPeerNodeID(),
                "Timeout waiting requests {}",
                EventType.TIMEOUT_MESSAGE,
                this.getClass(),
                selectedPeer.getPeerNodeID());
    }
}
