package co.rsk.net.discovery;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    @Test
    public void sortNodesInformation() {
        NodeInformation node1 = new NodeInformation(null, 10, 20);
        NodeInformation node2 = new NodeInformation(null, 10, 20);
        NodeInformation node3 = new NodeInformation(null, 10, 30);
        NodeInformation node4 = new NodeInformation(null,  5, 20);

        List<NodeInformation> list = new ArrayList<>();
        list.add(node1);
        list.add(node2);
        list.add(node3);
        list.add(node4);

        Collections.sort(list, new NodeDistanceScoreComparator());

        Assert.assertSame(node4, list.get(0));
        Assert.assertSame(node3, list.get(1));
        Assert.assertEquals(node2.getDistance(), list.get(2).getDistance());
        Assert.assertEquals(node2.getScore(), list.get(2).getScore());
        Assert.assertEquals(node1.getDistance(), list.get(3).getDistance());
        Assert.assertEquals(node1.getScore(), list.get(3).getScore());
    }
}
