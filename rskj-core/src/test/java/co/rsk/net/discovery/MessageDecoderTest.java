package co.rsk.net.discovery;

import co.rsk.net.discovery.message.*;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.RLP;
import org.junit.Assert;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.ethereum.util.ByteUtil.intToBytes;
import static org.ethereum.util.ByteUtil.stripLeadingZeroes;


public class MessageDecoderTest {

    private static final String KEY_1 = "bd1d20e480dfb1c9c07ba0bc8cf9052f89923d38b5128c5dbfc18d4eea38261f";
    private static final int NETWORK_ID = 1;
    public static final String LOCALHOST = "localhost";
    public static final int PORT = 44035;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void testMDCCheckFail() {
        //An array of all 0 would fail the sumcheck
        byte[] wire = new byte[172];
        exceptionRule.expect(PeerDiscoveryException.class);
        exceptionRule.expectMessage(MessageDecoder.MDC_CHECK_FAILED);
        MessageDecoder.decode(wire);
    }

    @Test
    public void testLengthFail() {
        exceptionRule.expect(PeerDiscoveryException.class);
        exceptionRule.expectMessage(MessageDecoder.BAD_MESSAGE);
        MessageDecoder.decode(new byte[] {11});
    }

    @Test
    public void testDataSizeNeighborsMessage() {
        exceptionRule.expect(PeerDiscoveryException.class);
        exceptionRule.expectMessage(NeighborsPeerMessage.MORE_DATA);
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
        MessageDecoder.decode(neighborsPeerMessage.getPacket());
    }

    @Test
    public void testDataSizePingMessage() {
        exceptionRule.expect(PeerDiscoveryException.class);
        exceptionRule.expectMessage(PingPeerMessage.MORE_DATA);
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
        MessageDecoder.decode(pingPeerMessage.getPacket());
        Assert.fail();
    }

    @Test
    public void testFromToListSizePingMessage() {
        exceptionRule.expect(PeerDiscoveryException.class);
        exceptionRule.expectMessage(PingPeerMessage.MORE_FROM_DATA);
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
        MessageDecoder.decode(pingPeerMessage.getPacket());
    }

    @Test
    public void testDataSizePongMessage() {
        exceptionRule.expect(PeerDiscoveryException.class);
        exceptionRule.expectMessage(PongPeerMessage.MORE_DATA);
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
        MessageDecoder.decode(pongPeerMessage.getPacket());
    }

    @Test
    public void testFromToListSizePongMessage() {
        exceptionRule.expect(PeerDiscoveryException.class);
        exceptionRule.expectMessage(PongPeerMessage.MORE_FROM_DATA);
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
        MessageDecoder.decode(pongPeerMessage.getPacket());
    }

    @Test
    public void testDataSizeFindNodeMessage() {
        exceptionRule.expect(PeerDiscoveryException.class);
        exceptionRule.expectMessage(FindNodePeerMessage.MORE_DATA);
        String check = UUID.randomUUID().toString();
        ECKey key1 = ECKey.fromPrivate(Hex.decode(KEY_1)).decompress();
        //String host, int port, String check, ECKey privKey, Integer networkId) {
        FindNodePeerMessage findNodePeerMessage = FindNodePeerMessage.create(key1.getNodeId(), check, key1, NETWORK_ID);
        byte[] type = new byte[]{(byte) DiscoveryMessageType.FIND_NODE.getTypeValue()};
        byte[] data = RLP.encodeList();
        findNodePeerMessage.encode(type, data, key1);
        MessageDecoder.decode(findNodePeerMessage.getPacket());
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

        Assert.assertEquals(actualPingMessage.getMessageType(), DiscoveryMessageType.PING);
        Assert.assertEquals(actualPingMessage.getPort(), PORT);
        Assert.assertEquals(actualPingMessage.getHost(), LOCALHOST);
        Assert.assertEquals(actualPingMessage.getNodeId(), expectedPingMessage.getNodeId());
        Assert.assertEquals(actualPingMessage.getKey(), expectedPingMessage.getKey());

        PongPeerMessage expectedPongMessage = PongPeerMessage.create(LOCALHOST, PORT+1, check, key1, NETWORK_ID);
        PongPeerMessage actualPongMessage = (PongPeerMessage) MessageDecoder.decode(expectedPongMessage.getPacket());

        assertDecodedMessage(actualPongMessage, expectedPongMessage);

        Assert.assertEquals(actualPongMessage.getMessageType(), DiscoveryMessageType.PONG);
        Assert.assertEquals(actualPongMessage.getPort(), PORT+1);
        Assert.assertEquals(actualPongMessage.getHost(), LOCALHOST);
        Assert.assertEquals(actualPongMessage.getNodeId(), expectedPongMessage.getNodeId());
        Assert.assertEquals(actualPongMessage.getKey(), expectedPongMessage.getKey());

        FindNodePeerMessage expectedFindNodePeerMessage = FindNodePeerMessage.create(key1.getNodeId(), check, key1, NETWORK_ID);
        FindNodePeerMessage actualFindNodePeerMessage = (FindNodePeerMessage) MessageDecoder.decode(expectedFindNodePeerMessage.getPacket());

        assertDecodedMessage(actualPingMessage, expectedPingMessage);

        Assert.assertEquals(actualFindNodePeerMessage.getMessageType(), DiscoveryMessageType.FIND_NODE);
        Assert.assertEquals(actualFindNodePeerMessage.getNodeId(), expectedFindNodePeerMessage.getNodeId());

        NeighborsPeerMessage expectedNeighborsPeerMessage = NeighborsPeerMessage.create(new ArrayList<>(), check, key1, NETWORK_ID);
        NeighborsPeerMessage actualNeighborsPeerMessage = (NeighborsPeerMessage) MessageDecoder.decode(expectedNeighborsPeerMessage.getPacket());

        assertDecodedMessage(actualNeighborsPeerMessage, expectedNeighborsPeerMessage);

        Assert.assertEquals(actualNeighborsPeerMessage.getNodes(), expectedNeighborsPeerMessage.getNodes());
        Assert.assertEquals(actualNeighborsPeerMessage.getMessageType(), DiscoveryMessageType.NEIGHBORS);
    }

    public void assertDecodedMessage(PeerDiscoveryMessage actualMessage, PeerDiscoveryMessage expectedMessage) {
        Assert.assertNotNull(actualMessage.getPacket());
        Assert.assertNotNull(actualMessage.getMdc());
        Assert.assertNotNull(actualMessage.getSignature());
        Assert.assertNotNull(actualMessage.getType());
        Assert.assertNotNull(actualMessage.getData());
        Assert.assertEquals(actualMessage.getMessageType(), expectedMessage.getMessageType());
        Assert.assertTrue(actualMessage.getNetworkId().isPresent());
        Assert.assertEquals(actualMessage.getNetworkId().getAsInt(), NETWORK_ID);
    }
}
