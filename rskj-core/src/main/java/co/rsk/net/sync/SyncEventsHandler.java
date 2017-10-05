package co.rsk.net.sync;

import co.rsk.net.MessageChannel;

public interface SyncEventsHandler {
    void sendSkeletonRequestTo(long height);

    void sendBlockHashRequestTo(long height);

    void startSyncing(MessageChannel peer);

    void stopSyncing();
}
