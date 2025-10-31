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
import co.rsk.metrics.profilers.Metric;
import co.rsk.metrics.profilers.MetricKind;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import javax.management.*;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JmxProfilerTest {

    @Test
    void register_shouldRegisterAllMetricKinds() throws NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException {
        // Arrange
        JmxProfiler jmxProfiler = new JmxProfiler();
        HashSet<MetricKind> metricKinds = new HashSet<>(List.of(MetricKind.values()));
        MBeanServer mbs = mock(MBeanServer.class);
        when(mbs.registerMBean(any(), any())).thenAnswer(new Answer<ObjectInstance>() {
            @Override
            public ObjectInstance answer(InvocationOnMock invocationOnMock) throws Throwable {
                JmxMetric jmxMetric = invocationOnMock.getArgument(0, JmxMetric.class);
                metricKinds.remove(jmxMetric.getKind());
                ObjectName objectName = invocationOnMock.getArgument(1, ObjectName.class);
                assertEquals("co.rsk.metrics.Jmx:type=profiler,name=" + jmxMetric.getKind(), objectName.toString());
                return mock(ObjectInstance.class);
            }
        });

        // Act
        jmxProfiler.register(mbs);

        // Assert
        assertTrue(metricKinds.isEmpty());
    }

    @Test
    void register_whenMBeanServerFailsToRegisterBeen_thenRuntimeExceptionIsThrown() throws NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException {
        // Arrange
        JmxProfiler jmxProfiler = new JmxProfiler();
        MBeanServer mbs = mock(MBeanServer.class);
        when(mbs.registerMBean(any(), any())).thenThrow(MBeanRegistrationException.class);

        // Act & Assert
        assertThrows(RuntimeException.class, () -> jmxProfiler.register(mbs));
    }

    @Test
    void start_whenNullMetricKind_thenNPEIsThrown() {
        // Arrange
        JmxProfiler jmxProfiler = new JmxProfiler();
        MBeanServer mbs = mock(MBeanServer.class);
        jmxProfiler.register(mbs);

        // Act & Assert
        assertThrows(NullPointerException.class, () -> jmxProfiler.start(null));
    }

    @Test
    void start_whenNonNullMetricKind_thenMetricShouldNotBeNull() {
        // Arrange
        JmxProfiler jmxProfiler = new JmxProfiler();
        MBeanServer mbs = mock(MBeanServer.class);
        jmxProfiler.register(mbs);

        // Act
        Metric metric = jmxProfiler.start(MetricKind.BLOCK_CONNECTION);

        // Assert
        assertNotNull(metric);
    }

    @Test
    void stop_whenNullMetric_thenShouldIgnore() {
        // Arrange
        JmxProfiler jmxProfiler = new JmxProfiler();
        MBeanServer mbs = mock(MBeanServer.class);
        jmxProfiler.register(mbs);

        // Act & Assert
        assertDoesNotThrow(() -> jmxProfiler.stop(null));
    }
}
