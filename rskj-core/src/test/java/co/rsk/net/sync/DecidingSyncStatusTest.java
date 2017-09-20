package co.rsk.net.sync;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

public class DecidingSyncStatusTest {
    @Test
    public void itIgnoresNewPeerInformation() {
        SyncStatus status = new DecidingSyncStatus();
        status = status.newPeerStatus(null, null, Collections.emptySet());
        Assert.assertEquals(SyncStatuses.DECIDING, status.getStatus());
    }
}
