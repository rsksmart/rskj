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
package co.rsk;

import co.rsk.util.PreflightCheckException;
import co.rsk.util.PreflightChecksUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;

public class StartTest {

    @Test
    public void threadIsNotSetUp_WhenSetThreadUp_ThenThreadNameShouldBeValid() {
        //noinspection InstantiatingAThreadWithDefaultRunMethod
        Thread thread = new Thread();

        Start.setUpThread(thread);

        assertEquals("main", thread.getName());
    }

    @Test
    public void nodeIsNotRunning_WhenRunNodeAndPreflightCheckFails_ThenNodeRunnerShouldNotRun() throws Exception {
        Runtime runtime = mock(Runtime.class);
        NodeRunner runner = mock(NodeRunner.class);
        RskContext ctx = mock(RskContext.class);
        doReturn(runner).when(ctx).getNodeRunner();
        PreflightChecksUtils preflightChecks = mock(PreflightChecksUtils.class);
        doThrow(new PreflightCheckException("test")).when(preflightChecks).runChecks();

        try {
            Start.runNode(runtime, preflightChecks, ctx);
            fail("runNode should throw an exception");
        } catch (PreflightCheckException e) {
            assertEquals("test", e.getMessage());

            verify(preflightChecks, times(1)).runChecks();
            verify(runner, never()).run();
            verify(runtime, never()).addShutdownHook(any());
        }
    }

    @Test
    public void nodeIsNotRunning_WhenRunNodeAndPreflightCheckSucceeds_ThenNodeRunnerShouldRunWithShutdownHookAdded() throws Exception {
        Runtime runtime = mock(Runtime.class);
        ArgumentCaptor<Thread> threadCaptor = ArgumentCaptor.forClass(Thread.class);
        NodeRunner runner = mock(NodeRunner.class);
        RskContext ctx = mock(RskContext.class);
        doReturn(runner).when(ctx).getNodeRunner();
        PreflightChecksUtils preflightChecks = mock(PreflightChecksUtils.class);

        Start.runNode(runtime, preflightChecks, ctx);

        verify(preflightChecks, times(1)).runChecks();
        verify(runner, times(1)).run();
        verify(runtime, times(1)).addShutdownHook(threadCaptor.capture());

        Thread stopperThread = threadCaptor.getValue();
        assertEquals("stopper", stopperThread.getName());

        verify(ctx, never()).close();
        //noinspection CallToThreadRun
        stopperThread.run();
        verify(ctx, times(1)).close();
    }
}
