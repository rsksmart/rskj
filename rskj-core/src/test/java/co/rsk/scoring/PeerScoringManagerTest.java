package co.rsk.scoring;

import co.rsk.net.NodeID;
import org.junit.Assert;
import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Random;

/**
 * Created by ajlopez on 28/06/2017.
 */
public class PeerScoringManagerTest {
    private static Random random = new Random();

    @Test
    public void getEmptyNodeStatusFromUnknownNodeId() {
        NodeID id = generateNodeID();
        PeerScoringManager manager = new PeerScoringManager();

        PeerScoring result = manager.getPeerScoring(id);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void newNodeHasGoodReputation() {
        NodeID id = generateNodeID();
        PeerScoringManager manager = new PeerScoringManager();

        Assert.assertTrue(manager.hasGoodReputation(id));
    }

    @Test
    public void recordEventUsingNodeID() {
        NodeID id = generateNodeID();
        PeerScoringManager manager = new PeerScoringManager();

        manager.recordEvent(id, null, EventType.INVALID_BLOCK);

        PeerScoring result = manager.getPeerScoring(id);

        Assert.assertNotNull(result);
        Assert.assertFalse(result.isEmpty());
        Assert.assertEquals(1, result.getEventCounter(EventType.INVALID_BLOCK));
        Assert.assertEquals(1, result.getTotalEventCounter());
    }

    @Test
    public void newAddressHasGoodReputation() throws UnknownHostException {
        InetAddress address = generateIPAddressV4();
        PeerScoringManager manager = new PeerScoringManager();

        Assert.assertTrue(manager.hasGoodReputation(address));
    }

    @Test
    public void recordEventUsingNodeIDAndAddress() throws UnknownHostException {
        NodeID id = generateNodeID();
        InetAddress address = generateIPAddressV4();

        PeerScoringManager manager = new PeerScoringManager();

        manager.recordEvent(id, address, EventType.INVALID_BLOCK);

        PeerScoring result = manager.getPeerScoring(id);

        Assert.assertNotNull(result);
        Assert.assertFalse(result.isEmpty());
        Assert.assertEquals(1, result.getEventCounter(EventType.INVALID_BLOCK));
        Assert.assertEquals(1, result.getTotalEventCounter());

        result = manager.getPeerScoring(address);

        Assert.assertNotNull(result);
        Assert.assertFalse(result.isEmpty());
        Assert.assertEquals(1, result.getEventCounter(EventType.INVALID_BLOCK));
        Assert.assertEquals(1, result.getTotalEventCounter());
    }

    @Test
    public void recordEventUsingIPV4Address() throws UnknownHostException {
        InetAddress address = generateIPAddressV4();
        PeerScoringManager manager = new PeerScoringManager();

        manager.recordEvent(null, address, EventType.INVALID_BLOCK);

        PeerScoring result = manager.getPeerScoring(address);

        Assert.assertNotNull(result);
        Assert.assertFalse(result.isEmpty());
        Assert.assertEquals(1, result.getEventCounter(EventType.INVALID_BLOCK));
        Assert.assertEquals(1, result.getTotalEventCounter());
    }

    @Test
    public void recordEventUsingIPV6Address() throws UnknownHostException {
        InetAddress address = generateIPAddressV6();
        PeerScoringManager manager = new PeerScoringManager();

        manager.recordEvent(null, address, EventType.INVALID_BLOCK);

        PeerScoring result = manager.getPeerScoring(address);

        Assert.assertNotNull(result);
        Assert.assertFalse(result.isEmpty());
        Assert.assertEquals(1, result.getEventCounter(EventType.INVALID_BLOCK));
        Assert.assertEquals(1, result.getTotalEventCounter());
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
}
