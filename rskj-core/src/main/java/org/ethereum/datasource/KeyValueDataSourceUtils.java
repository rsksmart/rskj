package org.ethereum.datasource;

import co.rsk.datasources.FlatDbDataSource;
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
    static private String DB_KIND_PROPERTIES_FILE = "dbKind.properties";
    static private String KEYVALUE_DATASOURCE_PROP_NAME = "keyvalue.datasource";

    @Nonnull
    static public KeyValueDataSource makeDataSource(@Nonnull Path datasourcePath, @Nonnull DbKind kind,boolean readOnly) {
        String name = datasourcePath.getFileName().toString();
        String databaseDir = datasourcePath.getParent().toString();

        KeyValueDataSource ds;
        switch (kind) {
            case LEVEL_DB:
                ds = new LevelDbDataSource(name, databaseDir,readOnly);
                break;
            case ROCKS_DB:
                ds = new RocksDbDataSource(name, databaseDir,readOnly);
                break;
            case FLAT_DB:
                int maxNodeCount = 16_000_000;
                int maxCapacity  = maxNodeCount*100;
                try {
                    ds = new FlatDbDataSource(maxNodeCount,maxCapacity,
                            datasourcePath.toString(), FlatDbDataSource.CreationFlag.All, FlatDbDataSource.latestDBVersion,readOnly);
                } catch (IOException e) {
                    ds = null;
                }
                break;
            default:
                throw new IllegalArgumentException("kind");
        }

        ds.init();

        return ds;
    }

    static public void mergeDataSources(@Nonnull Path destinationPath, @Nonnull List<Path> originPaths, @Nonnull DbKind kind) {
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

    static public DbKind getDbKindValueFromDbKindFile(String databaseDir) {
        try {
            File file = new File(databaseDir, DB_KIND_PROPERTIES_FILE);
            Properties props = new Properties();

            if (file.exists() && file.canRead()) {
                try (FileReader reader = new FileReader(file)) {
                    props.load(reader);
                }

                return DbKind.ofName(props.getProperty(KEYVALUE_DATASOURCE_PROP_NAME));
            }

            return DbKind.LEVEL_DB;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static public void generatedDbKindFile(DbKind dbKind, String databaseDir) {
        try {
            File file = new File(databaseDir, DB_KIND_PROPERTIES_FILE);
            Properties props = new Properties();
            props.setProperty(KEYVALUE_DATASOURCE_PROP_NAME, dbKind.name());
            file.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(file)) {
                props.store(writer, "Generated dbKind. In order to follow selected db.");

                LoggerFactory.getLogger("KeyValueDataSource").info("Generated dbKind.properties file.");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static public void validateDbKind(DbKind currentDbKind, String databaseDir, boolean databaseReset) {
        File dir = new File(databaseDir);
        boolean databaseDirExists = dir.exists() && dir.isDirectory();

        if (!databaseDirExists) {
            KeyValueDataSourceUtils.generatedDbKindFile(currentDbKind, databaseDir);
            return;
        }

        DbKind prevDbKind = KeyValueDataSourceUtils.getDbKindValueFromDbKindFile(databaseDir);

        if (prevDbKind != currentDbKind) {
            if (databaseReset) {
                KeyValueDataSourceUtils.generatedDbKindFile(currentDbKind, databaseDir);
            } else {
                LoggerFactory.getLogger("KeyValueDataSource").warn("Use the flag --reset when running the application if you are using a different datasource.");
                throw new IllegalStateException("DbKind mismatch. You have selected " + currentDbKind.name() + " when the previous detected DbKind was " + prevDbKind.name() + ".");
            }
        }
    }
}
