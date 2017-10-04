package co.rsk.net.sync;

import org.junit.Assert;
import org.junit.Test;

public class SyncPeerProcessorTest {
    @Test
    public void findConnectionPoint0In50() {
        Long expectedConnectionPoint = 0L;
        long[] expectedHeights = new long[] { 25, 12, 6, 3, 1 };
        SyncPeerProcessor syncPeerProcessor = new SyncPeerProcessor();
        syncPeerProcessor.startFindConnectionPoint(50);

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
        SyncPeerProcessor syncPeerProcessor = new SyncPeerProcessor();
        syncPeerProcessor.startFindConnectionPoint(100);

        for (long expectedHeight : expectedHeights) {
            Assert.assertEquals(expectedHeight, syncPeerProcessor.getFindingHeight());
            Assert.assertFalse(syncPeerProcessor.getConnectionPoint().isPresent());
            syncPeerProcessor.updateNotFound();
        }

        Assert.assertTrue(syncPeerProcessor.getConnectionPoint().isPresent());
        Assert.assertEquals(expectedConnectionPoint, syncPeerProcessor.getConnectionPoint().get());
    }

    @Test
    public void findConnectionPoint30In100() {
        Long expectedConnectionPoint = 30L;
        SyncPeerProcessor syncPeerProcessor = new SyncPeerProcessor();
        syncPeerProcessor.startFindConnectionPoint(100);

        Assert.assertEquals(50, syncPeerProcessor.getFindingHeight());
        syncPeerProcessor.updateNotFound();

        Assert.assertEquals(25, syncPeerProcessor.getFindingHeight());
        syncPeerProcessor.updateFound();

        Assert.assertEquals(37, syncPeerProcessor.getFindingHeight());
        syncPeerProcessor.updateNotFound();

        Assert.assertEquals(31, syncPeerProcessor.getFindingHeight());
        syncPeerProcessor.updateNotFound();

        Assert.assertEquals(28, syncPeerProcessor.getFindingHeight());
        syncPeerProcessor.updateFound();

        Assert.assertEquals(29, syncPeerProcessor.getFindingHeight());
        syncPeerProcessor.updateFound();

        Assert.assertEquals(30, syncPeerProcessor.getFindingHeight());
        syncPeerProcessor.updateFound();

        Assert.assertTrue(syncPeerProcessor.getConnectionPoint().isPresent());
        Assert.assertEquals(expectedConnectionPoint, syncPeerProcessor.getConnectionPoint().get());
    }

    @Test
    public void findConnectionPoint30In2030() {
        Long expectedConnectionPoint = 30L;
        long[] expectedHeights = new long[] { 1015, 507, 253, 126, 63, 31 };
        SyncPeerProcessor syncPeerProcessor = new SyncPeerProcessor();
        syncPeerProcessor.startFindConnectionPoint(2030);

        for (long expectedHeight : expectedHeights) {
            Assert.assertEquals(expectedHeight, syncPeerProcessor.getFindingHeight());
            Assert.assertFalse(syncPeerProcessor.getConnectionPoint().isPresent());
            syncPeerProcessor.updateNotFound();
        }

        Assert.assertEquals(15, syncPeerProcessor.getFindingHeight());
        syncPeerProcessor.updateFound();

        Assert.assertEquals(23, syncPeerProcessor.getFindingHeight());
        syncPeerProcessor.updateFound();

        Assert.assertEquals(27, syncPeerProcessor.getFindingHeight());
        syncPeerProcessor.updateFound();

        Assert.assertEquals(29, syncPeerProcessor.getFindingHeight());
        syncPeerProcessor.updateFound();

        Assert.assertEquals(30, syncPeerProcessor.getFindingHeight());
        syncPeerProcessor.updateFound();

        Assert.assertTrue(syncPeerProcessor.getConnectionPoint().isPresent());
        Assert.assertEquals(expectedConnectionPoint, syncPeerProcessor.getConnectionPoint().get());
    }

    @Test
    public void findConnectionPoint300In4300() {
        Long expectedConnectionPoint = 300L;
        long[] expectedHeights = new long[] { 2150, 1075, 537 };
        SyncPeerProcessor syncPeerProcessor = new SyncPeerProcessor();
        syncPeerProcessor.startFindConnectionPoint(4300);

        for (long expectedHeight : expectedHeights) {
            Assert.assertEquals(expectedHeight, syncPeerProcessor.getFindingHeight());
            Assert.assertFalse(syncPeerProcessor.getConnectionPoint().isPresent());
            syncPeerProcessor.updateNotFound();
        }

        Assert.assertEquals(268, syncPeerProcessor.getFindingHeight());
        syncPeerProcessor.updateFound();

        Assert.assertEquals(402, syncPeerProcessor.getFindingHeight());
        syncPeerProcessor.updateNotFound();

        Assert.assertEquals(335, syncPeerProcessor.getFindingHeight());
        syncPeerProcessor.updateNotFound();

        Assert.assertEquals(301, syncPeerProcessor.getFindingHeight());
        syncPeerProcessor.updateNotFound();

        Assert.assertEquals(284, syncPeerProcessor.getFindingHeight());
        syncPeerProcessor.updateFound();

        Assert.assertEquals(292, syncPeerProcessor.getFindingHeight());
        syncPeerProcessor.updateFound();

        Assert.assertEquals(296, syncPeerProcessor.getFindingHeight());
        syncPeerProcessor.updateFound();

        Assert.assertEquals(298, syncPeerProcessor.getFindingHeight());
        syncPeerProcessor.updateFound();

        Assert.assertEquals(299, syncPeerProcessor.getFindingHeight());
        syncPeerProcessor.updateFound();

        Assert.assertEquals(300, syncPeerProcessor.getFindingHeight());
        syncPeerProcessor.updateFound();

        Assert.assertTrue(syncPeerProcessor.getConnectionPoint().isPresent());
        Assert.assertEquals(expectedConnectionPoint, syncPeerProcessor.getConnectionPoint().get());
    }
}