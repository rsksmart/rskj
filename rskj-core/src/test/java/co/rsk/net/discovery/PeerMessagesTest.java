package co.rsk.net.discovery;

import co.rsk.net.discovery.message.DiscoveryMessageType;
import co.rsk.net.discovery.message.FindNodePeerMessage;
import co.rsk.net.discovery.message.PingPeerMessage;
import co.rsk.net.discovery.message.PongPeerMessage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

public class PeerMessagesTest {

    private static final String GOOD_HOST = "localhost";
    private static final int GOOD_PORT = 8080;

    private static byte[] wirePingPongPeerMessage = new byte[]{113,-5,-63,15,-4,21,-42,93,-10,19,-95,-37,-29,63,-92,-125,28,-27,-60,99,-85,-33,43,86,-12,-74,66,100,-31,-41,32,107,-69,20,-38,-122,-33,-110,-118,-126,-12,-85,20,91,44,44,-5,79,-101,-53,124,-57,-29,118,-81,-48,-89,-99,-29,-93,8,-17,113,-87,100,-123,-124,-77,-99,76,-51,54,4,-78,-41,116,84,-116,35,-35,-30,-12,16,-87,23,-32,-73,124,71,-26,-105,-28,-14,91,122,-114,1,1,-8,76,-48,-119,108,111,99,97,108,104,111,115,116,-126,31,-112,-126,31,-112,-48,-119,108,111,99,97,108,104,111,115,116,-126,31,-112,-126,31,-112,-92,48,97,57,54,55,56,53,53,45,99,102,98,97,45,52,98,49,98,45,57,56,57,48,45,102,54,99,57,51,49,97,52,97,55,97,48,-124,-59,3,-58,-55};
    private static byte[] mdcPingPongPeerMessage = new byte[]{113,-5,-63,15,-4,21,-42,93,-10,19,-95,-37,-29,63,-92,-125,28,-27,-60,99,-85,-33,43,86,-12,-74,66,100,-31,-41,32,107};
    private static byte[] signaturePingPongPeerMessage = new byte[]{-69,20,-38,-122,-33,-110,-118,-126,-12,-85,20,91,44,44,-5,79,-101,-53,124,-57,-29,118,-81,-48,-89,-99,-29,-93,8,-17,113,-87,100,-123,-124,-77,-99,76,-51,54,4,-78,-41,116,84,-116,35,-35,-30,-12,16,-87,23,-32,-73,124,71,-26,-105,-28,-14,91,122,-114,1};
    private static byte[] dataWithNetworkIdPingPongPeerMessage = new byte[]{-8,76,-48,-119,108,111,99,97,108,104,111,115,116,-126,31,-112,-126,31,-112,-48,-119,108,111,99,97,108,104,111,115,116,-126,31,-112,-126,31,-112,-92,48,97,57,54,55,56,53,53,45,99,102,98,97,45,52,98,49,98,45,57,56,57,48,45,102,54,99,57,51,49,97,52,97,55,97,48,-124,-59,3,-58,-55};
    private static byte[] dataWithoutNetworkIdPingPongPeerMessage = new byte[]{-8,71,-48,-119,108,111,99,97,108,104,111,115,116,-126,31,-112,-126,31,-112,-48,-119,108,111,99,97,108,104,111,115,116,-126,31,-112,-126,31,-112,-92,55,52,56,49,99,49,51,57,45,98,99,102,99,45,52,53,52,50,45,97,55,55,48,45,53,99,97,52,57,97,52,101,97,97,55,97};
    private static byte[] typePong = new byte[]{(byte) DiscoveryMessageType.PONG.getTypeValue()};
    private static byte[] typePing = new byte[]{(byte) DiscoveryMessageType.PING.getTypeValue()};

