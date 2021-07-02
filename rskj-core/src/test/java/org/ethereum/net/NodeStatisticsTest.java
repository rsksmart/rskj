package org.ethereum.net;

import org.junit.Assert;
import org.junit.Test;

public class NodeStatisticsTest {

    @Test
    public void getSessionFairReputation_OK() {
        NodeStatistics nodeStatistics = new NodeStatistics();

        Assert.assertEquals(0, nodeStatistics.getSessionFairReputation());
    }

}
