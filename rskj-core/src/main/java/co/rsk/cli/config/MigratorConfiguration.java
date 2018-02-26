package co.rsk.cli.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

class MigratorConfiguration {

    private static final String MIGRATION_FILE_FORMAT = "%s.new";
    private final Path sourceConfiguration;
    private final Properties migrationConfiguration;
    private final Path destinationConfiguration;

    public MigratorConfiguration(String sourceConfiguration, String migrationConfiguration, String destinationConfiguration, boolean replaceSource) {
        this.sourceConfiguration = Paths.get(sourceConfiguration);
        if (!Files.isRegularFile(this.sourceConfiguration)) {
            throw new IllegalArgumentException(String.format("%s is not a valid input file", sourceConfiguration));
        }
        Path migrationConfigurationPath = Paths.get(migrationConfiguration);
        if (!Files.isRegularFile(migrationConfigurationPath)) {
            throw new IllegalArgumentException(String.format("%s is not a valid migration file", migrationConfigurationPath));
        }
        this.migrationConfiguration = new Properties();
        try {
            this.migrationConfiguration.load(Files.newInputStream(migrationConfigurationPath));
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Unable to read migration config at %s", migrationConfigurationPath));
        }

        if (replaceSource) {
            this.destinationConfiguration = this.sourceConfiguration;
        } else if (destinationConfiguration != null) {
            Path destinationConfigurationPath = Paths.get(destinationConfiguration);
            if (!Files.isWritable(destinationConfigurationPath.getParent()) || Files.isDirectory(destinationConfigurationPath)) {
                throw new IllegalArgumentException(String.format("%s is not a valid output file", destinationConfigurationPath));
            }
            this.destinationConfiguration = destinationConfigurationPath;
        } else {
            this.destinationConfiguration = this.sourceConfiguration.getParent().resolve(String.format(MIGRATION_FILE_FORMAT, this.sourceConfiguration.getFileName().toString()));
        }
    }

    public Path getSourceConfiguration() {
        return sourceConfiguration;
    }

    public Properties getMigrationConfiguration() {
        return migrationConfiguration;
    }

    public Path getDestinationConfiguration() {
        return destinationConfiguration;
    }
}
