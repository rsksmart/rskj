package co.rsk.metrics.profilers.impl;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A LoggingProfiler tracks performance results by sending them to the "profiler" logger.
 */
public class LoggingProfiler extends BaseProfiler {

    private final Set<MetricType> filter;

    public LoggingProfiler() {
        this(Clock.NANOS, Collections.emptySet());
    }

    public LoggingProfiler(@Nonnull Clock clock, @Nonnull Collection<MetricType> filter) {
        super(clock);

        this.filter = new HashSet<>(filter);
    }

    @Override
    protected void onStop(@Nonnull MetricType type, long duration) {
        if (filter.isEmpty() || filter.contains(type)) {
            getLogger().info(type + " " + duration);
        }
    }

    @Override
    public void close() {
        // do nothing
    }
}
