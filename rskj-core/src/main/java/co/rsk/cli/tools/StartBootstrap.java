/*
 * This file is part of RskJ
 * Copyright (C) 2021 RSK Labs Ltd.
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
package co.rsk.cli.tools;

import co.rsk.NodeRunner;
import co.rsk.RskContext;
import co.rsk.config.InternalService;
import co.rsk.config.RskSystemProperties;
import co.rsk.net.discovery.UDPServer;
import co.rsk.util.NodeStopper;
import co.rsk.util.PreflightChecksUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

/**
 * Entry point of RSK bootstrap node.
 *
 * The bootstrap node starts one service which only participates in the peer discovery protocol. All other capabilities
 * (which are usually part of a full node, e.g. blocks propagation, syncing, JSON-RPC interface etc.) are disabled.
 *
 * Note: this is an experimental tool
 */
public class StartBootstrap {

    private static final Logger logger = LoggerFactory.getLogger("bootstrap");

    public static void main(String[] args) {
        setUpThread(Thread.currentThread());

        RskContext ctx = new BootstrapRskContext(args);
        PreflightChecksUtils preflightChecks = new PreflightChecksUtils(ctx);
        Runtime runtime = Runtime.getRuntime();
        NodeStopper nodeStopper = System::exit;

        runBootstrapNode(ctx, preflightChecks, runtime, nodeStopper);
    }

    static void runBootstrapNode(@Nonnull RskContext ctx,
                                 @Nonnull PreflightChecksUtils preflightChecks,
                                 @Nonnull Runtime runtime,
                                 @Nonnull NodeStopper nodeStopper) {
        try {
            // make preflight checks
            preflightChecks.runChecks();

            // subscribe to shutdown hook
            runtime.addShutdownHook(new Thread(ctx::close, "stopper"));

            // start node runner
            NodeRunner runner = ctx.getNodeRunner();
            runner.run();
        } catch (Exception e) {
            logger.error("Main thread of RSK bootstrap node crashed", e);

            ctx.close();

            nodeStopper.stop(1);
        }
    }

    static void setUpThread(@Nonnull Thread thread) {
        thread.setName("main");
    }

    /**
     * Bootstrap {@link RskContext} that spins up only one internal service - {@link UDPServer} - which takes part in
     * the peer discovery process.
     */
    static class BootstrapRskContext extends RskContext {

        BootstrapRskContext(String[] args) {
            super(args);
        }

        @Override
        public synchronized List<InternalService> buildInternalServices() {
            RskSystemProperties rskSystemProperties = getRskSystemProperties();
            UDPServer udpServer = new UDPServer(
                    rskSystemProperties.getBindAddress().getHostAddress(),
                    rskSystemProperties.getPeerPort(),
                    getPeerExplorer()
            );

            return Collections.singletonList(udpServer);
        }
    }
}
