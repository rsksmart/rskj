package co.rsk.metrics.profilers;

import co.rsk.metrics.profilers.impl.*;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * ProfilerFactory is used to get the configured Profiler instance.
 * Only one profiler can be defined, once a profiler is set, it cannot be changed.
 * If a profiler isn't configured, the DummyProfiler will be set upon the first request for the instance.
 */
public final class ProfilerFactory {

    private static final ProxyProfiler instance = new ProxyProfiler();

    private ProfilerFactory() { /* hidden */ }

    public static synchronized void configure(@Nonnull Profiler profiler) {
        if (instance.getBase() == null) {
            instance.setBase(Objects.requireNonNull(profiler));
        } else {
            throw new IllegalArgumentException("another profiler is already configured");
        }
    }

    public static synchronized void remove(@Nonnull Profiler profiler) {
        if (instance.getBase() == Objects.requireNonNull(profiler)) {
            instance.setBase(null);
        } else {
            throw new IllegalArgumentException("this profiler was not configured");
        }
    }

    public static Profiler getInstance() {
        return instance;
    }

    public static Profiler makeProfiler(@Nonnull ProfilerName profilerName) {
        switch (profilerName) {
            case IN_MEMORY:
                return new InMemProfiler();
            case LOGGING:
                return new LoggingProfiler();
            default:
                throw new IllegalArgumentException("Illegal profiler: " + profilerName);
        }
    }
}
