package co.rsk.net.discovery.message;

import co.rsk.net.discovery.PeerDiscoveryException;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.RLP;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.ethereum.util.ByteUtil.intToBytes;
import static org.ethereum.util.ByteUtil.stripLeadingZeroes;

public class FindNodePeerMessageTest {

    private static final String KEY_1 = "bd1d20e480dfb1c9c07ba0bc8cf9052f89923d38b5128c5dbfc18d4eea38261f";
    private static final int NETWORK_ID = 1;

    private static final byte[] signatureFindNodePeerMessage = new byte[]{-88, -13, -116, 63, 18, 39, -90, -30, 120, 126, 94, -25, -90, 90, 93, 63, -124, 120, -3, -116, -62, -38, 41, -84, 39, -69, -114, 114, 73, 52, 117, 6, 49, 66, 68, 14, -31, 17, -115, 19, 82, 9, 80, -57, -111, 119, -15, 108, -19, -89, 105, -59, -7, -52, -4, -73, -66, 111, -11, 68, 39, -46, 42, -120, 1};
    private static final byte[] wireFindNodePeerMessage = new byte[]{-23, 116, 115, 41, 75, 96, -74, 89, -26, -64, -33, -84, -81, -65, -34, -32, -89, -112, 125, 25, 48, 94, -75, 114, 109, 28, -58, -55, -83, -64, -46, 69, -74, -122, 61, 12, -101, 26, -14, -28, 72, -93, 26, -125, -12, -38, 55, 80, 31, 33, -105, 110, 54, -25, 80, 45, 18, -44, -127, -22, -50, -88, -78, 77, 116, -66, -56, -54, 126, -5, -118, -91, -88, -76, 52, 56, -103, 121, 59, 1, 72, 109, 94, -14, 118, 47, 62, -76, 41, -63, 82, 103, 125, -100, 13, -88, 0, 3, -16, -118, -2, -74, -97, -8, 69, 1, 67, 49, 16, 99, 49, 56, 51, 56, 100, 52, 100, 45, 49, 122, -92, 99, 52, 55, 45, 52, 49, 98, 50, 45, 57, 99, 54, 55, 45, 52, 57, 51, 97, 57, 56, 55, 101, 98, 48, 48, 99};
    private static final byte[] mdcFindNodePeerMessage = new byte[]{127, 110, 79, 83, 31, 7, 86, 104, 42, 124, 86, -57, 76, -92, 93, 6, 82, -37, 97, -127, -54, 72, 86, -29, -81, -97, -10, 94, -23, -102, -16, -82};

    @Test
    public void parseInvalidMessageId() {
        try {
            createFindNodePeerMessageWithCheck("http://fake-uuid.com/run");
            Assertions.fail("Invalid messageId exception should've been thrown");
        } catch (PeerDiscoveryException pde) {
            Assertions.assertEquals(FindNodePeerMessage.class.getSimpleName() + " needs valid messageId", pde.getMessage());
        }
    }

    @Test
    public void parseUUIDV1MessageId() {
        try {
            String uuidV1 = "06ce06f8-7230-11ec-90d6-0242ac120003";
            createFindNodePeerMessageWithCheck(uuidV1);
            Assertions.fail("Invalid messageId exception should've been thrown");
        } catch (PeerDiscoveryException pde) {
            Assertions.assertEquals(FindNodePeerMessage.class.getSimpleName() + " needs valid messageId", pde.getMessage());
        }
    }

    @Test
    public void parseValidMessageId() {
        try {
            FindNodePeerMessage message = createFindNodePeerMessageWithCheck(UUID.randomUUID().toString());
            Assertions.assertNotNull(message);
        } catch (PeerDiscoveryException pde) {
            Assertions.fail(FindNodePeerMessage.class.getSimpleName() + "should've worked with valid messageId");
        }
    }

    private FindNodePeerMessage createFindNodePeerMessageWithCheck(String check) {
        byte[] type = new byte[]{(byte) DiscoveryMessageType.FIND_NODE.getTypeValue()};

        ECKey key1 = ECKey.fromPrivate(Hex.decode(KEY_1)).decompress();
        byte[] rlpNodeId = RLP.encodeElement(key1.getNodeId());

        byte[] rlpCheck = RLP.encodeElement(check.getBytes(StandardCharsets.UTF_8));

        byte[] rlpNetworkId = RLP.encodeElement(stripLeadingZeroes(intToBytes(NETWORK_ID)));

        byte[] data = RLP.encodeList(rlpNodeId, rlpCheck, rlpNetworkId);

        return FindNodePeerMessage.buildFromReceived(wireFindNodePeerMessage, mdcFindNodePeerMessage, signatureFindNodePeerMessage, type, data);
    }
}
