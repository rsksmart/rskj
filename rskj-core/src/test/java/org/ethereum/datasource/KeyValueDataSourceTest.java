package org.ethereum.datasource;

import org.ethereum.TestUtils;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
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
import static org.hamcrest.Matchers.nullValue;

class KeyValueDataSourceTest {

    private static final int CACHE_SIZE = 100;

    // TODO:I check if this output is fine

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
            Assertions.assertTrue(iterator.hasNext());

            byte[] expectedValue = null;

            while (iterator.hasNext()) {
                expectedValue = iterator.next();
                if (ByteUtil.wrap(expectedValue).equals(ByteUtil.wrap(randomKey))) {
                    break;
                }
            }

            Assertions.assertArrayEquals(expectedValue, randomKey);
        } catch (Exception e) {
            if (!withFlush && keyValueDataSource instanceof DataSourceWithCache) {
                Assertions.assertEquals("There are uncommitted keys", e.getMessage());
            } else {
                Assertions.fail(e.getMessage());
            }
        }
    }

    @ParameterizedTest(name = "{1}, flush = {2}")
    @ArgumentsSource(DatasourceArgumentsProvider.class)
    void getNull(KeyValueDataSource keyValueDataSource, String className, boolean withFlush) {
        Assertions.assertThrows(NullPointerException.class, () -> keyValueDataSource.get(null));
    }

    @ParameterizedTest(name = "{1}, flush = {2}")
    @ArgumentsSource(DatasourceArgumentsProvider.class)
    void delete(KeyValueDataSource keyValueDataSource, String className, boolean withFlush) {
        byte[] randomKey = TestUtils.randomBytes(20);
        byte[] randomValue = TestUtils.randomBytes(20);

        keyValueDataSource.put(randomKey, randomValue);
        keyValueDataSource.delete(randomKey);
        if (withFlush) {
            keyValueDataSource.flush();
        }
        MatcherAssert.assertThat(keyValueDataSource.get(randomKey), is(nullValue()));
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
            MatcherAssert.assertThat(keyValueDataSource.get(updatedValue.getKey().getData()), is(updatedValue.getValue()));
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
            MatcherAssert.assertThat(keyValueDataSource.get(updatedValue.getKey().getData()), is(nullValue()));
        }
    }

    @ParameterizedTest(name = "{1}, flush = {2}")
    @ArgumentsSource(DatasourceArgumentsProvider.class)
    void putNullValue(KeyValueDataSource keyValueDataSource, String className, boolean withFlush) {
        byte[] randomKey = TestUtils.randomBytes(20);
        Assertions.assertThrows(RuntimeException.class, () -> keyValueDataSource.put(randomKey, null)); ;
    }

    @ParameterizedTest(name = "{1}, flush = {2}")
    @ArgumentsSource(DatasourceArgumentsProvider.class)
    void updateBatchWithNulls(KeyValueDataSource keyValueDataSource, String className, boolean withFlush) {
        Map<ByteArrayWrapper, byte[]> updatedValues = generateRandomValuesToUpdate(CACHE_SIZE);
        ByteArrayWrapper keyToNull = updatedValues.keySet().iterator().next();
        updatedValues.put(keyToNull, null);

        Set<ByteArrayWrapper> keysToRemove = Collections.emptySet();
        Assertions.assertThrows(IllegalArgumentException.class, () -> keyValueDataSource.updateBatch(updatedValues, keysToRemove));
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
