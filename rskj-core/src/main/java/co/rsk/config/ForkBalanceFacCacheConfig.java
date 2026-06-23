/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package co.rsk.config;

/**
 * Configuration for fork-balance FAC in-memory caches ({@code facBlocksCache} retention).
 */
public final class ForkBalanceFacCacheConfig {

    public static final long DEFAULT_DELAY_PARAMETER_SECONDS = 60L;

    private final long delayParameterSeconds;

    public ForkBalanceFacCacheConfig(long delayParameterSeconds) {
        if (delayParameterSeconds < 0) {
            throw new IllegalArgumentException("delayParameterSeconds must be >= 0");
        }
        this.delayParameterSeconds = delayParameterSeconds;
    }

    public long getDelayParameterSeconds() {
        return delayParameterSeconds;
    }
}
