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

import co.rsk.RskContext;
import co.rsk.cli.CliToolRskContextAware;
import org.ethereum.datasource.DbKind;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.KeyValueDataSourceUtils;
import org.ethereum.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * The entry point for db migration CLI tool
 * This is an experimental/unsupported tool
 *
 * Required cli args:
 * - args[0] - database target where we are going to insert the information from the current selected database.
 *
 * We do support the migrations between the following databases:
 * - LevelDb (leveldb as argument)
 * - RocksDb (rocksdb as argument)
 */
public class DbMigrate extends CliToolRskContextAware {
    private static final Logger logger = LoggerFactory.getLogger(DbMigrate.class);
    private static final String NODE_ID_FILE = "nodeId.properties";

    private static class DbInformation {
        private final KeyValueDataSource keyValueDataSource;
        private final String indexPath;

        public DbInformation(KeyValueDataSource keyValueDataSource, String indexPath) {
            this.keyValueDataSource = keyValueDataSource;
            this.indexPath = indexPath;
        }

        public KeyValueDataSource getKeyValueDataSource() {
            return keyValueDataSource;
        }

        public String getIndexPath() {
            return indexPath;
        }
    }

    private static class DbMigrationInformation {
        private final DbInformation targetDbInformation;
        private final DbInformation sourceDbInformation;

        public DbMigrationInformation(DbInformation targetDbInformation, DbInformation sourceDbInformation) {
            this.targetDbInformation = targetDbInformation;
            this.sourceDbInformation = sourceDbInformation;
        }

        public DbInformation getTargetDbInformation() {
            return targetDbInformation;
        }

        public DbInformation getSourceDbInformation() {
            return sourceDbInformation;
        }
    }


    public static void main(String[] args) {
        create(MethodHandles.lookup().lookupClass()).execute(args);
    }

    @SuppressWarnings("unused")
    public DbMigrate() {
    }

    @Override
    protected void onExecute(@Nonnull String[] args, @Nonnull RskContext ctx) throws IOException {
        logger.info("Starting db migration...");

        if (args.length == 0) {
            throw new IllegalArgumentException("Db to migrate not specified. Please specify in the first argument.");
        }

        DbKind sourceDbKind = ctx.getRskSystemProperties().databaseKind();
        DbKind targetDbKind = DbKind.ofName(args[0]);

        if (sourceDbKind == targetDbKind) {
            throw new IllegalArgumentException(String.format(
                    "Cannot migrate to the same database, current db is %s and target db is %s",
                    sourceDbKind,
                    targetDbKind
            ));
        }

        String sourceDbDir = ctx.getRskSystemProperties().databaseDir();
        String targetDbDir = sourceDbDir + "_tmp";

        logger.info("Preparing to migrate from {} to {}", sourceDbKind.name(), targetDbKind.name());

        FileUtil.recursiveDelete(targetDbDir);

        try (Stream<Path> databasePaths = Files.list(Paths.get(sourceDbDir))) {
            databasePaths.filter(path -> path.toFile().isDirectory())
                    .map(path -> path.getFileName().toString())
                    .map(dbName -> buildDbMigrationInformation(sourceDbDir, targetDbDir, sourceDbKind, targetDbKind, dbName))
                    .forEach(this::migrate);
        }

        KeyValueDataSourceUtils.validateDbKind(targetDbKind, targetDbDir, true);

        String nodeIdFilePath = "/" + NODE_ID_FILE;

        if (new File(sourceDbDir, NODE_ID_FILE).exists()) {
            Files.move(Paths.get(sourceDbDir + nodeIdFilePath), Paths.get(targetDbDir + nodeIdFilePath));
        }

        FileUtil.recursiveDelete(sourceDbDir);

        Files.move(Paths.get(targetDbDir), Paths.get(sourceDbDir));
    }

    private DbMigrationInformation buildDbMigrationInformation(
            String sourceDbDir,
            String targetDbDir,
            DbKind sourceDbKind,
            DbKind targetDbKind,
            String dbName
    ) {
        logger.info("Preparing data sources for db: {}", dbName);

        KeyValueDataSource sourceDataSource = KeyValueDataSourceUtils.makeDataSource(Paths.get(sourceDbDir, dbName), sourceDbKind, false);
        KeyValueDataSource targetDataSource = KeyValueDataSourceUtils.makeDataSource(Paths.get(targetDbDir, dbName), targetDbKind, false);

        logger.info("Data sources prepared successfully");
        logger.info("Preparing indexes for db: {}", dbName);

        String sourceIndexPath = getIndexDbPath(sourceDbDir, dbName);
        String targetIndexPath = getIndexDbPath(targetDbDir, dbName);

        logger.info("Indexes prepared successfully");
        logger.info("Preparing target and source db information...");

        DbInformation sourceDbInformation = new DbInformation(
                sourceDataSource,
                sourceIndexPath
        );
        DbInformation targetDbInformation = new DbInformation(
                targetDataSource,
                targetIndexPath
        );

        return new DbMigrationInformation(targetDbInformation, sourceDbInformation);
    }

    private String getIndexDbPath(String databaseDir, String dbName) {
        return databaseDir + "/" + dbName + "/index";
    }

    private void migrate(DbMigrationInformation dbMigrationInformation) {
        KeyValueDataSource sourceKeyValueDataSource = dbMigrationInformation.getSourceDbInformation().getKeyValueDataSource();
        KeyValueDataSource targetKeyValueDataSource = dbMigrationInformation.getTargetDbInformation().getKeyValueDataSource();

        logger.info("Migrating data source: {}", sourceKeyValueDataSource.getName());

        sourceKeyValueDataSource.keys().forEach(sourceKey ->
                targetKeyValueDataSource.put(
                        sourceKey.getData(),
                        sourceKeyValueDataSource.get(sourceKey.getData())
                )
        );

        sourceKeyValueDataSource.close();
        targetKeyValueDataSource.close();

        logger.info("{} data source migrated successfully", sourceKeyValueDataSource.getName());
        logger.info("Migrating index for data source: {}", sourceKeyValueDataSource.getName());

        String sourceIndexPath = dbMigrationInformation.getSourceDbInformation().getIndexPath();
        String targetIndexPath = dbMigrationInformation.getTargetDbInformation().getIndexPath();
        File sourceIndexFile = new File(sourceIndexPath);

        if (sourceIndexFile.exists() && sourceIndexFile.isFile()) {
            try {
                Files.move(Paths.get(sourceIndexPath), Paths.get(targetIndexPath));

                logger.info("Indexes for {} were migrated successfully", sourceKeyValueDataSource.getName());
            } catch (IOException e) {
                logger.error("An error happened while migrating indexes", e);
                throw new RuntimeException(e);
            }
        } else {
            logger.info("No indexes found for {}", sourceKeyValueDataSource.getName());
        }
    }
}
