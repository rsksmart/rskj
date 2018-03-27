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
import org.ethereum.config.DefaultConfig;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Bootstraps the node, creating the initial object graph.
 * It uses Spring at the moment.
 */
public class NodeBootstrapper {
    private final AnnotationConfigApplicationContext ctx;

    public NodeBootstrapper(String[] args) {
        ctx = new AnnotationConfigApplicationContext();
        CliArgs.Parser<NodeCliOptions, NodeCliFlags> parser = new CliArgs.Parser<>(
                NodeCliOptions.class,
                NodeCliFlags.class
        );

        // register it into the Spring context so it can be injected into components
        CliArgs<NodeCliOptions, NodeCliFlags> cliArgs = parser.parse(args);
        ctx.getBeanFactory().registerSingleton("cliArgs", cliArgs);
        ctx.register(DefaultConfig.class);
        ctx.refresh();
    }

    public Start getStart() {
        return ctx.getBean(Start.class);
    }
}
