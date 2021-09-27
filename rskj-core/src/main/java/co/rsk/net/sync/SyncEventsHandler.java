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

public interface SyncEventsHandler {
    void sendSkeletonRequest(Peer peer, long height);

    void sendBlockHashRequest(Peer peer, long height);

    void sendBlockHeadersRequest(Peer peer, ChunkDescriptor chunk);

    long sendBodyRequest(Peer peer, BlockHeader header);

    void startDownloadingBodies(List<Deque<BlockHeader>> pendingHeaders, Map<Peer, List<BlockIdentifier>> skeletons, Peer peer);

    void startDownloadingHeaders(Map<Peer, List<BlockIdentifier>> skeletons, long connectionPoint, Peer peer);

    void startDownloadingSkeleton(long connectionPoint, Peer peer);

    void startSyncing(Peer peer);

    void backwardDownloadBodies(Block parent, List<BlockHeader> toRequest, Peer peer);

    void stopSyncing();

    void onLongSyncUpdate(boolean isSyncing, @Nullable Long peerBestBlockNumber);

    void onErrorSyncing(NodeID peerId, String message, EventType eventType, Object... arguments);

    void onSyncIssue(String message, Object... arguments);

    void startFindingConnectionPoint(Peer peer);

    void backwardSyncing(Peer peer);
}
