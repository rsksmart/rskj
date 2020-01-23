import co.rsk.metrics.profilers.Metric;
import co.rsk.metrics.profilers.Profiler;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class PerformanceProfiler implements Profiler {

    private Collection<Consumer<Observation>> observers;
    private ThreadPoolExecutor executorService;


    public PerformanceProfiler() {
        observers = new ArrayList<>();
        executorService = new ThreadPoolExecutor(1, 1,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>());
    }

    @Override
    public Metric start(PROFILING_TYPE type) {
        return new ProfilerTimeMetric(type);
    }

    @Override
    public void stop(Metric metric) {
        ProfilerTimeMetric pmetric = (ProfilerTimeMetric) metric;
        executorService.submit(() ->
                observers.forEach(c ->
                        c.accept(new Observation(pmetric.getType().name(), pmetric.stop())))
        );
    }

    public void shutdown() {
        long taskCount = executorService.getTaskCount();
        long completedTasks = executorService.getCompletedTaskCount();
        long remainingTasks = taskCount - completedTasks;
        System.out.println(String.format("total %d completed %d remaining %d", taskCount, completedTasks, remainingTasks));
        executorService.shutdown();
    }

    public void registerConsumer(Consumer<Observation> observationConsumer) {
        observers.add(observationConsumer);

    }

    private class ProfilerTimeMetric implements Metric {

        private final PROFILING_TYPE type;
        Instant startInstant;

        ProfilerTimeMetric(PROFILING_TYPE type) {
            this.type = type;
            this.startInstant = Instant.now();
        }

        public Duration stop() {
            return Duration.between(startInstant, Instant.now());
        }

        public Profiler.PROFILING_TYPE getType() {
            return type;
        }
    }
}
