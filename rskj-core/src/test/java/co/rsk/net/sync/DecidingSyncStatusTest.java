package co.rsk.net.sync;

import co.rsk.net.simples.SimpleNode;
import co.rsk.net.simples.SimpleStatusHandler;
import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;
import java.util.Collections;

public class DecidingSyncStatusTest {

    @Test
    public void switchesToDecidingWith5Peers() {
        SimpleStatusHandler statusHandler = new SimpleStatusHandler();
        statusHandler.setStatus(new DecidingSyncStatus(SyncConfiguration.DEFAULT));
        for (int i = 0; i < 5; i++) {
            SyncStatus status = statusHandler.getStatus();
            Assert.assertEquals(SyncStatusIds.DECIDING, statusHandler.getStatus().getId());
            SimpleNode peer = SimpleNode.createNode();
            status.newPeerStatus(
                    statusHandler,
                    peer.getNodeID(),
                    peer.getFullStatus(),
                    Collections.emptySet());
        }

        Assert.assertEquals(SyncStatusIds.FINDING_CONNECTION_POINT, statusHandler.getStatus().getId());
    }

    @Test
    public void switchesToDecidingWith5NonRepeatedPeers() {
        SimpleStatusHandler statusHandler = new SimpleStatusHandler();
        statusHandler.setStatus(new DecidingSyncStatus(SyncConfiguration.DEFAULT));
        SimpleNode peerToRepeat = SimpleNode.createNode();
        for (int i = 0; i < 10; i++) {
            SyncStatus status = statusHandler.getStatus();
            Assert.assertEquals(SyncStatusIds.DECIDING, status.getId());
            status.newPeerStatus(statusHandler, peerToRepeat.getNodeID(), peerToRepeat.getFullStatus(), Collections.emptySet());
        }

        for (int i = 0; i < 4; i++) {
            SyncStatus status = statusHandler.getStatus();
            Assert.assertEquals(SyncStatusIds.DECIDING, status.getId());
            SimpleNode peer = SimpleNode.createNode();
            status.newPeerStatus(statusHandler, peer.getNodeID(), peer.getFullStatus(), Collections.emptySet());
        }

        Assert.assertEquals(SyncStatusIds.FINDING_CONNECTION_POINT, statusHandler.getStatus().getId());
    }

    @Test
    public void doesntSwitchWithNoPeersAfter2Minutes() {
        SimpleStatusHandler statusHandler = new SimpleStatusHandler();
        statusHandler.setStatus(new DecidingSyncStatus(SyncConfiguration.DEFAULT));
        Assert.assertEquals(SyncStatusIds.DECIDING, statusHandler.getStatus().getId());

        statusHandler.getStatus().tick(statusHandler, Duration.ofMinutes(2), Collections.emptySet());
        Assert.assertEquals(SyncStatusIds.DECIDING, statusHandler.getStatus().getId());
    }

    @Test
    public void switchesToDecidingWith1PeerAfter2Minutes() {
        SimpleStatusHandler statusHandler = new SimpleStatusHandler();
        statusHandler.setStatus(new DecidingSyncStatus(SyncConfiguration.DEFAULT));
        Assert.assertEquals(SyncStatusIds.DECIDING, statusHandler.getStatus().getId());

        SimpleNode peer = SimpleNode.createNode();
        statusHandler.getStatus().newPeerStatus(statusHandler, peer.getNodeID(), peer.getFullStatus(), Collections.emptySet());
        statusHandler.getStatus().tick(statusHandler, Duration.ofMinutes(2), Collections.emptySet());
        Assert.assertEquals(SyncStatusIds.FINDING_CONNECTION_POINT, statusHandler.getStatus().getId());
    }

    @Test
    public void doesntSwitchToDecidingWith1PeerAfter119Seconds() {
        SimpleStatusHandler statusHandler = new SimpleStatusHandler();
        statusHandler.setStatus(new DecidingSyncStatus(SyncConfiguration.DEFAULT));
        Assert.assertEquals(SyncStatusIds.DECIDING, statusHandler.getStatus().getId());

        SimpleNode peer = SimpleNode.createNode();
        statusHandler.getStatus().newPeerStatus(statusHandler, peer.getNodeID(), peer.getFullStatus(), Collections.emptySet());
        statusHandler.getStatus().tick(statusHandler, Duration.ofSeconds(119), Collections.emptySet());
        Assert.assertEquals(SyncStatusIds.DECIDING, statusHandler.getStatus().getId());
    }
}
