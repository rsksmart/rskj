package co.rsk.net.sync;

import co.rsk.net.simples.SimpleNode;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.time.Duration;
import java.util.Collections;

public class WaitingForPeersSyncStatusTest {
    @Test
    @Ignore("We're switching to decide mode immediately for now")
    public void switchesToDecidingWith5Peers() {
        PeersInformation peersInformation = new PeersInformation();
        SyncStatus status = new WaitingForPeersSyncStatus(peersInformation);
        for (int i = 0; i < 5; i++) {
            Assert.assertEquals(SyncStatuses.WAITING_FOR_PEERS, status.getStatus());
            SimpleNode peer = SimpleNode.createNode();
            status = status.newPeerStatus(peer.getNodeID(), peer.getFullStatus(), Collections.emptySet());
        }

        Assert.assertEquals(SyncStatuses.DECIDING, status.getStatus());
    }

    @Test
    @Ignore("We're switching to decide mode immediately for now")
    public void switchesToDecidingWith1PeerAfter2Minutes() {
        PeersInformation peersInformation = new PeersInformation();
        SyncStatus status = new WaitingForPeersSyncStatus(peersInformation);
        Assert.assertEquals(SyncStatuses.WAITING_FOR_PEERS, status.getStatus());

        SimpleNode peer = SimpleNode.createNode();
        status = status.newPeerStatus(peer.getNodeID(), peer.getFullStatus(), Collections.emptySet());
        status = status.tick(Duration.ofMinutes(2));
        Assert.assertEquals(SyncStatuses.DECIDING, status.getStatus());
    }

    @Test
    @Ignore("We're switching to decide mode immediately for now")
    public void doesntSwitchToDecidingWith1PeerAfter119Seconds() {
        PeersInformation peersInformation = new PeersInformation();
        SyncStatus status = new WaitingForPeersSyncStatus(peersInformation);
        Assert.assertEquals(SyncStatuses.WAITING_FOR_PEERS, status.getStatus());

        SimpleNode peer = SimpleNode.createNode();
        status = status.newPeerStatus(peer.getNodeID(), peer.getFullStatus(), Collections.emptySet());
        status = status.tick(Duration.ofSeconds(119));
        Assert.assertEquals(SyncStatuses.WAITING_FOR_PEERS, status.getStatus());
    }
}
