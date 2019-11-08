package co.rsk.net.sync;

import co.rsk.net.NodeID;
import co.rsk.net.Peer;
import co.rsk.net.Status;
import co.rsk.net.simples.SimplePeer;
import co.rsk.scoring.PeerScoringManager;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.BlockStore;
import org.ethereum.util.RskMockFactory;
import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class DecidingSyncStateTest {

    @Test
    public void startsSyncingWith5Peers() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SimpleSyncEventsHandler syncEventsHandler = new SimpleSyncEventsHandler();

        PeersInformation peersInformation = mock(PeersInformation.class);
        when(peersInformation.count()).thenReturn(1,2,3,4,5);

        BlockStore blockStore = mock(BlockStore.class);
        Block bestBlock = mock(Block.class);
        when(blockStore.getBestBlock()).thenReturn(bestBlock);
        when(bestBlock.getNumber()).thenReturn(100L);

        SyncState syncState = new DecidingSyncState(syncConfiguration, syncEventsHandler, peersInformation, blockStore);
        when(peersInformation.getBestPeer()).thenReturn(Optional.of(mock(Peer.class)));

        Status status = mock(Status.class);
        SyncPeerStatus bpStatus = mock(SyncPeerStatus.class);
        when(bpStatus.getStatus()).thenReturn(status);
        when(peersInformation.getPeer(any())).thenReturn(bpStatus);

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

        SyncState syncState = new DecidingSyncState(syncConfiguration, syncEventsHandler, knownPeers, mock(BlockStore.class));

        syncState.tick(Duration.ofMinutes(2));
        Assert.assertFalse(syncEventsHandler.startSyncingWasCalled());
    }

    @Test
    public void startsSyncingWith1PeerAfter2Minutes() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SyncEventsHandler syncEventsHandler = mock(SyncEventsHandler.class);

        PeersInformation peersInformation = mock(PeersInformation.class);
        when(peersInformation.count()).thenReturn(1);
        when(peersInformation.getBestPeer()).thenReturn(Optional.of(mock(Peer.class)));

        Status status = mock(Status.class);
        SyncPeerStatus bpStatus = mock(SyncPeerStatus.class);
        when(bpStatus.getStatus()).thenReturn(status);
        when(peersInformation.getPeer(any())).thenReturn(bpStatus);


        BlockStore blockStore = mock(BlockStore.class);
        Block bestBlock = mock(Block.class);
        when(blockStore.getBestBlock()).thenReturn(bestBlock);
        when(bestBlock.getNumber()).thenReturn(100L);
        SyncState syncState = new DecidingSyncState(
                syncConfiguration,
                syncEventsHandler,
                peersInformation,
                blockStore);

        verify(syncEventsHandler, never()).startSyncing(any());

        syncState.tick(Duration.ofMinutes(2));

        verify(syncEventsHandler).startSyncing(any());
    }

    @Test
    public void doesntStartSyncingWith1PeerBeforeTimeout() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SimpleSyncEventsHandler syncEventsHandler = new SimpleSyncEventsHandler();
        PeerScoringManager peerScoringManager = RskMockFactory.getPeerScoringManager();
        Blockchain blockchain = mock(Blockchain.class);

        PeersInformation knownPeers = new PeersInformation(RskMockFactory.getChannelManager(),
                syncConfiguration, blockchain, peerScoringManager);
        SyncState syncState = new DecidingSyncState(syncConfiguration, syncEventsHandler, knownPeers, mock(BlockStore.class));
        Assert.assertFalse(syncEventsHandler.startSyncingWasCalled());

        knownPeers.registerPeer(new SimplePeer(new NodeID(HashUtil.randomPeerId())));
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

        PeersInformation knownPeers = new PeersInformation(RskMockFactory.getChannelManager(),
                syncConfiguration, blockchain, peerScoringManager);
        SyncState syncState = new DecidingSyncState(syncConfiguration, syncEventsHandler, knownPeers, mock(BlockStore.class));
        Assert.assertFalse(syncEventsHandler.startSyncingWasCalled());

        knownPeers.registerPeer(new SimplePeer(new NodeID(HashUtil.randomPeerId())));
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

        PeersInformation knownPeers = new PeersInformation(RskMockFactory.getChannelManager(),
                syncConfiguration, blockchain, peerScoringManager);
        SyncState syncState = new DecidingSyncState(syncConfiguration, syncEventsHandler, knownPeers, mock(BlockStore.class));
        Assert.assertFalse(syncEventsHandler.startSyncingWasCalled());

        knownPeers.registerPeer(new SimplePeer(new NodeID(HashUtil.randomPeerId())));
        syncState.newPeerStatus();
        syncState.tick(Duration.ofMinutes(2));
        Assert.assertFalse(syncEventsHandler.startSyncingWasCalled());
    }

    @Test
    public void backwardsSynchronization() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        PeersInformation peersInformation = mock(PeersInformation.class);
        SyncEventsHandler syncEventsHandler = mock(SyncEventsHandler.class);
        BlockStore blockStore = mock(BlockStore.class);
        SyncState syncState = new DecidingSyncState(syncConfiguration,
                syncEventsHandler,
                peersInformation,
                blockStore);

        when(peersInformation.count()).thenReturn(syncConfiguration.getExpectedPeers() + 1);
        Peer peer = mock(Peer.class);
        when(peersInformation.getBestPeer()).thenReturn(Optional.of(peer));

        SyncPeerStatus syncPeerStatus = mock(SyncPeerStatus.class);
        Status status = mock(Status.class);
        when(syncPeerStatus.getStatus()).thenReturn(status);
        when(peersInformation.getPeer(peer)).thenReturn(syncPeerStatus);

        when(blockStore.getMinNumber()).thenReturn(1L);
        Block block = mock(Block.class);
        long myBestBlockNumber = 90L;
        when(block.getNumber()).thenReturn(myBestBlockNumber);
        when(blockStore.getBestBlock()).thenReturn(block);
        when(status.getBestBlockNumber()).thenReturn(myBestBlockNumber + syncConfiguration.getLongSyncLimit());

        syncState.newPeerStatus();

        verify(syncEventsHandler).backwardSyncing(peer);
    }

    @Test
    public void forwardsSynchronization_genesisIsConnected() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        PeersInformation peersInformation = mock(PeersInformation.class);
        SyncEventsHandler syncEventsHandler = mock(SyncEventsHandler.class);
        BlockStore blockStore = mock(BlockStore.class);
        SyncState syncState = new DecidingSyncState(syncConfiguration,
                syncEventsHandler,
                peersInformation,
                blockStore);

        when(peersInformation.count()).thenReturn(syncConfiguration.getExpectedPeers() + 1);
        Peer peer = mock(Peer.class);
        when(peersInformation.getBestPeer()).thenReturn(Optional.of(peer));

        SyncPeerStatus syncPeerStatus = mock(SyncPeerStatus.class);
        Status status = mock(Status.class);
        when(syncPeerStatus.getStatus()).thenReturn(status);
        when(peersInformation.getPeer(peer)).thenReturn(syncPeerStatus);

        when(blockStore.getMinNumber()).thenReturn(0L);
        Block block = mock(Block.class);
        long myBestBlockNumber = 90L;
        when(block.getNumber()).thenReturn(myBestBlockNumber);
        when(blockStore.getBestBlock()).thenReturn(block);
        when(status.getBestBlockNumber()).thenReturn(myBestBlockNumber + 1);

        syncState.newPeerStatus();

        verify(syncEventsHandler).startSyncing(peer);
    }

    @Test
    public void forwardsSynchronization() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        PeersInformation peersInformation = mock(PeersInformation.class);
        SyncEventsHandler syncEventsHandler = mock(SyncEventsHandler.class);
        BlockStore blockStore = mock(BlockStore.class);
        SyncState syncState = new DecidingSyncState(syncConfiguration,
                syncEventsHandler,
                peersInformation,
                blockStore);

        when(peersInformation.count()).thenReturn(syncConfiguration.getExpectedPeers() + 1);
        Peer peer = mock(Peer.class);
        when(peersInformation.getBestPeer()).thenReturn(Optional.of(peer));

        SyncPeerStatus syncPeerStatus = mock(SyncPeerStatus.class);
        Status status = mock(Status.class);
        when(syncPeerStatus.getStatus()).thenReturn(status);
        when(peersInformation.getPeer(peer)).thenReturn(syncPeerStatus);

        Block block = mock(Block.class);
        long myBestBlockNumber = 90L;
        when(block.getNumber()).thenReturn(myBestBlockNumber);
        when(blockStore.getBestBlock()).thenReturn(block);
        when(blockStore.getMinNumber()).thenReturn(1L);
        when(status.getBestBlockNumber())
                .thenReturn(myBestBlockNumber + 1 + syncConfiguration.getLongSyncLimit());

        syncState.newPeerStatus();

        verify(syncEventsHandler).startSyncing(peer);
    }
}
