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

import co.rsk.cli.CliArg;
import co.rsk.cli.OptionalizableCliArg;
import com.typesafe.config.*;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.Properties;

public class Migrator {

    private static final String NEW_CONFIG_PREFIX = "[new]";
    private static final String MINIMAL_CONFIG_FORMAT = "{%s = %s}";

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
            if (originalPath.startsWith(NEW_CONFIG_PREFIX)) {
                try {
                    String newConfigPath = originalPath.substring(NEW_CONFIG_PREFIX.length()).trim();
                    Config newConfiguration = ConfigFactory.parseString(String.format(MINIMAL_CONFIG_FORMAT, newConfigPath, migrationConfiguration.getProperty(originalPath)));
                    migratedConfig = migratedConfig.withFallback(newConfiguration);
                } catch (ConfigException e) {
                    throw new IllegalArgumentException(String.format("Unable to parse value for the %s property", originalPath), e);
                }
            } else if (migratedConfig.hasPath(originalPath)) {
                ConfigValue configurationValueToMigrate = migratedConfig.getValue(originalPath);
                migratedConfig = migratedConfig.withValue(migrationConfiguration.getProperty(originalPath), configurationValueToMigrate).withoutPath(originalPath);
            }
        }

        return migratedConfig.root().render(ConfigRenderOptions.defaults().setOriginComments(false).setJson(false));
    }

    public enum MigratorOptions implements OptionalizableCliArg {
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

        @Override
        public String getName() {
            return optionName;
        }
    }

    public enum MigratorFlags implements CliArg {
        REPLACE_IN_PLACE("replace"),
        ;

        private final String flagName;

        MigratorFlags(String flagName) {
            this.flagName = flagName;
        }

        @Override
        public String getName() {
            return flagName;
        }
    }
}
