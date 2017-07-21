package co.rsk.net.discovery;

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by ajlopez on 21/07/2017.
 */
public class NodeDistanceScoreComparatorTest {
    @Test
    public void compareNodesInformation() {
        NodeInformation node1 = new NodeInformation(null, 10, 20);
        NodeInformation node2 = new NodeInformation(null, 10, 20);
        NodeInformation node3 = new NodeInformation(null, 10, 30);
        NodeInformation node4 = new NodeInformation(null,  5, 20);

        NodeDistanceScoreComparator comparator = new NodeDistanceScoreComparator();

        // same distance and score
        Assert.assertEquals(0, comparator.compare(node1, node2));
        Assert.assertEquals(0, comparator.compare(node2, node1));

        // same distance, different score
        Assert.assertEquals(1, comparator.compare(node1, node3));
        Assert.assertEquals(-1, comparator.compare(node3, node1));

        // different distance, same score
        Assert.assertEquals(1, comparator.compare(node1, node4));
        Assert.assertEquals(-1, comparator.compare(node4, node1));
    }
}
