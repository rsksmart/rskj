package co.rsk.net.discovery;

import co.rsk.net.discovery.message.*;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.RLP;
import org.junit.jupiter.api.Assertions;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.ethereum.util.ByteUtil.intToBytes;
import static org.ethereum.util.ByteUtil.stripLeadingZeroes;


public class MessageDecoderTest {

    private static final String KEY_1 = "bd1d20e480dfb1c9c07ba0bc8cf9052f89923d38b5128c5dbfc18d4eea38261f";
    private static final int NETWORK_ID = 1;
    public static final String LOCALHOST = "localhost";
    public static final int PORT = 44035;

    @Test
    public void testMDCCheckFail() {
        //An array of all 0 would fail the sumcheck
        byte[] wire = new byte[172];
        Exception exception = Assertions.assertThrows(PeerDiscoveryException.class, () -> MessageDecoder.decode(wire));
        Assertions.assertEquals(MessageDecoder.MDC_CHECK_FAILED, exception.getMessage());
    }

    @Test
    public void testLengthFail() {
        Exception exception = Assertions.assertThrows(PeerDiscoveryException.class, () -> MessageDecoder.decode(new byte[] {11}));
        Assertions.assertEquals(MessageDecoder.BAD_MESSAGE, exception.getMessage());
    }

    @Test
    public void testDataSizeNeighborsMessage() {
        String check = UUID.randomUUID().toString();
        ECKey key1 = ECKey.fromPrivate(Hex.decode(KEY_1)).decompress();
        NeighborsPeerMessage neighborsPeerMessage = NeighborsPeerMessage.create(
                new ArrayList<>(),
                check,
                key1,
                NETWORK_ID);
        byte[] type = new byte[]{(byte) DiscoveryMessageType.NEIGHBORS.getTypeValue()};
        byte[] data = RLP.encodeList();
        neighborsPeerMessage.encode(type, data, key1);

        byte[] packet = neighborsPeerMessage.getPacket();
        Exception exception = Assertions.assertThrows(PeerDiscoveryException.class, () -> MessageDecoder.decode(packet));
        Assertions.assertEquals(NeighborsPeerMessage.MORE_DATA, exception.getMessage());
    }

    @Test
    public void testDataSizePingMessage() {
        String check = UUID.randomUUID().toString();
        ECKey key1 = ECKey.fromPrivate(Hex.decode(KEY_1)).decompress();
        //String host, int port, String check, ECKey privKey, Integer networkId) {
        PingPeerMessage pingPeerMessage = PingPeerMessage.create(
                LOCALHOST,
                PORT,
                check,
                key1,
                NETWORK_ID);
        byte[] type = new byte[]{(byte) DiscoveryMessageType.PING.getTypeValue()};
        byte[] data = RLP.encodeList();
        pingPeerMessage.encode(type, data, key1);

        byte[] packet = pingPeerMessage.getPacket();
        Exception exception = Assertions.assertThrows(PeerDiscoveryException.class, () -> MessageDecoder.decode(packet));
        Assertions.assertEquals(PingPeerMessage.MORE_DATA, exception.getMessage());
    }

    @Test
    public void testFromToListSizePingMessage() {
        String check = UUID.randomUUID().toString();
        ECKey key1 = ECKey.fromPrivate(Hex.decode(KEY_1)).decompress();
        //String host, int port, String check, ECKey privKey, Integer networkId) {
        PingPeerMessage pingPeerMessage = PingPeerMessage.create(
                LOCALHOST,
                PORT,
                check,
                key1,
                NETWORK_ID);
        byte[] tmpNetworkId = intToBytes(NETWORK_ID);
        byte[] rlpNetworkID = RLP.encodeElement(stripLeadingZeroes(tmpNetworkId));
        byte[] rlpCheck = RLP.encodeElement(check.getBytes(StandardCharsets.UTF_8));
        byte[] type = new byte[]{(byte) DiscoveryMessageType.PING.getTypeValue()};
        byte[] data = RLP.encodeList(RLP.encodeList(), RLP.encodeList(), rlpCheck, rlpNetworkID);
        pingPeerMessage.encode(type, data, key1);

        Exception exception = Assertions.assertThrows(PeerDiscoveryException.class, () -> MessageDecoder.decode(pingPeerMessage.getPacket()));
        Assertions.assertEquals(PingPeerMessage.MORE_FROM_DATA, exception.getMessage());
    }

    @Test
    public void testDataSizePongMessage() {
        String check = UUID.randomUUID().toString();
        ECKey key1 = ECKey.fromPrivate(Hex.decode(KEY_1)).decompress();
        //String host, int port, String check, ECKey privKey, Integer networkId) {
        PongPeerMessage pongPeerMessage = PongPeerMessage.create(
                LOCALHOST,
                PORT,
                check,
                key1,
                NETWORK_ID);
        byte[] type = new byte[]{(byte) DiscoveryMessageType.PONG.getTypeValue()};
        byte[] data = RLP.encodeList();
        pongPeerMessage.encode(type, data, key1);

        Exception exception = Assertions.assertThrows(PeerDiscoveryException.class, () -> MessageDecoder.decode(pongPeerMessage.getPacket()));
        Assertions.assertEquals(PongPeerMessage.MORE_DATA, exception.getMessage());
    }

