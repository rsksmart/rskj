package co.rsk.net.sync;

import org.junit.Assert;
import org.junit.Test;

public class SyncStatusHandlerTest {
    @Test
    public void startsAsWaitingForPeers() {
        SyncStatusHandler status = new SyncStatusHandler();
        Assert.assertEquals(SyncStatuses.WAITING_FOR_PEERS, status.getStatus());
    }
}