package co.rsk.net.sync;

import co.rsk.net.MessageChannel;
import co.rsk.scoring.EventType;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Queue;

public interface SyncEventsHandler {
    void sendSkeletonRequest(long height);

    void sendBlockHashRequest(long height);

    void sendBlockHeadersRequest(ChunkDescriptor chunk);

    long sendBodyRequest(@Nonnull BlockHeader header);

    void startDownloadingBodies(Queue<BlockHeader> pendingHeaders);

    void startDownloadingHeaders(List<BlockIdentifier> skeleton, long connectionPoint);

    void startDownloadingSkeleton(long connectionPoint);

    void startSyncing(MessageChannel peer);

    void stopSyncing();

    void onErrorSyncing(String message, EventType eventType, Object... arguments);

    void onCompletedSyncing();

    void startFindingConnectionPoint();
}
