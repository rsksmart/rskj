package co.rsk.net.sync;


import co.rsk.net.MessageChannel;

public class SimpleSyncEventsHandler implements SyncEventsHandler {
    private boolean startSyncingWasCalled_;
    private boolean stopSyncingWasCalled_;

    @Override
    public void sendSkeletonRequestTo(long height) { }

    @Override
    public void sendBlockHashRequestTo(long height) { }

    @Override
    public void startSyncing(MessageChannel peer) {
        this.startSyncingWasCalled_ = true;
    }

    @Override
    public void stopSyncing() { this.stopSyncingWasCalled_ = true; }

    public boolean startSyncingWasCalled() {
        return startSyncingWasCalled_;
    }

    public boolean stopSyncingWasCalled() {
        return stopSyncingWasCalled_;
    }
}