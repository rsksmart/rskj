package co.rsk.net.discovery;

import co.rsk.net.discovery.message.*;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.UUID;
import org.junit.Test;


public class MessageDecoderTest {

    private static final String KEY_1 = "bd1d20e480dfb1c9c07ba0bc8cf9052f89923d38b5128c5dbfc18d4eea38261f";
    private static final int NETWORK_ID = 1;

    @Test(expected = PeerDiscoveryException.class)
    public void testMDCCheckFail() {
        byte[] wire = Hex.decode("00000000000000000000000000000000000000000000000000000000000000000000000000000000" +
                "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
                "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
                "000000000000000000000000000000000000000000000000000000000000000000");
        MessageDecoder.decode(wire);
        Assert.fail();
    }

    @Test(expected = PeerDiscoveryException.class)
    public void testLengthFail() {
        MessageDecoder.decode(new byte[] {11});
        Assert.fail();
    }

    @Test(expected = PeerDiscoveryException.class)
    public void testDataSizeNeighborsMessage() {
        byte[] wire = Hex.decode("64161b75a22291a416f2b3ad0c9e69311bda74d0fd8b168c018228fa1c9ecd33352771ac6b300bd2" +
            "a4590064340da20824cacaf46dec55e9abf0be17b5ebde786bab0a7e90c0a27115a27d30b4694500b8a3c9f19c8a5d3e802e" +
            "2094b17fce230104f856f854f852893132372e302e302e31827661827661b8402bc32aa570b3e292a9bd93380c1a23fb5c87" +
            "7d91bad5462affc80f45658e0abad0c4bef2d44aea6c2715e891114aa95ee09731dedf96bec099377a2f92aa13f0");
        MessageDecoder.decode(wire);
        Assert.fail();
    }

    @Test(expected = PeerDiscoveryException.class)
    public void testDataSizePingMessage() {
        byte[] wire = Hex.decode("1ccab8cd78349c8d08a7f1145b2d6066bb91df6286e50746d391ef351174d28ce61029cfd6392" +
                "d1a44fdf8f9433c5f22e57bb3290f5cede073498bd61993564a6ed74bb60173781daa5e3efd83cbf33e87e8041ac17f1" +
                "c63532f2ae688798dc40001f838d0896c6f63616c686f737482ac0382ac03c0a462316237623631332d333163652d343" +
                "262342d613762612d62343436373161666564323401");
        MessageDecoder.decode(wire);
        Assert.fail();
    }

    @Test(expected = PeerDiscoveryException.class)
    public void testDataSizePingMessage2() {
        byte[] wire = Hex.decode("271886d091b351c39711d8a7da9003d2d2f5974bfa53f832b74b7de8db59612bcda62f5d428" +
                "7bdad055045993cba5e6a35b7f7af956b1528a3fe8b9a4cfff3fa643d430c3a156301dc30a49464b7ee5fcb18564bba" +
                "6a9ec86eee017e713bf0860001c101");
        MessageDecoder.decode(wire);
        Assert.fail();
    }

    public void decode() {
        String check = UUID.randomUUID().toString();
        ECKey key1 = ECKey.fromPrivate(Hex.decode(KEY_1)).decompress();

        PingPeerMessage expectedPingMessage = PingPeerMessage.create(
                "localhost",
                44035,
                check,
                key1,
                NETWORK_ID);
        PingPeerMessage actualPingMessage = (PingPeerMessage) MessageDecoder.decode(expectedPingMessage.getPacket());

        assertDecodedMessage(actualPingMessage, expectedPingMessage);

        Assert.assertEquals(actualPingMessage.getMessageId(), DiscoveryMessageType.PING);
        Assert.assertEquals(actualPingMessage.getPort(), expectedPingMessage.getPort());
        Assert.assertEquals(actualPingMessage.getHost(), expectedPingMessage.getHost());
        Assert.assertEquals(actualPingMessage.getNodeId(), expectedPingMessage.getNodeId());
        Assert.assertEquals(actualPingMessage.getKey(), expectedPingMessage.getKey());

        PongPeerMessage expectedPongMessage = PongPeerMessage.create("localhost", 44036, check, key1, NETWORK_ID);
        PongPeerMessage actualPongMessage = (PongPeerMessage) MessageDecoder.decode(expectedPongMessage.getPacket());

        assertDecodedMessage(actualPongMessage, expectedPongMessage);

        Assert.assertEquals(actualPongMessage.getMessageId(), DiscoveryMessageType.PONG);
        Assert.assertEquals(actualPongMessage.getPort(), expectedPongMessage.getPort());
        Assert.assertEquals(actualPongMessage.getHost(), expectedPongMessage.getHost());
        Assert.assertEquals(actualPongMessage.getNodeId(), expectedPongMessage.getNodeId());
        Assert.assertEquals(actualPongMessage.getKey(), expectedPongMessage.getKey());

        FindNodePeerMessage expectedFindNodePeerMessage = FindNodePeerMessage.create(key1.getNodeId(), check, key1, NETWORK_ID);
        FindNodePeerMessage actualFindNodePeerMessage = (FindNodePeerMessage) MessageDecoder.decode(expectedFindNodePeerMessage.getPacket());

        assertDecodedMessage(actualPingMessage, expectedPingMessage);

        Assert.assertEquals(actualFindNodePeerMessage.getMessageId(), DiscoveryMessageType.FIND_NODE);
        Assert.assertEquals(actualFindNodePeerMessage.getNodeId(), expectedFindNodePeerMessage.getNodeId());

        NeighborsPeerMessage expectedNeighborsPeerMessage = NeighborsPeerMessage.create(new ArrayList<>(), check, key1, NETWORK_ID);
        NeighborsPeerMessage actualNeighborsPeerMessage = (NeighborsPeerMessage) MessageDecoder.decode(expectedNeighborsPeerMessage.getPacket());

        assertDecodedMessage(actualNeighborsPeerMessage, expectedNeighborsPeerMessage);

        Assert.assertEquals(actualNeighborsPeerMessage.getNodes(), expectedNeighborsPeerMessage.getNodes());
        Assert.assertEquals(actualNeighborsPeerMessage.getMessageId(), DiscoveryMessageType.NEIGHBORS);
    }

    public void assertDecodedMessage(PeerDiscoveryMessage actualMessage, PeerDiscoveryMessage expectedMessage) {
        Assert.assertNotNull(actualMessage.getPacket());
        Assert.assertNotNull(actualMessage.getMdc());
        Assert.assertNotNull(actualMessage.getSignature());
        Assert.assertNotNull(actualMessage.getType());
        Assert.assertNotNull(actualMessage.getData());
        Assert.assertEquals(actualMessage.getMessageType(), expectedMessage.getMessageType());
        Assert.assertTrue(actualMessage.getNetworkId().isPresent());
        Assert.assertEquals(actualMessage.getNetworkId().getAsInt(), expectedMessage.getNetworkId().getAsInt());
    }
}
