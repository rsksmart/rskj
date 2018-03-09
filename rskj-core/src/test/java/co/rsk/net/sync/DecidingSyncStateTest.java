package co.rsk.net.sync;

import co.rsk.net.NodeID;
import co.rsk.net.utils.StatusUtils;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.RskMockFactory;
import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;

public class DecidingSyncStateTest {

    @Test
    public void startsSyncingWith5Peers() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SimpleSyncEventsHandler syncEventsHandler = new SimpleSyncEventsHandler();
        SimpleSyncInformation syncInformation = new SimpleSyncInformation();
        PeersInformation knownPeers = new PeersInformation(syncInformation, RskMockFactory.getChannelManager(), syncConfiguration);
        SyncState syncState = new DecidingSyncState(syncConfiguration, syncEventsHandler, syncInformation, knownPeers);

        for (int i = 0; i < 5; i++) {
            Assert.assertFalse(syncEventsHandler.startSyncingWasCalled());
            knownPeers.registerPeer(new NodeID(HashUtil.randomPeerId())).setStatus(StatusUtils.getFakeStatus());
            syncState.newPeerStatus();
        }

        Assert.assertTrue(syncEventsHandler.startSyncingWasCalled());
    }

    @Test
    public void startsSyncingWith5NonRepeatedPeers() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SimpleSyncEventsHandler syncEventsHandler = new SimpleSyncEventsHandler();
        SimpleSyncInformation syncInformation = new SimpleSyncInformation();
        PeersInformation knownPeers = new PeersInformation(syncInformation, RskMockFactory.getChannelManager(), syncConfiguration);
        SyncState syncState = new DecidingSyncState(syncConfiguration, syncEventsHandler, syncInformation, knownPeers);

        NodeID peerToRepeat = new NodeID(HashUtil.randomPeerId());
        for (int i = 0; i < 10; i++) {
            Assert.assertFalse(syncEventsHandler.startSyncingWasCalled());
            knownPeers.registerPeer(peerToRepeat).setStatus(StatusUtils.getFakeStatus());
            syncState.newPeerStatus();
        }

        for (int i = 0; i < 4; i++) {
            Assert.assertFalse(syncEventsHandler.startSyncingWasCalled());
            knownPeers.registerPeer(new NodeID(HashUtil.randomPeerId())).setStatus(StatusUtils.getFakeStatus());
            syncState.newPeerStatus();
        }

        Assert.assertTrue(syncEventsHandler.startSyncingWasCalled());
    }

    @Test
    public void doesntStartSyncingWithNoPeersAfter2Minutes() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SimpleSyncEventsHandler syncEventsHandler = new SimpleSyncEventsHandler();
        SimpleSyncInformation syncInformation = new SimpleSyncInformation();
        PeersInformation knownPeers = new PeersInformation(syncInformation, RskMockFactory.getChannelManager(), syncConfiguration);
        SyncState syncState = new DecidingSyncState(syncConfiguration, syncEventsHandler, syncInformation, knownPeers);

        syncState.tick(Duration.ofMinutes(2));
        Assert.assertFalse(syncEventsHandler.startSyncingWasCalled());
    }

    @Test
    public void startsSyncingWith1PeerAfter2Minutes() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SimpleSyncEventsHandler syncEventsHandler = new SimpleSyncEventsHandler();
        SimpleSyncInformation syncInformation = new SimpleSyncInformation();
        PeersInformation knownPeers = new PeersInformation(syncInformation, RskMockFactory.getChannelManager(), syncConfiguration);
        SyncState syncState = new DecidingSyncState(syncConfiguration, syncEventsHandler, syncInformation, knownPeers);
        Assert.assertFalse(syncEventsHandler.startSyncingWasCalled());

        knownPeers.registerPeer(new NodeID(HashUtil.randomPeerId()));
        syncState.newPeerStatus();
        syncState.tick(Duration.ofMinutes(2));
        Assert.assertTrue(syncEventsHandler.startSyncingWasCalled());
    }

    @Test
    public void doesntStartSyncingWith1PeerBeforeTimeout() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SimpleSyncEventsHandler syncEventsHandler = new SimpleSyncEventsHandler();
        SimpleSyncInformation syncInformation = new SimpleSyncInformation();
        PeersInformation knownPeers = new PeersInformation(syncInformation, RskMockFactory.getChannelManager(), syncConfiguration);
        SyncState syncState = new DecidingSyncState(syncConfiguration, syncEventsHandler, syncInformation, knownPeers);
        Assert.assertFalse(syncEventsHandler.startSyncingWasCalled());

        knownPeers.registerPeer(new NodeID(HashUtil.randomPeerId()));
        syncState.newPeerStatus();
        syncState.tick(syncConfiguration.getTimeoutWaitingPeers().minusSeconds(1L));
        Assert.assertFalse(syncEventsHandler.startSyncingWasCalled());
    }

    @Test
    public void doesntStartSyncingIfAllPeersHaveLowerDifficulty() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SimpleSyncEventsHandler syncEventsHandler = new SimpleSyncEventsHandler();
        SimpleSyncInformation syncInformation = new SimpleSyncInformation().withWorsePeers();
        PeersInformation knownPeers = new PeersInformation(syncInformation, RskMockFactory.getChannelManager(), syncConfiguration);
        SyncState syncState = new DecidingSyncState(syncConfiguration, syncEventsHandler, syncInformation, knownPeers);
        Assert.assertFalse(syncEventsHandler.startSyncingWasCalled());

        knownPeers.registerPeer(new NodeID(HashUtil.randomPeerId()));
        syncState.newPeerStatus();
        syncState.tick(Duration.ofMinutes(2));
        Assert.assertFalse(syncEventsHandler.startSyncingWasCalled());
    }

    @Test
    public void doesntStartSyncingIfAllPeersHaveBadReputation() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SimpleSyncEventsHandler syncEventsHandler = new SimpleSyncEventsHandler();
        SimpleSyncInformation syncInformation = new SimpleSyncInformation().withBadReputation();
        PeersInformation knownPeers = new PeersInformation(syncInformation, RskMockFactory.getChannelManager(), syncConfiguration);
        SyncState syncState = new DecidingSyncState(syncConfiguration, syncEventsHandler, syncInformation, knownPeers);
        Assert.assertFalse(syncEventsHandler.startSyncingWasCalled());

        knownPeers.registerPeer(new NodeID(HashUtil.randomPeerId()));
        syncState.newPeerStatus();
        syncState.tick(Duration.ofMinutes(2));
        Assert.assertFalse(syncEventsHandler.startSyncingWasCalled());
    }
}
