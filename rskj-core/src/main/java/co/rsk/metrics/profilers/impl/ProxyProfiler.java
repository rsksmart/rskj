/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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
import co.rsk.metrics.profilers.Profiler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * A DummyProfiler has no logic, it does not perform any profiling. It can be used as the default Profiler implementation
 */
public class ProxyProfiler implements Profiler {
    
    private static final Metric EMPTY = () -> { /* do nothing */ };

    @Nullable
    private Profiler profiler;
    
    public void setProfiler(@Nullable Profiler profiler) {
        this.profiler = profiler;
    }

    @Override
    @Nonnull
    public Metric start(@Nonnull MetricType type) {
        Profiler profiler = this.profiler;
        
        return profiler == null ? EMPTY : profiler.start(Objects.requireNonNull(type));
    }
}
