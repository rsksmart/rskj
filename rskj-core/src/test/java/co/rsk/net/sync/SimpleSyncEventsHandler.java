package co.rsk.net.sync;


import co.rsk.net.MessageChannel;
import co.rsk.net.NodeID;
import co.rsk.scoring.EventType;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class SimpleSyncEventsHandler implements SyncEventsHandler {
    private boolean startSyncingWasCalled_;
    private boolean stopSyncingWasCalled_;

    @Override
    public void sendBlockHashRequest(long height) { }

    @Override
    public void sendBlockHeadersRequest(ChunkDescriptor chunk) { }

    @Override
    public void onErrorSyncing(String message, EventType eventType, Object... arguments) {
        stopSyncing();
    }

    @Override
    public void onCompletedSyncing() {
        stopSyncing();
    }

    @Override
    public void startFindingConnectionPoint() {

    }

    @Override
    public long sendBodyRequest(@Nonnull BlockHeader header, NodeID peerId) { return 0; }

    @Override
    public void sendSkeletonRequest(MessageChannel peer, long height) { }

    @Override
    public void startDownloadingHeaders(Map<NodeID, List<BlockIdentifier>> skeletons, long connectionPoint) { }

    @Override
    public void startSyncing(MessageChannel peer) {
        this.startSyncingWasCalled_ = true;
    }

    @Override
    public void startDownloadingBodies(List<Stack<BlockHeader>> pendingHeaders, Map<NodeID, List<BlockIdentifier>> skeletons) { }

    @Override
    public void startDownloadingSkeleton(long connectionPoint) { }

    @Override
    public void stopSyncing() { this.stopSyncingWasCalled_ = true; }

    public boolean startSyncingWasCalled() {
        return startSyncingWasCalled_;
    }

    public boolean stopSyncingWasCalled() {
        return stopSyncingWasCalled_;
    }
}