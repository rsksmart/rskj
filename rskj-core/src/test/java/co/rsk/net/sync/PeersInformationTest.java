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

import co.rsk.core.BlockDifficulty;
import co.rsk.core.bc.BlockChainStatus;
import co.rsk.net.NodeID;
import co.rsk.net.Peer;
import co.rsk.net.Status;
import co.rsk.scoring.PeerScoringManager;
import org.ethereum.core.Blockchain;
import org.ethereum.net.server.ChannelManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigInteger;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class PeersInformationTest {

    @Test
    void testGetBestPeer_ShouldReturnBestPeerWithTopBestAt0Perc() {
        PeersInformation peersInformation = setupTopBestScenario(0.0D);
        Optional<Peer> optionalPeer = peersInformation.getBestPeer();

        Assertions.assertEquals(optionalPeer.get().getPeerNodeID(), new NodeID("peer5".getBytes()));
    }

    @Test
    void testGetBestPeer_ShouldReturnBestPeerWithTopBestAt30Perc() {
        PeersInformation peersInformation = setupTopBestScenario(30.0D);
        Optional<Peer> optionalPeer = peersInformation.getBestPeer();

        Assertions.assertTrue(
                Stream.of(new NodeID("peer5".getBytes()), new NodeID("peer4".getBytes()))
                        .anyMatch(n -> n.equals(optionalPeer.get().getPeerNodeID()))
        );
    }

    @Test
    void testGetBestPeer_ShouldReturnBestPeerWithTopBestAt60Perc() {
        PeersInformation peersInformation = setupTopBestScenario(60.0D);
        Optional<Peer> optionalPeer = peersInformation.getBestPeer();

        Assertions.assertTrue(
                Stream.of(new NodeID("peer5".getBytes()), new NodeID("peer4".getBytes()),
                                new NodeID("peer3".getBytes()))
                        .anyMatch(n -> n.equals(optionalPeer.get().getPeerNodeID()))
        );
    }

    @Test
    void testGetBestPeer_ShouldReturnBestPeerWithTopBestAt80Perc() {
        PeersInformation peersInformation = setupTopBestScenario(80.0D);
        Optional<Peer> optionalPeer = peersInformation.getBestPeer();

        Assertions.assertTrue(
                Stream.of(new NodeID("peer5".getBytes()), new NodeID("peer4".getBytes()),
                                new NodeID("peer3".getBytes()), new NodeID("peer2".getBytes()))
                        .anyMatch(n -> n.equals(optionalPeer.get().getPeerNodeID()))
        );
    }

    @Test
    void testGetBestPeer_ShouldReturnBestPeerWithTopBestAt100Perc() {
        PeersInformation peersInformation = setupTopBestScenario(100.0D);
        Optional<Peer> optionalPeer = peersInformation.getBestPeer();

        Assertions.assertTrue(
                Stream.of(new NodeID("peer5".getBytes()), new NodeID("peer4".getBytes()),
                                new NodeID("peer3".getBytes()), new NodeID("peer2".getBytes()),
                                new NodeID("peer1".getBytes()))
                        .anyMatch(n -> n.equals(optionalPeer.get().getPeerNodeID()))
        );
    }

    private PeersInformation setupTopBestScenario(double topBest) {
        Peer peer1 = Mockito.mock(Peer.class);
        Peer peer2 = Mockito.mock(Peer.class);
        Peer peer3 = Mockito.mock(Peer.class);
        Peer peer4 = Mockito.mock(Peer.class);
        Peer peer5 = Mockito.mock(Peer.class);

        Mockito.when(peer1.getPeerNodeID()).thenReturn(new NodeID("peer1".getBytes()));
        Mockito.when(peer2.getPeerNodeID()).thenReturn(new NodeID("peer2".getBytes()));
        Mockito.when(peer3.getPeerNodeID()).thenReturn(new NodeID("peer3".getBytes()));
        Mockito.when(peer4.getPeerNodeID()).thenReturn(new NodeID("peer4".getBytes()));
        Mockito.when(peer5.getPeerNodeID()).thenReturn(new NodeID("peer5".getBytes()));

        Blockchain blockchain = Mockito.mock(Blockchain.class);
        BlockChainStatus blockChainStatus = Mockito.mock(BlockChainStatus.class);
        ChannelManager channelManager = Mockito.mock(ChannelManager.class);
        SyncConfiguration syncConfiguration = Mockito.mock(SyncConfiguration.class);
        PeerScoringManager peerScoringManager = Mockito.mock(PeerScoringManager.class);

        Mockito.when(blockchain.getStatus()).thenReturn(blockChainStatus);

        Mockito.when(channelManager.getActivePeers()).thenReturn(Stream.of(
                peer1, peer2, peer3, peer4, peer5
        ).collect(Collectors.toList()));

        Mockito.when(syncConfiguration.getExpirationTimePeerStatus())
                .thenReturn(Duration.of(1, ChronoUnit.HOURS));

        Mockito.when(peerScoringManager.hasGoodReputation(Mockito.eq(peer1.getPeerNodeID()))).thenReturn(true);
        Mockito.when(peerScoringManager.hasGoodReputation(Mockito.eq(peer2.getPeerNodeID()))).thenReturn(true);
        Mockito.when(peerScoringManager.hasGoodReputation(Mockito.eq(peer3.getPeerNodeID()))).thenReturn(true);
        Mockito.when(peerScoringManager.hasGoodReputation(Mockito.eq(peer4.getPeerNodeID()))).thenReturn(true);
        Mockito.when(peerScoringManager.hasGoodReputation(Mockito.eq(peer5.getPeerNodeID()))).thenReturn(true);

        PeersInformation peersInformation = new PeersInformation(channelManager, syncConfiguration, blockchain, peerScoringManager);

        SyncPeerStatus syncPeerStatus1 = peersInformation.registerPeer(peer1);
        syncPeerStatus1.setStatus(new Status(1L, "".getBytes(), null, new BlockDifficulty(BigInteger.valueOf(1L))));
        Mockito.when(blockChainStatus.hasLowerTotalDifficultyThan(Mockito.eq(syncPeerStatus1.getStatus()))).thenReturn(true);

        SyncPeerStatus syncPeerStatus2 = peersInformation.registerPeer(peer2);
        syncPeerStatus2.setStatus(new Status(2L, "".getBytes(), null, new BlockDifficulty(BigInteger.valueOf(2L))));
        Mockito.when(blockChainStatus.hasLowerTotalDifficultyThan(Mockito.eq(syncPeerStatus2.getStatus()))).thenReturn(true);

        SyncPeerStatus syncPeerStatus3 = peersInformation.registerPeer(peer3);
        syncPeerStatus3.setStatus(new Status(3L, "".getBytes(), null, new BlockDifficulty(BigInteger.valueOf(3L))));
        Mockito.when(blockChainStatus.hasLowerTotalDifficultyThan(Mockito.eq(syncPeerStatus3.getStatus()))).thenReturn(true);

        SyncPeerStatus syncPeerStatus4 = peersInformation.registerPeer(peer4);
        syncPeerStatus4.setStatus(new Status(4L, "".getBytes(), null, new BlockDifficulty(BigInteger.valueOf(4L))));
        Mockito.when(blockChainStatus.hasLowerTotalDifficultyThan(Mockito.eq(syncPeerStatus4.getStatus()))).thenReturn(true);

        SyncPeerStatus syncPeerStatus5 = peersInformation.registerPeer(peer5);
        syncPeerStatus5.setStatus(new Status(5L, "".getBytes(), null, new BlockDifficulty(BigInteger.valueOf(5L))));
        Mockito.when(blockChainStatus.hasLowerTotalDifficultyThan(Mockito.eq(syncPeerStatus5.getStatus()))).thenReturn(true);

        return peersInformation;
    }
}
