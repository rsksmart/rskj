package co.rsk.net.sync;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class WaitingForPeersSyncStatusTest {
    @Test
    public void switchesToDecidingWith5Peers() {
        SyncStatus status = new WaitingForPeersSyncStatus();
        for (int i = 0; i < 5; i++) {
            Assert.assertEquals(SyncStatuses.WAITING_FOR_PEERS, status.getStatus());
            status = status.newPeerFound(null);
        }

        Assert.assertEquals(SyncStatuses.DECIDING, status.getStatus());
    }

    @Test
    public void switchesToDecidingWith1PeerAfter2Minutes() {
        SyncStatus status = new WaitingForPeersSyncStatus();
        Assert.assertEquals(SyncStatuses.WAITING_FOR_PEERS, status.getStatus());

        status = status.newPeerFound(null);
        status = status.tick(2, TimeUnit.MINUTES);
        Assert.assertEquals(SyncStatuses.DECIDING, status.getStatus());
    }

    @Test
    public void doesntSwitchToDecidingWith1PeerAfter119Seconds() {
        SyncStatus status = new WaitingForPeersSyncStatus();
        Assert.assertEquals(SyncStatuses.WAITING_FOR_PEERS, status.getStatus());

        status = status.newPeerFound(null);
        status = status.tick(119, TimeUnit.SECONDS);
        Assert.assertEquals(SyncStatuses.WAITING_FOR_PEERS, status.getStatus());
    }
}
