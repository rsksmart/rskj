package org.ethereum.net.p2p;


import co.rsk.util.RLPException;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PeersMessageTest {
    //This is the result of encoding the test peerList
    private static final String encodedHexString = "db05cc847649472482b6de821010c0cc8473cce52482f8ab821010c0";

    @Test
    void peerMessageIsCreatedProperly() throws UnknownHostException {
        byte[] payload = getTestPeerMessagePayload();
        PeersMessage message = new PeersMessage(payload);
        Set<PeerConnectionData> peerList = message.getPeers();
        List<PeerConnectionData> expectedPeerList = getTestPeerConnectionDataList();

        assertTrue(expectedPeerList.size() == peerList.size() && peerList.containsAll(expectedPeerList));
    }

    @Test
    void getPeersFromCreatePeers() throws UnknownHostException {
        List<PeerConnectionData> peerList = getTestPeerConnectionDataList();
        Set<PeerConnectionData> peerSet = new HashSet<>(peerList);
        PeersMessage peersMessage = new PeersMessage(peerSet);

        Set<PeerConnectionData> messagePeers = peersMessage.getPeers();
        assertEquals(messagePeers, peerSet);
        byte[] encodedData = peersMessage.getEncoded();
        assertEquals(Hex.toHexString(encodedData), encodedHexString);
    }

    @Test
    void parseFunctionIsCalledWith() {
        byte[] payload = TestUtils.generateBytes(PeersMessage.class
                , "payload", 1);
        PeersMessage message = new PeersMessage(payload);
        byte[] encoded = message.getEncoded();
        assertEquals(payload, encoded, "Payload must be used as encoded data");
        assertThrows(RLPException.class, message::getPeers, "Exception expected as payload is not valid and parse method is called");
    }

    @Test
    void getAnswerMessageReturnsNull(){
        PeersMessage peersMessage = new PeersMessage(getTestPeerMessagePayload());
        assertNull(peersMessage.getAnswerMessage());
    }

    @Test
    void correctStringMustBeReturned(){
        String expectedString = "[PEERS\n" +
                "       [ip=118.73.71.36 port=46814 peerId=1010]\n" +
                "       [ip=115.204.229.36 port=63659 peerId=1010]]";
        PeersMessage peersMessage = new PeersMessage(getTestPeerMessagePayload());
        assertEquals(expectedString,peersMessage.toString());

    }

    private byte[] getTestPeerMessagePayload(){
        return Hex.decode(encodedHexString);
    }

    private List<PeerConnectionData> getTestPeerConnectionDataList() throws UnknownHostException {
        PeerConnectionData peer1 = getPeer("peer1");
        PeerConnectionData peer2 = getPeer("peer2");

        return Arrays.asList(peer1, peer2);
    }

    private PeerConnectionData getPeer(String discriminator) throws UnknownHostException {
        Random random = new Random(discriminator.hashCode());
        byte[] bytes = TestUtils.generateBytes(discriminator.hashCode(), 4);
        InetAddress address = InetAddress.getByAddress(bytes);
        int port = random.nextInt(65535);
        String peerId = "1010";

        return new PeerConnectionData(address, port, peerId);
    }
}