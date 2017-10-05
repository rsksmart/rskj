package co.rsk.net.sync;

public interface SyncEventsHandler {
    void sendSkeletonRequestTo(long height);

    void sendBlockHashRequestTo(long height);

    void canStartSyncing();

    void stopSyncing();
}
