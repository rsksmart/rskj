package co.rsk.metrics.profilers.impl;

import co.rsk.metrics.profilers.Metric;
import co.rsk.metrics.profilers.Profiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

public class LoggingProfiler implements Profiler {
    
    @Nonnull
    @Override
    public Metric start(@Nonnull MetricType type) {
        return MetricImpl.make(type);
    }
    
    private static class MetricImpl implements Metric {

        private static final Logger logger = LoggerFactory.getLogger(MetricImpl.class);
        private static final Logger loggerMetrics = LoggerFactory.getLogger("metrics");
        
        @Nonnull
        private final MetricType type;
        
        private final long startTime;
        
        private volatile boolean closed;
        
        static MetricImpl make(@Nonnull MetricType type) {
            return new MetricImpl(type, System.nanoTime());
        }

        MetricImpl(@Nonnull MetricType type, long startTime) {
            this.type = type;
            this.startTime = startTime;
        }

        @Override
        public void close() {
            long endTime = System.nanoTime();
            
            if (closed) {
                logger.warn("Metric of type {} was closed more than one time", type);
                return;
            }
            closed = true;

            long duration = endTime - startTime;
            loggerMetrics.info("{}: {}", type, duration);
        }
    }
}
