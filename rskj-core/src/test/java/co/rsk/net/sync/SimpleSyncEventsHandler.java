package co.rsk.net.sync;


public class SimpleSyncEventsHandler implements SyncEventsHandler {
    private boolean canStartSyncingWasCalled_;

    @Override
    public void canStartSyncing() {
        this.canStartSyncingWasCalled_ = true;
    }

    @Override
    public void stopSyncing() {

    }

    public boolean canStartSyncingWasCalled() {
        return canStartSyncingWasCalled_;
    }
}