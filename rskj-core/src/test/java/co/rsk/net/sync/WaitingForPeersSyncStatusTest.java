package co.rsk.net.sync;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.time.Duration;

public class WaitingForPeersSyncStatusTest {
    @Test
    @Ignore("We're switching to decide mode immediately for now")
    public void switchesToDecidingWith5Peers() {
        SyncStatus status = new WaitingForPeersSyncStatus();
        for (int i = 0; i < 5; i++) {
            Assert.assertEquals(SyncStatuses.WAITING_FOR_PEERS, status.getStatus());
            status = status.newPeerFound();
        }

        Assert.assertEquals(SyncStatuses.DECIDING, status.getStatus());
    }

    @Test
    @Ignore("We're switching to decide mode immediately for now")
    public void switchesToDecidingWith1PeerAfter2Minutes() {
        SyncStatus status = new WaitingForPeersSyncStatus();
        Assert.assertEquals(SyncStatuses.WAITING_FOR_PEERS, status.getStatus());

        status = status.newPeerFound();
        status = status.tick(Duration.ofMinutes(2));
        Assert.assertEquals(SyncStatuses.DECIDING, status.getStatus());
    }

    @Test
    @Ignore("We're switching to decide mode immediately for now")
    public void doesntSwitchToDecidingWith1PeerAfter119Seconds() {
        SyncStatus status = new WaitingForPeersSyncStatus();
        Assert.assertEquals(SyncStatuses.WAITING_FOR_PEERS, status.getStatus());

        status = status.newPeerFound();
        status = status.tick(Duration.ofSeconds(119));
        Assert.assertEquals(SyncStatuses.WAITING_FOR_PEERS, status.getStatus());
    }
}
