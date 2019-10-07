package co.rsk.net.sync;

import co.rsk.net.NodeID;
import co.rsk.net.Peer;
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

    boolean sendBlockHeadersRequest(ChunkDescriptor chunk, NodeID peer);

    Long sendBodyRequest(@Nonnull BlockHeader header, NodeID peer);

    void startDownloadingBodies(List<Deque<BlockHeader>> pendingHeaders, Map<Peer, List<BlockIdentifier>> skeletons, Peer peer);

    void startDownloadingHeaders(Map<Peer, List<BlockIdentifier>> skeletons, long connectionPoint, Peer peer);

    void startDownloadingSkeleton(long connectionPoint, Peer peer);

    void startSyncing(Peer peer);

    void backwardDownloadBodies(Block parent, List<BlockHeader> toRequest, Peer peer);

    void stopSyncing();

    void onErrorSyncing(NodeID peerId, String message, EventType eventType, Object... arguments);

    void onSyncIssue(String message, Object... arguments);

    void startFindingConnectionPoint(Peer peer);

    void backwardSyncing(Peer peer);
}
