package co.rsk.scoring;

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by ajlopez on 27/06/2017.
 */
public class NodeStatusTest {
    @Test
    public void newStatusHasCounterInZero() {
        NodeStatus status = new NodeStatus();

        Assert.assertEquals(0, status.getEventCounter(EventType.INVALID_BLOCK));
        Assert.assertEquals(0, status.getEventCounter(EventType.INVALID_TRANSACTION));
    }

    @Test
    public void recordEvent() {
        NodeStatus status = new NodeStatus();

        status.recordEvent(EventType.INVALID_BLOCK);

        Assert.assertEquals(1, status.getEventCounter(EventType.INVALID_BLOCK));
        Assert.assertEquals(0, status.getEventCounter(EventType.INVALID_TRANSACTION));
    }

    @Test
    public void recordManyEvent() {
        NodeStatus status = new NodeStatus();

        status.recordEvent(EventType.INVALID_BLOCK);
        status.recordEvent(EventType.INVALID_BLOCK);
        status.recordEvent(EventType.INVALID_BLOCK);

        Assert.assertEquals(3, status.getEventCounter(EventType.INVALID_BLOCK));
        Assert.assertEquals(0, status.getEventCounter(EventType.INVALID_TRANSACTION));
    }

    @Test
    public void recordManyEventOfDifferentType() {
        NodeStatus status = new NodeStatus();

        status.recordEvent(EventType.INVALID_BLOCK);
        status.recordEvent(EventType.INVALID_BLOCK);
        status.recordEvent(EventType.INVALID_BLOCK);
        status.recordEvent(EventType.INVALID_TRANSACTION);
        status.recordEvent(EventType.INVALID_TRANSACTION);

        Assert.assertEquals(3, status.getEventCounter(EventType.INVALID_BLOCK));
        Assert.assertEquals(2, status.getEventCounter(EventType.INVALID_TRANSACTION));
    }
}
