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
import co.rsk.cli.PicoCliToolRskContextAware;
import org.ethereum.datasource.DataSourceKeyIterator;
import org.ethereum.datasource.DbKind;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
@CommandLine.Command(name = "db-migrate", mixinStandardHelpOptions = true, version = "db-migrate 1.0",
        description = "Migrates between different databases such as leveldb and rocksdb.")
public class DbMigrate extends PicoCliToolRskContextAware {
    private static final int BATCH_SIZE = 10_000;
    private static final Logger logger = LoggerFactory.getLogger(DbMigrate.class);
    private static final String NODE_ID_FILE = "nodeId.properties";

    @CommandLine.Option(names = {"-t", "--targetDb"}, description = "The target db to migrate to. Example: leveldb, rocksdb ...", required = true)
    private String targetdb;

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

    public DbMigrate() {
    }

    @Override
    public Integer call() throws IOException {
        logger.info("Starting db migration...");

        if (this.targetdb == null) {
            throw new IllegalArgumentException("Db to migrate not specified. Please specify in the first argument.");
        }

        DbKind sourceDbKind = ctx.getRskSystemProperties().databaseKind();
        DbKind defaultDbKind = ctx.getRskSystemProperties().databaseKind();
        DbKind targetDbKind = DbKind.ofName(this.targetdb);

        if (sourceDbKind == targetDbKind) {
            throw new IllegalArgumentException(String.format(
                    "Cannot migrate to the same database, current db is %s and target db is %s",
                    sourceDbKind,
                    targetDbKind
            ));
        }

        String sourceDbDir = Paths.get(ctx.getRskSystemProperties().databaseDir()).toFile().getAbsolutePath();
        String targetDbDir =  sourceDbDir + "_tmp";

        logger.info("Preparing to migrate from {} to {}", sourceDbKind.name(), targetDbKind.name());

        FileUtil.recursiveDelete(targetDbDir);

        try (Stream<Path> databasePaths = Files.list(Paths.get(sourceDbDir))) {
            List<Path> paths = databasePaths.collect(Collectors.toList());

            for (Path path : paths) {
                if (!path.toFile().isDirectory()) {
                    continue;
                }

                String fullSourceDbPath = path.toAbsolutePath().toString();
                String dbName = path.getFileName().toString();
                String fullTargetDbPath = String.format("%s/%s", targetDbDir, dbName);

                DbKind dbSourceDbKind = KeyValueDataSource.getDbKindValueFromDbKindFile(fullSourceDbPath, defaultDbKind);

                if (dbSourceDbKind == targetDbKind) {
                    File targetDbFolder = new File(targetDbDir);
                    targetDbFolder.mkdirs();
                    Files.move(Paths.get(fullSourceDbPath), Paths.get(fullTargetDbPath));
                    continue;
                }

                DbMigrationInformation dbMigrationInformation = buildDbMigrationInformation(sourceDbDir, targetDbDir, dbSourceDbKind, targetDbKind, dbName, ctx);

                this.migrate(dbMigrationInformation);

                KeyValueDataSource.generatedDbKindFile(targetDbKind, fullTargetDbPath);

                FileUtil.recursiveDelete(fullSourceDbPath);
            }
        }

        String nodeIdFilePath = "/" + NODE_ID_FILE;

        if (new File(sourceDbDir, NODE_ID_FILE).exists()) {
            Files.move(Paths.get(sourceDbDir + nodeIdFilePath), Paths.get(targetDbDir + nodeIdFilePath));
        }

        FileUtil.recursiveDelete(sourceDbDir);

        Files.move(Paths.get(targetDbDir), Paths.get(sourceDbDir));

        return 0;
    }

    private DbMigrationInformation buildDbMigrationInformation(
            String sourceDbDir,
            String targetDbDir,
            DbKind sourceDbKind,
            DbKind targetDbKind,
            String dbName,
            RskContext ctx
    ) {
        logger.info("Preparing data sources for db: {}", dbName);

        KeyValueDataSource sourceDataSource = KeyValueDataSource.makeDataSource(Paths.get(sourceDbDir, dbName), sourceDbKind, ctx.getRskSystemProperties());
        KeyValueDataSource targetDataSource = KeyValueDataSource.makeDataSource(Paths.get(targetDbDir, dbName), targetDbKind, ctx.getRskSystemProperties());

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

        try (DataSourceKeyIterator iterator = sourceKeyValueDataSource.keyIterator()) {
            final Map<ByteArrayWrapper, byte[]> bulkDataMap = new HashMap<>();

            while (iterator.hasNext()) {
                byte[] data = iterator.next();

                bulkDataMap.put(new ByteArrayWrapper(data), sourceKeyValueDataSource.get(data));

                if (bulkDataMap.size() > BATCH_SIZE - 1) {
                    targetKeyValueDataSource.updateBatch(bulkDataMap, new HashSet<>());
                    bulkDataMap.clear();
                }
            }

            if (bulkDataMap.size() > 0) {
                targetKeyValueDataSource.updateBatch(bulkDataMap, new HashSet<>());
                bulkDataMap.clear();
            }
        } catch (Exception e) {
            logger.error("An error happened closing DB Key Iterator", e);
            throw new RuntimeException(e);
        }

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
