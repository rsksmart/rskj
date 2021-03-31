package org.ethereum.net.server;

import co.rsk.net.messages.MessageType;
import org.junit.Assert;
import org.junit.Test;

public class StatsTest {

    @Test
    public void TestGeneralPerformance() {
        Stats stats = new Stats();

        double score = stats.score(MessageType.STATUS_MESSAGE);
        //time starts at 0
        stats.update(10, MessageType.STATUS_MESSAGE);
        stats.update(20, MessageType.STATUS_MESSAGE);
        stats.update(30, MessageType.STATUS_MESSAGE);

        Assert.assertTrue(score > stats.score(MessageType.STATUS_MESSAGE));
        score = stats.score(MessageType.STATUS_MESSAGE);

        int iters = 11500;
        for (int i = 0; i < iters; i++) {
            stats.update(1000 * i, MessageType.STATUS_MESSAGE);
        }

        Assert.assertTrue(score < stats.score(MessageType.STATUS_MESSAGE));
        Assert.assertTrue(Math.abs(stats.getMpm() - 60) < 5);
        Assert.assertEquals((iters - 1) % 60, stats.getMinute());

        System.out.println(stats);

    }

    @Test
    public void TestHighFrequency() {
        Stats stats = new Stats();
        stats.setAvg(500);

        double old = stats.score(MessageType.STATUS_MESSAGE);
        double updated = stats.update(100, MessageType.STATUS_MESSAGE);

        Assert.assertTrue(updated < old);
    }

    @Test
    public void TestLowFrequency() {
        Stats stats = new Stats();
        stats.setAvg(500);;

        double old = stats.score(MessageType.STATUS_MESSAGE);
        double updated = stats.update(1000, MessageType.STATUS_MESSAGE);

        Assert.assertTrue(updated > old);
    }

    @Test
    public void TestImportedBest() {
        Stats stats = new Stats();
        stats.setAvg(500);
        stats.setImportedBest(20);
        stats.setImportedNotBest(100);

        double old = stats.score(MessageType.STATUS_MESSAGE);
        stats.imported(true);
        double updated = stats.score(MessageType.STATUS_MESSAGE);

        Assert.assertTrue(updated > old);
    }

    @Test
    public void TestImportedNotBest() {
        Stats stats = new Stats();
        stats.setAvg(500);
        stats.setImportedBest(20);
        stats.setImportedNotBest(100);

        double old = stats.score(MessageType.STATUS_MESSAGE);
        stats.imported(false);
        double updated = stats.score(MessageType.STATUS_MESSAGE);

        Assert.assertTrue(updated < old);
    }

    @Test
    public void TestMessageTypes() {
        Stats stats = new Stats();
        stats.setAvg(500);

        double v1 = stats.score(MessageType.BLOCK_RESPONSE_MESSAGE);
        double v2 = stats.score(MessageType.BLOCK_MESSAGE);
        double v3 = stats.score(MessageType.TRANSACTIONS);
        double v4 = stats.score(MessageType.STATUS_MESSAGE);
        double v5 = stats.score(MessageType.BLOCK_HEADERS_REQUEST_MESSAGE);

        Assert.assertTrue(v1 > v2);
        Assert.assertTrue(v2 > v3);
        Assert.assertTrue(v3 > v4);
        Assert.assertTrue(v4 > v5);

    }
}