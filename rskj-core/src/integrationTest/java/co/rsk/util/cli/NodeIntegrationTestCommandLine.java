/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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
package co.rsk.util.cli;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class NodeIntegrationTestCommandLine extends RskjCommandLineBase {
    private final String modeArg;
    private final String rskConfFilePath;
    private int timeout = 0;

    public NodeIntegrationTestCommandLine(String rskConfFilePath, String modeArg) {
        super("co.rsk.Start", new String[]{}, new String[]{});
        this.rskConfFilePath = rskConfFilePath;
        this.modeArg = modeArg;
    }

    public NodeIntegrationTestCommandLine(String rskConfFilePath, String modeArg, int timeout) {
        this(rskConfFilePath, modeArg);
        this.timeout = timeout;
    }

    private void setUp() {
        String projectPath = System.getProperty("user.dir");
        String integrationTestResourcesPath = String.format("%s/src/integrationTest/resources", projectPath);
        String logbackXmlFile = String.format("%s/logback.xml", integrationTestResourcesPath);
        arguments = new String[]{
                this.modeArg,
        };
        parameters = new String[]{
                String.format("-Dlogback.configurationFile=%s", logbackXmlFile),
                String.format("-Drsk.conf.file=%s", rskConfFilePath)
        };
    }

    public Process startNode() throws IOException, InterruptedException {
        return startNode(null);
    }

    public Process startNode(Consumer<Process> beforeDestroyFn) throws IOException, InterruptedException {
        setUp();

        try {
            executeCommand(timeout);
        } finally {
            if(timeout != 0) {
                killNode(beforeDestroyFn);
            }
        }

        return cliProcess;  // We return the process so the test can use it to waitFor, to kill, to add in a Future operation
    }

    public int killNode() throws InterruptedException {
        return this.killNode(null);
    }

    public int killNode(Consumer<Process> beforeDestroyFn) throws InterruptedException {
        if (beforeDestroyFn != null) {
            beforeDestroyFn.accept(cliProcess);
        }
        if(cliProcess.isAlive()){
            cliProcess.destroyForcibly();
        }
        cliProcess.waitFor(1, TimeUnit.MINUTES); // We have to wait a bit so the process finishes the kill command
        return cliProcess.exitValue();
    }
}
