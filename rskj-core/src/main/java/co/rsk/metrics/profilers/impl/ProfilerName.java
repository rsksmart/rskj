package co.rsk.metrics.profilers.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

public enum ProfilerName {

    IN_MEMORY, LOGGING;

    private static final Logger logger = LoggerFactory.getLogger("general");

    @Nullable
    public static ProfilerName of(@Nullable String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }

        try {
            return ProfilerName.valueOf(name);
        } catch (IllegalArgumentException e) {
            logger.warn("Illegal name of profiler: " + name, e);
            return null;
        }
    }
}
