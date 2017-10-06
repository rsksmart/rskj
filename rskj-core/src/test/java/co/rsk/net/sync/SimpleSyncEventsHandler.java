package co.rsk.net.sync;


import co.rsk.net.MessageChannel;
import org.ethereum.core.BlockIdentifier;

import java.util.List;

public class SimpleSyncEventsHandler implements SyncEventsHandler {
    private boolean startSyncingWasCalled_;
    private boolean stopSyncingWasCalled_;

    @Override
    public void sendSkeletonRequest(long height) { }

    @Override
    public void sendBlockHashRequest(long height) { }

    @Override
    public void sendNextBodyRequest() { }

    @Override
    public void startSyncing(MessageChannel peer) {
        this.startSyncingWasCalled_ = true;
    }

    @Override
    public void startRequestingHeaders(List<BlockIdentifier> skeleton, long connectionPoint) { }

    @Override
    public void stopSyncing() { this.stopSyncingWasCalled_ = true; }

    public boolean startSyncingWasCalled() {
        return startSyncingWasCalled_;
    }

    public boolean stopSyncingWasCalled() {
        return stopSyncingWasCalled_;
    }
}