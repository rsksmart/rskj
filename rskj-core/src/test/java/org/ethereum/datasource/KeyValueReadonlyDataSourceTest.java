package org.ethereum.datasource;

import org.ethereum.TestUtils;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@RunWith(Parameterized.class)
public class KeyValueReadonlyDataSourceTest {

    private static final int CACHE_SIZE = 10;

    private final DbKind dbKind;
    KeyValueDataSource keyValueDataSource =null;


    public KeyValueReadonlyDataSourceTest(DbKind aDbKind, String testName) {
        this.dbKind = aDbKind;
    }

    @Parameterized.Parameters(name = "{1}")
    public static Collection<Object[]> data() {

        return Arrays.asList(new Object[][]{
                {DbKind.LEVEL_DB, LevelDbDataSource.class.getSimpleName()},
                {DbKind.ROCKS_DB, RocksDbDataSource.class.getSimpleName()},
             });
    }

    @Before
    public void setup() throws IOException {
        Path tmpDir = Files.createTempDirectory("rskj");
        Path dbPath = Files.createTempDirectory(tmpDir, "default").resolve("test");
        // first create and close
        keyValueDataSource = KeyValueDataSourceUtils.makeDataSource(dbPath, dbKind);

        keyValueDataSource.init();
        keyValueDataSource.close();

        // now re-create with readonly mode
        keyValueDataSource = KeyValueDataSourceUtils.makeReadonlyDataSource(dbPath, dbKind);
        keyValueDataSource.init();
    }

    @Test(expected = ReadonlyDbDataSource.ReadOnlyException.class)
    public void put() {
        byte[] randomKey = TestUtils.randomBytes(20);
        byte[] randomValue = TestUtils.randomBytes(20);

        keyValueDataSource.put(randomKey, randomValue);
    }

    @Test(expected = ReadonlyDbDataSource.ReadOnlyException.class)
    public void delete() {
        byte[] randomKey = TestUtils.randomBytes(20);
        byte[] randomValue = TestUtils.randomBytes(20);

        keyValueDataSource.delete(randomKey);
    }

    @Test(expected = ReadonlyDbDataSource.ReadOnlyException.class)
    public void updateBatch() {
        Map<ByteArrayWrapper, byte[]> updatedValues = generateRandomValuesToUpdate(CACHE_SIZE);

        keyValueDataSource.updateBatch(updatedValues, Collections.emptySet());
    }

    private Map<ByteArrayWrapper, byte[]> generateRandomValuesToUpdate(int maxValuesToCreate) {
        Map<ByteArrayWrapper, byte[]> updatedValues;
        updatedValues = new HashMap<>();

        for (int i = 0; i < maxValuesToCreate; i++) {
            updatedValues.put(ByteUtil.wrap(TestUtils.randomBytes(20)), TestUtils.randomBytes(20));
        }
        return updatedValues;
    }
}
