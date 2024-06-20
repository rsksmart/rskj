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
import co.rsk.util.HexUtils;
import org.ethereum.core.Blockchain;
import org.ethereum.net.rlpx.Node;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
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
    void testGetOrRegisterSnapPeer_ShouldThrowIllegalArgumentException(){
        SnapshotPeersInformation peersInformation = setupTopBestSnapshotScenario(100.0D);
        Peer snapPeer5 = Mockito.mock(Peer.class);
        Mockito.when(snapPeer5.getPeerNodeID()).thenReturn(new NodeID("snapPeer6".getBytes()));

        Mockito.doReturn(4).when(random).nextInt(Mockito.eq(5));

        Exception exception = Assertions.assertThrows(IllegalArgumentException.class, () -> peersInformation.getOrRegisterSnapPeer(snapPeer5));

        String expectedMessage = "Peer not allowed:";
        String actualMessage = exception.getMessage();

        Assertions.assertTrue(actualMessage.contains(expectedMessage));
    }

    @Test
    void testGetOrRegisterSnapPeer_ShouldRegisterSnapPeer(){
        PeersInformation peersInformation = setupTopBestSnapshotScenario(100.0D);
        Peer snapPeer5 = Mockito.mock(Peer.class);
        Mockito.when(snapPeer5.getPeerNodeID()).thenReturn(new NodeID("snapPeer5".getBytes()));

        Mockito.doReturn(4).when(random).nextInt(Mockito.eq(5));

        SyncPeerStatus expectedPeerStatus = peersInformation.getOrRegisterSnapPeer(snapPeer5);
        SyncPeerStatus actualPeerStatus = peersInformation.getPeer(snapPeer5);

        Assertions.assertEquals(expectedPeerStatus,actualPeerStatus);
    }

    @Test
    void testGetBestPeerCandidateForSnapSync_ShouldReturnBestCandidates(){
        SnapshotPeersInformation peersInformation = setupTopBestSnapshotScenario(100.0D);

        Mockito.doReturn(4).when(random).nextInt(Mockito.eq(5));

        List<Peer> actualPeersForSnapSync= peersInformation.getBestSnapPeerCandidates();

        String expectedNodeIdSnapPeer1 = ByteUtil.toHexString("snapPeer1".getBytes());
        String expectedNodeIdSnapPeer2 = ByteUtil.toHexString("snapPeer2".getBytes());

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

        Assertions.assertEquals(optionalSnapPeer.get().getPeerNodeID(), new NodeID("snapPeer2".getBytes()));
    }

    @Test
    void testGetBestSnapPeer_ShouldReturnBestSnapPeerWithTopBestAt60Perc() {
        SnapshotPeersInformation peersInformation = setupTopBestSnapshotScenario(60.0D);

        Mockito.doReturn(0).when(random).nextInt(Mockito.eq(2));

        Optional<Peer> optionalSnapPeer = peersInformation.getBestSnapPeer();

        Assertions.assertEquals(optionalSnapPeer.get().getPeerNodeID(), new NodeID("snapPeer2".getBytes()));
    }

    @Test
    void testGetBestSnapPeer_ShouldReturnBestSnapPeerWithTopBestAt100Perc() {
        SnapshotPeersInformation snapPeersInformation = setupTopBestSnapshotScenario(100.0D);

        Mockito.doReturn(1).when(random).nextInt(Mockito.eq(2));

        Optional<Peer> optionalSnapPeer = snapPeersInformation.getBestSnapPeer();

        Assertions.assertEquals(optionalSnapPeer.get().getPeerNodeID(), new NodeID("snapPeer1".getBytes()));
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

        Mockito.when(blockchain.getStatus()).thenReturn(blockChainStatus);

        Mockito.when(channelManager.getActivePeers()).thenReturn(Stream.of(
                peer1, peer2, peer3, peer4, peer5
        ).collect(Collectors.toList()));

        Mockito.when(syncConfiguration.getExpirationTimePeerStatus())
                .thenReturn(Duration.of(1, ChronoUnit.HOURS));

        Mockito.when(syncConfiguration.getTopBest())
                .thenReturn(topBest);

        Mockito.when(peerScoringManager.hasGoodReputation(Mockito.eq(peer1.getPeerNodeID()))).thenReturn(true);
        Mockito.when(peerScoringManager.hasGoodReputation(Mockito.eq(peer2.getPeerNodeID()))).thenReturn(true);
        Mockito.when(peerScoringManager.hasGoodReputation(Mockito.eq(peer3.getPeerNodeID()))).thenReturn(true);
        Mockito.when(peerScoringManager.hasGoodReputation(Mockito.eq(peer4.getPeerNodeID()))).thenReturn(true);
        Mockito.when(peerScoringManager.hasGoodReputation(Mockito.eq(peer5.getPeerNodeID()))).thenReturn(true);

        PeersInformation peersInformation = new PeersInformation(channelManager, syncConfiguration, blockchain, peerScoringManager, random);

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

    private PeersInformation setupTopBestSnapshotScenario(double topBest) {
        Peer snapPeer1 = Mockito.mock(Peer.class);
        Peer snapPeer2= Mockito.mock(Peer.class);
        Peer snapPeer3= Mockito.mock(Peer.class);
        Peer snapPeer4= Mockito.mock(Peer.class);
        Peer snapPeer5 = Mockito.mock(Peer.class);

        Mockito.when(snapPeer1.getPeerNodeID()).thenReturn(new NodeID("snapPeer1".getBytes()));
        Mockito.when(snapPeer2.getPeerNodeID()).thenReturn(new NodeID("snapPeer2".getBytes()));
        Mockito.when(snapPeer3.getPeerNodeID()).thenReturn(new NodeID("snapPeer3".getBytes()));
        Mockito.when(snapPeer4.getPeerNodeID()).thenReturn(new NodeID("snapPeer4".getBytes()));
        Mockito.when(snapPeer5.getPeerNodeID()).thenReturn(new NodeID("snapPeer5".getBytes()));

        Map<String, Node> trustedSnapPeersMap = new HashMap<>();
        trustedSnapPeersMap.put(snapPeer1.getPeerNodeID().toString(),new Node(HexUtils.strHexOrStrNumberToByteArray("0x0FF"), "snapPeer1.com", 0));
        trustedSnapPeersMap.put(snapPeer2.getPeerNodeID().toString(),new Node(HexUtils.strHexOrStrNumberToByteArray("0xAFE"), "snapPeer2.com", 0));
        trustedSnapPeersMap.put(snapPeer3.getPeerNodeID().toString(),new Node(HexUtils.strHexOrStrNumberToByteArray("0xA8E"), "snapPeer3.com", 0));
        trustedSnapPeersMap.put(snapPeer4.getPeerNodeID().toString(),new Node(HexUtils.strHexOrStrNumberToByteArray("0xA9E"), "snapPeer4.com", 0));
        trustedSnapPeersMap.put(snapPeer5.getPeerNodeID().toString(),new Node(HexUtils.strHexOrStrNumberToByteArray("0xA9E"), "snapPeer5.com", 0));

        Mockito.when(blockchain.getStatus()).thenReturn(blockChainStatus);

        Mockito.when(channelManager.getActivePeers()).thenReturn(Stream.of(
                snapPeer1, snapPeer2, snapPeer3, snapPeer4
        ).collect(Collectors.toList()));

        Mockito.when(syncConfiguration.getExpirationTimePeerStatus())
                .thenReturn(Duration.of(1, ChronoUnit.HOURS));

        Mockito.when(syncConfiguration.getTopBest())
                .thenReturn(topBest);

        Mockito.when(syncConfiguration.getNodeIdToSnapshotTrustedPeerMap()).thenReturn(trustedSnapPeersMap);
        Mockito.when(syncConfiguration.getSnapshotTrustedPeers()).thenReturn(new ArrayList<>(trustedSnapPeersMap.values()));

        Mockito.when(peerScoringManager.hasGoodReputation(Mockito.eq(snapPeer1.getPeerNodeID()))).thenReturn(true);
        Mockito.when(peerScoringManager.hasGoodReputation(Mockito.eq(snapPeer2.getPeerNodeID()))).thenReturn(true);
        Mockito.when(peerScoringManager.hasGoodReputation(Mockito.eq(snapPeer3.getPeerNodeID()))).thenReturn(true);
        Mockito.when(peerScoringManager.hasGoodReputation(Mockito.eq(snapPeer4.getPeerNodeID()))).thenReturn(false);

        PeersInformation peersInformation = new PeersInformation(channelManager, syncConfiguration, blockchain, peerScoringManager, random);

        SyncPeerStatus syncSnapPeerStatus1 = peersInformation.registerPeer(snapPeer1);
        syncSnapPeerStatus1.setStatus(new Status(10L, "".getBytes(), null, new BlockDifficulty(BigInteger.valueOf(10L))));
        Mockito.when(blockChainStatus.hasLowerTotalDifficultyThan(Mockito.eq(syncSnapPeerStatus1.getStatus()))).thenReturn(true);

        SyncPeerStatus syncSnapPeerStatus2 = peersInformation.registerPeer(snapPeer2);
        syncSnapPeerStatus2.setStatus(new Status(20L, "".getBytes(), null, new BlockDifficulty(BigInteger.valueOf(20L))));
        Mockito.when(blockChainStatus.hasLowerTotalDifficultyThan(Mockito.eq(syncSnapPeerStatus2.getStatus()))).thenReturn(true);

        SyncPeerStatus syncSnapPeerStatus3 = peersInformation.registerPeer(snapPeer3);
        syncSnapPeerStatus3.setStatus(new Status(30L, "".getBytes(), null, new BlockDifficulty(BigInteger.valueOf(30L))));
        Mockito.when(blockChainStatus.hasLowerTotalDifficultyThan(Mockito.eq(syncSnapPeerStatus3.getStatus()))).thenReturn(false);

        SyncPeerStatus syncSnapPeerStatus4 = peersInformation.registerPeer(snapPeer4);
        syncSnapPeerStatus4.setStatus(new Status(40L, "".getBytes(), null, new BlockDifficulty(BigInteger.valueOf(40L))));
        Mockito.when(blockChainStatus.hasLowerTotalDifficultyThan(Mockito.eq(syncSnapPeerStatus4.getStatus()))).thenReturn(true);

        return peersInformation;
    }
}
