package org.ethereum.datasource;

import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles LevelDB datasource instantiation. Instances are stored in a map to avoid generating duplicated data sources.
 * They are identified by their name (the file name on the path on creation).
 * <p>
 * If a duplicated datasource is required, instead of instantiating a new {@link LevelDbDataSource}, the previously
 * stored one is retrieved.
 * <p>
 * When closing a LevelDbDataSource instantiated by this factory, they are automatically removed from the register.
 */
public class LevelDbDataSourceFactory {

    /**
     * Stores {@link LevelDbDataSource LevelDbDataSources}.
     */
    private Map<String, LevelDbDataSource> createdDataSources = new ConcurrentHashMap<>();

    /**
     * Instantiates or retrieves a {@link LevelDbDataSource}
     * @param datasourcePath The path the DataSource will be created on. It will be identified by the path's file name.
     * @return The instantiated {@link LevelDbDataSource}.
     */
    public LevelDbDataSource makeDataSource(Path datasourcePath) {

        String name = datasourcePath.getFileName().toString();
        String databaseDir = datasourcePath.getParent().toString();

        createdDataSources.computeIfAbsent(name, (k) -> {
            LevelDbDataSource ds = new LevelDbDataSource(k, databaseDir, () -> createdDataSources.remove(k));
            ds.init();
            return ds;
        });

        return createdDataSources.get(name);
    }

    /**
     * Every key/value pair stored in the DataSources of the originPaths is stored in the destinationPath.
     *
     * @param destinationPath The DataSource to store the key/value pairs. It is closed afterwards. Cannot be null.
     * @param originPaths The DataSource to retrieve key/value pairs from. The {@link LevelDbDataSource} is closed
     * afterwards. Cannot be null but can be empty.
     */
    public void mergeDataSources(Path destinationPath, List<Path> originPaths) {
        Map<ByteArrayWrapper, byte[]> mergedStores = new HashMap<>();
        for (Path originPath : originPaths) {
            KeyValueDataSource singleOriginDataSource = makeDataSource(originPath);
            for (byte[] key : singleOriginDataSource.keys()) {
                mergedStores.put(ByteUtil.wrap(key), singleOriginDataSource.get(key));
            }
            singleOriginDataSource.close();
        }
        KeyValueDataSource destinationDataSource = makeDataSource(destinationPath);
        destinationDataSource.updateBatch(mergedStores, Collections.emptySet());
        destinationDataSource.close();
    }
}
