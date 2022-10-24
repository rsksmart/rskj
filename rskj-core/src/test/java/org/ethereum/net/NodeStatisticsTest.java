package org.ethereum.net;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class NodeStatisticsTest {

    @Test
    void getSessionFairReputation_OK() {
        NodeStatistics nodeStatistics = new NodeStatistics();

        Assertions.assertEquals(0, nodeStatistics.getSessionFairReputation());
    }

}
