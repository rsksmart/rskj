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

public interface SyncEventsHandler {
    void sendSkeletonRequest(MessageChannel peer, long height);

    void sendBlockHashRequest(long height);

    void sendBlockHeadersRequest(ChunkDescriptor chunk);

    long sendBodyRequest(@Nonnull BlockHeader header, NodeID peerId);

    void startDownloadingBodies(List<Stack<BlockHeader>> pendingHeaders, Map<NodeID, List<BlockIdentifier>> skeletons);

    void startDownloadingHeaders(Map<NodeID, List<BlockIdentifier>> skeletons, long connectionPoint);

    void startDownloadingSkeleton(long connectionPoint);

    void startSyncing(MessageChannel peer);

    void stopSyncing();

    void onErrorSyncing(String message, EventType eventType, Object... arguments);

    void onCompletedSyncing();

    void startFindingConnectionPoint();
}
