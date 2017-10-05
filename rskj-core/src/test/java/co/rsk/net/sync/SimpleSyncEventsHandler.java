package co.rsk.net.sync;


public class SimpleSyncEventsHandler implements SyncEventsHandler {
    private boolean canStartSyncingWasCalled_;
    private boolean stopSyncingWasCalled_;

    @Override
    public void sendSkeletonRequestTo(long height) { }

    @Override
    public void sendBlockHashRequestTo(long height) { }

    @Override
    public void canStartSyncing() {
        this.canStartSyncingWasCalled_ = true;
    }

    @Override
    public void stopSyncing() { this.stopSyncingWasCalled_ = true; }

    public boolean canStartSyncingWasCalled() {
        return canStartSyncingWasCalled_;
    }

    public boolean stopSyncingWasCalled() {
        return stopSyncingWasCalled_;
    }
}