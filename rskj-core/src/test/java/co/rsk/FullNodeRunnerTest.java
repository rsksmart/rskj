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

package co.rsk;

import co.rsk.config.InternalService;
import co.rsk.config.RskSystemProperties;
import co.rsk.spi.PluginService;
import org.ethereum.util.BuildInfo;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class FullNodeRunnerTest {
    private List<InternalService> internalServices;
    private List<PluginService> pluginServices;
    private FullNodeRunner runner;

    @Before
    public void setup() {
        internalServices = Arrays.asList(
                mock(InternalService.class),
                mock(InternalService.class)
        );
        pluginServices = Arrays.asList(
                mock(PluginService.class),
                mock(PluginService.class)
        );
        runner = new FullNodeRunner(internalServices, pluginServices, mock(RskSystemProperties.class), mock(BuildInfo.class));
    }

    @Test
    public void callingRunStartsInternalServices() {
        runner.run();
        for (InternalService internalService : internalServices) {
            verify(internalService).start();
        }
    }

    @Test
    public void callingStopStopsInternalServices() {
        runner.stop();

        for (InternalService internalService : internalServices) {
            verify(internalService).stop();
        }
    }

    @Test
    public void callingRunStartsPluginServices() {
        runner.run();

        for (PluginService pluginService : pluginServices) {
            verify(pluginService).start();
        }
    }

    @Test
    public void callingStopStopsPluginServices() {
        runner.stop();

        for (PluginService pluginService : pluginServices) {
            verify(pluginService).stop();
        }
    }
}