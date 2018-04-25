package co.rsk.net.sync;

import co.rsk.net.NodeID;
import co.rsk.net.utils.StatusUtils;
import org.ethereum.crypto.HashUtil;
import org.ethereum.net.server.Channel;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.util.RskMockFactory;
import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DecidingSyncStateTest {

    @Test
    public void startsSyncingWith5Peers() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SimpleSyncEventsHandler syncEventsHandler = new SimpleSyncEventsHandler();
        SimpleSyncInformation syncInformation = new SimpleSyncInformation();
        ChannelManager channelManager = RskMockFactory.getChannelManager();
        PeersInformation knownPeers = new PeersInformation(syncInformation, channelManager, syncConfiguration);
        SyncState syncState = new DecidingSyncState(syncConfiguration, syncEventsHandler, syncInformation, knownPeers);
        Collection<Channel> peers = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            Assert.assertFalse(syncEventsHandler.startSyncingWasCalled());
            NodeID nodeID = new NodeID(HashUtil.randomPeerId());
            Channel channel = mock(Channel.class);
            when(channel.getNodeId()).thenReturn(nodeID);
            peers.add(channel);
            when(channelManager.getActivePeers()).thenReturn(peers);
            knownPeers.registerPeer(nodeID).setStatus(StatusUtils.getFakeStatus());
            syncState.newPeerStatus();
        }

        Assert.assertTrue(syncEventsHandler.startSyncingWasCalled());
    }

    @Test
    public void startsSyncingWith5NonRepeatedPeers() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SimpleSyncEventsHandler syncEventsHandler = new SimpleSyncEventsHandler();
        SimpleSyncInformation syncInformation = new SimpleSyncInformation();
        ChannelManager channelManager = RskMockFactory.getChannelManager();
        PeersInformation knownPeers = new PeersInformation(syncInformation, channelManager, syncConfiguration);
        SyncState syncState = new DecidingSyncState(syncConfiguration, syncEventsHandler, syncInformation, knownPeers);
        Collection<Channel> peers = new ArrayList<>();

        NodeID peerToRepeat = new NodeID(HashUtil.randomPeerId());
        Channel channel = mock(Channel.class);
        when(channel.getNodeId()).thenReturn(peerToRepeat);
        peers.add(channel);
        for (int i = 0; i < 10; i++) {
            Assert.assertFalse(syncEventsHandler.startSyncingWasCalled());
            knownPeers.registerPeer(peerToRepeat).setStatus(StatusUtils.getFakeStatus());
            syncState.newPeerStatus();
        }

        for (int i = 0; i < 4; i++) {
            Assert.assertFalse(syncEventsHandler.startSyncingWasCalled());
            NodeID nodeID = new NodeID(HashUtil.randomPeerId());
            knownPeers.registerPeer(nodeID).setStatus(StatusUtils.getFakeStatus());
            channel = mock(Channel.class);
            when(channel.getNodeId()).thenReturn(nodeID);
            peers.add(channel);
            when(channelManager.getActivePeers()).thenReturn(peers);
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
        ChannelManager channelManager = RskMockFactory.getChannelManager();
        PeersInformation knownPeers = new PeersInformation(syncInformation, channelManager, syncConfiguration);
        SyncState syncState = new DecidingSyncState(syncConfiguration, syncEventsHandler, syncInformation, knownPeers);
        Assert.assertFalse(syncEventsHandler.startSyncingWasCalled());

        Channel channel = mock(Channel.class);
        NodeID nodeID = new NodeID(HashUtil.randomPeerId());
        when(channel.getNodeId()).thenReturn(nodeID);
        Collection<Channel> peers = Collections.singletonList(channel);
        when(channelManager.getActivePeers()).thenReturn(peers);

        knownPeers.registerPeer(nodeID);
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
