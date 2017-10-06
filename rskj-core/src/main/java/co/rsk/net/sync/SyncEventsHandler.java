package co.rsk.net.sync;

import co.rsk.net.MessageChannel;
import org.ethereum.core.BlockIdentifier;

import java.util.List;

public interface SyncEventsHandler {
    void sendSkeletonRequest(long height);

    void sendBlockHashRequest(long height);

    void startSyncing(MessageChannel peer);

    void startRequestingHeaders(List<BlockIdentifier> skeleton, long connectionPoint);

    void stopSyncing();
}
