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

import java.util.ServiceLoader;

public class PluginLoader {
    private final RskContext rskContext;
    private final PluginServiceRegistry registry;

    public PluginLoader(RskContext rskContext, PluginServiceRegistry registry) {
        this.rskContext = rskContext;
        this.registry = registry;
    }

    public void load() {
        ServiceLoader<Plugin> loader = ServiceLoader.load(Plugin.class);
        Plugin.Parameters parameters = new Parameters(rskContext, registry);
        loader.iterator().forEachRemaining(c -> c.load(parameters));
    }

    private static class Parameters implements Plugin.Parameters {
        private final RskContext rskContext;
        private final PluginServiceRegistry registry;

        private Parameters(RskContext rskContext, PluginServiceRegistry registry) {
            this.rskContext = rskContext;
            this.registry = registry;
        }

        @Override
        public RskContext context() {
            return rskContext;
        }

        @Override
        public PluginServiceRegistry registry() {
            return registry;
        }
    }
}
