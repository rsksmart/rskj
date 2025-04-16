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

package co.rsk.metrics.profilers;

/**
 * Interface every profiler has to implement. The profiler is responsible of the profiling logic.
 * Different profilers may take completely different measurements or use different approaches
 */
public interface Profiler {

    /**
     * Starts a metric of a specific type
     * @param kind task category that needs to be profiled
     * @return new Metric instance
     */
    Metric start(MetricKind kind);

    /**
     * Stops a metric finalizing all the properties being profiled
     * @param metric Metric instance that needs to be finalized
     */
    void stop(Metric metric);
}
