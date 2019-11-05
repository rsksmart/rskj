package org.ethereum.datasource;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.*;

import static org.hamcrest.Matchers.*;

public class LevelDbDataSourceFactoryTest {

    @Rule
    public TemporaryFolder databaseDir = new TemporaryFolder();

    private LevelDbDataSourceFactory target;

    @Before
    public void setUp() {
        target = new LevelDbDataSourceFactory();
    }

    @Test
    public void closedDatasourceDeregisters() {
        KeyValueDataSource ds1 = target.makeDataSource(databaseDir.getRoot().toPath().resolve("test"));
        ds1.close();
        KeyValueDataSource ds2 = target.makeDataSource(databaseDir.getRoot().toPath().resolve("test"));

        Assert.assertThat(ds1, not(equalTo(ds2)));
    }

    @Test
    public void retrieveCreatedDataSource() {
        KeyValueDataSource ds1 = target.makeDataSource(databaseDir.getRoot().toPath().resolve("test"));
        KeyValueDataSource ds2 = target.makeDataSource(databaseDir.getRoot().toPath().resolve("test"));
        Assert.assertThat(ds1, equalTo(ds2));
    }

    @Test
    public void mergeMultiTrieStoreDBs() {
        Path testDatabasesDirectory = databaseDir.getRoot().toPath();
        List<Path> sourcePaths = new ArrayList<>();
        Set<byte[]> sourceKeys = new HashSet<>();
        int sourcesCount = 3;
        for (int i = 0; i < sourcesCount; i++) {
            Path sourcePath = testDatabasesDirectory.resolve(String.format("src-%d", i));
            sourcePaths.add(sourcePath);
            KeyValueDataSource originDataSource = target.makeDataSource(sourcePath);
            byte[] currentElement = {(byte) i};
            sourceKeys.add(currentElement);
            originDataSource.put(currentElement, currentElement);
            originDataSource.close();
        }

        Path destination = testDatabasesDirectory.resolve("destination");
        target.mergeDataSources(destination, sourcePaths);
        KeyValueDataSource destinationDataSource = target.makeDataSource(destination);
        try {
            Set<byte[]> destinationKeys = destinationDataSource.keys();
            Assert.assertThat(destinationKeys, hasSize(sourcesCount));

            for (byte[] destinationKey : destinationKeys) {
                boolean keyFound = false;
                for (byte[] sourceKey : sourceKeys) {
                    if (Arrays.equals(destinationKey, sourceKey)) {
                        keyFound = true;
                        break;
                    }
                }
                if (keyFound) {
                    Assert.assertThat(destinationDataSource.get(destinationKey), equalTo(destinationKey));
                } else {
                    StringBuilder sourceKeysToString = new StringBuilder("[");
                    for (byte[] sourceKey : sourceKeys) {
                        sourceKeysToString.append(Arrays.toString(sourceKey)).append(", ");
                    }
                    sourceKeysToString.delete(sourceKeysToString.length() - 2, sourceKeysToString.length());
                    sourceKeysToString.append("]");
                    Assert.fail(String.format("%s wasn't found in %s", Arrays.toString(destinationKey), sourceKeysToString.toString()));
                }
            }
        } finally {
            destinationDataSource.close();
        }
    }
}