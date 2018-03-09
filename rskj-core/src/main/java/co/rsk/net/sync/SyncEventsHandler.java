package co.rsk.net.sync;

import co.rsk.net.NodeID;
import co.rsk.scoring.EventType;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;

import javax.annotation.Nonnull;
import java.util.Deque;
import java.util.List;
import java.util.Map;

public interface SyncEventsHandler {
    boolean sendSkeletonRequest(NodeID nodeID, long height);

    boolean sendBlockHashRequest(long height);

    boolean sendBlockHeadersRequest(ChunkDescriptor chunk);

    Long sendBodyRequest(@Nonnull BlockHeader header, NodeID peerId);

    void startDownloadingBodies(List<Deque<BlockHeader>> pendingHeaders, Map<NodeID, List<BlockIdentifier>> skeletons);

    void startDownloadingHeaders(Map<NodeID, List<BlockIdentifier>> skeletons, long connectionPoint);

    void startDownloadingSkeleton(long connectionPoint);

    void startSyncing(NodeID nodeID);

    void stopSyncing();

    void onSyncIssue(String message, Object... arguments);

    void onErrorSyncing(String message, EventType eventType, Object... arguments);

    void onCompletedSyncing();

    void startFindingConnectionPoint();
}
