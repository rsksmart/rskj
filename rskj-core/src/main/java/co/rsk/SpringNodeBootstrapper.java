/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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
package co.rsk;

import co.rsk.cli.CliArgs;
import co.rsk.config.NodeCliFlags;
import co.rsk.config.NodeCliOptions;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Creates the initial object graph using Spring and exposes the context for child classes (e.g. FedNodeBootstrapper).
 * Returns a NodeRunner instance by default, but should be overrode when multiple NodeRunners are present.
 */
public class SpringNodeBootstrapper implements NodeBootstrapper {
    private final AnnotationConfigApplicationContext ctx;

    public SpringNodeBootstrapper(Class springConfigClass, String[] args) {
        this.ctx = new AnnotationConfigApplicationContext();
        CliArgs.Parser<NodeCliOptions, NodeCliFlags> parser = new CliArgs.Parser<>(
                NodeCliOptions.class,
                NodeCliFlags.class
        );

        // register it into the Spring context so it can be injected into components
        CliArgs<NodeCliOptions, NodeCliFlags> cliArgs = parser.parse(args);
        this.ctx.getBeanFactory().registerSingleton("cliArgs", cliArgs);
        this.ctx.register(springConfigClass);
        this.ctx.refresh();
    }

    @Override
    public NodeRunner getNodeRunner() {
        return getSpringContext().getBean(NodeRunner.class);
    }

    protected AnnotationConfigApplicationContext getSpringContext() {
        return ctx;
    }
}
