package org.ethereum.datasource;

import org.ethereum.TestUtils;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class KeyValueDataSourceTest {

    private static final int CACHE_SIZE = 100;

    private final KeyValueDataSource keyValueDataSource;
    private final boolean withFlush;

    public KeyValueDataSourceTest(KeyValueDataSource keyValueDataSource, String testName, boolean withFlush) {
        this.keyValueDataSource = keyValueDataSource;
        this.withFlush = withFlush;
    }

    @Parameterized.Parameters(name = "{1}, flush = {2}")
    public static Collection<Object[]> data() throws IOException {
        Path tmpDir = Files.createTempDirectory("rskj");
        return Arrays.asList(new Object[][] {
            { new HashMapDB(), HashMapDB.class.getSimpleName(), true},
            { new RocksDbDataSource("test", Files.createTempDirectory(tmpDir, "default").toString()), RocksDbDataSource.class.getSimpleName(), true },
            { new DataSourceWithCache(new HashMapDB(), CACHE_SIZE), String.format("Cache with %s", HashMapDB.class.getSimpleName()), true },
            { new DataSourceWithCache(new RocksDbDataSource("test", Files.createTempDirectory(tmpDir, "default").toString()), CACHE_SIZE), String.format("Cache with %s", RocksDbDataSource.class.getSimpleName()), true },
            { new HashMapDB(), HashMapDB.class.getSimpleName(), false },
            { new RocksDbDataSource("test", Files.createTempDirectory(tmpDir, "default").toString()), RocksDbDataSource.class.getSimpleName(), false },
            { new DataSourceWithCache(new HashMapDB(), CACHE_SIZE), String.format("Cache with %s", HashMapDB.class.getSimpleName()), false },
            { new DataSourceWithCache(new RocksDbDataSource("test", Files.createTempDirectory(tmpDir, "default").toString()), CACHE_SIZE), String.format("Cache with %s", RocksDbDataSource.class.getSimpleName()), false }
        });
    }

    @Before
    public void setup() {
        keyValueDataSource.init();
    }

    @Test
    public void put() {
        byte[] randomKey = TestUtils.randomBytes(20);
        byte[] randomValue = TestUtils.randomBytes(20);

        keyValueDataSource.put(randomKey, randomValue);
        if (withFlush) {
            keyValueDataSource.flush();
        }
        assertThat(keyValueDataSource.get(randomKey), is(randomValue));
    }

    @Test(expected = NullPointerException.class)
    public void getNull() {
        keyValueDataSource.get(null);
    }

    @Test
    public void delete() {
        byte[] randomKey = TestUtils.randomBytes(20);
        byte[] randomValue = TestUtils.randomBytes(20);

        keyValueDataSource.put(randomKey, randomValue);
        keyValueDataSource.delete(randomKey);
        if (withFlush) {
            keyValueDataSource.flush();
        }
        assertThat(keyValueDataSource.get(randomKey), is(nullValue()));
    }

    @Test
    public void updateBatch() {
        Map<ByteArrayWrapper, byte[]> updatedValues = generateRandomValuesToUpdate(CACHE_SIZE);

        keyValueDataSource.updateBatch(updatedValues, Collections.emptySet());

        if (withFlush) {
            keyValueDataSource.flush();
        }
        for (Map.Entry<ByteArrayWrapper, byte[]> updatedValue : updatedValues.entrySet()) {
            assertThat(keyValueDataSource.get(updatedValue.getKey().getData()), is(updatedValue.getValue()));
        }
    }

    @Test
    public void updateBatchWithKeysToRemove() {
        Map<ByteArrayWrapper, byte[]> updatedValues = generateRandomValuesToUpdate(CACHE_SIZE);
        keyValueDataSource.updateBatch(updatedValues, Collections.emptySet());
        keyValueDataSource.updateBatch(Collections.emptyMap(), updatedValues.keySet());

        if (withFlush) {
            keyValueDataSource.flush();
        }

        for (Map.Entry<ByteArrayWrapper, byte[]> updatedValue : updatedValues.entrySet()) {
            assertThat(keyValueDataSource.get(updatedValue.getKey().getData()), is(nullValue()));
        }
    }

    @Test(expected = RuntimeException.class)
    public void putNullValue() {
        byte[] randomKey = TestUtils.randomBytes(20);

        keyValueDataSource.put(randomKey, null);
    }

    @Test(expected = RuntimeException.class)
    public void updateBatchWithNulls() {
        Map<ByteArrayWrapper, byte[]> updatedValues = generateRandomValuesToUpdate(CACHE_SIZE);
        ByteArrayWrapper keyToNull = updatedValues.keySet().iterator().next();
        updatedValues.put(keyToNull, null);

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