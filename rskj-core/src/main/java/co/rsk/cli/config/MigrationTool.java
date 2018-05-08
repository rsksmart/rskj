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

public class MigrationTool {

    public static void main(String[] commandLineArgs) throws IOException {
        CliArgs.Parser<Migrator.MigratorOptions, Migrator.MigratorFlags> parser = new CliArgs.Parser<>(
                Migrator.MigratorOptions.class,
                Migrator.MigratorFlags.class
        );

        CliArgs<Migrator.MigratorOptions, Migrator.MigratorFlags> cliArgs = parser.parse(commandLineArgs);

        MigratorConfiguration configuration = new MigratorConfiguration(
                cliArgs.getOptions().get(Migrator.MigratorOptions.INPUT_FILE),
                cliArgs.getOptions().get(Migrator.MigratorOptions.MIGRATION_FILE),
                cliArgs.getOptions().get(Migrator.MigratorOptions.OUTPUT_FILE),
                cliArgs.getFlags().contains(Migrator.MigratorFlags.REPLACE_IN_PLACE)
        );

        Migrator migrator = new Migrator(configuration);
        String migratedConfigOutput = migrator.migrateConfiguration();
        Path destination = configuration.getDestinationConfiguration();
        Files.write(destination, migratedConfigOutput.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        System.out.println("Configuration successfully migrated.");
        System.out.printf("Source: %s\nDestination: %s\n", configuration.getSourceConfiguration(), destination);
    }
}
