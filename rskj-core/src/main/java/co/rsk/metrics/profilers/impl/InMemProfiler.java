package co.rsk.metrics.profilers.impl;

import co.rsk.metrics.profilers.Profiler;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.io.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class InMemProfiler extends BaseProfiler {

    private final int limit;
    private final TemporalUnit timeUnit;
    private final boolean loggerEnabled;
    private final LinkedList<Map<MetricType, Long>> metrics = new LinkedList<>();

    private Instant lastTime;

    public InMemProfiler() {
        this(ChronoUnit.MINUTES, 60, Clock.NANOS, true);
    }

    public InMemProfiler(@Nonnull TemporalUnit timeUnit, int limit, @Nonnull Clock clock, boolean loggerEnabled) {
        super(clock);

        if (limit < 1) {
            throw new IllegalArgumentException("limit");
        }

        this.limit = limit;
        this.timeUnit = Objects.requireNonNull(timeUnit);
        this.loggerEnabled = loggerEnabled;
    }

    @Override
    protected synchronized void onStop(@Nonnull MetricType type, long duration) {
        Instant now = nowTruncatedToUnit();
        if (lastTime == null) {
            lastTime = now;

            addMetricMap();
        } else {
            while (lastTime.isBefore(now)) {
                lastTime = lastTime.plus(1, timeUnit);

                addMetricMap();
            }
        }

        metrics.getLast().merge(type, duration, Long::sum);
    }

    @Override
    public void close() throws IOException {
        logLastMetricsIfNeeded();

        printMetricsToCsv();
    }

    private void printMetricsToCsv() throws IOException {
        InMemProfiler.Snapshot metricsSnapshot = getMetricsSnapshot();
        String fileName = "metrics-till-" + metricsSnapshot.getToTime().getEpochSecond()
                + "-step-" + metricsSnapshot.getTimeUnit().toString().toLowerCase()
                + ".csv";
        File fileFolder = new File("logs");
        //noinspection ResultOfMethodCallIgnored
        fileFolder.mkdirs();
        File filePath = new File(fileFolder, fileName);

        try (PrintStream writer = new PrintStream(new BufferedOutputStream(new FileOutputStream(filePath)))) {
            Arrays.stream(Profiler.MetricType.values()).forEach(new Consumer<MetricType>() {
                private boolean firstTime = true;

                @Override
                public void accept(Profiler.MetricType type) {
                    if (firstTime) {
                        firstTime = false;
                    } else {
                        writer.print(',');
                    }

                    writer.print(type.name());
                }
            });

            writer.println();

            metricsSnapshot.getMetrics().forEach(map -> {
                Arrays.stream(Profiler.MetricType.values()).forEach(new Consumer<Profiler.MetricType>() {
                    private boolean firstTime = true;

                    @Override
                    public void accept(Profiler.MetricType type) {
                        if (firstTime) {
                            firstTime = false;
                        } else {
                            writer.print(',');
                        }

                        Long value = map.get(type);
                        if (value == null) {
                            value = 0L;
                        }

                        writer.print(value);
                    }
                });

                writer.println();
            });
        }
    }

    public synchronized void clearMetrics() {
        lastTime = null;
        metrics.clear();
    }

    public synchronized Snapshot getMetricsSnapshot() {
        List<Map<MetricType, Long>> metricListSnapshot = metrics.stream().map(HashMap::new).collect(Collectors.toList());
        Instant toTime = Optional.ofNullable(lastTime).orElseGet(this::nowTruncatedToUnit);

        return new Snapshot(timeUnit, toTime, metricListSnapshot);
    }

    private Instant nowTruncatedToUnit() {
        return Instant.now().truncatedTo(timeUnit);
    }

    private void addMetricMap() {
        logLastMetricsIfNeeded();

        if (metrics.size() >= limit) {
            metrics.removeFirst();
        }

        metrics.add(new HashMap<>());
    }

    private void logLastMetricsIfNeeded() {
        if (!loggerEnabled || metrics.isEmpty()) {
            return;
        }

        Logger logger = getLogger();
        Map<MetricType, Long> metricMap = metrics.getLast();

        Arrays.stream(Profiler.MetricType.values()).forEach(type -> {
            Long value = metricMap.get(type);
            if (value != null) {
                logger.info(type + " " + value);
            }
        });
    }

    public static final class Snapshot {
        private final TemporalUnit timeUnit;
        private final Instant toTime;
        private final List<Map<MetricType, Long>> metrics;

        public Snapshot(TemporalUnit timeUnit, Instant toTime, List<Map<MetricType, Long>> metrics) {
            this.timeUnit = timeUnit;
            this.toTime = toTime;
            this.metrics = metrics;
        }

        public TemporalUnit getTimeUnit() {
            return timeUnit;
        }

        public Instant getToTime() {
            return toTime;
        }

        public List<Map<MetricType, Long>> getMetrics() {
            return metrics;
        }
    }
}
