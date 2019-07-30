package co.rsk.net.sync;

import co.rsk.net.NodeID;
import co.rsk.scoring.PeerScoringManager;
import org.ethereum.core.Blockchain;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.RskMockFactory;
import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DecidingSyncStateTest {

    @Test
    public void startsSyncingWith5Peers() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SimpleSyncEventsHandler syncEventsHandler = new SimpleSyncEventsHandler();

        PeersInformation peersInformation = mock(PeersInformation.class);
        when(peersInformation.count()).thenReturn(1,2,3,4,5);

        SyncState syncState = new DecidingSyncState(syncConfiguration, syncEventsHandler, peersInformation);
        when(peersInformation.getBestPeer()).thenReturn(Optional.of(mock(NodeID.class)));

        for (int i = 0; i < 5; i++) {
            Assert.assertFalse(syncEventsHandler.startSyncingWasCalled());
            syncState.newPeerStatus();
        }

        Assert.assertTrue(syncEventsHandler.startSyncingWasCalled());
    }

    @Test
    public void doesntStartSyncingWithNoPeersAfter2Minutes() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SimpleSyncEventsHandler syncEventsHandler = new SimpleSyncEventsHandler();

        PeersInformation knownPeers = mock(PeersInformation.class);
        when(knownPeers.count()).thenReturn(0);
        when(knownPeers.getBestPeer()).thenReturn(Optional.empty());

        SyncState syncState = new DecidingSyncState(syncConfiguration, syncEventsHandler, knownPeers);

        syncState.tick(Duration.ofMinutes(2));
        Assert.assertFalse(syncEventsHandler.startSyncingWasCalled());
    }

    @Test
    public void startsSyncingWith1PeerAfter2Minutes() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SimpleSyncEventsHandler syncEventsHandler = new SimpleSyncEventsHandler();

        PeersInformation knownPeers = mock(PeersInformation.class);
        when(knownPeers.count()).thenReturn(1);
        when(knownPeers.getBestPeer()).thenReturn(Optional.of(mock(NodeID.class)));

        SyncState syncState = new DecidingSyncState(syncConfiguration, syncEventsHandler, knownPeers);
        Assert.assertFalse(syncEventsHandler.startSyncingWasCalled());

        syncState.newPeerStatus();
        syncState.tick(Duration.ofMinutes(2));
        Assert.assertTrue(syncEventsHandler.startSyncingWasCalled());
    }

    @Test
    public void doesntStartSyncingWith1PeerBeforeTimeout() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SimpleSyncEventsHandler syncEventsHandler = new SimpleSyncEventsHandler();
        PeerScoringManager peerScoringManager = RskMockFactory.getPeerScoringManager();
        Blockchain blockchain = mock(Blockchain.class);
        Map<NodeID, Instant> failedPeers = new LinkedHashMap<>();

        PeersInformation knownPeers = new PeersInformation(RskMockFactory.getChannelManager(),
                syncConfiguration, blockchain, peerScoringManager);
        SyncState syncState = new DecidingSyncState(syncConfiguration, syncEventsHandler, knownPeers);
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
        PeerScoringManager peerScoringManager = RskMockFactory.getPeerScoringManager();
        Blockchain blockchain = mock(Blockchain.class);
        Map<NodeID, Instant> failedPeers = new LinkedHashMap<>();

        PeersInformation knownPeers = new PeersInformation(RskMockFactory.getChannelManager(),
                syncConfiguration, blockchain, peerScoringManager);
        SyncState syncState = new DecidingSyncState(syncConfiguration, syncEventsHandler, knownPeers);
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
        PeerScoringManager peerScoringManager = RskMockFactory.getPeerScoringManager();
        Blockchain blockchain = mock(Blockchain.class);
        Map<NodeID, Instant> failedPeers = new LinkedHashMap<>();

        PeersInformation knownPeers = new PeersInformation(RskMockFactory.getChannelManager(),
                syncConfiguration, blockchain, peerScoringManager);
        SyncState syncState = new DecidingSyncState(syncConfiguration, syncEventsHandler, knownPeers);
        Assert.assertFalse(syncEventsHandler.startSyncingWasCalled());

        knownPeers.registerPeer(new NodeID(HashUtil.randomPeerId()));
        syncState.newPeerStatus();
        syncState.tick(Duration.ofMinutes(2));
        Assert.assertFalse(syncEventsHandler.startSyncingWasCalled());
    }
}
