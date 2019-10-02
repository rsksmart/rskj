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

public interface SyncEventsHandler {
    boolean sendSkeletonRequest(NodeID nodeID, long height);

    boolean sendBlockHashRequest(long height, NodeID peerId);

    boolean sendBlockHeadersRequest(ChunkDescriptor chunk, NodeID peerId);

    Long sendBodyRequest(@Nonnull BlockHeader header, NodeID peerId);

    void startDownloadingBodies(List<Deque<BlockHeader>> pendingHeaders, Map<NodeID, List<BlockIdentifier>> skeletons, NodeID peerId);

    void startDownloadingHeaders(Map<NodeID, List<BlockIdentifier>> skeletons, long connectionPoint, NodeID peerId);

    void startDownloadingSkeleton(long connectionPoint, NodeID peerId);

    void startSyncing(NodeID nodeID);

    void backwardDownloadBodies(NodeID peerId, Block parent, List<BlockHeader> toRequest);

    void stopSyncing();

    void onErrorSyncing(NodeID peerId, String message, EventType eventType, Object... arguments);

    void onSyncIssue(String message, Object... arguments);

    void startFindingConnectionPoint(NodeID peerId);

    void backwardSyncing(NodeID peerId);
}
