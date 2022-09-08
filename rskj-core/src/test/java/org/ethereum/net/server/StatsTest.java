package org.ethereum.net.server;

import co.rsk.net.messages.MessageType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class StatsTest {

    @Test
    public void TestGeneralPerformance() {
        Stats stats = new Stats();

        double score = stats.score(MessageType.STATUS_MESSAGE);
        //time starts at 0
        stats.update(10, MessageType.STATUS_MESSAGE);
        stats.update(20, MessageType.STATUS_MESSAGE);
        stats.update(30, MessageType.STATUS_MESSAGE);

        Assertions.assertTrue(score > stats.score(MessageType.STATUS_MESSAGE));
        score = stats.score(MessageType.STATUS_MESSAGE);

        int iters = 11500;
        for (int i = 0; i < iters; i++) {
            stats.update(1000 * i, MessageType.STATUS_MESSAGE);
        }

        Assertions.assertTrue(score < stats.score(MessageType.STATUS_MESSAGE));
        Assertions.assertTrue(Math.abs(stats.getMpm() - 60) < 5);
        Assertions.assertEquals((iters - 1) % 60, stats.getMinute());

        System.out.println(stats);

    }

    @Test
    public void TestHighFrequency() {
        Stats stats = new Stats();
        stats.setAvg(500);

        double old = stats.score(MessageType.STATUS_MESSAGE);
        double updated = stats.update(100, MessageType.STATUS_MESSAGE);

        Assertions.assertTrue(updated < old);
    }

    @Test
    public void TestLowFrequency() {
        Stats stats = new Stats();
        stats.setAvg(500);;

        double old = stats.score(MessageType.STATUS_MESSAGE);
        double updated = stats.update(1000, MessageType.STATUS_MESSAGE);

        Assertions.assertTrue(updated > old);
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

        Assertions.assertTrue(updated > old);
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

        Assertions.assertTrue(updated < old);
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

        Assertions.assertTrue(v1 > v2);
        Assertions.assertTrue(v2 > v3);
        Assertions.assertTrue(v3 > v4);
        Assertions.assertTrue(v4 > v5);

    }
}
