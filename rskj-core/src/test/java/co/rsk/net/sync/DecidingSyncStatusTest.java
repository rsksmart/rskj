package co.rsk.net.sync;

import co.rsk.net.simples.SimpleNode;
import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;
import java.util.Collections;

public class DecidingSyncStatusTest {
    @Test
    public void switchesToDecidingWith5Peers() {
        SyncStatus status = new DecidingSyncStatus(SyncConfiguration.DEFAULT);
        for (int i = 0; i < 5; i++) {
            Assert.assertEquals(SyncStatuses.DECIDING, status.getStatus());
            SimpleNode peer = SimpleNode.createNode();
            status = status.newPeerStatus(peer.getNodeID(), peer.getFullStatus(), Collections.emptySet());
        }

        Assert.assertEquals(SyncStatuses.FINDING_CONNECTION_POINT, status.getStatus());
    }

    @Test
    public void switchesToDecidingWith5NonRepeatedPeers() {
        SyncStatus status = new DecidingSyncStatus(SyncConfiguration.DEFAULT);
        SimpleNode peerToRepeat = SimpleNode.createNode();
        for (int i = 0; i < 10; i++) {
            Assert.assertEquals(SyncStatuses.DECIDING, status.getStatus());
            status = status.newPeerStatus(peerToRepeat.getNodeID(), peerToRepeat.getFullStatus(), Collections.emptySet());
        }

        for (int i = 0; i < 4; i++) {
            Assert.assertEquals(SyncStatuses.DECIDING, status.getStatus());
            SimpleNode peer = SimpleNode.createNode();
            status = status.newPeerStatus(peer.getNodeID(), peer.getFullStatus(), Collections.emptySet());
        }

        Assert.assertEquals(SyncStatuses.FINDING_CONNECTION_POINT, status.getStatus());
    }

    @Test
    public void doesntSwitchWithNoPeersAfter2Minutes() {
        SyncStatus status = new DecidingSyncStatus(SyncConfiguration.DEFAULT);
        Assert.assertEquals(SyncStatuses.DECIDING, status.getStatus());

        status = status.tick(Duration.ofMinutes(2));
        Assert.assertEquals(SyncStatuses.DECIDING, status.getStatus());
    }

    @Test
    public void switchesToDecidingWith1PeerAfter2Minutes() {
        SyncStatus status = new DecidingSyncStatus(SyncConfiguration.DEFAULT);
        Assert.assertEquals(SyncStatuses.DECIDING, status.getStatus());

        SimpleNode peer = SimpleNode.createNode();
        status = status.newPeerStatus(peer.getNodeID(), peer.getFullStatus(), Collections.emptySet());
        status = status.tick(Duration.ofMinutes(2));
        Assert.assertEquals(SyncStatuses.FINDING_CONNECTION_POINT, status.getStatus());
    }

    @Test
    public void doesntSwitchToDecidingWith1PeerAfter119Seconds() {
        SyncStatus status = new DecidingSyncStatus(SyncConfiguration.DEFAULT);
        Assert.assertEquals(SyncStatuses.DECIDING, status.getStatus());

        SimpleNode peer = SimpleNode.createNode();
        status = status.newPeerStatus(peer.getNodeID(), peer.getFullStatus(), Collections.emptySet());
        status = status.tick(Duration.ofSeconds(119));
        Assert.assertEquals(SyncStatuses.DECIDING, status.getStatus());
    }
}
