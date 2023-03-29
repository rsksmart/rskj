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
import co.rsk.cli.exceptions.PicocliBadResultException;
import co.rsk.config.InternalService;
import co.rsk.config.RskSystemProperties;
import co.rsk.net.discovery.UDPServer;
import co.rsk.util.NodeStopper;
import co.rsk.util.PreflightChecksUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Entry point of RSK bootstrap node.
 *
 * The bootstrap node starts one service which only participates in the peer discovery protocol. All other capabilities
 * (which are usually part of a full node, e.g. blocks propagation, syncing, JSON-RPC interface etc.) are disabled.
 *
 * Note: this is an experimental tool
 */
@CommandLine.Command(name = "start-bootstrap", mixinStandardHelpOptions = true, version = "start-bootstrap 1.0",
        description = "Starts a bootstrap node with one service, which only participates in the peer discovery protocol")
public class StartBootstrap implements Callable<Integer> {

    private static final Logger logger = LoggerFactory.getLogger("bootstrap");

    private static final Object syncObj = new Object();

    private final RskContext ctx;

    public StartBootstrap(RskContext ctx) {
        this.ctx = ctx;
    }

    public static void main(String[] args) {
        setUpThread(Thread.currentThread());

        try (RskContext ctx = new BootstrapRskContext(args)) {
            int result = new CommandLine(new StartBootstrap(ctx)).setUnmatchedArgumentsAllowed(true).execute(args);

            if (result != 0) {
                throw new PicocliBadResultException(result);
            }
        }
    }

    @Override
    public Integer call() throws IOException {
        PreflightChecksUtils preflightChecks = new PreflightChecksUtils(ctx);
        Runtime runtime = Runtime.getRuntime();
        NodeStopper nodeStopper = System::exit;

        runBootstrapNode(ctx, preflightChecks, runtime, syncObj, nodeStopper);

        return 0;
    }

    static void runBootstrapNode(@Nonnull RskContext ctx,
                                 @Nonnull PreflightChecksUtils preflightChecks,
                                 @Nonnull Runtime runtime,
                                 @Nonnull Object syncObjInstance,
                                 @Nonnull NodeStopper nodeStopper) {
        try {
            // make preflight checks
            preflightChecks.runChecks();

            // subscribe to shutdown hook
            runtime.addShutdownHook(new Thread(() -> {
                synchronized (syncObjInstance) {
                    ctx.close();
                    syncObjInstance.notify();
                }
            }, "stopper"));

            // start node runner
            NodeRunner runner = ctx.getNodeRunner();
            runner.run();

            synchronized (syncObjInstance) {
                try {
                    if (!ctx.isClosed()) {
                        syncObjInstance.wait();
                    }
                } catch (InterruptedException e) { /* ignore */ }
            }
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
            super(args, true);
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
