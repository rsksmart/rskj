package co.rsk.scoring;

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by ajlopez on 27/06/2017.
 */
public class NodeScoringTest {
    @Test
    public void newStatusHasCounterInZero() {
        NodeScoring scoring = new NodeScoring();

        Assert.assertEquals(0, scoring.getEventCounter(EventType.INVALID_BLOCK));
        Assert.assertEquals(0, scoring.getEventCounter(EventType.INVALID_TRANSACTION));
        Assert.assertEquals(0, scoring.getTotalEventCounter());
    }

    @Test
    public void recordEvent() {
        NodeScoring scoring = new NodeScoring();

        scoring.recordEvent(EventType.INVALID_BLOCK);

        Assert.assertEquals(1, scoring.getEventCounter(EventType.INVALID_BLOCK));
        Assert.assertEquals(0, scoring.getEventCounter(EventType.INVALID_TRANSACTION));
        Assert.assertEquals(1, scoring.getTotalEventCounter());
    }

    @Test
    public void recordManyEvent() {
        NodeScoring scoring = new NodeScoring();

        scoring.recordEvent(EventType.INVALID_BLOCK);
        scoring.recordEvent(EventType.INVALID_BLOCK);
        scoring.recordEvent(EventType.INVALID_BLOCK);

        Assert.assertEquals(3, scoring.getEventCounter(EventType.INVALID_BLOCK));
        Assert.assertEquals(0, scoring.getEventCounter(EventType.INVALID_TRANSACTION));
        Assert.assertEquals(3, scoring.getTotalEventCounter());
    }

    @Test
    public void recordManyEventOfDifferentType() {
        NodeScoring scoring = new NodeScoring();

        scoring.recordEvent(EventType.INVALID_BLOCK);
        scoring.recordEvent(EventType.INVALID_BLOCK);
        scoring.recordEvent(EventType.INVALID_BLOCK);
        scoring.recordEvent(EventType.INVALID_TRANSACTION);
        scoring.recordEvent(EventType.INVALID_TRANSACTION);

        Assert.assertEquals(3, scoring.getEventCounter(EventType.INVALID_BLOCK));
        Assert.assertEquals(2, scoring.getEventCounter(EventType.INVALID_TRANSACTION));
        Assert.assertEquals(5, scoring.getTotalEventCounter());
    }
}
