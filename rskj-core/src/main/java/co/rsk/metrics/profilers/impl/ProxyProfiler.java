package co.rsk.metrics.profilers.impl;

import co.rsk.metrics.profilers.Metric;
import co.rsk.metrics.profilers.Profiler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ProxyProfiler implements Profiler {

    private static final Metric EMPTY_METRIC = new Metric() {};

    private volatile Profiler base;

    public Profiler getBase() {
        return base;
    }

    public void setBase(Profiler base) {
        this.base = base;
    }

    @Nonnull
    @Override
    public Metric start(MetricType type) {
        return startOrDefault(base, type);
    }

    @Override
    public void stop(@Nonnull Metric metric) {
        stopOrDefault(base, metric);
    }

    private static Metric startOrDefault(@Nullable Profiler base, @Nonnull MetricType type) {
        if (base == null) {
            return EMPTY_METRIC;
        }
        return base.start(type);
    }

    private static void stopOrDefault(@Nullable Profiler base, @Nonnull Metric metric) {
        if (base == null || metric == EMPTY_METRIC) {
            return;
        }
        base.stop(metric);
    }
}
