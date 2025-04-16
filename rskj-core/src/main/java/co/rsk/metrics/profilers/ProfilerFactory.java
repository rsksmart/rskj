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

import co.rsk.metrics.profilers.impl.DummyProfiler;

/**
 * ProfilerFactory is used to get the configured Profiler instance.
 * Only one profiler can be defined, once a profiler is set, it cannot be changed.
 * If a profiler isn't configured, the DummyProfiler will be set upon the first request for the instance.
 */
public final class ProfilerFactory {

    private static volatile Profiler instance = null;

    private ProfilerFactory(){
        super();
    }

    public static synchronized void configure(Profiler profiler){
        if(instance == null){
            instance = profiler;
        }
    }

    public static Profiler getInstance(){
        if(instance == null){
            configure(new DummyProfiler());
        }

        return instance;
    }
}
