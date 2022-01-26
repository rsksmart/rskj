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
import co.rsk.util.ExecState;
import org.ethereum.util.BuildInfo;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class NodeRunnerImplTest {
    private NodeContext nodeContext;
    private List<InternalService> internalServices;
    private NodeRunnerImpl runner;

    @Before
    public void setup() {
        nodeContext = mock(NodeContext.class);
        internalServices = Arrays.asList(
                mock(InternalService.class),
                mock(InternalService.class)
        );

        runner = new NodeRunnerImpl(nodeContext, internalServices, mock(RskSystemProperties.class), mock(BuildInfo.class));
    }

    @Test
    public void contextIsNotClosed_WhenRunNode_ThenShouldStartInternalServices() throws Exception {
        doReturn(false).when(nodeContext).isClosed();

        runner.run();
        for (InternalService internalService : internalServices) {
            verify(internalService).start();
        }
    }

    @Test
    public void nodeIsNotRunning_WhenRunNode_IsRunningShouldReturnTrue() throws Exception {
        doReturn(false).when(nodeContext).isClosed();
        assertEquals(ExecState.CREATED, runner.getState());

        runner.run();

        assertEquals(ExecState.RUNNING, runner.getState());
    }

    @Test
    public void nodeIsRunning_WhenStopNode_IsRunningShouldReturnFalse() throws Exception {
        doReturn(false).when(nodeContext).isClosed();

        runner.run();
        runner.stop();

        assertEquals(ExecState.FINISHED, runner.getState());
    }

    @Test
    public void nodeIsNotRunning_WhenRunNodeFails_ThenAlreadyStartedInternalServicesShouldStop() throws Exception {
        doReturn(false).when(nodeContext).isClosed();

        doThrow(new RuntimeException("Service #2 failed to start")).when(internalServices.get(1)).start();

        try {
            runner.run();
            fail("Run should throw an exception");
        } catch (RuntimeException e) {
            assertEquals("Service #2 failed to start", e.getMessage());

            verify(internalServices.get(0), atLeastOnce()).stop();
        }
    }

    @Test
    public void nodeIsAlreadyRunning_WhenRunNode_ThenShouldThrowError() throws Exception {
        doReturn(false).when(nodeContext).isClosed();

        runner.run();

        try {
            runner.run();
            fail("Run should throw an exception");
        } catch (IllegalStateException e) {
            assertEquals("The node is already running", e.getMessage());
        }
    }

    @Test
    public void contextIsClosed_WhenRunNode_ThenShouldThrowError() throws Exception {
        doReturn(true).when(nodeContext).isClosed();

        try {
            runner.run();
            fail("Run should throw an exception");
        } catch (IllegalStateException e) {
            assertEquals("Node Context is closed. Consider creating a brand new RskContext", e.getMessage());
        }
    }

    @Test
    public void contextIsNotClosed_WhenStopRunningNode_ThenShouldStopInternalServices() throws Exception {
        doReturn(false).when(nodeContext).isClosed();

        runner.run();
        runner.stop();

        for (InternalService internalService : internalServices) {
            verify(internalService).stop();
        }
    }

    @Test
    public void nodeIsNotRunning_WhenStopNode_ThenShouldNotThrowError() {
        try {
            runner.stop();
        } catch (RuntimeException e) {
            fail();
        }
    }

    @Test
    public void nodeIsAlreadyStopped_WhenStopNode_ThenShouldNotThrowError() throws Exception {
        doReturn(false).when(nodeContext).isClosed();

        runner.run();
        runner.stop();

        try {
            runner.stop();
        } catch (RuntimeException e) {
            fail();
        }
    }
}
