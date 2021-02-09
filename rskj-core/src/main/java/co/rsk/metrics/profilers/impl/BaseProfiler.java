package co.rsk.metrics.profilers.impl;

import co.rsk.metrics.profilers.Metric;
import co.rsk.metrics.profilers.Profiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.Closeable;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.Objects;

public abstract class BaseProfiler implements Profiler, Closeable {

    private static final Logger logger = LoggerFactory.getLogger("profiler");

    private final Clock clock;

    public BaseProfiler() {
        this(Clock.NANOS);
    }

    public BaseProfiler(@Nonnull Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    protected abstract void onStop(@Nonnull MetricType type, long duration);

    protected static Logger getLogger() {
        return logger;
    }

    @Override
    @Nonnull
    public Metric start(MetricType type) {
        final long now = clock.currentTimeInUnits();
        return new MetricImpl(type, now);
    }

    @Override
    public void stop(@Nonnull Metric metric) {
        final long now = clock.currentTimeInUnits();

        if (!(metric instanceof MetricImpl)) {
            throw new IllegalArgumentException("metric");
        }

        final MetricImpl metricImpl = (MetricImpl) metric;
        final long duration = now - metricImpl.timestamp;

        onStop(metricImpl.type, duration);
    }

    static final class MetricImpl implements Metric {
        private final MetricType type;
        private final long timestamp;

        MetricImpl(@Nonnull MetricType type, long timestamp) {
            this.type = type;
            this.timestamp = timestamp;
        }
    }

    private interface CurrentTimeSupplier {
        long currentTime();
    }

    enum Clock {
        MILLIS(ChronoUnit.MILLIS, System::currentTimeMillis), NANOS(ChronoUnit.NANOS, System::nanoTime);

        private final TemporalUnit timeUnit;
        private final CurrentTimeSupplier curTimeSupplier;

        Clock(TemporalUnit timeUnit, CurrentTimeSupplier curTimeSupplier) {
            this.timeUnit = timeUnit;
            this.curTimeSupplier = curTimeSupplier;
        }

        long currentTimeInUnits() {
            return curTimeSupplier.currentTime();
        }
    }
}
