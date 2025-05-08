/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

import co.rsk.metrics.profilers.Metric;
import co.rsk.metrics.profilers.MetricKind;
import co.rsk.metrics.profilers.Profiler;

/**
 * A DisabledProfiler has no logic, it does not perform any profiling. It can be used as the default Profiler implementation
 */
public final class DisabledProfiler implements Profiler {

    public static final DisabledProfiler INSTANCE = new DisabledProfiler();

    private DisabledProfiler() { /* hidden */ }

    @Override
    public Metric start(MetricKind kind) {
        return null;
    }

    @Override
    public void stop(Metric metric) {
        // ignore
    }
}
