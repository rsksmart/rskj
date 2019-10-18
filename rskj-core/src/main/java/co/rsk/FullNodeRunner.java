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
import co.rsk.spi.PluginService;
import org.ethereum.net.eth.EthVersion;
import org.ethereum.util.BuildInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class FullNodeRunner implements NodeRunner {
    private static Logger logger = LoggerFactory.getLogger("fullnoderunner");

    private final List<InternalService> internalServices;
    private final List<PluginService> pluginServices;
    private final RskSystemProperties rskSystemProperties;
    private final BuildInfo buildInfo;

    public FullNodeRunner(
            List<InternalService> internalServices,
            List<PluginService> pluginServices,
            RskSystemProperties rskSystemProperties,
            BuildInfo buildInfo) {
        this.internalServices = Collections.unmodifiableList(internalServices);
        this.pluginServices = Collections.unmodifiableList(pluginServices);
        this.rskSystemProperties = rskSystemProperties;
        this.buildInfo = buildInfo;
    }

    @Override
    public void run() {
        logger.info("Starting RSK");

        logger.info(
                "Running {},  core version: {}-{}",
                rskSystemProperties.genesisInfo(),
                rskSystemProperties.projectVersion(),
                rskSystemProperties.projectVersionModifier()
        );
        buildInfo.printInfo(logger);

        for (InternalService internalService : internalServices) {
            internalService.start();
        }

        for (PluginService pluginService : pluginServices) {
            pluginService.start();
        }

        if (logger.isInfoEnabled()) {
            String versions = EthVersion.supported().stream().map(EthVersion::name).collect(Collectors.joining(", "));
            logger.info("Capability eth version: [{}]", versions);
        }

        logger.info("done");
    }

    @Override
    public void stop() {
        logger.info("Shutting down RSK node");

        for (int i = pluginServices.size() - 1; i >= 0; i--) {
            pluginServices.get(i).stop();
        }

        for (int i = internalServices.size() - 1; i >= 0; i--) {
            internalServices.get(i).stop();
        }

        logger.info("RSK node Shut down");
    }
}
