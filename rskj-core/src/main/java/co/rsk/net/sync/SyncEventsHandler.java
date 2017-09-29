package co.rsk.net.sync;

public interface SyncEventsHandler {
    void canStartSyncing();

    void stopSyncing();
}
