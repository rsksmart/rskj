package co.rsk.net.sync;

import java.util.concurrent.TimeUnit;

public class SyncStatusHandler {
    private SyncStatus status;

    public SyncStatusHandler() {
        status = new WaitingForPeersSyncStatus();
    }

    public SyncStatuses getStatus() {
        return status.getStatus();
    }

    public void newPeerFound(Object peer) {
        status = status.newPeerFound(peer);
    }

    public void tick(int amount, TimeUnit unit) {
        status = status.tick(amount, unit);
    }
}
