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
package co.rsk.cli.tools;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

class CliToolsIntegrationTest {
    private String projectPath;
    private String buildLibsPath;
    private String logsFile;
    private String jarName;
    private String databaseDir;

    class CustomProcess {
        private final Process process;
        private final String input;
        private final String errors;

        public CustomProcess(Process process, String input, String errors) {
            this.process = process;
            this.input = input;
            this.errors = errors;
        }

        public Process getProcess() {
            return process;
        }

        public String getInput() {
            return input;
        }

        public String getErrors() {
            return errors;
        }
    }

    @TempDir
    private Path tempDir;

    private String getProcStreamAsString(InputStream in) throws IOException {
        byte bytesAvailable[] = new byte[in.available()];
        in.read(bytesAvailable, 0, bytesAvailable.length);
        return new String(bytesAvailable);
    }

    @BeforeEach
    public void setup() throws IOException {
        projectPath = System.getProperty("user.dir");
        buildLibsPath = String.format("%s/build/libs", projectPath);
        logsFile = String.format("%s/logs/rsk.log", projectPath);
        Stream<Path> pathsStream = Files.list(Paths.get(buildLibsPath));
        jarName = pathsStream.filter(p -> !p.toFile().isDirectory())
                .map(p -> p.getFileName().toString())
                .filter(fn -> fn.endsWith("-all.jar"))
                .findFirst()
                .get();
        databaseDir = tempDir.toString();
        Files.deleteIfExists(Paths.get(logsFile));
    }

    private CustomProcess runCommand(String cmd, int timeuout, TimeUnit timeUnit) throws InterruptedException, IOException {
        Process proc = Runtime.getRuntime().exec(cmd);

        proc.waitFor(timeuout, timeUnit);
        String procInput = getProcStreamAsString(proc.getInputStream());
        String procErrors = getProcStreamAsString(proc.getErrorStream());
        proc.destroy();

        return new CustomProcess(proc, procInput, procErrors);
    }

    @Test
    void whenDbMigrateRuns_shouldMigrateLevelDbToRocksDbAndShouldNotStartNodeWithPrevDbKind() throws Exception {
        String dbDir = tempDir.toString();
        String cmd = String.format("java -cp %s/%s co.rsk.Start --reset --regtest -Xkeyvalue.datasource=leveldb -Xdatabase.dir=%s", buildLibsPath, jarName, databaseDir);
        runCommand(cmd, 1, TimeUnit.MINUTES);

        Files.delete(Paths.get(logsFile));

        cmd = String.format("java -cp %s/%s co.rsk.cli.tools.DbMigrate --targetDb rocksdb -Xdatabase.dir=%s", buildLibsPath, jarName, dbDir);
        CustomProcess dbMigrateProc = runCommand(cmd, 1, TimeUnit.MINUTES);

        Files.delete(Paths.get(logsFile));

        cmd = String.format("java -cp %s/%s co.rsk.Start --regtest -Xkeyvalue.datasource=leveldb -Xdatabase.dir=%s", buildLibsPath, jarName, databaseDir);
        runCommand(cmd, 1, TimeUnit.MINUTES);

        List<String> rskProc2Lines = Files.readAllLines(Paths.get(logsFile));
        Files.delete(Paths.get(logsFile));

        Assertions.assertTrue(dbMigrateProc.getInput().contains("DbMigrate finished"));
        Assertions.assertTrue(rskProc2Lines.stream().anyMatch(l -> l.equals("java.lang.IllegalStateException: DbKind mismatch. You have selected LEVEL_DB when the previous detected DbKind was ROCKS_DB.")));
    }

    @Test
    public void whenDbMigrateRuns_shouldMigrateLevelDbToRocksDbAndShouldStartNodeSuccessfully() throws Exception {
        String dbDir = tempDir.toString();
        String cmd = String.format("java -cp %s/%s co.rsk.Start --reset --regtest -Xkeyvalue.datasource=leveldb -Xdatabase.dir=%s", buildLibsPath, jarName, databaseDir);
        runCommand(cmd, 1, TimeUnit.MINUTES);

        Files.delete(Paths.get(logsFile));

        cmd = String.format("java -cp %s/%s co.rsk.cli.tools.DbMigrate --targetDb rocksdb -Xdatabase.dir=%s", buildLibsPath, jarName, dbDir);
        CustomProcess dbMigrateProc = runCommand(cmd, 1, TimeUnit.MINUTES);

        Files.delete(Paths.get(logsFile));

        cmd = String.format("java -cp %s/%s co.rsk.Start --regtest -Xkeyvalue.datasource=rocksdb -Xdatabase.dir=%s", buildLibsPath, jarName, databaseDir);
        runCommand(cmd, 1, TimeUnit.MINUTES);

        List<String> rskProc2Lines = Files.readAllLines(Paths.get(logsFile));
        Files.delete(Paths.get(logsFile));

        Assertions.assertTrue(dbMigrateProc.getInput().contains("DbMigrate finished"));
        Assertions.assertTrue(rskProc2Lines.stream().anyMatch(l -> l.contains("DEBUG [minerClient] [Refresh work for mining]  There is a new best block")));
    }
}
