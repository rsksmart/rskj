/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.metrics.jmx;

import co.rsk.metrics.profilers.MetricKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
class JmxMetricTest {

    @Test
    void givenUpdateDurationCalledWithMetricAggregateMAX_thenCorrectAggregateApplied() {
        JmxMetric jmxMetric  = new JmxMetric(MetricKind.BLOCK_CONNECTION, MetricAggregate.MAX);
        jmxMetric.updateDuration(1000);
        jmxMetric.updateDuration(2000);
        jmxMetric.updateDuration(500);

        assertEquals(2000, jmxMetric.getDuration());
    }

    @Test
    void givenUpdateDurationCalledWithMetricAggregateMIN_thenCorrectAggregateApplied() {
        JmxMetric jmxMetric  = new JmxMetric(MetricKind.BLOCK_CONNECTION, MetricAggregate.MIN);
        jmxMetric.updateDuration(1000);
        jmxMetric.updateDuration(500);
        jmxMetric.updateDuration(2000);

        assertEquals(500, jmxMetric.getDuration());
    }

    @Test
    void givenUpdateDurationCalledWithMetricAggregateSUM_thenCorrectAggregateApplied() {
        JmxMetric jmxMetric  = new JmxMetric(MetricKind.BLOCK_CONNECTION, MetricAggregate.SUM);
        jmxMetric.updateDuration(1000);
        jmxMetric.updateDuration(500);
        jmxMetric.updateDuration(1500);

        assertEquals(3000, jmxMetric.getDuration());
    }

    @Test
    void givenUpdateDurationCalledWithMetricAggregateAVG_thenCorrectAggregateApplied() {
        JmxMetric jmxMetric  = new JmxMetric(MetricKind.BLOCK_CONNECTION, MetricAggregate.AVG);
        jmxMetric.updateDuration(1000);
        jmxMetric.updateDuration(500);
        jmxMetric.updateDuration(1500);

        assertEquals(1000, jmxMetric.getDuration());
    }

    @Test
    void givenUpdateDurationCalledWithMetricAggregateLAST_thenCorrectAggregateApplied() {
        JmxMetric jmxMetric  = new JmxMetric(MetricKind.BLOCK_CONNECTION, MetricAggregate.LAST);
        jmxMetric.updateDuration(500);
        jmxMetric.updateDuration(1500);
        jmxMetric.updateDuration(1000);

        assertEquals(1000, jmxMetric.getDuration());
    }
}
