package org.ethereum.datasource;

import org.ethereum.config.SystemProperties;
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
    private static String KEYVALUE_DATASOURCE = "KeyValueDataSource";

    private KeyValueDataSourceUtils() {

    }

    @Nonnull
    public static KeyValueDataSource makeDataSource(@Nonnull Path datasourcePath, @Nonnull DbKind kind, boolean readOnly) {
        String name = datasourcePath.getFileName().toString();
        String databaseDir = datasourcePath.getParent().toString();

        KeyValueDataSource ds;
        switch (kind) {
            case LEVEL_DB:
                ds = new LevelDbDataSource(name, databaseDir, readOnly);
                break;
            case ROCKS_DB:
                ds = new RocksDbDataSource(name, databaseDir, readOnly);
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
            KeyValueDataSource singleOriginDataSource = makeDataSource(originPath, kind,true);
            for (ByteArrayWrapper byteArrayWrapper : singleOriginDataSource.keys()) {
                mergedStores.put(byteArrayWrapper, singleOriginDataSource.get(byteArrayWrapper.getData()));
            }
            singleOriginDataSource.close();
        }
        KeyValueDataSource destinationDataSource = makeDataSource(destinationPath, kind,false);
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

                return DbKind.ofName(props.getProperty(SystemProperties.PROPERTY_KEYVALUE_DATASOURCE));
            }

            return DbKind.LEVEL_DB;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void generatedDbKindFile(DbKind dbKind, String databaseDir) {
        try {
            File file = new File(databaseDir, DB_KIND_PROPERTIES_FILE);
            Properties props = new Properties();
            props.setProperty(SystemProperties.PROPERTY_KEYVALUE_DATASOURCE, dbKind.name());
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
        File dir = new File(databaseDir);

        if (dir.exists() && !dir.isDirectory()) {
            LoggerFactory.getLogger(KEYVALUE_DATASOURCE).error("database.dir should be a folder.");
            throw new IllegalStateException("database.dir should be a folder");
        }

        boolean databaseDirExists = dir.exists() && dir.isDirectory();

        if (!databaseDirExists || dir.list().length == 0) {
            generatedDbKindFile(currentDbKind, databaseDir);
            return;
        }

        DbKind prevDbKind = getDbKindValueFromDbKindFile(databaseDir);

        if (prevDbKind != currentDbKind) {
            if (databaseReset) {
                generatedDbKindFile(currentDbKind, databaseDir);
            } else {
                LoggerFactory.getLogger(KEYVALUE_DATASOURCE).warn("Use the flag --reset when running the application if you are using a different datasource. Also you can use the cli tool DbMigrate, in order to migrate data between databases.");
                throw new IllegalStateException("DbKind mismatch. You have selected " + currentDbKind.name() + " when the previous detected DbKind was " + prevDbKind.name() + ".");
            }
        }
    }
}
