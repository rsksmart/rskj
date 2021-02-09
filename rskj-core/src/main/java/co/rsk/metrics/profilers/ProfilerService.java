package co.rsk.metrics.profilers;

import co.rsk.config.InternalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Objects;

public class ProfilerService implements InternalService, AutoCloseable  {

    private static final Logger logger = LoggerFactory.getLogger("general");

    private final String profilerName;
    private final Profiler profiler;

    public ProfilerService(@Nonnull Profiler profiler, @Nonnull String name) {
        this.profilerName = name;
        this.profiler = Objects.requireNonNull(profiler);

        ProfilerFactory.configure(profiler);
    }

    public Profiler getProfiler() {
        return profiler;
    }

    @Override
    public void start() {
        logger.info("Profiler service with [" + profilerName + "] profiler has been started");
    }

    @Override
    public void stop() {
        try {
            close();
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public synchronized void close() throws Exception {
        ProfilerFactory.remove(profiler);

        if (profiler instanceof AutoCloseable) {
            ((AutoCloseable) profiler).close();
        }
    }
}
