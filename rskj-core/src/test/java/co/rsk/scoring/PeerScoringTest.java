package co.rsk.scoring;

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by ajlopez on 27/06/2017.
 */
public class PeerScoringTest {
    @Test
    public void newStatusHasCounterInZero() {
        PeerScoring scoring = new PeerScoring();

        Assert.assertEquals(0, scoring.getEventCounter(EventType.INVALID_BLOCK));
        Assert.assertEquals(0, scoring.getEventCounter(EventType.INVALID_TRANSACTION));
        Assert.assertEquals(0, scoring.getTotalEventCounter());
    }

    @Test
    public void newStatusHasGoodReputation() {
        PeerScoring scoring = new PeerScoring();

        Assert.assertTrue(scoring.hasGoodReputation());
    }

    @Test
    public void newStatusHasNoTimeLostGoodReputation() {
        PeerScoring scoring = new PeerScoring();

        Assert.assertEquals(0, scoring.getTimeLostGoodReputation());
    }

    @Test
    public void recordEvent() {
        PeerScoring scoring = new PeerScoring();

        scoring.recordEvent(EventType.INVALID_BLOCK);

        Assert.assertEquals(1, scoring.getEventCounter(EventType.INVALID_BLOCK));
        Assert.assertEquals(0, scoring.getEventCounter(EventType.INVALID_TRANSACTION));
        Assert.assertEquals(1, scoring.getTotalEventCounter());
    }

    @Test
    public void recordManyEvent() {
        PeerScoring scoring = new PeerScoring();

        scoring.recordEvent(EventType.INVALID_BLOCK);
        scoring.recordEvent(EventType.INVALID_BLOCK);
        scoring.recordEvent(EventType.INVALID_BLOCK);

        Assert.assertEquals(3, scoring.getEventCounter(EventType.INVALID_BLOCK));
        Assert.assertEquals(0, scoring.getEventCounter(EventType.INVALID_TRANSACTION));
        Assert.assertEquals(3, scoring.getTotalEventCounter());
    }

    @Test
    public void recordManyEventOfDifferentType() {
        PeerScoring scoring = new PeerScoring();

        scoring.recordEvent(EventType.INVALID_BLOCK);
        scoring.recordEvent(EventType.INVALID_BLOCK);
        scoring.recordEvent(EventType.INVALID_BLOCK);
        scoring.recordEvent(EventType.INVALID_TRANSACTION);
        scoring.recordEvent(EventType.INVALID_TRANSACTION);

        Assert.assertEquals(3, scoring.getEventCounter(EventType.INVALID_BLOCK));
        Assert.assertEquals(2, scoring.getEventCounter(EventType.INVALID_TRANSACTION));
        Assert.assertEquals(5, scoring.getTotalEventCounter());
    }

    @Test
    public void getZeroScoreWhenEmpty() {
        PeerScoring scoring = new PeerScoring();

        Assert.assertEquals(0, scoring.getScore());
    }

    @Test
    public void getPositiveScoreWhenValidBlock() {
        PeerScoring scoring = new PeerScoring();

        scoring.recordEvent(EventType.VALID_BLOCK);

        Assert.assertTrue(scoring.getScore() > 0);
    }

    @Test
    public void getNegativeScoreWhenInvalidBlock() {
        PeerScoring scoring = new PeerScoring();

        scoring.recordEvent(EventType.INVALID_BLOCK);

        Assert.assertTrue(scoring.getScore() < 0);
    }

    @Test
    public void getPositiveScoreWhenValidTransaction() {
        PeerScoring scoring = new PeerScoring();

        scoring.recordEvent(EventType.VALID_TRANSACTION);

        Assert.assertTrue(scoring.getScore() > 0);
    }

    @Test
    public void getNegativeScoreWhenInvalidTransaction() {
        PeerScoring scoring = new PeerScoring();

        scoring.recordEvent(EventType.INVALID_TRANSACTION);

        Assert.assertTrue(scoring.getScore() < 0);
    }

    @Test
    public void getNegativeScoreWhenValidAndInvalidTransaction() {
        PeerScoring scoring = new PeerScoring();

        scoring.recordEvent(EventType.VALID_TRANSACTION);
        scoring.recordEvent(EventType.INVALID_TRANSACTION);

        Assert.assertTrue(scoring.getScore() < 0);
    }

    @Test
    public void getNegativeScoreWhenInvalidAndValidTransaction() {
        PeerScoring scoring = new PeerScoring();

        scoring.recordEvent(EventType.INVALID_TRANSACTION);
        scoring.recordEvent(EventType.VALID_TRANSACTION);

        Assert.assertTrue(scoring.getScore() < 0);
    }
}
