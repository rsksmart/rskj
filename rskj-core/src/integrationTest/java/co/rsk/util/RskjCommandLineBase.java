/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
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

package co.rsk.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

public abstract class RskjCommandLineBase {

    private String buildJarPath;
    private String rskjClass;
    private StringBuilder processOutputBuilder;
    private Consumer<String> procOutputConsumer = output -> appendLinesToProcessOutput(output);
    protected String[] arguments;
    protected String[] parameters;
    protected Process cliProcess;

    public RskjCommandLineBase(String rskjClass, String[] parameters, String[] arguments){
        this.rskjClass = rskjClass;
        this.parameters = parameters;
        this.arguments = arguments;
    }

    private void appendToCommandIfArrayNotEmpty(StringBuilder command, String[] array){
        if(array.length > 0){
            command.append(String.join(" ", array));
        }
    }

    private void appendLinesToProcessOutput(String output){
        processOutputBuilder.append(output).append(System.lineSeparator());
    }

    private void configureProcessStreamReaderToGetCommandLineOutput() {
        StreamGobbler streamGobbler = new StreamGobbler(cliProcess.getInputStream(), procOutputConsumer);
        Executors.newSingleThreadExecutor().submit(streamGobbler);
    }

    private void setUp() throws IOException {
        processOutputBuilder = new StringBuilder();
        String projectPath = System.getProperty("user.dir");
        String buildLibsPath = String.format("%s/build/libs", projectPath);
        Stream<Path> pathsStream = Files.list(Paths.get(buildLibsPath));
        String jarName = pathsStream.filter(p -> !p.toFile().isDirectory())
                .map(p -> p.getFileName().toString())
                .filter(fn -> fn.endsWith("-all.jar"))
                .findFirst()
                .get();
        buildJarPath = String.format("%s/%s", buildLibsPath, jarName);
    }

    public Process executeCommand() throws IOException, InterruptedException {
        return executeCommand(0);
    }

    public Process executeCommand(int timeout) throws IOException, InterruptedException {
        setUp();
        StringBuilder fullCommand = new StringBuilder("java ");

        appendToCommandIfArrayNotEmpty(fullCommand, parameters);

        fullCommand.append(" -cp ")
                .append(buildJarPath).append(" ")
                .append(rskjClass).append(" ");

        appendToCommandIfArrayNotEmpty(fullCommand, arguments);

        cliProcess = Runtime.getRuntime().exec(fullCommand.toString());

        configureProcessStreamReaderToGetCommandLineOutput();

        try {
            if(timeout != 0) {
                cliProcess.waitFor(timeout, TimeUnit.MINUTES);
            }
        } finally {
            if (!cliProcess.isAlive() && cliProcess.exitValue() != 0) {
                String procError = CommandLineFixture.readProcStream(cliProcess.getErrorStream());
                System.out.println("procError:" + procError);
                System.out.println("Proc exited with value: " + cliProcess.exitValue());
            } else if(timeout != 0) {
                cliProcess.destroyForcibly();
            }
        }

        return cliProcess;  // We return the process so the test can use it to waitFor, to kill, to add in a Future operation
    }

    public String getOutput() {
        return processOutputBuilder.toString();
    }
}