    @Test
    public void testFromToListSizePongMessage() {
        String check = UUID.randomUUID().toString();
        ECKey key1 = ECKey.fromPrivate(Hex.decode(KEY_1)).decompress();
        //String host, int port, String check, ECKey privKey, Integer networkId) {
        PongPeerMessage pongPeerMessage = PongPeerMessage.create(
                LOCALHOST,
                PORT,
                check,
                key1,
                NETWORK_ID);
        byte[] tmpNetworkId = intToBytes(NETWORK_ID);
        byte[] rlpNetworkID = RLP.encodeElement(stripLeadingZeroes(tmpNetworkId));
        byte[] rlpCheck = RLP.encodeElement(check.getBytes(StandardCharsets.UTF_8));
        byte[] type = new byte[]{(byte) DiscoveryMessageType.PONG.getTypeValue()};
        byte[] data = RLP.encodeList(RLP.encodeList(), RLP.encodeList(), rlpCheck, rlpNetworkID);
        pongPeerMessage.encode(type, data, key1);

        Exception exception = Assertions.assertThrows(PeerDiscoveryException.class, () -> MessageDecoder.decode(pongPeerMessage.getPacket()));
        Assertions.assertEquals(PongPeerMessage.MORE_FROM_DATA, exception.getMessage());
    }

    @Test
    public void testDataSizeFindNodeMessage() {
        String check = UUID.randomUUID().toString();
        ECKey key1 = ECKey.fromPrivate(Hex.decode(KEY_1)).decompress();
        //String host, int port, String check, ECKey privKey, Integer networkId) {
        FindNodePeerMessage findNodePeerMessage = FindNodePeerMessage.create(key1.getNodeId(), check, key1, NETWORK_ID);
        byte[] type = new byte[]{(byte) DiscoveryMessageType.FIND_NODE.getTypeValue()};
        byte[] data = RLP.encodeList();
        findNodePeerMessage.encode(type, data, key1);

        Exception exception = Assertions.assertThrows(PeerDiscoveryException.class, () -> MessageDecoder.decode(findNodePeerMessage.getPacket()));
        Assertions.assertEquals(FindNodePeerMessage.MORE_DATA, exception.getMessage());
    }

    @Test
    public void decode() {
        String check = UUID.randomUUID().toString();
        ECKey key1 = ECKey.fromPrivate(Hex.decode(KEY_1)).decompress();

        PingPeerMessage expectedPingMessage = PingPeerMessage.create(
                LOCALHOST,
                PORT,
                check,
                key1,
                NETWORK_ID);
        PingPeerMessage actualPingMessage = (PingPeerMessage) MessageDecoder.decode(expectedPingMessage.getPacket());

        assertDecodedMessage(actualPingMessage, expectedPingMessage);

        Assertions.assertEquals(actualPingMessage.getMessageType(), DiscoveryMessageType.PING);
        Assertions.assertEquals(actualPingMessage.getPort(), PORT);
        Assertions.assertEquals(actualPingMessage.getHost(), LOCALHOST);
        Assertions.assertEquals(actualPingMessage.getNodeId(), expectedPingMessage.getNodeId());
        Assertions.assertEquals(actualPingMessage.getKey(), expectedPingMessage.getKey());

        PongPeerMessage expectedPongMessage = PongPeerMessage.create(LOCALHOST, PORT+1, check, key1, NETWORK_ID);
        PongPeerMessage actualPongMessage = (PongPeerMessage) MessageDecoder.decode(expectedPongMessage.getPacket());

        assertDecodedMessage(actualPongMessage, expectedPongMessage);

        Assertions.assertEquals(actualPongMessage.getMessageType(), DiscoveryMessageType.PONG);
        Assertions.assertEquals(actualPongMessage.getPort(), PORT+1);
        Assertions.assertEquals(actualPongMessage.getHost(), LOCALHOST);
        Assertions.assertEquals(actualPongMessage.getNodeId(), expectedPongMessage.getNodeId());
        Assertions.assertEquals(actualPongMessage.getKey(), expectedPongMessage.getKey());

        FindNodePeerMessage expectedFindNodePeerMessage = FindNodePeerMessage.create(key1.getNodeId(), check, key1, NETWORK_ID);
        FindNodePeerMessage actualFindNodePeerMessage = (FindNodePeerMessage) MessageDecoder.decode(expectedFindNodePeerMessage.getPacket());

        assertDecodedMessage(actualPingMessage, expectedPingMessage);

        Assertions.assertEquals(actualFindNodePeerMessage.getMessageType(), DiscoveryMessageType.FIND_NODE);
        Assertions.assertEquals(actualFindNodePeerMessage.getNodeId(), expectedFindNodePeerMessage.getNodeId());

        NeighborsPeerMessage expectedNeighborsPeerMessage = NeighborsPeerMessage.create(new ArrayList<>(), check, key1, NETWORK_ID);
        NeighborsPeerMessage actualNeighborsPeerMessage = (NeighborsPeerMessage) MessageDecoder.decode(expectedNeighborsPeerMessage.getPacket());

        assertDecodedMessage(actualNeighborsPeerMessage, expectedNeighborsPeerMessage);

        Assertions.assertEquals(actualNeighborsPeerMessage.getNodes(), expectedNeighborsPeerMessage.getNodes());
        Assertions.assertEquals(actualNeighborsPeerMessage.getMessageType(), DiscoveryMessageType.NEIGHBORS);
    }

    public void assertDecodedMessage(PeerDiscoveryMessage actualMessage, PeerDiscoveryMessage expectedMessage) {
        Assertions.assertNotNull(actualMessage.getPacket());
        Assertions.assertNotNull(actualMessage.getMdc());
        Assertions.assertNotNull(actualMessage.getSignature());
        Assertions.assertNotNull(actualMessage.getType());
        Assertions.assertNotNull(actualMessage.getData());
        Assertions.assertEquals(actualMessage.getMessageType(), expectedMessage.getMessageType());
        Assertions.assertTrue(actualMessage.getNetworkId().isPresent());
        Assertions.assertEquals(actualMessage.getNetworkId().getAsInt(), NETWORK_ID);
    }
}
