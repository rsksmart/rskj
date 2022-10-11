package org.ethereum.datasource;

import org.ethereum.TestUtils;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.ethereum.TestUtils.randomBytes;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;

class KeyValueDataSourceTest {

    private static final int CACHE_SIZE = 100;

    @ParameterizedTest(name = "{1}, flush = {2}")
    @ArgumentsSource(DatasourceArgumentsProvider.class)
    void put(KeyValueDataSource keyValueDataSource, String className, boolean withFlush) {
        byte[] randomKey = TestUtils.randomBytes(20);
        byte[] randomValue = TestUtils.randomBytes(20);

        keyValueDataSource.put(randomKey, randomValue);
        if (withFlush) {
            keyValueDataSource.flush();
        }
        MatcherAssert.assertThat(keyValueDataSource.get(randomKey), is(randomValue));

        try (DataSourceKeyIterator iterator = keyValueDataSource.keyIterator()) {
            assertTrue(iterator.hasNext());

            byte[] expectedValue = null;

            while (iterator.hasNext()) {
                expectedValue = iterator.next();
                if (ByteUtil.wrap(expectedValue).equals(ByteUtil.wrap(randomKey))) {
                    break;
                }
            }

            assertArrayEquals(expectedValue, randomKey);
        } catch (Exception e) {
            if (!withFlush && keyValueDataSource instanceof DataSourceWithCache) {
                assertEquals("There are uncommitted keys", e.getMessage());
            } else {
                fail(e.getMessage());
            }
        }
    }

    @ParameterizedTest(name = "{1}, flush = {2}")
    @ArgumentsSource(DatasourceArgumentsProvider.class)
    public void putNullKey(KeyValueDataSource keyValueDataSource, String className, boolean withFlush) {
        byte[] value = randomBytes(32);
        assertThrows(NullPointerException.class, () -> keyValueDataSource.put(null, value));
    }

    @ParameterizedTest(name = "{1}, flush = {2}")
    @ArgumentsSource(DatasourceArgumentsProvider.class)
    public void putNullValue(KeyValueDataSource keyValueDataSource, String className, boolean withFlush) {
        byte[] key = randomBytes(32);
        assertThrows(NullPointerException.class, () -> keyValueDataSource.put(key, null));
    }

    @ParameterizedTest(name = "{1}, flush = {2}")
    @ArgumentsSource(DatasourceArgumentsProvider.class)
    public void getAfterMiss(KeyValueDataSource keyValueDataSource, String className, boolean withFlush) {
        byte[] key = randomBytes(32);
        assertNull(keyValueDataSource.get(key));

        byte[] value = randomBytes(32);
        keyValueDataSource.put(key, value);

        if (withFlush) {
            keyValueDataSource.flush();
        }

        assertArrayEquals(keyValueDataSource.get(key), value);
    }

    @ParameterizedTest(name = "{1}, flush = {2}")
    @ArgumentsSource(DatasourceArgumentsProvider.class)
    public void getAfterUpdate(KeyValueDataSource keyValueDataSource, String className, boolean withFlush) {
        byte[] key = randomBytes(32);
        byte[] value = randomBytes(32);

        keyValueDataSource.put(key, value);
        assertArrayEquals(keyValueDataSource.get(key), value);

        byte[] newValue = randomBytes(32);
        keyValueDataSource.put(key, newValue);

        if (withFlush) {
            keyValueDataSource.flush();
        }

        assertArrayEquals(keyValueDataSource.get(key), newValue);

        try (DataSourceKeyIterator iterator = keyValueDataSource.keyIterator()) {
            assertTrue(iterator.hasNext());

            byte[] expectedValue = null;

            while (iterator.hasNext()) {
                expectedValue = iterator.next();
                if (ByteUtil.wrap(expectedValue).equals(ByteUtil.wrap(key))) {
                    break;
                }
            }

            assertArrayEquals(expectedValue, key);
        } catch (Exception e) {
            if (!withFlush && keyValueDataSource instanceof DataSourceWithCache) {
                assertEquals("There are uncommitted keys", e.getMessage());
            } else {
                fail(e.getMessage());
            }
        }
    }

