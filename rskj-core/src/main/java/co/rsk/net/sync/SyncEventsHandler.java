package co.rsk.net.sync;

import co.rsk.net.MessageChannel;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Queue;

public interface SyncEventsHandler {
    void sendSkeletonRequest(long height);

    void sendBlockHashRequest(long height);

    void sendBodyRequest(@Nonnull BlockHeader header);

    void startSyncing(MessageChannel peer);

    void startDownloadingBodies(Queue<BlockHeader> pendingHeaders);

    void startDownloadingHeaders(List<BlockIdentifier> skeleton, long connectionPoint);

    void startDownloadingSkeleton(long connectionPoint);

    void stopSyncing();

    void sendBlockHeadersRequest(ChunkDescriptor chunk);
}
