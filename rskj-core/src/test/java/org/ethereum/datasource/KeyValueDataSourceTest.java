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

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;

class KeyValueDataSourceTest {

    private static final int CACHE_SIZE = 100;

    @ParameterizedTest(name = "{1}, flush = {2}")
    @ArgumentsSource(DatasourceArgumentsProvider.class)
    void put(KeyValueDataSource keyValueDataSource, String className, boolean withFlush) {
        byte[] key = TestUtils.generateBytes(this.getClass(), "key", 20);
        byte[] value = TestUtils.generateBytes(this.getClass(), "value", 20);

        keyValueDataSource.put(key, value);
        if (withFlush) {
            keyValueDataSource.flush();
        }
        MatcherAssert.assertThat(keyValueDataSource.get(key), is(value));

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
    void putNullKey(KeyValueDataSource keyValueDataSource, String className, boolean withFlush) {
        byte[] value = TestUtils.generateBytes(this.getClass(), "value", 32);
        assertThrows(NullPointerException.class, () -> keyValueDataSource.put(null, value));
    }

    @ParameterizedTest(name = "{1}, flush = {2}")
    @ArgumentsSource(DatasourceArgumentsProvider.class)
    void putNullValue(KeyValueDataSource keyValueDataSource, String className, boolean withFlush) {
        byte[] key = TestUtils.generateBytes(this.getClass(), "key", 32);
        assertThrows(NullPointerException.class, () -> keyValueDataSource.put(key, null));
    }

    @ParameterizedTest(name = "{1}, flush = {2}")
    @ArgumentsSource(DatasourceArgumentsProvider.class)
    void getAfterMiss(KeyValueDataSource keyValueDataSource, String className, boolean withFlush) {
        byte[] key = TestUtils.generateBytes(this.getClass(), "key", 32);
        assertNull(keyValueDataSource.get(key));

        byte[] value = TestUtils.generateBytes(this.getClass(), "value", 32);
        keyValueDataSource.put(key, value);

        if (withFlush) {
            keyValueDataSource.flush();
        }

        assertArrayEquals(keyValueDataSource.get(key), value);
    }


    @ParameterizedTest(name = "{1}, flush = {2}")
    @ArgumentsSource(DatasourceArgumentsProvider.class)
    void getAfterUpdate(KeyValueDataSource keyValueDataSource, String className, boolean withFlush) {
        byte[] key = TestUtils.generateBytes(this.getClass(), "key", 32);
        byte[] value = TestUtils.generateBytes(this.getClass(), "value", 32);

        keyValueDataSource.put(key, value);
        assertArrayEquals(keyValueDataSource.get(key), value);

        byte[] newValue = TestUtils.generateBytes(this.getClass(), "newValue", 32);
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
    void getAfterDelete(KeyValueDataSource keyValueDataSource, String className, boolean withFlush) {
        byte[] key = TestUtils.generateBytes(this.getClass(), "key", 32);
        byte[] value = TestUtils.generateBytes(this.getClass(), "value", 32);

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
    void delete(KeyValueDataSource keyValueDataSource, String className, boolean withFlush) {
        byte[] key1 = TestUtils.generateBytes(this.getClass(), "key1", 20);
        byte[] value1 = TestUtils.generateBytes(this.getClass(), "value1", 20);
        byte[] key2 = TestUtils.generateBytes(this.getClass(), "key2", 20);
        byte[] value2 = TestUtils.generateBytes(this.getClass(), "value2", 20);
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
    void keys(KeyValueDataSource keyValueDataSource, String className, boolean withFlush) {
        assertTrue(keyValueDataSource.keys().isEmpty());

        byte[] key1 = TestUtils.generateBytes(this.getClass(), "key1", 20);
        byte[] value1 = TestUtils.generateBytes(this.getClass(), "value1", 20);
        byte[] key2 = TestUtils.generateBytes(this.getClass(), "key2", 20);
        byte[] value2 = TestUtils.generateBytes(this.getClass(), "value2", 20);

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
    void updateBatchNullKey(KeyValueDataSource keyValueDataSource, String className, boolean withFlush) {
        Map<ByteArrayWrapper, byte[]> batch = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            byte[] key = TestUtils.generateBytes(this.getClass(), "key" + i, 32);
            byte[] value = TestUtils.generateBytes(this.getClass(), "value" + i, 32);
            batch.put(ByteUtil.wrap(key), value);
        }
        byte[] lastValue = TestUtils.generateBytes(this.getClass(), "lastValue", 32);
        batch.put(null, lastValue);

        Set<ByteArrayWrapper> deleteKeys = Collections.emptySet();
        IllegalArgumentException iae = assertThrows(IllegalArgumentException.class, () -> keyValueDataSource.updateBatch(batch, deleteKeys));
        assertEquals("Cannot update null values", iae.getMessage());
    }

    @ParameterizedTest(name = "{1}, flush = {2}")
    @ArgumentsSource(DatasourceArgumentsProvider.class)
    void updateBatchNullValue(KeyValueDataSource keyValueDataSource, String className, boolean withFlush) {
        Map<ByteArrayWrapper, byte[]> batch = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            byte[] key = TestUtils.generateBytes(this.getClass(), "key" + i, 32);
            byte[] value = TestUtils.generateBytes(this.getClass(), "value" + i, 32);
            batch.put(ByteUtil.wrap(key), value);
        }
        byte[] lastKey = TestUtils.generateBytes(this.getClass(), "lastKey", 32);
        batch.put(ByteUtil.wrap(lastKey), null);

        Set<ByteArrayWrapper> deleteKeys = Collections.emptySet();
        IllegalArgumentException iae = assertThrows(IllegalArgumentException.class, () -> keyValueDataSource.updateBatch(batch, deleteKeys));
        assertEquals("Cannot update null values", iae.getMessage());
    }

    private Map<ByteArrayWrapper, byte[]> generateRandomValuesToUpdate(int maxValuesToCreate) {
        Map<ByteArrayWrapper, byte[]> updatedValues;
        updatedValues = new HashMap<>();
        Random random = new Random(KeyValueDataSourceTest.class.hashCode());
        for (int i = 0; i < maxValuesToCreate; i++) {
            updatedValues.put(ByteUtil.wrap(TestUtils.generateBytesFromRandom(random,20)), TestUtils.generateBytesFromRandom(random,20));
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
