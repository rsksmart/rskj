package co.rsk.net.sync;

import co.rsk.net.NodeID;
import co.rsk.net.Peer;
import co.rsk.scoring.EventType;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;

import javax.annotation.Nullable;
import java.util.Deque;
import java.util.List;
import java.util.Map;

public class SimpleSyncEventsHandler implements SyncEventsHandler {
    private boolean startSyncingWasCalled_;
    private boolean stopSyncingWasCalled_;

    @Override
    public void sendBlockHeadersRequest(Peer peer, ChunkDescriptor chunk) {
    }

    @Override
    public void startFindingConnectionPoint(Peer peer) {

    }

    @Override
    public void backwardSyncing(Peer peer) {
    }

    @Override
    public long sendBodyRequest(Peer peer, BlockHeader header) { return 0L; }

    @Override
    public void startDownloadingBodies(List<Deque<BlockHeader>> pendingHeaders, Map<Peer, List<BlockIdentifier>> skeletons, Peer peer) {

    }

    @Override
    public void sendSkeletonRequest(Peer peer, long height) {
    }

    @Override
    public void sendBlockHashRequest(Peer peer, long height) {
    }

    @Override
    public void startDownloadingHeaders(Map<Peer, List<BlockIdentifier>> skeletons, long connectionPoint, Peer peer) { }

    @Override
    public void startSyncing(Peer peer) {
        this.startSyncingWasCalled_ = true;
    }

    @Override
    public void backwardDownloadBodies(Block parent, List<BlockHeader> toRequest, Peer peer) {

    }

    @Override
    public void startDownloadingSkeleton(long connectionPoint, Peer peer) { }

    @Override
    public void stopSyncing() { this.stopSyncingWasCalled_ = true; }

    @Override
    public void onLongSyncUpdate(boolean isSyncing, @Nullable Long peerBestBlockNumber) {

    }

    @Override
    public void onErrorSyncing(NodeID peerId, String message, EventType eventType, Object... arguments) {
        stopSyncing();
    }

    @Override
    public void onSyncIssue(String message, Object... arguments) {

    }

    public boolean startSyncingWasCalled() {
        return startSyncingWasCalled_;
    }

    public boolean stopSyncingWasCalled() {
        return stopSyncingWasCalled_;
    }
}
