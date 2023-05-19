/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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
package co.rsk.cli.config;

import co.rsk.cli.CliArgs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

// TODO: make it extend from PicoCliRskContextAware?
@Command(name = "migrate", mixinStandardHelpOptions = true, version = "migrate 1.0",
        description = "Migrates configuration.")
public class MigrationTool implements Callable<Integer> {
    @Option(names = {"-i", "--input"}, description = "Input file", required = true)
    private String inputFile;

    @Option(names = {"-m", "--migration"}, description = "Migration file", required = true)
    private String migrationFile;

    @Option(names = {"-o", "--output"}, description = "Output file", required = true)
    private String outputFile;

    @Option(names = {"-r", "--replace"}, description = "Replace")
    private boolean replaceInPlace = false;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new MigrationTool()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws IOException {
        MigratorConfiguration configuration = new MigratorConfiguration(
                inputFile,
                migrationFile,
                outputFile,
                replaceInPlace
        );

        Migrator migrator = new Migrator(configuration);
        String migratedConfigOutput = migrator.migrateConfiguration();
        Path destination = configuration.getDestinationConfiguration();
        Files.write(destination, migratedConfigOutput.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        System.out.println("Configuration successfully migrated.");
        System.out.printf("Source: %s\nDestination: %s\n", configuration.getSourceConfiguration(), destination);

        return 0;
    }
}

