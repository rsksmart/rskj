package co.rsk.net.discovery;

import co.rsk.net.NodeID;
import co.rsk.scoring.*;
import org.ethereum.net.rlpx.Node;
import org.junit.Assert;
import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Random;

/**
 * Created by ajlopez on 21/07/2017.
 */
public class ScoreCalculatorTest {
    private static Random random = new Random();

    @Test
    public void createWithoutPeerScoringManager() {
        ScoreCalculator calculator = new ScoreCalculator(null);

        Assert.assertEquals(0, calculator.calculateScore(null));
    }

    @Test
    public void calculateScoreForNodeWithEmptyPeerScoringManager() {
        PeerScoringManager peerScoringManager = createPeerScoringManager();
        ScoreCalculator calculator = new ScoreCalculator(peerScoringManager);

        NodeID nodeID = generateNodeID();
        Node node = new Node(nodeID.getID(), "192.168..51.1", 3000);

        Assert.assertEquals(0, calculator.calculateScore(node));
    }

    @Test
    public void calculateScoreForNodeWithEventsAndPeerScoringManager() throws InvalidInetAddressException {
        PeerScoringManager peerScoringManager = createPeerScoringManager();
        ScoreCalculator calculator = new ScoreCalculator(peerScoringManager);

        NodeID nodeID = generateNodeID();
        Node node = new Node(nodeID.getID(), "192.168.51.1", 3000);
        InetAddress address = InetAddressUtils.getAddressForBan("192.168.51.1");

        peerScoringManager.recordEvent(nodeID, address, EventType.VALID_BLOCK);
        peerScoringManager.recordEvent(nodeID, null, EventType.VALID_BLOCK);

        int addressScore = peerScoringManager.getPeerScoring(address).getScore();

        Assert.assertEquals(addressScore, calculator.calculateScore(node));
    }

    @Test
    public void calculateScoreForNodeWithBadEventsAndPeerScoringManager() throws InvalidInetAddressException {
        PeerScoringManager peerScoringManager = createPeerScoringManager();
        ScoreCalculator calculator = new ScoreCalculator(peerScoringManager);

        NodeID nodeID = generateNodeID();
        Node node = new Node(nodeID.getID(), "192.168.51.1", 3000);
        InetAddress address = InetAddressUtils.getAddressForBan("192.168.51.1");

        peerScoringManager.recordEvent(null, address, EventType.VALID_BLOCK);
        peerScoringManager.recordEvent(nodeID, null, EventType.INVALID_BLOCK);

        int nodeScore = peerScoringManager.getPeerScoring(nodeID).getScore();

        Assert.assertEquals(nodeScore, calculator.calculateScore(node));
    }

    @Test
    public void calculateScoreForNodeWithIPV6Address() throws InvalidInetAddressException {
        PeerScoringManager peerScoringManager = createPeerScoringManager();
        ScoreCalculator calculator = new ScoreCalculator(peerScoringManager);

        NodeID nodeID = generateNodeID();
        Node node = new Node(nodeID.getID(), "fe80::498a:7f0e:e63d:6b98", 3000);
        InetAddress address = InetAddressUtils.getAddressForBan("fe80::498a:7f0e:e63d:6b98");

        peerScoringManager.recordEvent(null, address, EventType.VALID_BLOCK);
        peerScoringManager.recordEvent(nodeID, null, EventType.INVALID_BLOCK);

        int nodeScore = peerScoringManager.getPeerScoring(nodeID).getScore();

        Assert.assertEquals(nodeScore, calculator.calculateScore(node));
    }

    private static NodeID generateNodeID() {
        byte[] bytes = new byte[32];

        random.nextBytes(bytes);

        return new NodeID(bytes);
    }

    private static InetAddress generateIPAddressV4() throws UnknownHostException {
        byte[] bytes = new byte[4];

        random.nextBytes(bytes);

        return InetAddress.getByAddress(bytes);
    }

    private static InetAddress generateIPAddressV6() throws UnknownHostException {
        byte[] bytes = new byte[16];

        random.nextBytes(bytes);

        return InetAddress.getByAddress(bytes);
    }

    private static PeerScoringManager createPeerScoringManager() {
        return createPeerScoringManager(100);
    }

    private static PeerScoringManager createPeerScoringManager(int nnodes) {
        return new PeerScoringManager(nnodes, new PunishmentParameters(10, 10, 1000), new PunishmentParameters(10, 10, 1000));
    }
}
