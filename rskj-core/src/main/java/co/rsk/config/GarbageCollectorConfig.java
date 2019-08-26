/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.config;

import com.typesafe.config.Config;

/**
 * Wraps configuration for the Garbage Collector, which is usually derived from configuration files.
 */
public class GarbageCollectorConfig {
    private final boolean enabled;
    private final int blocksPerEpoch;
    private final int numberOfEpochs;

    public GarbageCollectorConfig(boolean enabled, int blocksPerEpoch, int numberOfEpochs) {
        this.enabled = enabled;
        this.blocksPerEpoch = blocksPerEpoch;
        this.numberOfEpochs = numberOfEpochs;
    }

    public boolean enabled() {
        return enabled;
    }

    public int blocksPerEpoch() {
        return blocksPerEpoch;
    }

    public int numberOfEpochs() {
        return numberOfEpochs;
    }

    /**
     * Reads configuration in the form of { enabled: boolean, blocksPerEpoch: int, epochs: int }
     */
    public static GarbageCollectorConfig fromConfig(Config config) {
        return new GarbageCollectorConfig(
                config.getBoolean("enabled"),
                config.getInt("blocksPerEpoch"),
                config.getInt("epochs")
        );
    }
}