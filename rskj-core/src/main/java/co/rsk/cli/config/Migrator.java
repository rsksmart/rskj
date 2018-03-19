package co.rsk.cli.config;

import co.rsk.cli.OptionalizableArgument;
import com.typesafe.config.*;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.Optional;
import java.util.stream.Stream;

public class Migrator {

    private static final String MINIMAL_CONFIG_FORMAT = "{%s: %s}";

    private final MigratorConfiguration configuration;

    public Migrator(MigratorConfiguration configuration) {
        this.configuration = configuration;
    }

    public String migrateConfiguration() throws IOException {
        return  migrateConfiguration(
            Files.newBufferedReader(configuration.getSourceConfiguration(), StandardCharsets.UTF_8),
            configuration.getMigrationConfiguration()
        );
    }

    public static String migrateConfiguration(Reader sourceReader, Properties migrationConfiguration) {
        Config migratedConfig = ConfigFactory.parseReader(sourceReader);
        Enumeration migrationPaths = migrationConfiguration.propertyNames();
        while (migrationPaths.hasMoreElements()) {
            String originalPath = (String) migrationPaths.nextElement();
            if (migratedConfig.hasPath(originalPath)) {
                ConfigValue configurationValueToMigrate = migratedConfig.getValue(originalPath);
                migratedConfig = migratedConfig.withValue(migrationConfiguration.getProperty(originalPath), configurationValueToMigrate).withoutPath(originalPath);
            } else {
                try {
                    Config newConfiguration = ConfigFactory.parseString(String.format(MINIMAL_CONFIG_FORMAT, originalPath, migrationConfiguration.getProperty(originalPath)));
                    migratedConfig = migratedConfig.withFallback(newConfiguration);
                } catch (ConfigException e) {
                    throw new IllegalArgumentException(String.format("Unable to parse value for the %s property", originalPath), e);
                }
            }
        }

        return migratedConfig.root().render(ConfigRenderOptions.defaults().setOriginComments(false).setJson(false));
    }

    public enum MigratorOptions implements OptionalizableArgument {
        INPUT_FILE("i", false),
        OUTPUT_FILE("o", true),
        MIGRATION_FILE("m", false),
        ;

        private final String optionName;
        private final boolean optional;

        MigratorOptions(String name, boolean optional) {
            this.optionName = name;
            this.optional = optional;
        }

        @Override
        public boolean isOptional() {
            return optional;
        }

        public static MigratorOptions getByOptionName(String optionName) {
            Optional<MigratorOptions> cliOption = Stream.of(values()).filter(option -> option.optionName.equals(optionName)).findFirst();
            return cliOption.orElseThrow(() -> new NoSuchElementException(String.format("-%s is not a valid option", optionName)));
        }

    }

    public enum MigratorFlags {
        REPLACE_IN_PLACE("replace"),
        ;

        private final String flagName;

        MigratorFlags(String flagName) {
            this.flagName = flagName;
        }

        public static MigratorFlags getByFlagName(String flagName) {
            Optional<MigratorFlags> cliFlag = Stream.of(values()).filter(flag -> flag.flagName.equals(flagName)).findFirst();
            return cliFlag.orElseThrow(() -> new NoSuchElementException(String.format("--%s is not a valid flag", flagName)));
        }
    }
}
