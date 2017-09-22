package co.rsk.net.simples;

import co.rsk.net.sync.SyncStatus;
import co.rsk.net.sync.SyncStatusSetter;

public class SimpleStatusHandler implements SyncStatusSetter {

    private SyncStatus status;

    @Override
    public void setStatus(SyncStatus status) {
        this.status = status;
    }

    public SyncStatus getStatus() {
        return this.status;
    }
}