    private static byte[] signatureFindNodePeerMessage = new byte[]{-88,-13,-116,63,18,39,-90,-30,120,126,94,-25,-90,90,93,63,-124,120,-3,-116,-62,-38,41,-84,39,-69,-114,114,73,52,117,6,49,66,68,14,-31,17,-115,19,82,9,80,-57,-111,119,-15,108,-19,-89,105,-59,-7,-52,-4,-73,-66,111,-11,68,39,-46,42,-120,1};
    private static byte[] typeFindNodePeerMessage = new byte[]{3};
    private static byte[] wireFindNodePeerMessage = new byte[]{-23,116,115,41,75,96,-74,89,-26,-64,-33,-84,-81,-65,-34,-32,-89,-112,125,25,48,94,-75,114,109,28,-58,-55,-83,-64,-46,69,-74,-122,61,12,-101,26,-14,-28,72,-93,26,-125,-12,-38,55,80,31,33,-105,110,54,-25,80,45,18,-44,-127,-22,-50,-88,-78,77,116,-66,-56,-54,126,-5,-118,-91,-88,-76,52,56,-103,121,59,1,72,109,94,-14,118,47,62,-76,41,-63,82,103,125,-100,13,-88,0,3,-16,-118,-2,-74,-97,-8,69,1,67,49,16,99,49,56,51,56,100,52,100,45,49,122,-92,99,52,55,45,52,49,98,50,45,57,99,54,55,45,52,57,51,97,57,56,55,101,98,48,48,99};
    private static byte[] mdcFindNodePeerMessage = new byte[]{127,110,79,83,31,7,86,104,42,124,86,-57,76,-92,93,6,82,-37,97,-127,-54,72,86,-29,-81,-97,-10,94,-23,-102,-16,-82};
    private static byte[] dataWithoutNetworkId = new byte[]{-16,-118,55,-9,-121,-84,69,-97,31,36,-115,111,-92,98,101,48,52,97,98,98,98,45,54,48,50,102,45,52,99,49,50,45,56,99,48,56,45,101,55,52,101,51,49,101,54,54,48,97,97};
    private static byte[] dataWithNetworkId = new byte[]{-11,-118,86,54,14,-30,58,118,14,-123,57,84,-92,54,53,98,52,54,57,99,54,45,98,97,54,57,45,52,50,51,56,45,98,102,53,97,45,97,52,56,53,99,48,50,52,101,51,99,101,-124,-112,-104,-118,19};


    @Test
    public void testParsePongPeerMessageWithoutNetworkId(){
        PongPeerMessage pongPeerMessage = PongPeerMessage.buildFromReceived(wirePingPongPeerMessage, mdcPingPongPeerMessage, signaturePingPongPeerMessage, typePong, dataWithoutNetworkIdPingPongPeerMessage);
        Assertions.assertFalse(pongPeerMessage.getNetworkId().isPresent());
        Assertions.assertEquals("7481c139-bcfc-4542-a770-5ca49a4eaa7a",pongPeerMessage.getMessageId());
        Assertions.assertEquals(GOOD_HOST,pongPeerMessage.getHost());
        Assertions.assertEquals(GOOD_PORT,pongPeerMessage.getPort());
        Assertions.assertEquals(DiscoveryMessageType.PONG.getTypeValue() ,pongPeerMessage.getType()[0]);
    }

    @Test
    public void testParsePongPeerMessageWithNetworkId() {
        PongPeerMessage pongPeerMessage = PongPeerMessage.buildFromReceived(wirePingPongPeerMessage, mdcPingPongPeerMessage, signaturePingPongPeerMessage, typePong, dataWithNetworkIdPingPongPeerMessage);
        Assertions.assertTrue(pongPeerMessage.getNetworkId().isPresent());
        Assertions.assertEquals(-989608247,pongPeerMessage.getNetworkId().getAsInt());
        Assertions.assertEquals("0a967855-cfba-4b1b-9890-f6c931a4a7a0",pongPeerMessage.getMessageId());
        Assertions.assertEquals(GOOD_HOST,pongPeerMessage.getHost());
        Assertions.assertEquals(GOOD_PORT,pongPeerMessage.getPort());
        Assertions.assertEquals(DiscoveryMessageType.PONG.getTypeValue() ,pongPeerMessage.getType()[0]);
    }

