package co.rsk.net.sync;


import co.rsk.net.MessageChannel;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Queue;

public class SimpleSyncEventsHandler implements SyncEventsHandler {
    private boolean startSyncingWasCalled_;
    private boolean stopSyncingWasCalled_;

    @Override
    public void sendBlockHashRequest(long height) { }

    @Override
    public void sendNextBlockHeadersRequest() { }

    @Override
    public void sendNextBodyRequest(@Nonnull BlockHeader header) { }

    @Override
    public void sendSkeletonRequest(long height) { }

    @Override
    public void startRequestingHeaders(List<BlockIdentifier> skeleton, long connectionPoint) { }

    @Override
    public void startSyncing(MessageChannel peer) {
        this.startSyncingWasCalled_ = true;
    }

    @Override
    public void startDownloadingBodies(Queue<BlockHeader> pendingHeaders) { }

    @Override
    public void stopSyncing() { this.stopSyncingWasCalled_ = true; }

    public boolean startSyncingWasCalled() {
        return startSyncingWasCalled_;
    }

    public boolean stopSyncingWasCalled() {
        return stopSyncingWasCalled_;
    }
}