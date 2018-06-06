package co.rsk.net.discovery;

import co.rsk.net.discovery.message.FindNodePeerMessage;
import co.rsk.net.discovery.message.PingPeerMessage;
import co.rsk.net.discovery.message.PongPeerMessage;
import org.ethereum.crypto.ECKey;
import org.junit.Assert;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.UUID;

public class PeerMessagesTest {

    private ECKey key = ECKey.fromPrivate(Hex.decode("b2acb10771a26743ae5dd14ad48a02695b3f4ea4d383882155266cc1d1ebbda0")).decompress();;
    private static final String GOOD_HOST = "localhost";
    private static final int GOOD_PORT = 8080;

    //helpers - methods that create messages
    private PingPeerMessage testAndCreatePing(Integer networkId) {
        return testAndCreatePing(GOOD_HOST, GOOD_PORT, UUID.randomUUID().toString(), networkId);
    }

    private PingPeerMessage testAndCreatePing(String host, Integer port, String check, Integer networkId) {
        OptionalInt actualNetworkId = getNetworkId(networkId);
        PingPeerMessage ping = PingPeerMessage.create(host, port, check, key, actualNetworkId);
        Assert.assertEquals(host, ping.getHost());
        Assert.assertEquals(port.intValue(), ping.getPort());
        Assert.assertEquals(check, ping.getMessageId());
        Assert.assertEquals(actualNetworkId.isPresent(), ping.getNetworkId().isPresent());
        if (actualNetworkId.isPresent()) {
            Assert.assertEquals(actualNetworkId.getAsInt(), ping.getNetworkId().getAsInt());
        }
        return ping;
    }

    private PongPeerMessage testAndCreatePong(Integer networkId) {
        return testAndCreatePong(GOOD_HOST, GOOD_PORT, UUID.randomUUID().toString(), networkId);
    }

    private PongPeerMessage testAndCreatePong(String host, Integer port, String check, Integer netowrkId) {
        OptionalInt actualNetworkId = getNetworkId(netowrkId);
        PongPeerMessage pong = PongPeerMessage.create(host, port, check, key, actualNetworkId);
        Assert.assertEquals(host, pong.getHost());
        Assert.assertEquals(port.intValue(), pong.getPort());
        Assert.assertEquals(check, pong.getMessageId());
        Assert.assertEquals(actualNetworkId.isPresent(), pong.getNetworkId().isPresent());
        if (actualNetworkId.isPresent()) {
            Assert.assertEquals(actualNetworkId.getAsInt(), pong.getNetworkId().getAsInt());
        }
        return pong;
    }

    @Test
    public void testCreatePongMessage() {
        testAndCreatePong(null);
        testAndCreatePing(new Random().nextInt());
    }

    @Test
    public void testCreatePingMessage() {
        testAndCreatePing(null);
        testAndCreatePing(new Random().nextInt());
    }

    //Constructor uses parse method, so this is a test for parse.
    @Test
    public void testParseCreationPingMessage() {
        PingPeerMessage expectedPing = testAndCreatePing(null);
        PingPeerMessage ping =
                new PingPeerMessage(expectedPing.getPacket(),
                                    expectedPing.getMdc(),
                                    expectedPing.getSignature(),
                                    expectedPing.getType(),
                                    expectedPing.getData());
        Assert.assertEquals(expectedPing.getNetworkId(), ping.getNetworkId());
        Assert.assertEquals(expectedPing.getMessageId(), ping.getMessageId());
        Assert.assertEquals(expectedPing.getHost(), ping.getHost());
        Assert.assertEquals(expectedPing.getPort(), ping.getPort());

        int networkId = new Random().nextInt();
        expectedPing = testAndCreatePing(networkId);
        ping =
                new PingPeerMessage(expectedPing.getPacket(),
                        expectedPing.getMdc(),
                        expectedPing.getSignature(),
                        expectedPing.getType(),
                        expectedPing.getData());

        Assert.assertEquals(expectedPing.getNetworkId(), ping.getNetworkId());
        Assert.assertEquals(expectedPing.getMessageId(), ping.getMessageId());
        Assert.assertEquals(expectedPing.getHost(), ping.getHost());
        Assert.assertEquals(expectedPing.getPort(), ping.getPort());
    }

    @Test
    public void testParseCreationPongMessage() {
        PongPeerMessage expectedPong = testAndCreatePong(null);
        PongPeerMessage pong =
                new PongPeerMessage(expectedPong.getPacket(),
                        expectedPong.getMdc(),
                        expectedPong.getSignature(),
                        expectedPong.getType(),
                        expectedPong.getData());

        Assert.assertEquals(expectedPong.getNetworkId(), pong.getNetworkId());
        Assert.assertEquals(expectedPong.getMessageId(), pong.getMessageId());
        Assert.assertEquals(expectedPong.getHost(), pong.getHost());
        Assert.assertEquals(expectedPong.getPort(), pong.getPort());
        int networkId = new Random().nextInt();
        expectedPong = testAndCreatePong(networkId);
        pong =
                new PongPeerMessage(expectedPong.getPacket(),
                        expectedPong.getMdc(),
                        expectedPong.getSignature(),
                        expectedPong.getType(),
                        expectedPong.getData());

        Assert.assertEquals(expectedPong.getNetworkId(), pong.getNetworkId());
        Assert.assertEquals(expectedPong.getMessageId(), pong.getMessageId());
        Assert.assertEquals(expectedPong.getHost(), pong.getHost());
        Assert.assertEquals(expectedPong.getPort(), pong.getPort());
    }

//    @Test
//    public void testFindNodePeerMessage() {
//        FindNodePeerMessage findNodePeerMessage = FindNodePeerMessage.create();
////        public static FindNodePeerMessage create(byte[] nodeId, String check, ECKey privKey, OptionalInt networkId) {
//
//        }

    private OptionalInt getNetworkId(Integer networkId) {
        return Optional.ofNullable(networkId).map(OptionalInt::of).orElseGet(OptionalInt::empty);
    }
}
