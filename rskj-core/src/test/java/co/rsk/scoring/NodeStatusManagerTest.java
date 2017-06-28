package co.rsk.scoring;

import co.rsk.net.NodeID;
import org.junit.Assert;
import org.junit.Test;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Random;

/**
 * Created by ajlopez on 28/06/2017.
 */
public class NodeStatusManagerTest {
    private static Random random = new Random();

    @Test
    public void getEmptyNodeStatusFromUnknownNodeId() {
        NodeID id = generateNodeID();
        NodeStatusManager manager = new NodeStatusManager();

        NodeStatus result = manager.getNodeStatus(id);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void recordEventUsingNodeID() {
        NodeID id = generateNodeID();
        NodeStatusManager manager = new NodeStatusManager();

        manager.recordEvent(id, null, EventType.INVALID_BLOCK);

        NodeStatus result = manager.getNodeStatus(id);

        Assert.assertNotNull(result);
        Assert.assertFalse(result.isEmpty());
        Assert.assertEquals(1, result.getEventCounter(EventType.INVALID_BLOCK));
        Assert.assertEquals(1, result.getTotalEventCounter());
    }

    @Test
    public void recordEventUsingIPV4Address() throws UnknownHostException {
        InetAddress address = generateIPAddressV4();
        NodeStatusManager manager = new NodeStatusManager();

        manager.recordEvent(null, address, EventType.INVALID_BLOCK);

        NodeStatus result = manager.getNodeStatus(address);

        Assert.assertNotNull(result);
        Assert.assertFalse(result.isEmpty());
        Assert.assertEquals(1, result.getEventCounter(EventType.INVALID_BLOCK));
        Assert.assertEquals(1, result.getTotalEventCounter());
    }

    @Test
    public void recordEventUsingIPV6Address() throws UnknownHostException {
        InetAddress address = generateIPAddressV6();
        NodeStatusManager manager = new NodeStatusManager();

        manager.recordEvent(null, address, EventType.INVALID_BLOCK);

        NodeStatus result = manager.getNodeStatus(address);

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
