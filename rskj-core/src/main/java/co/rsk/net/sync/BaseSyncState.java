package co.rsk.net.sync;

import co.rsk.net.Peer;
import co.rsk.net.messages.BodyResponseMessage;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;

import java.time.Duration;
import java.util.List;

public abstract class BaseSyncState implements SyncState {
    protected SyncConfiguration syncConfiguration;
    protected SyncEventsHandler syncEventsHandler;

    protected Duration timeElapsed;

    public BaseSyncState(SyncEventsHandler syncEventsHandler, SyncConfiguration syncConfiguration) {
        this.syncEventsHandler = syncEventsHandler;
        this.syncConfiguration = syncConfiguration;

        this.resetTimeElapsed();
    }

    protected void resetTimeElapsed() {
        timeElapsed = Duration.ZERO;
    }

    @Override
    public void tick(Duration duration) {
        timeElapsed = timeElapsed.plus(duration);
        if (timeElapsed.compareTo(syncConfiguration.getTimeoutWaitingRequest()) >= 0) {
            onMessageTimeOut();
        }
    }

    protected void onMessageTimeOut() {
    }

    @Override
    public void newBlockHeaders(List<BlockHeader> chunk) {
    }

    @Override
    public void newBody(BodyResponseMessage message, Peer peer) {
    }

    @Override
    public void newConnectionPointData(byte[] hash) {
    }

    @Override
    public void newPeerStatus() { }

    @Override
    public void newSkeleton(List<BlockIdentifier> skeleton, Peer peer) {
    }

    @Override
    public void onEnter() { }

    @VisibleForTesting
    public void messageSent() {
        resetTimeElapsed();
    }
}