    @ParameterizedTest(name = "{1}, flush = {2}")
    @ArgumentsSource(DatasourceArgumentsProvider.class)
    public void getAfterDelete(KeyValueDataSource keyValueDataSource, String className, boolean withFlush) {
        byte[] key = randomBytes(32);
        byte[] value = randomBytes(32);

        keyValueDataSource.put(key, value);
        assertArrayEquals(keyValueDataSource.get(key), value);

        keyValueDataSource.delete(key);

        if (withFlush) {
            keyValueDataSource.flush();
        }

        assertNull(keyValueDataSource.get(key));
    }

    @ParameterizedTest(name = "{1}, flush = {2}")
    @ArgumentsSource(DatasourceArgumentsProvider.class)
    void getNull(KeyValueDataSource keyValueDataSource, String className, boolean withFlush) {
        assertThrows(NullPointerException.class, () -> keyValueDataSource.get(null));
    }

    @ParameterizedTest(name = "{1}, flush = {2}")
    @ArgumentsSource(DatasourceArgumentsProvider.class)
    public void delete(KeyValueDataSource keyValueDataSource, String className, boolean withFlush) {
        byte[] key1 = TestUtils.randomBytes(20);
        byte[] value1 = TestUtils.randomBytes(20);
        byte[] key2 = TestUtils.randomBytes(20);
        byte[] value2 = TestUtils.randomBytes(20);
        keyValueDataSource.put(key1, value1);
        keyValueDataSource.put(key2, value2);

        keyValueDataSource.delete(key2);

        if (withFlush) {
            keyValueDataSource.flush();
        }

        assertNull(keyValueDataSource.get(key2));
        assertNotNull(keyValueDataSource.get(key1));
    }

    @ParameterizedTest(name = "{1}, flush = {2}")
    @ArgumentsSource(DatasourceArgumentsProvider.class)
    public void keys(KeyValueDataSource keyValueDataSource, String className, boolean withFlush) {
        assertTrue(keyValueDataSource.keys().isEmpty());

        byte[] key1 = TestUtils.randomBytes(20);
        byte[] value1 = TestUtils.randomBytes(20);
        byte[] key2 = TestUtils.randomBytes(20);
        byte[] value2 = TestUtils.randomBytes(20);

        keyValueDataSource.put(key1, value1);
        keyValueDataSource.put(key2, value2);

        Set<ByteArrayWrapper> expectedKeys = new HashSet<>();
        expectedKeys.add(ByteUtil.wrap(key1));
        expectedKeys.add(ByteUtil.wrap(key2));
        assertEquals(expectedKeys, keyValueDataSource.keys());
    }

    @ParameterizedTest(name = "{1}, flush = {2}")
    @ArgumentsSource(DatasourceArgumentsProvider.class)
    void updateBatch(KeyValueDataSource keyValueDataSource, String className, boolean withFlush) {
        Map<ByteArrayWrapper, byte[]> updatedValues = generateRandomValuesToUpdate(CACHE_SIZE);

        keyValueDataSource.updateBatch(updatedValues, Collections.emptySet());

        if (withFlush) {
            keyValueDataSource.flush();
        }

        for (Map.Entry<ByteArrayWrapper, byte[]> updatedValue : updatedValues.entrySet()) {
            assertArrayEquals(updatedValue.getValue(), keyValueDataSource.get(updatedValue.getKey().getData()));
        }
    }

    @ParameterizedTest(name = "{1}, flush = {2}")
    @ArgumentsSource(DatasourceArgumentsProvider.class)
    void updateBatchWithKeysToRemove(KeyValueDataSource keyValueDataSource, String className, boolean withFlush) {
        Map<ByteArrayWrapper, byte[]> updatedValues = generateRandomValuesToUpdate(CACHE_SIZE);
        keyValueDataSource.updateBatch(updatedValues, Collections.emptySet());
        keyValueDataSource.updateBatch(Collections.emptyMap(), updatedValues.keySet());

        if (withFlush) {
            keyValueDataSource.flush();
        }

        for (Map.Entry<ByteArrayWrapper, byte[]> updatedValue : updatedValues.entrySet()) {
            assertNull(keyValueDataSource.get(updatedValue.getKey().getData()));
        }
    }

    @ParameterizedTest(name = "{1}, flush = {2}")
    @ArgumentsSource(DatasourceArgumentsProvider.class)
    public void updateBatchNullKey(KeyValueDataSource keyValueDataSource, String className, boolean withFlush) {
        Map<ByteArrayWrapper, byte[]> batch = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            batch.put(ByteUtil.wrap(randomBytes(32)), randomBytes(32));
        }
        batch.put(null, randomBytes(32));

