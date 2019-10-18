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
package co.rsk.spi;

import co.rsk.RskContext;

/**
 * Defines the interface that plugins must implement to be discovered through SPI.
 *
 * Program flow:
 * 1. the node context is created
 * 2. plugins, which might depend on the context, are loaded
 * 3. the node is ran
 */
public interface Plugin {
    void load(Parameters parameters);

    /**
     * Exposes the node services ({@link RskContext} for now) and different hooks.
     */
    interface Parameters {
        /**
         * We chose to deprecate this method from start because it exposes a lot of internal details which should be
         * hidden. We expect to remove this as soon as we develop and are able to provide smaller interfaces.
         */
        @Deprecated
        RskContext context();

        PluginServiceRegistry registry();
    }
}
