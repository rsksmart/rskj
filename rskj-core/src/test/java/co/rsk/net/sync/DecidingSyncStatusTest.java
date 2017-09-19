package co.rsk.net.sync;

import org.junit.Assert;
import org.junit.Test;

public class DecidingSyncStatusTest {
    @Test
    public void itDoesntChangeStatusWithANewPeer() {
        SyncStatus status = new DecidingSyncStatus();
        status = status.newPeerFound();
        Assert.assertEquals(SyncStatuses.DECIDING, status.getStatus());
    }
}
