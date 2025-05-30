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

package co.rsk.metrics.profilers.impl;

import co.rsk.metrics.jmx.JmxMetric;
import co.rsk.metrics.jmx.MetricAggregate;
import co.rsk.metrics.profilers.Metric;
import co.rsk.metrics.profilers.MetricKind;
import co.rsk.metrics.profilers.Profiler;

import javax.annotation.Nonnull;
import javax.management.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class JmxProfiler implements Profiler {

    private final Map<MetricKind, JmxMetric> jmxMetrics = new HashMap<>();

    public void register(@Nonnull MBeanServer mbs) {
        Objects.requireNonNull(mbs);

        for (MetricKind kind : MetricKind.values()) {
            JmxMetric jmxMetric = new JmxMetric(kind, MetricAggregate.MAX);
            try {
                ObjectName name = new ObjectName("co.rsk.metrics.Jmx:type=profiler,name=" + kind);
                mbs.registerMBean(jmxMetric, name);
            } catch (MalformedObjectNameException | NotCompliantMBeanException | MBeanRegistrationException |
                     InstanceAlreadyExistsException e) {
                jmxMetrics.clear();
                throw new RuntimeException("Failed to register JMX metric: " + kind, e);
            }

            jmxMetrics.put(kind, jmxMetric);
        }
    }

    @Override
    public Metric start(MetricKind kind) {
        return new MetricImpl(kind);
    }

    @Override
    public void stop(Metric metric) {
        if (metric instanceof MetricImpl) {
            JmxMetric jmxMetric = jmxMetrics.get(((MetricImpl) metric).getKind());
            if (jmxMetric != null) {
                jmxMetric.updateDuration(((MetricImpl) metric).getDuration());
            }
        }
    }

    private static class MetricImpl implements Metric {
        private final MetricKind kind;
        private final long startTime;

        MetricImpl(MetricKind kind) {
            this.kind = Objects.requireNonNull(kind);
            this.startTime = System.nanoTime();
        }

        MetricKind getKind() {
            return kind;
        }

        long getDuration() {
            return System.nanoTime() - startTime;
        }
    }
}