    @Test
    public void testParsePingPeerMessageWithoutNetworkId(){
        PingPeerMessage pingPeerMessage = PingPeerMessage.buildFromReceived(wirePingPongPeerMessage, mdcPingPongPeerMessage, signaturePingPongPeerMessage,typePing, dataWithoutNetworkIdPingPongPeerMessage);
        Assertions.assertFalse(pingPeerMessage.getNetworkId().isPresent());
        Assertions.assertEquals("7481c139-bcfc-4542-a770-5ca49a4eaa7a",pingPeerMessage.getMessageId());
        Assertions.assertEquals(GOOD_HOST,pingPeerMessage.getHost());
        Assertions.assertEquals(GOOD_PORT,pingPeerMessage.getPort());
        Assertions.assertEquals(DiscoveryMessageType.PING.getTypeValue() ,pingPeerMessage.getType()[0]);
    }

    @Test
    public void testParsePingPeerMessageWithNetworkId() {
        PingPeerMessage pingPeerMessage = PingPeerMessage.buildFromReceived(wirePingPongPeerMessage, mdcPingPongPeerMessage, signaturePingPongPeerMessage,typePing, dataWithNetworkIdPingPongPeerMessage);
        Assertions.assertTrue(pingPeerMessage.getNetworkId().isPresent());
        Assertions.assertEquals(-989608247,pingPeerMessage.getNetworkId().getAsInt());
        Assertions.assertEquals("0a967855-cfba-4b1b-9890-f6c931a4a7a0",pingPeerMessage.getMessageId());
        Assertions.assertEquals(GOOD_HOST,pingPeerMessage.getHost());
        Assertions.assertEquals(GOOD_PORT,pingPeerMessage.getPort());
        Assertions.assertEquals(DiscoveryMessageType.PING.getTypeValue() ,pingPeerMessage.getType()[0]);
    }

    @Test
    public void testParseFindNodePeerMessageWithoutNetworkId() {
        FindNodePeerMessage findNodePeerMessageExpected = FindNodePeerMessage.buildFromReceived(wireFindNodePeerMessage,mdcFindNodePeerMessage,signatureFindNodePeerMessage,typeFindNodePeerMessage,dataWithoutNetworkId);
        Assertions.assertFalse(findNodePeerMessageExpected.getNetworkId().isPresent());
        Assertions.assertEquals("be04abbb-602f-4c12-8c08-e74e31e660aa", findNodePeerMessageExpected.getMessageId());
        Assertions.assertTrue(Arrays.equals(findNodePeerMessageExpected.getMdc(), mdcFindNodePeerMessage));
        Assertions.assertTrue(Arrays.equals(findNodePeerMessageExpected.getPacket(), wireFindNodePeerMessage));
        Assertions.assertTrue(Arrays.equals(findNodePeerMessageExpected.getType(), typeFindNodePeerMessage));
        Assertions.assertTrue(Arrays.equals(findNodePeerMessageExpected.getSignature(), signatureFindNodePeerMessage));
    }

    @Test
    public void testParseFindNodePeerMessage() {
        FindNodePeerMessage findNodePeerMessageExpected = FindNodePeerMessage.buildFromReceived(wireFindNodePeerMessage,mdcFindNodePeerMessage,signatureFindNodePeerMessage,typeFindNodePeerMessage,dataWithNetworkId);
        Assertions.assertTrue(findNodePeerMessageExpected.getNetworkId().isPresent());
        Assertions.assertEquals(-1869051373,findNodePeerMessageExpected.getNetworkId().getAsInt());
        Assertions.assertEquals("65b469c6-ba69-4238-bf5a-a485c024e3ce", findNodePeerMessageExpected.getMessageId());
        Assertions.assertTrue(Arrays.equals(findNodePeerMessageExpected.getMdc(), mdcFindNodePeerMessage));
        Assertions.assertTrue(Arrays.equals(findNodePeerMessageExpected.getPacket(), wireFindNodePeerMessage));
        Assertions.assertTrue(Arrays.equals(findNodePeerMessageExpected.getType(), typeFindNodePeerMessage));
        Assertions.assertTrue(Arrays.equals(findNodePeerMessageExpected.getSignature(), signatureFindNodePeerMessage));
    }

}
