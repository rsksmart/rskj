/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

import co.rsk.config.InternalService;
import co.rsk.config.RskSystemProperties;
import co.rsk.util.ExecState;
import co.rsk.util.SystemUtils;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.net.eth.EthVersion;
import org.ethereum.util.BuildInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class NodeRunnerImpl implements NodeRunner {

    private static final Logger logger = LoggerFactory.getLogger("fullnoderunner");

    private final NodeContext nodeContext;
    private final List<InternalService> internalServices;
    private final RskSystemProperties rskSystemProperties;
    private final BuildInfo buildInfo;

    private volatile ExecState state = ExecState.CREATED;

    public NodeRunnerImpl(
            NodeContext nodeContext,
            List<InternalService> internalServices,
            RskSystemProperties rskSystemProperties,
            BuildInfo buildInfo) {
        this.nodeContext = nodeContext;
        this.internalServices = Collections.unmodifiableList(internalServices);
        this.rskSystemProperties = rskSystemProperties;
        this.buildInfo = buildInfo;
    }

    @VisibleForTesting
    ExecState getState() {
        return state;
    }

    /**
     * This method starts internal services.
     *
     * If some internal service throws an exception while starting, then {@link InternalService#stop()} method will be
     * called on each already started service.
     *
     * Note that this method is not idempotent, which means that calling this method on a running node will throw an exception.
     *
     * @throws IllegalStateException - if this node is already running.
     * @throws IllegalStateException - if this node's context is closed.
     */
    @Override
    public synchronized void run() throws Exception {
        if (state == ExecState.RUNNING) {
            throw new IllegalStateException("The node is already running");
        }

        if (nodeContext.isClosed()) {
            throw new IllegalStateException("Node Context is closed. Consider creating a brand new RskContext");
        }

        if (state == ExecState.FINISHED) {
            throw new IllegalStateException("The node is stopped and cannot run again. Consider creating a brand new RskContext");
        }

        logger.info("Starting RSK");

        logger.info(
                "Running {},  core version: {}-{}",
                rskSystemProperties.genesisInfo(),
                rskSystemProperties.projectVersion(),
                rskSystemProperties.projectVersionModifier()
        );
        buildInfo.printInfo(logger);

        if (rskSystemProperties.shouldPrintSystemInfo()) {
            SystemUtils.printSystemInfo(logger);
        }

        ArrayList<InternalService> startedServices = new ArrayList<>(internalServices.size());
        InternalService curService = null;
        try {
            for (InternalService internalService : internalServices) {
                curService = internalService;
                internalService.start();
                startedServices.add(internalService);
            }
        } catch (RuntimeException e) {
            logger.error("{} failed to start. Stopping already started services...", Optional.ofNullable(curService).map(Object::getClass).map(Class::getSimpleName), e);

            startedServices.forEach(InternalService::stop);

            throw e;
        }

        if (logger.isInfoEnabled()) {
            String versions = EthVersion.supported().stream().map(EthVersion::name).collect(Collectors.joining(", "));
            logger.info("Capability eth version: [{}]", versions);
        }

        logger.info("done");

        state = ExecState.RUNNING;
    }

    /**
     * This method stops internal services in reverse order that they were started.
     *
     * It has no effect, if a node has not yet been started.
     *
     * Note that this method is idempotent, which means that calling this method more than once does not have any
     * visible side effect.
     */
    @Override
    public synchronized void stop() {
        if (state != ExecState.RUNNING) {
            logger.warn("The node is not running. Ignoring");
            return;
        }

        state = ExecState.FINISHED;

        logger.info("Shutting down RSK node");

        for (int i = internalServices.size() - 1; i >= 0; i--) {
            internalServices.get(i).stop();
        }

        logger.info("RSK node Shut down");
    }
}
