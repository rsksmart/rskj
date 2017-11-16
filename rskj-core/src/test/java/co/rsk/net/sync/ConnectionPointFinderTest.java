package co.rsk.net.sync;

import org.junit.Assert;
import org.junit.Test;

public class ConnectionPointFinderTest {
    @Test
    public void findConnectionPoint0In50() {
        Long expectedConnectionPoint = 0L;
        long[] expectedHeights = new long[] { 25, 12, 6, 3, 1 };
        ConnectionPointFinder syncPeerProcessor = new ConnectionPointFinder(50);

        for (long expectedHeight : expectedHeights) {
            Assert.assertEquals(expectedHeight, syncPeerProcessor.getFindingHeight());
            Assert.assertFalse(syncPeerProcessor.getConnectionPoint().isPresent());
            syncPeerProcessor.updateNotFound();
        }

        Assert.assertTrue(syncPeerProcessor.getConnectionPoint().isPresent());
        Assert.assertEquals(expectedConnectionPoint, syncPeerProcessor.getConnectionPoint().get());
    }

    @Test
    public void findConnectionPoint0In100() {
        Long expectedConnectionPoint = 0L;
        long[] expectedHeights = new long[] { 50, 25, 12, 6, 3, 1 };
        ConnectionPointFinder connectionPointFinder = new ConnectionPointFinder(100);

        for (long expectedHeight : expectedHeights) {
            Assert.assertEquals(expectedHeight, connectionPointFinder.getFindingHeight());
            Assert.assertFalse(connectionPointFinder.getConnectionPoint().isPresent());
            connectionPointFinder.updateNotFound();
        }

        Assert.assertTrue(connectionPointFinder.getConnectionPoint().isPresent());
        Assert.assertEquals(expectedConnectionPoint, connectionPointFinder.getConnectionPoint().get());
    }

    @Test
    public void findConnectionPoint30In100() {
        Long expectedConnectionPoint = 30L;
        ConnectionPointFinder connectionPointFinder = new ConnectionPointFinder(100);

        Assert.assertEquals(50, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateNotFound();

        Assert.assertEquals(25, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateFound();

        Assert.assertEquals(37, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateNotFound();

        Assert.assertEquals(31, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateNotFound();

        Assert.assertEquals(28, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateFound();

        Assert.assertEquals(29, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateFound();

        Assert.assertEquals(30, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateFound();

        Assert.assertTrue(connectionPointFinder.getConnectionPoint().isPresent());
        Assert.assertEquals(expectedConnectionPoint, connectionPointFinder.getConnectionPoint().get());
    }

    @Test
    public void findConnectionPoint30In2030() {
        Long expectedConnectionPoint = 30L;
        long[] expectedHeights = new long[] { 1015, 507, 253, 126, 63, 31 };
        ConnectionPointFinder connectionPointFinder = new ConnectionPointFinder(2030);

        for (long expectedHeight : expectedHeights) {
            Assert.assertEquals(expectedHeight, connectionPointFinder.getFindingHeight());
            Assert.assertFalse(connectionPointFinder.getConnectionPoint().isPresent());
            connectionPointFinder.updateNotFound();
        }

        Assert.assertEquals(15, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateFound();

        Assert.assertEquals(23, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateFound();

        Assert.assertEquals(27, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateFound();

        Assert.assertEquals(29, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateFound();

        Assert.assertEquals(30, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateFound();

        Assert.assertTrue(connectionPointFinder.getConnectionPoint().isPresent());
        Assert.assertEquals(expectedConnectionPoint, connectionPointFinder.getConnectionPoint().get());
    }

    @Test
    public void findConnectionPoint300In4300() {
        Long expectedConnectionPoint = 300L;
        long[] expectedHeights = new long[] { 2150, 1075, 537 };
        ConnectionPointFinder connectionPointFinder = new ConnectionPointFinder(4300);

        for (long expectedHeight : expectedHeights) {
            Assert.assertEquals(expectedHeight, connectionPointFinder.getFindingHeight());
            Assert.assertFalse(connectionPointFinder.getConnectionPoint().isPresent());
            connectionPointFinder.updateNotFound();
        }

        Assert.assertEquals(268, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateFound();

        Assert.assertEquals(402, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateNotFound();

        Assert.assertEquals(335, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateNotFound();

        Assert.assertEquals(301, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateNotFound();

        Assert.assertEquals(284, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateFound();

        Assert.assertEquals(292, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateFound();

        Assert.assertEquals(296, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateFound();

        Assert.assertEquals(298, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateFound();

        Assert.assertEquals(299, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateFound();

        Assert.assertEquals(300, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateFound();

        Assert.assertTrue(connectionPointFinder.getConnectionPoint().isPresent());
        Assert.assertEquals(expectedConnectionPoint, connectionPointFinder.getConnectionPoint().get());
    }
}