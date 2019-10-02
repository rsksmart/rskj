package co.rsk.net.sync;


import co.rsk.net.NodeID;
import co.rsk.scoring.EventType;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;

import javax.annotation.Nonnull;
import java.util.Deque;
import java.util.List;
import java.util.Map;

public class SimpleSyncEventsHandler implements SyncEventsHandler {
    private boolean startSyncingWasCalled_;
    private boolean stopSyncingWasCalled_;

    @Override
    public boolean sendBlockHeadersRequest(ChunkDescriptor chunk, NodeID peerId) { return true;}

    @Override
    public void startFindingConnectionPoint(NodeID peerId) {

    }

    @Override
    public void backwardSyncing(NodeID peerId) {

    }

    @Override
    public Long sendBodyRequest(@Nonnull BlockHeader header, NodeID peerId) { return 0L; }

    @Override
    public void startDownloadingBodies(List<Deque<BlockHeader>> pendingHeaders, Map<NodeID, List<BlockIdentifier>> skeletons, NodeID peerId) {

    }

    @Override
    public boolean sendSkeletonRequest(NodeID nodeID, long height) { return true;}

    @Override
    public boolean sendBlockHashRequest(long height, NodeID peerId) {
        return true;
    }

    @Override
    public void startDownloadingHeaders(Map<NodeID, List<BlockIdentifier>> skeletons, long connectionPoint, NodeID peerId) { }

    @Override
    public void startSyncing(NodeID nodeID) {
        this.startSyncingWasCalled_ = true;
    }

    @Override
    public void backwardDownloadBodies(NodeID peerId, Block parent, List<BlockHeader> toRequest) {

    }

    @Override
    public void startDownloadingSkeleton(long connectionPoint, NodeID peerId) { }

    @Override
    public void stopSyncing() { this.stopSyncingWasCalled_ = true; }

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