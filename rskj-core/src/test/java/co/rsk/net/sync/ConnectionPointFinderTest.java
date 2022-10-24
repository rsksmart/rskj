package co.rsk.net.sync;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ConnectionPointFinderTest {
    @Test
    void findConnectionPoint0In50() {
        Long expectedConnectionPoint = 0L;
        long[] expectedHeights = new long[] { 25, 12, 6, 3, 1 };
        ConnectionPointFinder syncPeerProcessor = new ConnectionPointFinder(0L, 50);

        for (long expectedHeight : expectedHeights) {
            Assertions.assertEquals(expectedHeight, syncPeerProcessor.getFindingHeight());
            Assertions.assertFalse(syncPeerProcessor.getConnectionPoint().isPresent());
            syncPeerProcessor.updateNotFound();
        }

        Assertions.assertTrue(syncPeerProcessor.getConnectionPoint().isPresent());
        Assertions.assertEquals(expectedConnectionPoint, syncPeerProcessor.getConnectionPoint().get());
    }

    @Test
    void findConnectionPoint0In100() {
        Long expectedConnectionPoint = 0L;
        long[] expectedHeights = new long[] { 50, 25, 12, 6, 3, 1 };
        ConnectionPointFinder connectionPointFinder = new ConnectionPointFinder(0L, 100);

        for (long expectedHeight : expectedHeights) {
            Assertions.assertEquals(expectedHeight, connectionPointFinder.getFindingHeight());
            Assertions.assertFalse(connectionPointFinder.getConnectionPoint().isPresent());
            connectionPointFinder.updateNotFound();
        }

        Assertions.assertTrue(connectionPointFinder.getConnectionPoint().isPresent());
        Assertions.assertEquals(expectedConnectionPoint, connectionPointFinder.getConnectionPoint().get());
    }

    @Test
    void findConnectionPoint30In100() {
        Long expectedConnectionPoint = 30L;
        ConnectionPointFinder connectionPointFinder = new ConnectionPointFinder(0L, 100);

        Assertions.assertEquals(50, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateNotFound();

        Assertions.assertEquals(25, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateFound();

        Assertions.assertEquals(37, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateNotFound();

        Assertions.assertEquals(31, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateNotFound();

        Assertions.assertEquals(28, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateFound();

        Assertions.assertEquals(29, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateFound();

        Assertions.assertEquals(30, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateFound();

        Assertions.assertTrue(connectionPointFinder.getConnectionPoint().isPresent());
        Assertions.assertEquals(expectedConnectionPoint, connectionPointFinder.getConnectionPoint().get());
    }

    @Test
    void findConnectionPoint30In2030() {
        Long expectedConnectionPoint = 30L;
        long[] expectedHeights = new long[] { 1015, 507, 253, 126, 63, 31 };
        ConnectionPointFinder connectionPointFinder = new ConnectionPointFinder(0L, 2030);

        for (long expectedHeight : expectedHeights) {
            Assertions.assertEquals(expectedHeight, connectionPointFinder.getFindingHeight());
            Assertions.assertFalse(connectionPointFinder.getConnectionPoint().isPresent());
            connectionPointFinder.updateNotFound();
        }

        Assertions.assertEquals(15, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateFound();

        Assertions.assertEquals(23, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateFound();

        Assertions.assertEquals(27, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateFound();

        Assertions.assertEquals(29, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateFound();

        Assertions.assertEquals(30, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateFound();

        Assertions.assertTrue(connectionPointFinder.getConnectionPoint().isPresent());
        Assertions.assertEquals(expectedConnectionPoint, connectionPointFinder.getConnectionPoint().get());
    }

    @Test
    void findConnectionPoint300In4300() {
        Long expectedConnectionPoint = 300L;
        long[] expectedHeights = new long[] { 2150, 1075, 537 };
        ConnectionPointFinder connectionPointFinder = new ConnectionPointFinder(0L, 4300);

        for (long expectedHeight : expectedHeights) {
            Assertions.assertEquals(expectedHeight, connectionPointFinder.getFindingHeight());
            Assertions.assertFalse(connectionPointFinder.getConnectionPoint().isPresent());
            connectionPointFinder.updateNotFound();
        }

        Assertions.assertEquals(268, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateFound();

        Assertions.assertEquals(402, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateNotFound();

        Assertions.assertEquals(335, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateNotFound();

        Assertions.assertEquals(301, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateNotFound();

        Assertions.assertEquals(284, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateFound();

        Assertions.assertEquals(292, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateFound();

        Assertions.assertEquals(296, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateFound();

        Assertions.assertEquals(298, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateFound();

        Assertions.assertEquals(299, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateFound();

        Assertions.assertEquals(300, connectionPointFinder.getFindingHeight());
        connectionPointFinder.updateFound();

        Assertions.assertTrue(connectionPointFinder.getConnectionPoint().isPresent());
        Assertions.assertEquals(expectedConnectionPoint, connectionPointFinder.getConnectionPoint().get());
    }
}
