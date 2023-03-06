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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

class CliToolsIntegrationTest {

    @TempDir
    private Path tempDir;

    @Test
    void testExample() throws Exception {
        String projectPath = System.getProperty("user.dir");
        String buildLibsPath = String.format("%s/build/libs", projectPath);
        String dbDir = tempDir.toString();
        Stream<Path> pathsStream = Files.list(Paths.get(buildLibsPath));
        String jarName = pathsStream.filter(p -> !p.toFile().isDirectory())
                .map(p -> p.getFileName().toString())
                .filter(fn -> fn.endsWith("-all.jar"))
                .findFirst()
                .get();
        Process proc = Runtime.getRuntime().exec(
                String.format("java -cp %s/%s co.rsk.cli.tools.DbMigrate --targetDb leveldb -Xdatabase.dir=%s", buildLibsPath, jarName, dbDir)
        );
        proc.waitFor();
        // Then retreive the process output
        InputStream in = proc.getInputStream();
        InputStream err = proc.getErrorStream();

        byte b[]=new byte[in.available()];
        in.read(b,0,b.length);
        System.out.println(new String(b));

        byte c[]=new byte[err.available()];
        err.read(c,0,c.length);
        System.out.println(new String(c));
    }
}
