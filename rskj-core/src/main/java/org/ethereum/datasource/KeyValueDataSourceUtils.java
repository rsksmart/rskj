package org.ethereum.datasource;

import org.ethereum.db.ByteArrayWrapper;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class KeyValueDataSourceUtils {
    public static final String DB_KIND_PROPERTIES_FILE = "dbKind.properties";
    public static final String KEYVALUE_DATASOURCE_PROP_NAME = "keyvalue.datasource";
    public static final String KEYVALUE_DATASOURCE = "KeyValueDataSource";

    private KeyValueDataSourceUtils() { /* hidden */ }

    @Nonnull
    public static KeyValueDataSource makeDataSource(@Nonnull Path datasourcePath, @Nonnull DbKind kind) {
        String name = datasourcePath.getFileName().toString();
        String databaseDir = datasourcePath.getParent().toString();

        KeyValueDataSource ds;
        switch (kind) {
            case LEVEL_DB:
                ds = new LevelDbDataSource(name, databaseDir);
                break;
            case ROCKS_DB:
                ds = new RocksDbDataSource(name, databaseDir);
                break;
            default:
                throw new IllegalArgumentException("kind");
        }

        ds.init();

        return ds;
    }

    public static void mergeDataSources(@Nonnull Path destinationPath, @Nonnull List<Path> originPaths, @Nonnull DbKind kind) {
        Map<ByteArrayWrapper, byte[]> mergedStores = new HashMap<>();
        for (Path originPath : originPaths) {
            KeyValueDataSource singleOriginDataSource = makeDataSource(originPath, kind);
            for (ByteArrayWrapper byteArrayWrapper : singleOriginDataSource.keys()) {
                mergedStores.put(byteArrayWrapper, singleOriginDataSource.get(byteArrayWrapper.getData()));
            }
            singleOriginDataSource.close();
        }
        KeyValueDataSource destinationDataSource = makeDataSource(destinationPath, kind);
        destinationDataSource.updateBatch(mergedStores, Collections.emptySet());
        destinationDataSource.close();
    }

    public static DbKind getDbKindValueFromDbKindFile(String databaseDir) {
        try {
            File file = new File(databaseDir, DB_KIND_PROPERTIES_FILE);
            Properties props = new Properties();

            if (file.exists() && file.canRead()) {
                try (FileReader reader = new FileReader(file)) {
                    props.load(reader);
                }

                return DbKind.ofName(props.getProperty(KEYVALUE_DATASOURCE_PROP_NAME));
            }

            return DbKind.ROCKS_DB;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void generatedDbKindFile(DbKind dbKind, String databaseDir) {
        try {
            File file = new File(databaseDir, DB_KIND_PROPERTIES_FILE);
            Properties props = new Properties();
            props.setProperty(KEYVALUE_DATASOURCE_PROP_NAME, dbKind.name());
            file.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(file)) {
                props.store(writer, "Generated dbKind. In order to follow selected db.");

                LoggerFactory.getLogger(KEYVALUE_DATASOURCE).info("Generated dbKind.properties file.");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void validateDbKind(DbKind currentDbKind, String databaseDir, boolean databaseReset) {
        File dbDir = new File(databaseDir);
        boolean dbDirExists = dbDir.exists();

        if (dbDirExists && !dbDir.isDirectory()) {
            LoggerFactory.getLogger(KEYVALUE_DATASOURCE).error("database.dir should be a folder.");
            throw new IllegalStateException("database.dir should be a folder");
        }

        File dbKindFile = new File(dbDir, DB_KIND_PROPERTIES_FILE);
        boolean dbKindFileExists = dbKindFile.exists();
        if (dbKindFileExists && !dbKindFile.isFile()) {
            LoggerFactory.getLogger(KEYVALUE_DATASOURCE).error("dbKind file should be a file.");
            throw new IllegalStateException("dbKind file should be a file");
        }

        boolean isEmptyDbDir = dbDirExists && Objects.requireNonNull(dbDir.list()).length == 0;
        if (!dbDirExists || isEmptyDbDir) { // use dbKind from config, if db folder doesn't exist or is empty
            KeyValueDataSourceUtils.generatedDbKindFile(currentDbKind, databaseDir);
            return;
        }

        if (!dbKindFileExists || !dbKindFile.canRead()) { // use LEVEL_DB (for backward compatibility), if db folder is not empty and dbKind file doesn't exist
            KeyValueDataSourceUtils.generatedDbKindFile(DbKind.LEVEL_DB, databaseDir);
            return;
        }

        DbKind prevDbKind = KeyValueDataSourceUtils.getDbKindValueFromDbKindFile(databaseDir);
        if (prevDbKind != currentDbKind) {
            LoggerFactory.getLogger(KEYVALUE_DATASOURCE).warn("Current Db kind {} does not match with db kind {} from properties file, using value from properties file." +
                            " keyvalue.datasource from .conf file is used the first time the node is run or db is created from scratch.",
                    currentDbKind.name(), prevDbKind.name());
        }
    }
}
