package co.rsk.metrics.profilers.impl;


import co.rsk.metrics.profilers.Metric;
import co.rsk.metrics.profilers.MetricKind;
import co.rsk.metrics.profilers.Profiler;

/**
 * A DummyProfiler has no logic, it does not perform any profiling. It can be used as the default Profiler implementation
 */
public class DummyProfiler implements Profiler {


    @Override
    public Metric start(MetricKind kind) {
        return null;
    }

    @Override
    public void stop(Metric metric) {
    }
}
