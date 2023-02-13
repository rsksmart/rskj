/*
 * This file is part of RskJ
 * Copyright (C) 2022 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.net.sync;

import co.rsk.net.NodeID;
import co.rsk.net.Peer;
import co.rsk.net.Status;
import co.rsk.net.simples.SimplePeer;
import co.rsk.scoring.PeerScoringManager;
import org.ethereum.TestUtils;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.db.BlockStore;
import org.ethereum.util.RskMockFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PeerAndModeDecidingSyncStateTest {

    @Test
    void startsSyncingWith5Peers() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SimpleSyncEventsHandler syncEventsHandler = new SimpleSyncEventsHandler();

        PeersInformation peersInformation = mock(PeersInformation.class);
        when(peersInformation.count()).thenReturn(1,2,3,4,5);

        BlockStore blockStore = mock(BlockStore.class);
        Block bestBlock = mock(Block.class);
        when(blockStore.getBestBlock()).thenReturn(bestBlock);
        when(bestBlock.getNumber()).thenReturn(100L);

        SyncState syncState = new PeerAndModeDecidingSyncState(syncConfiguration, syncEventsHandler, peersInformation, blockStore);
        when(peersInformation.getBestPeer()).thenReturn(Optional.of(mock(Peer.class)));

        Status status = mock(Status.class);
        SyncPeerStatus bpStatus = mock(SyncPeerStatus.class);
        when(bpStatus.getStatus()).thenReturn(status);
        when(peersInformation.getPeer(any())).thenReturn(bpStatus);

        for (int i = 0; i < 5; i++) {
            Assertions.assertFalse(syncEventsHandler.startSyncingWasCalled());
            syncState.newPeerStatus();
        }

        Assertions.assertTrue(syncEventsHandler.startSyncingWasCalled());
    }

    @Test
    void doesntStartSyncingWithNoPeersAfter2Minutes() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SimpleSyncEventsHandler syncEventsHandler = new SimpleSyncEventsHandler();

        PeersInformation knownPeers = mock(PeersInformation.class);
        when(knownPeers.count()).thenReturn(0);
        when(knownPeers.getBestPeer()).thenReturn(Optional.empty());

        SyncState syncState = new PeerAndModeDecidingSyncState(syncConfiguration, syncEventsHandler, knownPeers, mock(BlockStore.class));

        syncState.tick(Duration.ofMinutes(2));
        Assertions.assertFalse(syncEventsHandler.startSyncingWasCalled());
    }

    @Test
    void startsSyncingWith1PeerAfter2Minutes() {
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
        SyncState syncState = new PeerAndModeDecidingSyncState(
                syncConfiguration,
                syncEventsHandler,
                peersInformation,
                blockStore);

        verify(syncEventsHandler, never()).startSyncing(any());

        syncState.tick(Duration.ofMinutes(2));

        verify(syncEventsHandler).startSyncing(any());
    }

    @Test
    void doesntStartSyncingWith1PeerBeforeTimeout() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SimpleSyncEventsHandler syncEventsHandler = new SimpleSyncEventsHandler();
        PeerScoringManager peerScoringManager = RskMockFactory.getPeerScoringManager();
        Blockchain blockchain = mock(Blockchain.class);

        PeersInformation knownPeers = new PeersInformation(RskMockFactory.getChannelManager(),
                syncConfiguration, blockchain, peerScoringManager);
        SyncState syncState = new PeerAndModeDecidingSyncState(syncConfiguration, syncEventsHandler, knownPeers, mock(BlockStore.class));
        Assertions.assertFalse(syncEventsHandler.startSyncingWasCalled());

        knownPeers.registerPeer(new SimplePeer(new NodeID(TestUtils.generatePeerId("knownPeer"))));
        syncState.newPeerStatus();
        syncState.tick(syncConfiguration.getTimeoutWaitingPeers().minusSeconds(1L));
        Assertions.assertFalse(syncEventsHandler.startSyncingWasCalled());
    }

    @Test
    void doesntStartSyncingIfAllPeersHaveLowerDifficulty() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SimpleSyncEventsHandler syncEventsHandler = new SimpleSyncEventsHandler();
        PeerScoringManager peerScoringManager = RskMockFactory.getPeerScoringManager();
        Blockchain blockchain = mock(Blockchain.class);

        PeersInformation knownPeers = new PeersInformation(RskMockFactory.getChannelManager(),
                syncConfiguration, blockchain, peerScoringManager);
        BlockStore blockStore = mock(BlockStore.class);
        SyncState syncState = new PeerAndModeDecidingSyncState(syncConfiguration, syncEventsHandler, knownPeers, blockStore);
        Assertions.assertFalse(syncEventsHandler.startSyncingWasCalled());

        when(blockStore.getMinNumber()).thenReturn(1L);

        knownPeers.registerPeer(new SimplePeer(new NodeID(TestUtils.generatePeerId("knowPeer"))));
        syncState.newPeerStatus();
        syncState.tick(Duration.ofMinutes(2));
        Assertions.assertFalse(syncEventsHandler.startSyncingWasCalled());
    }

    @Test
    void doesntStartSyncingIfAllPeersHaveBadReputation() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SimpleSyncEventsHandler syncEventsHandler = new SimpleSyncEventsHandler();
        PeerScoringManager peerScoringManager = RskMockFactory.getPeerScoringManager();
        Blockchain blockchain = mock(Blockchain.class);

        PeersInformation knownPeers = new PeersInformation(RskMockFactory.getChannelManager(),
                syncConfiguration, blockchain, peerScoringManager);

        BlockStore blockStore = mock(BlockStore.class);
        SyncState syncState = new PeerAndModeDecidingSyncState(syncConfiguration, syncEventsHandler, knownPeers, blockStore);
        Assertions.assertFalse(syncEventsHandler.startSyncingWasCalled());

        when(blockStore.getMinNumber()).thenReturn(1L);

        knownPeers.registerPeer(new SimplePeer(new NodeID(TestUtils.generatePeerId("knownPeer"))));
        syncState.newPeerStatus();
        syncState.tick(Duration.ofMinutes(2));
        Assertions.assertFalse(syncEventsHandler.startSyncingWasCalled());
    }

    @Test
    void backwardsSynchronization_genesisNotConnected_withMinBlock() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        PeersInformation peersInformation = mock(PeersInformation.class);
        SyncEventsHandler syncEventsHandler = mock(SyncEventsHandler.class);
        BlockStore blockStore = mock(BlockStore.class);
        SyncState syncState = new PeerAndModeDecidingSyncState(syncConfiguration,
                syncEventsHandler,
                peersInformation,
                blockStore);

        when(peersInformation.count()).thenReturn(syncConfiguration.getExpectedPeers() + 1);
        Peer peer = mock(Peer.class);
        when(peersInformation.getBestPeer()).thenReturn(Optional.of(peer));

        when(blockStore.getMinNumber()).thenReturn(1L);
        when(peersInformation.getBestOrEqualPeer()).thenReturn(Optional.of(peer));

        syncState.newPeerStatus();
        verify(syncEventsHandler, times(1)).backwardSyncing(peer);
    }

    @Test
    void backwardsSynchronization_genesisConnected() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        PeersInformation peersInformation = mock(PeersInformation.class);
        SyncEventsHandler syncEventsHandler = mock(SyncEventsHandler.class);
        BlockStore blockStore = mock(BlockStore.class);
        SyncState syncState = new PeerAndModeDecidingSyncState(syncConfiguration,
                syncEventsHandler,
                peersInformation,
                blockStore);

        when(peersInformation.count()).thenReturn(syncConfiguration.getExpectedPeers() + 1);
        Peer peer = mock(Peer.class);
        when(peersInformation.getBestPeer()).thenReturn(Optional.of(peer));

        when(blockStore.getMinNumber()).thenReturn(0L);
        Block block = mock(Block.class);
        when(peersInformation.getBestOrEqualPeer()).thenReturn(Optional.of(peer));

        when(block.isGenesis()).thenReturn(true);
        syncState.newPeerStatus();
        verify(syncEventsHandler, never()).backwardSyncing(peer);
    }

    @Test
    void forwardsSynchronization_genesisIsConnected() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        PeersInformation peersInformation = mock(PeersInformation.class);
        SyncEventsHandler syncEventsHandler = mock(SyncEventsHandler.class);
        BlockStore blockStore = mock(BlockStore.class);
        SyncState syncState = new PeerAndModeDecidingSyncState(syncConfiguration,
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
    void forwardsSynchronization() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        PeersInformation peersInformation = mock(PeersInformation.class);
        SyncEventsHandler syncEventsHandler = mock(SyncEventsHandler.class);
        BlockStore blockStore = mock(BlockStore.class);
        SyncState syncState = new PeerAndModeDecidingSyncState(syncConfiguration,
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