        Set<ByteArrayWrapper> deleteKeys = Collections.emptySet();
        IllegalArgumentException iae = assertThrows(IllegalArgumentException.class, () -> keyValueDataSource.updateBatch(batch, deleteKeys));
        assertEquals("Cannot update null values", iae.getMessage());
    }

    @ParameterizedTest(name = "{1}, flush = {2}")
    @ArgumentsSource(DatasourceArgumentsProvider.class)
    public void updateBatchNullValue(KeyValueDataSource keyValueDataSource, String className, boolean withFlush) {
        Map<ByteArrayWrapper, byte[]> batch = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            batch.put(ByteUtil.wrap(randomBytes(32)), randomBytes(32));
        }
        batch.put(ByteUtil.wrap(randomBytes(32)), null);

        Set<ByteArrayWrapper> deleteKeys = Collections.emptySet();
        IllegalArgumentException iae = assertThrows(IllegalArgumentException.class, () -> keyValueDataSource.updateBatch(batch, deleteKeys));
        assertEquals("Cannot update null values", iae.getMessage());
    }

    private Map<ByteArrayWrapper, byte[]> generateRandomValuesToUpdate(int maxValuesToCreate) {
        Map<ByteArrayWrapper, byte[]> updatedValues;
        updatedValues = new HashMap<>();

        for (int i = 0; i < maxValuesToCreate; i++) {
            updatedValues.put(ByteUtil.wrap(TestUtils.randomBytes(20)), TestUtils.randomBytes(20));
        }
        return updatedValues;
    }

    private static class DatasourceArgumentsProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws IOException {
            Path tmpDir = Files.createTempDirectory("rskj");
            return Stream.of(
                    Arguments.of(initHashmapDB(), HashMapDB.class.getSimpleName(), true),
                    Arguments.of(initLevelDBDatasource(tmpDir), LevelDbDataSource.class.getSimpleName(), true),
                    Arguments.of(initRocksDBDatasource(tmpDir), RocksDbDataSource.class.getSimpleName(), true),
                    Arguments.of(initHashmapDBWithCache(), String.format("Cache with %s", HashMapDB.class.getSimpleName()), true),
                    Arguments.of(initDatasourceWithCache(tmpDir), String.format("Cache with %s", RocksDbDataSource.class.getSimpleName()), true),
                    Arguments.of(initHashmapDB(), HashMapDB.class.getSimpleName(), false),
                    Arguments.of(initLevelDBDatasource(tmpDir), LevelDbDataSource.class.getSimpleName(), true),
                    Arguments.of(initRocksDBDatasource(tmpDir), RocksDbDataSource.class.getSimpleName(), false),
                    Arguments.of(initHashmapDBWithCache(), String.format("Cache with %s", HashMapDB.class.getSimpleName()), false),
                    Arguments.of(initDatasourceWithCache(tmpDir), String.format("Cache with %s", RocksDbDataSource.class.getSimpleName()), false)
            );
        }

        private static HashMapDB initHashmapDB() {
            return new HashMapDB();
        }

        private static LevelDbDataSource initLevelDBDatasource(Path tmpDir) throws IOException {
            LevelDbDataSource levelDbDataSource = new LevelDbDataSource("test", Files.createTempDirectory(tmpDir, "default").toString());
            levelDbDataSource.init();
            return levelDbDataSource;
        }

        private static RocksDbDataSource initRocksDBDatasource(Path tmpDir) throws IOException {
            RocksDbDataSource rocksDbDataSource = new RocksDbDataSource("test", Files.createTempDirectory(tmpDir, "default").toString());
            rocksDbDataSource.init();
            return rocksDbDataSource;
        }

        private static DataSourceWithCache initDatasourceWithCache(Path tmpDir) throws IOException {
            DataSourceWithCache dataSourceWithCache = new DataSourceWithCache(initRocksDBDatasource(tmpDir), CACHE_SIZE);
            dataSourceWithCache.init();
            return dataSourceWithCache;
        }

        private static DataSourceWithCache initHashmapDBWithCache() {
            DataSourceWithCache dataSourceWithCache = new DataSourceWithCache(initHashmapDB(), CACHE_SIZE);
            dataSourceWithCache.init();
            return dataSourceWithCache;
        }
    }
}
