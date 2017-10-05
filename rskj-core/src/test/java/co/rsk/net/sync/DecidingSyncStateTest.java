package co.rsk.net.sync;

import co.rsk.net.simples.SimpleMessageChannel;
import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;

public class DecidingSyncStateTest {

    @Test
    public void switchesToDecidingWith5Peers() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SimpleSyncEventsHandler syncEventsHandler = new SimpleSyncEventsHandler();
        PeersInformation knownPeers = new PeersInformation(syncConfiguration);
        SyncState syncState = new DecidingSyncState(syncConfiguration, syncEventsHandler, knownPeers);

        for (int i = 0; i < 5; i++) {
            Assert.assertFalse(syncEventsHandler.canStartSyncingWasCalled());
            knownPeers.registerPeer(new SimpleMessageChannel());
            syncState.newPeerStatus();
        }

        Assert.assertTrue(syncEventsHandler.canStartSyncingWasCalled());
    }

    @Test
    public void switchesToDecidingWith5NonRepeatedPeers() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SimpleSyncEventsHandler syncEventsHandler = new SimpleSyncEventsHandler();
        PeersInformation knownPeers = new PeersInformation(syncConfiguration);
        SyncState syncState = new DecidingSyncState(syncConfiguration, syncEventsHandler, knownPeers);

        SimpleMessageChannel peerToRepeat = new SimpleMessageChannel();
        for (int i = 0; i < 10; i++) {
            Assert.assertFalse(syncEventsHandler.canStartSyncingWasCalled());
            knownPeers.registerPeer(peerToRepeat);
            syncState.newPeerStatus();
        }

        for (int i = 0; i < 4; i++) {
            Assert.assertFalse(syncEventsHandler.canStartSyncingWasCalled());
            knownPeers.registerPeer(new SimpleMessageChannel());
            syncState.newPeerStatus();
        }

        Assert.assertTrue(syncEventsHandler.canStartSyncingWasCalled());
    }

    @Test
    public void doesntSwitchWithNoPeersAfter2Minutes() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SimpleSyncEventsHandler syncEventsHandler = new SimpleSyncEventsHandler();
        PeersInformation knownPeers = new PeersInformation(syncConfiguration);
        SyncState syncState = new DecidingSyncState(syncConfiguration, syncEventsHandler, knownPeers);

        syncState.tick(Duration.ofMinutes(2));
        Assert.assertFalse(syncEventsHandler.canStartSyncingWasCalled());
    }

    @Test
    public void switchesToDecidingWith1PeerAfter2Minutes() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SimpleSyncEventsHandler syncEventsHandler = new SimpleSyncEventsHandler();
        PeersInformation knownPeers = new PeersInformation(syncConfiguration);
        SyncState syncState = new DecidingSyncState(syncConfiguration, syncEventsHandler, knownPeers);
        Assert.assertFalse(syncEventsHandler.canStartSyncingWasCalled());

        knownPeers.registerPeer(new SimpleMessageChannel());
        syncState.newPeerStatus();
        syncState.tick(Duration.ofMinutes(2));
        Assert.assertTrue(syncEventsHandler.canStartSyncingWasCalled());
    }

    @Test
    public void doesntSwitchToDecidingWith1PeerBeforeTimeout() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SimpleSyncEventsHandler syncEventsHandler = new SimpleSyncEventsHandler();
        PeersInformation knownPeers = new PeersInformation(syncConfiguration);
        SyncState syncState = new DecidingSyncState(syncConfiguration, syncEventsHandler, knownPeers);
        Assert.assertFalse(syncEventsHandler.canStartSyncingWasCalled());

        knownPeers.registerPeer(new SimpleMessageChannel());
        syncState.newPeerStatus();
        syncState.tick(syncConfiguration.getTimeoutWaitingPeers().minusSeconds(1L));
        Assert.assertFalse(syncEventsHandler.canStartSyncingWasCalled());
    }
}
