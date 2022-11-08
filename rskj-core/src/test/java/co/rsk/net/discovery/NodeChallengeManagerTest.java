/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.net.discovery;

import co.rsk.net.discovery.table.KademliaOptions;
import co.rsk.net.discovery.table.NodeDistanceTable;
import co.rsk.scoring.PeerScoringManager;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;
import org.ethereum.net.rlpx.Node;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.UUID;

import static org.mockito.Mockito.mock;

/**
 * Created by mario on 22/02/17.
 */
class NodeChallengeManagerTest {

    private static final String KEY_1 = "bd1d20e480dfb1c9c07ba0bc8cf9052f89923d38b5128c5dbfc18d4eea38261f";
    private static final String HOST_1 = "localhost";
    private static final int PORT_1 = 44035;
    private static final int NETWORK_ID = 1;

    private static final String KEY_2 = "bd2d20e480dfb1c9c07ba0bc8cf9052f89923d38b5128c5dbfc18d4eea38262f";
    private static final String HOST_2 = "localhost";
    private static final int PORT_2 = 44036;

    private static final String KEY_3 = "bd3d20e480dfb1c9c07ba0bc8cf9052f89923d38b5128c5dbfc18d4eea38263f";
    private static final String HOST_3 = "localhost";
    private static final int PORT_3 = 44037;

    private static final long TIMEOUT = 30000;
    private static final long UPDATE = 60000;
    private static final long CLEAN = 60000;

    @Test
    void startChallenge() {
        ECKey key1 = ECKey.fromPrivate(Hex.decode(KEY_1)).decompress();
        ECKey key2 = ECKey.fromPrivate(Hex.decode(KEY_2)).decompress();
        ECKey key3 = ECKey.fromPrivate(Hex.decode(KEY_3)).decompress();

        Node node1 = new Node(key1.getNodeId(), HOST_1, PORT_1);
        Node node2 = new Node(key2.getNodeId(), HOST_2, PORT_2);
        Node node3 = new Node(key3.getNodeId(), HOST_3, PORT_3);

        NodeDistanceTable distanceTable = new NodeDistanceTable(KademliaOptions.BINS, KademliaOptions.BUCKET_SIZE, node1);
        PeerExplorer peerExplorer = new PeerExplorer(new ArrayList<>(), node1, distanceTable, new ECKey(), TIMEOUT, UPDATE, CLEAN, NETWORK_ID, mock(PeerScoringManager.class), true);
        peerExplorer.setUDPChannel(mock(UDPChannel.class));

        NodeChallengeManager manager = new NodeChallengeManager();
        NodeChallenge challenge = manager.startChallenge(node2, node3, (chn) -> peerExplorer.sendPing(chn.getAddress(), 1, chn));

        Assertions.assertNotNull(challenge);
        Assertions.assertEquals(challenge.getChallengedNode(), node2);
        Assertions.assertEquals(challenge.getChallenger(), node3);

        NodeChallenge anotherChallenge = manager.removeChallenge(UUID.randomUUID().toString());
        Assertions.assertNull(anotherChallenge);

        anotherChallenge = manager.removeChallenge(challenge.getChallengeId());
        Assertions.assertEquals(challenge, anotherChallenge);
    }
}
