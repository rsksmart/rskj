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
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.math.BigInteger;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class PeersInformationTest {
    @Mock
    Random random;
    @Mock
    private Blockchain blockchain;
    @Mock
    private BlockChainStatus blockChainStatus;
    @Mock
    private ChannelManager channelManager;
    @Mock
    private SyncConfiguration syncConfiguration;
    @Mock
    private PeerScoringManager peerScoringManager;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetBestPeer_ShouldReturnBestPeerWithTopBestAt0Perc() {
        PeersInformation peersInformation = setupTopBestScenario(0.0D);
        Optional<Peer> optionalPeer = peersInformation.getBestPeer();

        Assertions.assertEquals(optionalPeer.get().getPeerNodeID(), new NodeID("peer5".getBytes()));
    }

    @Test
    void testGetBestPeer_ShouldReturnBestPeerWithTopBestAt30Perc() {
        PeersInformation peersInformation = setupTopBestScenario(30.0D);

        Mockito.doReturn(1).when(random).nextInt(Mockito.eq(2));

        Optional<Peer> optionalPeer = peersInformation.getBestPeer();

        Assertions.assertEquals(optionalPeer.get().getPeerNodeID(), new NodeID("peer4".getBytes()));
    }

    @Test
    void testGetBestPeer_ShouldReturnBestPeerWithTopBestAt60Perc() {
        PeersInformation peersInformation = setupTopBestScenario(60.0D);

        Mockito.doReturn(2).when(random).nextInt(Mockito.eq(3));

        Optional<Peer> optionalPeer = peersInformation.getBestPeer();

        Assertions.assertEquals(optionalPeer.get().getPeerNodeID(), new NodeID("peer3".getBytes()));
    }

    @Test
    void testGetBestPeer_ShouldReturnBestPeerWithTopBestAt80Perc() {
        PeersInformation peersInformation = setupTopBestScenario(80.0D);

        Mockito.doReturn(3).when(random).nextInt(Mockito.eq(4));

        Optional<Peer> optionalPeer = peersInformation.getBestPeer();

        Assertions.assertEquals(optionalPeer.get().getPeerNodeID(), new NodeID("peer2".getBytes()));
    }

    @Test
    void testGetBestPeer_ShouldReturnBestPeerWithTopBestAt100Perc() {
        PeersInformation peersInformation = setupTopBestScenario(100.0D);

        Mockito.doReturn(4).when(random).nextInt(Mockito.eq(5));

        Optional<Peer> optionalPeer = peersInformation.getBestPeer();

        Assertions.assertEquals(optionalPeer.get().getPeerNodeID(), new NodeID("peer1".getBytes()));
    }

    @Test
    void testGetOrRegisterSnapPeer_ShouldRegisterSnapPeer(){
        PeersInformation peersInformation = setupTopBestSnapshotScenario(100.0D);
        Peer snapPeer5 = Mockito.mock(Peer.class);
        Mockito.when(snapPeer5.getPeerNodeID()).thenReturn(new NodeID("0xA9E".getBytes()));

        Mockito.doReturn(4).when(random).nextInt(Mockito.eq(5));

        SyncPeerStatus expectedPeerStatus = peersInformation.getOrRegisterPeer(snapPeer5);
        SyncPeerStatus actualPeerStatus = peersInformation.getPeer(snapPeer5);

        Assertions.assertEquals(expectedPeerStatus,actualPeerStatus);
    }

    @Test
    void testGetBestPeerCandidateForSnapSync_ShouldReturnBestCandidates(){
        SnapshotPeersInformation peersInformation = setupTopBestSnapshotScenario(100.0D);

        Mockito.doReturn(4).when(random).nextInt(Mockito.eq(5));

        List<Peer> actualPeersForSnapSync= peersInformation.getBestSnapPeerCandidates();

        String expectedNodeIdSnapPeer1 = ByteUtil.toHexString("0x0FF".getBytes());
        String expectedNodeIdSnapPeer2 = ByteUtil.toHexString("0xAFE".getBytes());

        boolean listShouldHaveSnapPeer1 = actualPeersForSnapSync.stream()
                .anyMatch(node -> ByteUtil.toHexString(node.getPeerNodeID().getID()).equals(expectedNodeIdSnapPeer1));
        boolean listShouldHaveSnapPeer2 = actualPeersForSnapSync.stream()
                .anyMatch(node -> ByteUtil.toHexString(node.getPeerNodeID().getID()).equals(expectedNodeIdSnapPeer2));

        Assertions.assertEquals(2, actualPeersForSnapSync.size());
        Assertions.assertTrue(listShouldHaveSnapPeer1, "List should contain a Snap Peer with NodeID " + expectedNodeIdSnapPeer1);
        Assertions.assertTrue(listShouldHaveSnapPeer2, "List should contain a Snap Peer with NodeID " + expectedNodeIdSnapPeer2);
    }

    @Test
    void testGetBestSnapPeer_ShouldReturnBestSnapPeerWithTopBestAt0Perc() {
        SnapshotPeersInformation peersInformation = setupTopBestSnapshotScenario(0.0D);
        Optional<Peer> optionalSnapPeer = peersInformation.getBestSnapPeer();

        Assertions.assertEquals(new NodeID("0xAFE".getBytes()), optionalSnapPeer.get().getPeerNodeID());
    }

    @Test
    void testGetBestSnapPeer_ShouldReturnBestSnapPeerWithTopBestAt60Perc() {
        SnapshotPeersInformation peersInformation = setupTopBestSnapshotScenario(60.0D);

        Mockito.doReturn(0).when(random).nextInt(Mockito.eq(2));

        Optional<Peer> optionalSnapPeer = peersInformation.getBestSnapPeer();

        Assertions.assertEquals(new NodeID("0xAFE".getBytes()), optionalSnapPeer.get().getPeerNodeID());
    }

    @Test
    void testGetBestSnapPeer_ShouldReturnBestSnapPeerWithTopBestAt100Perc() {
        SnapshotPeersInformation snapPeersInformation = setupTopBestSnapshotScenario(100.0D);

        Mockito.doReturn(1).when(random).nextInt(Mockito.eq(2));

        Optional<Peer> optionalSnapPeer = snapPeersInformation.getBestSnapPeer();

        Assertions.assertEquals(new NodeID("0x0FF".getBytes()), optionalSnapPeer.get().getPeerNodeID());
    }

    private PeersInformation setupTopBestScenario(double topBest) {
        Mockito.when(syncConfiguration.getExpirationTimePeerStatus())
                .thenReturn(Duration.of(1, ChronoUnit.HOURS));

        Mockito.when(syncConfiguration.getTopBest())
                .thenReturn(topBest);

        Mockito.when(blockchain.getStatus()).thenReturn(blockChainStatus);

        PeersInformation peersInformation = new PeersInformation(channelManager, syncConfiguration, blockchain, peerScoringManager, random);

        Peer peer1 = setupPeer(peersInformation, null, "peer1", "peerHost1.COM", true, 1L, 1L, true, false );
        Peer peer2 = setupPeer(peersInformation, null, "peer2", "peerHost2.COM", true, 2L, 2L, true, false );
        Peer peer3 = setupPeer(peersInformation, null, "peer3", "peerHost3.COM", true, 3L, 3L, true, false );
        Peer peer4 = setupPeer(peersInformation, null, "peer4", "peerHost4.COM", true, 4L, 4L, true, false );
        Peer peer5 = setupPeer(peersInformation, null, "peer5", "peerHost5.COM", true, 5L, 5L, true, false );

        Mockito.when(channelManager.getActivePeers()).thenReturn(Stream.of(
                peer1, peer2, peer3, peer4, peer5
        ).collect(Collectors.toList()));


        return peersInformation;
    }

    private PeersInformation setupTopBestSnapshotScenario(double topBest) {
        Set<NodeID> trustedSnapPeersMap = new HashSet<>();

        Mockito.when(syncConfiguration.getExpirationTimePeerStatus())
                .thenReturn(Duration.of(1, ChronoUnit.HOURS));

        Mockito.when(syncConfiguration.getTopBest())
                .thenReturn(topBest);

        Mockito.when(blockchain.getStatus()).thenReturn(blockChainStatus);

        Mockito.when(syncConfiguration.getSnapBootNodeIds()).thenReturn(trustedSnapPeersMap);

        PeersInformation peersInformation = new PeersInformation(channelManager, syncConfiguration, blockchain, peerScoringManager, random);

        Peer snapPeer1 = setupPeer(peersInformation, trustedSnapPeersMap, "0x0FF", "snapPeerHost1.COM", true, 10L, 10L, true, true );
        Peer snapPeer2 = setupPeer(peersInformation, trustedSnapPeersMap, "0xAFE", "snapPeerHost2.COM", true, 20L, 20L, true, true );
        Peer snapPeer3 = setupPeer(peersInformation, trustedSnapPeersMap, "0xA8E", "snapPeerHost3.COM", true, 30L, 30L, false, true );
        Peer snapPeer4 = setupPeer(peersInformation, trustedSnapPeersMap, "0xA9E", "snapPeerHost4.COM", false, 40L, 40L, true, true );
        setupPeer(peersInformation, trustedSnapPeersMap, "0xA9E", "snapPeerHost5.COM", false, 50L, 50L, true, true );

        Mockito.when(channelManager.getActivePeers()).thenReturn(Stream.of(
                snapPeer1, snapPeer2, snapPeer3, snapPeer4
        ).collect(Collectors.toList()));


        return peersInformation;
    }
    private Peer setupPeer(PeersInformation peersInformation, Set<NodeID> trustedSnapPeersMap, String nodeId, String nodeHost,
                           boolean hasGoodReputation, long bestBlockNumber, long blockDifficulty,
                           boolean hasLowerTotalDifficultyThan, boolean isSnapCapable) {
        Peer peer = Mockito.mock(Peer.class);

        Mockito.when(peer.isSnapCapable()).thenReturn(isSnapCapable);
        Mockito.when(peer.getPeerNodeID()).thenReturn(new NodeID(nodeId.getBytes()));

        if(trustedSnapPeersMap!=null){
            trustedSnapPeersMap.add(peer.getPeerNodeID());
        }

        Mockito.when(peerScoringManager.hasGoodReputation(Mockito.eq(peer.getPeerNodeID()))).thenReturn(hasGoodReputation);

        SyncPeerStatus syncSnapPeerStatus = peersInformation.registerPeer(peer);
        syncSnapPeerStatus.setStatus(new Status(bestBlockNumber, "".getBytes(), null, new BlockDifficulty(BigInteger.valueOf(blockDifficulty))));
        Mockito.when(blockChainStatus.hasLowerTotalDifficultyThan(Mockito.eq(syncSnapPeerStatus.getStatus()))).thenReturn(hasLowerTotalDifficultyThan);

        return peer;
    }
}
