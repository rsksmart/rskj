/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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
package co.rsk.cli;

import co.rsk.RskContext;
import co.rsk.config.RskSystemProperties;
import co.rsk.util.NodeStopper;
import org.ethereum.datasource.DbKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PicoCliToolRskContextAwareTest {

    @TempDir
    private Path tempDir;

    @CommandLine.Command(name = "probe-tool")
    static class ProbeTool extends PicoCliToolRskContextAware {
        @CommandLine.Option(names = {"--threads"})
        private int threads = 1;

        boolean ran;

        @Override
        public Integer call() {
            ran = true;
            return 0;
        }
    }

    @Test
    void mistypedToolOption_failsWithoutRunningTheTool() {
        ProbeTool tool = new ProbeTool();
        NodeStopper stopper = mock(NodeStopper.class);

        tool.execute(new String[]{"--main", "--thraeds", "4"}, this::context, stopper);

        verify(stopper).stop(1);
        assertFalse(tool.ran);
    }

    @Test
    void nodeFlagsAndOverridesPassThroughNextToToolOptions() {
        ProbeTool tool = new ProbeTool();
        NodeStopper stopper = mock(NodeStopper.class);

        tool.execute(new String[]{"--main", "-Xdatabase.dir=/tmp/db", "-base-path", "/tmp/base", "--threads", "4"},
                this::context, stopper);

        verify(stopper).stop(0);
        assertTrue(tool.ran);
        assertEquals(4, tool.threads);
    }

    private RskContext context() {
        RskContext rskContext = mock(RskContext.class);
        RskSystemProperties properties = mock(RskSystemProperties.class);
        doReturn(properties).when(rskContext).getRskSystemProperties();
        doReturn(tempDir.toString()).when(properties).databaseDir();
        doReturn(DbKind.LEVEL_DB).when(properties).databaseKind();
        return rskContext;
    }
}
