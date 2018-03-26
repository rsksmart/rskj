package co.rsk.cli.config;

import co.rsk.cli.CliArgs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collection;

public class MigrationTool {

    public static void main(String[] commandLineArgs) throws IOException {
        CliArgs.Parser<Migrator.MigratorOptions, Migrator.MigratorFlags> parser = new CliArgs.Parser<>(
                new CliArgs.ArgByNameProvider<Migrator.MigratorOptions>() {
                    @Override
                    public Migrator.MigratorOptions byName(String name) {
                        return Migrator.MigratorOptions.getByOptionName(name);
                    }

                    @Override
                    public Collection<Migrator.MigratorOptions> values() {
                        return Arrays.asList(Migrator.MigratorOptions.values());
                    }
                },
                new CliArgs.ArgByNameProvider<Migrator.MigratorFlags>() {
                    @Override
                    public Migrator.MigratorFlags byName(String name) {
                        return Migrator.MigratorFlags.getByFlagName(name);
                    }

                    @Override
                    public Collection<Migrator.MigratorFlags> values() {
                        return Arrays.asList(Migrator.MigratorFlags.values());
                    }
                }
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
